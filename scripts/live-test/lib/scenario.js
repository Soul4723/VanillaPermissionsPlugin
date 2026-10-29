'use strict'
// Runs one scenario (a list of steps) against a booted server and returns result rows.
const crypto = require('crypto')
const { Bot } = require('./bot')

// Messages that mean "the server refused this command", across 1.8–26.x, Spigot and Paper.
const DENIED = new RegExp([
  'Unknown or incomplete command',
  'Unknown command', // Spigot/CraftBukkit before 1.13 and Bukkit's own message
  'do(?: not|n.t) have permission', // Bukkit default message, our selector messages, Paper
  'Incorrect argument for command',
  'Selector not allowed', // Paper 1.21.3+ parse-time selector check
  'You can select at most \\d+ targets?', // this plugin's selector limit
  'could not be checked against selector permissions|cannot be checked against selector permissions',
  'permissions\\.requires'
].join('|'), 'i')

const delay = ms => new Promise(resolve => setTimeout(resolve, ms))

/** Offline-mode UUID, as the server computes it (UUID v3 of "OfflinePlayer:<name>"). */
function offlineUuid (name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name, 'utf8').digest()
  h[6] = (h[6] & 0x0f) | 0x30
  h[8] = (h[8] & 0x3f) | 0x80
  const x = h.toString('hex')
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`
}

/** Compare dotted versions; "26.1" > "1.21.11". */
function cmpVersion (a, b) {
  const pa = String(a).split('.').map(Number); const pb = String(b).split('.').map(Number)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0)
    if (d) return Math.sign(d)
  }
  return 0
}

/**
 * Why `cond` does not hold for this run, or null if it does.
 * cond: { backend, plugin, software, minVersion, maxVersion, not }
 * `software` is "spigot", "paper" or "folia", from the status ping's version name.
 * `not` is another cond; the step is skipped when every part of it holds.
 */
function unmet (cond, ctx) {
  if (!cond) return null
  if (cond.backend && cond.backend !== ctx.backend) return `needs backend ${cond.backend}`
  if (cond.plugin === true && !ctx.plugin) return 'needs the plugin'
  if (cond.plugin === false && ctx.plugin) return 'needs a run without the plugin'
  if (cond.minVersion && cmpVersion(ctx.mcVersion, cond.minVersion) < 0) return `needs MC >= ${cond.minVersion}`
  if (cond.maxVersion && cmpVersion(ctx.mcVersion, cond.maxVersion) > 0) return `needs MC <= ${cond.maxVersion}`
  if (cond.software && cond.software !== ctx.software) return `needs ${cond.software}`
  if (cond.not && unmet(cond.not, ctx) === null) return `not for ${JSON.stringify(cond.not)}`
  return null
}

class ScenarioRunner {
  constructor (ctx) {
    // ctx: { server, backend, plugin, software, mcVersion, clientVersion, port, log, verbose, timeScale }
    this.ctx = ctx
    this.scale = ctx.timeScale || 1
    this.server = ctx.server
    this.bots = new Map()
    this.rows = []
  }

  bot (name) {
    const b = name ? this.bots.get(name) : this.bots.values().next().value
    if (!b) throw new Error(`bot ${name || '(default)'} is not connected`)
    return b
  }

  ms (n) { return Math.round(n * this.scale) }

  async console (command, expect) {
    const mark = this.server.mark()
    this.server.console(command)
    if (!expect) { await delay(this.ms(300)); return null }
    return this.server.waitFor(new RegExp(expect, 'i'), this.ms(30000), mark)
  }

  /** Set (true/false) or unset (null) a node on a player through the permissions backend. */
  async perm (user, node, value) {
    if (this.ctx.backend !== 'luckperms') throw new Error('perm steps need --luckperms (bukkit backend has no per-player nodes; use permissions.yml)')
    const id = offlineUuid(user)
    const cmd = value === null || value === undefined
      ? `lp user ${id} permission unset ${node}`
      : `lp user ${id} permission set ${node} ${value}`
    await this.console(cmd, '\\[LP\\].*(Set|Unset|already|does not have)')
  }

  async meta (user, key, value) {
    if (this.ctx.backend !== 'luckperms') throw new Error('meta steps need --luckperms')
    const id = offlineUuid(user)
    const cmd = value === null || value === undefined ? `lp user ${id} meta unset ${key}` : `lp user ${id} meta set ${key} ${value}`
    await this.console(cmd, '\\[LP\\].*(Set|Unset|already|does not have)')
  }

  async op (user, on) {
    // "Made X a server operator" (1.13+), "Opped X" (1.8–1.12); "Nothing changed" (1.13+) or
    // "Could not (de-)op X" (1.8–1.12) if already in that state.
    const re = on ? `(Made ${user} a server operator|Opped ${user}|Nothing changed|Could not op ${user})` : `(Made ${user} no longer a server operator|De-opped ${user}|Nothing changed|Could not de-op ${user})`
    await this.console(`${on ? 'op' : 'deop'} ${user}`, re)
  }

  async connect (name, expectRefused) {
    const b = new Bot({ host: '127.0.0.1', port: this.ctx.port, username: name, version: this.ctx.clientVersion, log: this.ctx.verbose ? this.ctx.log : () => {} })
    try {
      await b.connect(this.ms(30000))
    } catch (e) {
      if (typeof expectRefused === 'string') {
        const ok = new RegExp(expectRefused, 'i').test(b.ended || e.message)
        return { pass: ok, detail: `refused: ${b.ended || e.message}` }
      }
      throw e
    }
    this.bots.set(name, b)
    // Player data survives between scenarios; start each scenario from survival.
    if (!this.fresh.has(name)) {
      this.fresh.add(name)
      await this.console(`gamemode survival ${name}`)
    }
    // Let the join settle (command tree, permissions attachment, LuckPerms login processing).
    await delay(this.ms(1500))
    if (expectRefused) return { pass: false, detail: 'joined, but expected to be refused' }
    return { pass: true, detail: `joined (protocol ${b.protocolVersion}, ${b.gameMode || 'mode ?'})` }
  }

  async disconnect (name) {
    const b = this.bot(name)
    b.end()
    this.bots.delete(b.username)
    await delay(this.ms(1000))
  }

  /** Run /command (or chat) as a bot and check the outcome. */
  async command (step) {
    const b = this.bot(step.as)
    return this.observe(b, step.expect || {}, () => b.say(step.command))
  }

  /** Right-click a block with the main hand, then check the outcome like a command. */
  async use (step) {
    const b = this.bot(step.as)
    if (!b.has('block_place')) return { skip: 'no block_place packet in this protocol' }
    const { x, y, z, face = 1 } = step.use
    return this.observe(b, step.expect || {}, () => b.useBlock({ x, y, z }, face))
  }

  /** Do `act`, then watch the bot's messages, game mode and teleports against `e`. */
  async observe (b, e, act) {
    const timeout = this.ms(3000)
    const from = b.messages.length
    const teleportsBefore = b.teleports
    act()
    const deadline = Date.now() + timeout
    const failures = []
    const newMessages = () => b.messages.slice(from).map(m => m.text)
    // Poll until every positive expectation holds; then (for "allowed") keep watching for a denial.
    const wants = () => {
      const msgs = newMessages()
      const denied = msgs.some(t => DENIED.test(t))
      const got = []
      if (e.outcome === 'denied') got.push(denied)
      if (e.outcome === 'allowed') got.push(!denied)
      if (e.chat) got.push(msgs.some(t => new RegExp(e.chat, 'i').test(t)))
      if (e.noChat) got.push(!msgs.some(t => new RegExp(e.noChat, 'i').test(t)))
      if (e.gamemode) got.push(b.gameMode === e.gamemode)
      if (e.teleported !== undefined) got.push((b.teleports > teleportsBefore) === e.teleported)
      return got.every(Boolean)
    }
    // Negative expectations (allowed = "no denial", noChat, teleported:false, unchanged gamemode)
    // can only be judged at the end of the window, so always wait at least `settleMs`.
    const settle = this.ms(e.settleMs ?? 1500)
    const start = Date.now()
    while (Date.now() < deadline) {
      if (Date.now() - start >= settle && wants()) break
      await delay(100)
    }
    const msgs = newMessages()
    const denied = msgs.find(t => DENIED.test(t))
    if (e.outcome === 'denied' && !denied) failures.push('expected a denial message')
    if (e.outcome === 'allowed' && denied) failures.push(`denied: "${denied}"`)
    if (e.chat && !msgs.some(t => new RegExp(e.chat, 'i').test(t))) failures.push(`no message matching /${e.chat}/`)
    if (e.noChat) { const m = msgs.find(t => new RegExp(e.noChat, 'i').test(t)); if (m) failures.push(`unexpected message "${m}"`) }
    if (e.gamemode && b.gameMode !== e.gamemode) failures.push(`game mode is ${b.gameMode}, expected ${e.gamemode}`)
    if (e.teleported !== undefined && (b.teleports > teleportsBefore) !== e.teleported) failures.push(e.teleported ? 'not teleported' : 'was teleported')
    const seen = msgs.length ? msgs.map(t => t.length > 90 ? t.slice(0, 87) + '...' : t).join(' | ') : '(no messages)'
    return { pass: failures.length === 0, detail: failures.length ? `${failures.join('; ')}; saw: ${seen}` : seen }
  }

  /** Command tree visibility: every `has` path present and every `lacks` path absent (1.13+). */
  async tree (step) {
    const b = this.bot(step.as)
    if (b.treeHas([]) === null) return { skip: 'no command tree before 1.13' }
    const { has = [], lacks = [] } = step.tree
    const timeout = this.ms(5000)
    const deadline = Date.now() + timeout
    let bad
    do {
      bad = [
        ...has.filter(p => !b.treeHas(p.split(' '))).map(p => `missing "${p}"`),
        ...lacks.filter(p => b.treeHas(p.split(' '))).map(p => `has "${p}"`)
      ]
      if (!bad.length) break
      await delay(200)
    } while (Date.now() < deadline)
    return { pass: bad.length === 0, detail: bad.length ? bad.join(', ') : `ok (tree #${b.treeCount})` }
  }

  /** Tab completion of a /command prefix: `has` entries completed, `lacks` entries not. */
  async tab (step) {
    const b = this.bot(step.as)
    const { text, has = [], lacks = [] } = step.tab
    const matches = await b.tabComplete(text, this.ms(3000))
    const bad = [
      ...has.filter(m => !matches.includes(m)).map(m => `missing "${m}"`),
      ...lacks.filter(m => matches.includes(m)).map(m => `offered "${m}"`)
    ]
    const shown = matches.slice(0, 8).join(', ') + (matches.length > 8 ? ', ...' : '')
    return { pass: bad.length === 0, detail: `${bad.length ? bad.join(', ') + '; ' : ''}got [${shown}]` }
  }

  label (step) {
    if (step.name) return step.name
    if (step.command) return `${step.as || 'bot'}: ${step.command}`
    if (step.use) return `${step.as || 'bot'}: right-click ${step.use.x} ${step.use.y} ${step.use.z}`
    const k = Object.keys(step).find(x => !['as', 'when', 'expect', 'name'].includes(x))
    return `${k} ${JSON.stringify(step[k])}`
  }

  async step (step) {
    if (step.console !== undefined) { const line = await this.console(step.console, step.expect); return line === null ? null : { pass: true, detail: line } }
    if (step.op) { await this.op(step.op, true); return null }
    if (step.deop) { await this.op(step.deop, false); return null }
    if (step.perm) { await this.perm(step.perm.user || this.bot().username, step.perm.node, step.perm.value); return null }
    if (step.meta) { await this.meta(step.meta.user || this.bot().username, step.meta.key, step.meta.value); return null }
    if (step.connect) return this.connect(step.connect, step.expect && step.expect.refused)
    if (step.disconnect) { await this.disconnect(step.disconnect); return null }
    if (step.wait) { await delay(this.ms(step.wait)); return null }
    if (step.command) return this.command(step)
    if (step.use) return this.use(step)
    if (step.tree) return this.tree(step)
    if (step.tab) return this.tab(step)
    if (step.log) {
      const line = this.server.lines.find(l => new RegExp(step.log, 'i').test(l))
      if (step.absent) return { pass: !line, detail: line ? `found: ${line}` : 'not in log' }
      return { pass: Boolean(line), detail: line || 'not in log' }
    }
    throw new Error(`unknown step ${JSON.stringify(step)}`)
  }

  /** Put the scenario's players back to a clean state: offline, not op, no LuckPerms data. */
  async reset (users) {
    for (const b of this.bots.values()) b.end()
    this.bots.clear()
    for (const u of users) {
      await this.op(u, false)
      if (this.ctx.backend === 'luckperms') await this.console(`lp user ${offlineUuid(u)} clear`, '\\[LP\\].*clear') // untranslated key on LuckPerms' first command
    }
  }

  async run (scenario) {
    this.fresh = new Set()
    const prefix = scenario.name
    const why = unmet(scenario.when, this.ctx)
    if (why) { this.rows.push({ scenario: prefix, check: '(whole scenario)', status: 'SKIP', detail: why }); return }
    try {
      await this.reset(scenario.players || [])
    } catch (err) {
      this.rows.push({ scenario: prefix, check: '(reset players)', status: 'FAIL', detail: err.message })
      return
    }
    let n = 0
    for (const step of scenario.steps) {
      n++
      const label = this.label(step)
      const skip = unmet(step.when, this.ctx)
      if (skip) { if (step.command || step.use || step.tree || step.tab || step.log || step.connect) this.rows.push({ scenario: prefix, check: label, status: 'SKIP', detail: skip }); continue }
      let r
      try {
        r = await this.step(step)
      } catch (err) {
        this.rows.push({ scenario: prefix, check: `#${n} ${label}`, status: 'FAIL', detail: err.message })
        this.rows.push({ scenario: prefix, check: '(remaining steps)', status: 'SKIP', detail: 'setup step failed' })
        break
      }
      if (!r) continue
      if (r.skip) this.rows.push({ scenario: prefix, check: label, status: 'SKIP', detail: r.skip })
      else this.rows.push({ scenario: prefix, check: label, status: r.pass ? 'PASS' : 'FAIL', detail: r.detail })
    }
    for (const b of this.bots.values()) b.end()
    this.bots.clear()
  }
}

module.exports = { ScenarioRunner, cmpVersion }
