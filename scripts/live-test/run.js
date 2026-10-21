#!/usr/bin/env node
'use strict'
// Live permission test: boot a Bukkit-family server in a throwaway dir, join headless bots and
// run declarative scenarios. See docs/development/testing.md.
process.noDeprecation = true // minecraft-protocol's dependencies still load punycode
const fs = require('fs')
const os = require('os')
const path = require('path')
const crypto = require('crypto')
const mc = require('minecraft-protocol')
const minecraftData = require('minecraft-data')
const { Server } = require('./lib/server')
const { ScenarioRunner, cmpVersion } = require('./lib/scenario')

const USAGE = `usage: node run.js --server-jar <jar> --scenario <file.json> [--scenario ...] [options]

  --server-jar <jar>       Paper, Spigot, Purpur or Pufferfish server jar (1.8.8 - 26.x)
  --java <bin>             Java binary for the server (default: java on PATH)
  --plugin <jar>           VanillaPermissionsPlugin jar (omit to run without it)
  --luckperms <jar>        LuckPerms-Bukkit jar; selects the luckperms backend
  --extra-plugin <jar>     any other plugin to install, e.g. ViaVersion (repeatable)
  --client-version <ver>   protocol version for the bot (default: the server's own)
  --java-opts "<flags>"    extra JVM flags, e.g. a trust store
  --cache-dir <dir>        keeps Paper's patched jars and LuckPerms' libraries between runs
                           (default: \${XDG_CACHE_HOME:-~/.cache}/vpp-live-test)
  --work-dir <dir>         where server dirs are created (default: <cache-dir>/runs; on disk,
                           since /tmp is often RAM-backed)
  --keep                   keep the server dirs; by default they are deleted after each boot,
                           keeping <dir>.console.log when a check failed
  --boot-timeout <s>       default 300; maximum time allowed for server startup
  --time-scale <x>         multiply every step timeout (default 1; use 3+ on slow or shared hosts)
  --report <file.json>     also write the results as JSON
  --verbose                print bot chat and console commands as they happen
`

function parseArgs (argv) {
  const o = { scenarios: [], extraPlugins: [], javaOpts: [] }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const val = () => { if (i + 1 >= argv.length) usage(`${a} needs a value`); return argv[++i] }
    switch (a) {
      case '--server-jar': o.serverJar = val(); break
      case '--java': o.java = val(); break
      case '--plugin': o.plugin = val(); break
      case '--luckperms': o.luckperms = val(); break
      case '--extra-plugin': o.extraPlugins.push(val()); break
      case '--scenario': o.scenarios.push(val()); break
      case '--client-version': o.clientVersion = val(); break
      case '--java-opts': o.javaOpts.push(...val().split(/\s+/).filter(Boolean)); break
      case '--cache-dir': o.cacheDir = val(); break
      case '--work-dir': o.workDir = val(); break
      case '--keep': o.keep = true; break
      case '--boot-timeout': o.bootTimeout = Number(val()); break
      case '--time-scale': o.timeScale = Number(val()); break
      case '--report': o.report = val(); break
      case '--verbose': o.verbose = true; break
      case '-h': case '--help': process.stdout.write(USAGE); process.exit(0); break
      default: usage(`unknown argument ${a}`)
    }
  }
  if (!o.serverJar || !o.scenarios.length) usage('--server-jar and --scenario are required')
  o.backend = o.luckperms ? 'luckperms' : 'bukkit'
  for (const f of [o.serverJar, o.plugin, o.luckperms, ...o.extraPlugins, ...o.scenarios]) {
    if (f && !fs.existsSync(f)) usage(`no such file: ${f}`)
  }
  return o
}

function usage (msg) {
  process.stderr.write(`${msg}\n\n${USAGE}`)
  process.exit(2)
}

const log = msg => process.stdout.write(`[live-test] ${msg}\n`)
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

function freePort () {
  return new Promise((resolve, reject) => {
    const srv = require('net').createServer()
    srv.listen(0, '127.0.0.1', () => { const p = srv.address().port; srv.close(() => resolve(p)) })
    srv.on('error', reject)
  })
}

/** The server prints "Done" slightly before it answers status pings (as minecraft-protocol's own tests note). */
async function pingUntilReady (port, tries = 40) {
  let last
  for (let i = 0; i < tries; i++) {
    try { return await mc.ping({ host: '127.0.0.1', port, closeTimeout: 3000 }) } catch (e) { last = e; await delay(250) }
  }
  throw new Error(`server did not answer status pings: ${last && last.message}`)
}

/** Map the server's protocol number to a version minecraft-protocol can speak, if any. */
function clientVersionFor (protocol) {
  const known = (minecraftData.postNettyVersionsByProtocolVersion.pc[protocol] || []).map(v => v.minecraftVersion)
  return known.find(v => mc.supportedVersions.includes(v)) ||
    known.find(v => minecraftData(v) && minecraftData(v).protocol) || null
}

function sha256 (file) { return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex') }

function cacheRoot (o) {
  return o.cacheDir || path.join(process.env.XDG_CACHE_HOME || path.join(os.homedir(), '.cache'), 'vpp-live-test')
}

/** Paper's paperclip downloads the vanilla jar and libraries on first start; reuse them. */
function cachePaths (o, workDir) {
  const root = cacheRoot(o)
  const key = sha256(o.serverJar).slice(0, 16)
  const pairs = ['cache', 'libraries', 'versions'].map(d => [path.join(root, 'server', key, d), path.join(workDir, d)])
  if (o.luckperms) pairs.push([path.join(root, 'luckperms-libs', sha256(o.luckperms).slice(0, 16)), path.join(workDir, 'plugins', 'LuckPerms', 'libs')])
  return pairs
}

/** Link the cached dirs in (they are only read once populated); missing ones are filled after the run. */
function restoreCache (pairs) {
  for (const [cached, live] of pairs) {
    if (!fs.existsSync(cached)) continue
    fs.mkdirSync(path.dirname(live), { recursive: true })
    fs.symlinkSync(cached, live)
  }
}

function saveCache (pairs) {
  for (const [cached, live] of pairs) {
    if (fs.existsSync(cached) || !fs.existsSync(live)) continue
    const tmp = `${cached}.tmp-${process.pid}`
    fs.cpSync(live, tmp, { recursive: true })
    fs.mkdirSync(path.dirname(cached), { recursive: true })
    try { fs.renameSync(tmp, cached) } catch { fs.rmSync(tmp, { recursive: true, force: true }) }
  }
}

function loadScenarios (files) {
  return files.map(f => {
    const s = JSON.parse(fs.readFileSync(f, 'utf8'))
    s.name = s.name || path.basename(f, '.json')
    s.file = f
    return s
  })
}

/** Scenarios that need the same server settings share one boot. */
function groupByServer (scenarios) {
  const groups = []
  for (const s of scenarios) {
    const key = JSON.stringify(s.server || {})
    const g = groups.find(x => x.key === key)
    if (g) g.scenarios.push(s)
    else groups.push({ key, server: s.server || {}, scenarios: [s] })
  }
  return groups
}

async function runGroup (o, group, index, rows) {
  const base = o.workDir || path.join(cacheRoot(o), 'runs')
  fs.mkdirSync(base, { recursive: true })
  const workDir = fs.mkdtempSync(path.join(base, `${index}-`))
  const rowsBefore = rows.length
  const port = await freePort()
  const plugins = [o.plugin, o.backend === 'luckperms' ? o.luckperms : null, ...o.extraPlugins].filter(Boolean)
  const server = new Server({
    workDir, jar: o.serverJar, javaBin: o.java || 'java', javaOpts: o.javaOpts, port,
    properties: group.server.properties || {}, files: group.server.files || {}, plugins,
    log: o.verbose ? log : () => {}
  })
  const pairs = cachePaths(o, workDir)
  const names = group.scenarios.map(s => s.name).join(', ')
  let booted = false
  let runner = null
  try {
    server.prepare()
    restoreCache(pairs)
    log(`booting ${path.basename(o.serverJar)} on port ${port} in ${workDir} (${names})`)
    await server.start((o.bootTimeout || 300) * 1000)
    const ping = await pingUntilReady(port)
    booted = true
    const protocol = ping.version.protocol
    const fromName = (String(ping.version.name).match(/\d+\.\d+(?:\.\d+)?/) || [])[0]
    const known = clientVersionFor(protocol)
    // The ping's name ("Paper 1.21.10") is exact; the protocol number can be shared (1.21.9/1.21.10).
    const mcVersion = fromName || known
    const clientVersion = o.clientVersion || known
    rows.push({ scenario: '(server)', check: 'boot', status: 'PASS', detail: `${ping.version.name} protocol ${protocol} in ${Math.round((Date.now() - server.startedAt) / 1000)} s` })
    if (!clientVersion) {
      rows.push({ scenario: '(server)', check: 'client', status: 'FAIL', detail: `minecraft-protocol ${require('minecraft-protocol/package.json').version} cannot speak protocol ${protocol}; pass --client-version and install ViaVersion + ViaBackwards with --extra-plugin (see testing.md)` })
      return
    }
    if (!mc.supportedVersions.includes(clientVersion)) log(`note: ${clientVersion} is not in minecraft-protocol's tested list; trying anyway`)
    if (o.clientVersion && cmpVersion(o.clientVersion, mcVersion) !== 0) log(`bot speaks ${o.clientVersion} to a ${mcVersion} server (needs ViaVersion/ViaBackwards on the server)`)
    if (o.backend === 'luckperms' && !server.lines.some(l => /Enabling LuckPerms/.test(l))) {
      const why = server.lines.find(l => /Could not load .*LuckPerms|UnsupportedClassVersionError.*luckperms/i.test(l))
      throw new Error(`LuckPerms did not enable${why ? `: ${why.trim()}` : ''} (LuckPerms 5.5.x needs Java 11+; use 5.4.158 on Java 8)`)
    }

    const software = /^folia/i.test(String(ping.version.name))
      ? 'folia'
      : /^spigot|^craftbukkit/i.test(String(ping.version.name)) ? 'spigot' : 'paper'
    runner = new ScenarioRunner({ server, backend: o.backend, plugin: Boolean(o.plugin), software, mcVersion, clientVersion, port, log, verbose: o.verbose, timeScale: o.timeScale })
    for (const s of group.scenarios) {
      log(`scenario ${s.name}`)
      await runner.run(s)
    }
  } catch (err) {
    if (runner) { rows.push(...runner.rows); runner = null }
    rows.push({ scenario: '(server)', check: booted ? 'run' : 'boot', status: 'FAIL', detail: err.message })
    const tail = server.lines.slice(-40).join('\n')
    if (tail) process.stdout.write(`--- last server console lines ---\n${tail}\n---\n`)
  } finally {
    if (runner) rows.push(...runner.rows)
    await server.stop().catch(() => server.kill())
    saveCache(pairs)
    if (o.keep) {
      log(`server dir kept: ${workDir}`)
    } else {
      // Cached dirs are symlinks; rmSync removes the links, not the cache.
      const failed = rows.slice(rowsBefore).some(r => r.status === 'FAIL')
      const consoleLog = path.join(workDir, 'console.out')
      if (failed && fs.existsSync(consoleLog)) {
        fs.copyFileSync(consoleLog, `${workDir}.console.log`)
        log(`server console kept: ${workDir}.console.log`)
      }
      fs.rmSync(workDir, { recursive: true, force: true })
    }
  }
}

function printTable (rows) {
  rows = rows.map(r => ({ ...r, detail: String(r.detail).replace(/\s*\n\s*/g, ' / ') }))
  const cols = ['scenario', 'check', 'status', 'detail']
  const clip = (s, n) => (s.length > n ? s.slice(0, n - 3) + '...' : s)
  const width = { scenario: 24, check: 52, status: 6, detail: 110 }
  const w = Object.fromEntries(cols.map(c => [c, Math.min(width[c], Math.max(c.length, ...rows.map(r => String(r[c]).length)))]))
  const line = r => cols.map(c => clip(String(r[c]), w[c]).padEnd(w[c])).join(' | ')
  process.stdout.write('\n' + line(Object.fromEntries(cols.map(c => [c, c.toUpperCase()]))) + '\n')
  process.stdout.write(cols.map(c => '-'.repeat(w[c])).join('-+-') + '\n')
  for (const r of rows) process.stdout.write(line(r) + '\n')
}

function stepSummary (o, rows, counts) {
  const file = process.env.GITHUB_STEP_SUMMARY
  if (!file) return
  const esc = s => String(s).replace(/\|/g, '\\|').replace(/\s*\n\s*/g, ' / ')
  const md = [`### Live test: ${path.basename(o.serverJar)} (${o.backend}${o.plugin ? ', plugin' : ''})`, '',
    `${counts.PASS} passed, ${counts.FAIL} failed, ${counts.SKIP} skipped`, '',
    '| Scenario | Check | Status | Detail |', '| --- | --- | --- | --- |',
    ...rows.map(r => `| ${esc(r.scenario)} | ${esc(r.check)} | ${r.status} | ${esc(r.detail)} |`), '']
  fs.appendFileSync(file, md.join('\n'))
}

async function main () {
  const o = parseArgs(process.argv.slice(2))
  const rows = []
  const groups = groupByServer(loadScenarios(o.scenarios))
  let stopping = false
  const onSignal = () => { if (stopping) process.exit(130); stopping = true }
  process.on('SIGINT', onSignal)
  process.on('SIGTERM', onSignal)
  for (const [i, g] of groups.entries()) {
    if (stopping) break
    await runGroup(o, g, i, rows)
  }
  printTable(rows)
  const counts = { PASS: 0, FAIL: 0, SKIP: 0 }
  for (const r of rows) counts[r.status]++
  const ok = counts.FAIL === 0 && rows.some(r => r.status === 'PASS' && r.scenario !== '(server)')
  process.stdout.write(`\nRESULT ${ok ? 'PASS' : 'FAIL'} passed=${counts.PASS} failed=${counts.FAIL} skipped=${counts.SKIP}\n`)
  if (o.report) fs.writeFileSync(o.report, JSON.stringify({ serverJar: path.basename(o.serverJar), backend: o.backend, plugin: Boolean(o.plugin), rows, ok }, null, 2))
  stepSummary(o, rows, counts)
  process.exit(ok ? 0 : 1)
}

main().catch(err => { console.error(err); process.exit(1) })
