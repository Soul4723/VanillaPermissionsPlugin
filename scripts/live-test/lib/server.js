'use strict'
// Throwaway Bukkit-family server: write eula/properties, start the jar, read the console, stop.
// Same contract as PrismarineJS/node-minecraft-wrap (lib/wrap_server.js), but for Paper/Spigot jars.
const fs = require('fs')
const path = require('path')
const { spawn } = require('child_process')

const DONE = /\]: Done \(|\bDone \([0-9.,]+s\)!/

class Server {
  constructor ({ workDir, jar, javaBin, javaOpts = [], port, properties = {}, plugins = [], files = {}, log = () => {} }) {
    Object.assign(this, { workDir, jar, javaBin, javaOpts, port, properties, plugins, files, log })
    this.lines = []
    this.waiters = []
    this.proc = null
    this.exited = null
  }

  prepare () {
    fs.mkdirSync(path.join(this.workDir, 'plugins'), { recursive: true })
    // Symlinks, not copies: the jars are read-only inputs and copying them is slow on busy runners.
    fs.symlinkSync(path.resolve(this.jar), path.join(this.workDir, 'server.jar'))
    for (const p of this.plugins) fs.symlinkSync(path.resolve(p), path.join(this.workDir, 'plugins', path.basename(p)))
    fs.writeFileSync(path.join(this.workDir, 'eula.txt'), 'eula=true\n')
    const props = {
      'server-port': this.port,
      'server-ip': '127.0.0.1',
      'online-mode': false,
      'enforce-secure-profile': false,
      // Plain "flat" is read by every version: 1.8–1.15 match the name, 1.16+ parse it as minecraft:flat.
      'level-type': 'flat',
      'generate-structures': false,
      'spawn-protection': 0,
      'spawn-monsters': false,
      difficulty: 'peaceful', // slimes still spawn in flat worlds otherwise, and a dead bot cannot chat
      'spawn-animals': false,
      'spawn-npcs': false,
      'view-distance': 3,
      'simulation-distance': 3,
      'max-players': 10,
      'white-list': false, // 26.x generates white-list=true
      'enforce-whitelist': false,
      'use-native-transport': false,
      'network-compression-threshold': -1,
      'enable-query': false,
      'enable-rcon': false,
      'sync-chunk-writes': false,
      ...this.properties
    }
    fs.writeFileSync(path.join(this.workDir, 'server.properties'),
      Object.entries(props).map(([k, v]) => `${k}=${v}`).join('\n') + '\n')
    for (const [name, content] of Object.entries(this.files)) {
      const dest = path.join(this.workDir, name)
      fs.mkdirSync(path.dirname(dest), { recursive: true })
      fs.writeFileSync(dest, content)
    }
  }

  async start (timeoutMs) {
    const args = [
      '-Xmx1G', ...this.javaOpts,
      // Plain console on every version: old jline otherwise needs a TTY.
      '-Djline.terminal=jline.UnsupportedTerminal', '-Dterminal.jline=false', '-Dterminal.ansi=false',
      '-DIReallyKnowWhatIAmDoingISwear=true', '-Dpaper.disableChannelLimit=true',
      '-Dcom.mojang.eula.agree=true',
      '-jar', 'server.jar', 'nogui'
    ]
    this.spawn(this.javaBin, args)
    this.startedAt = Date.now()
    await this.waitFor(DONE, timeoutMs, 0)
    return true
  }

  spawn (cmd, argv) {
    this.log(`$ ${cmd} ${argv.join(' ')}`)
    this.proc = spawn(cmd, argv, { cwd: this.workDir, stdio: ['pipe', 'pipe', 'pipe'] })
    const out = fs.createWriteStream(path.join(this.workDir, 'console.out'), { flags: 'a' })
    let buf = ''
    const onData = chunk => {
      out.write(chunk)
      buf += chunk.toString('utf8')
      let i
      while ((i = buf.indexOf('\n')) >= 0) {
        // eslint-disable-next-line no-control-regex
        const line = buf.slice(0, i).replace(/\x1b\[[0-9;]*m/g, '').replace(/\r$/, '')
        buf = buf.slice(i + 1)
        this.lines.push(line)
        this.waiters = this.waiters.filter(w => !w.check(line))
      }
    }
    this.proc.stdout.on('data', onData)
    this.proc.stderr.on('data', onData)
    this.exited = new Promise(resolve => this.proc.on('exit', code => {
      out.end()
      this.proc = null
      this.exitCode = code
      for (const w of this.waiters) w.fail(new Error(`server exited (code ${code})`))
      this.waiters = []
      resolve(code)
    }))
  }

  /** Resolve with the first console line matching re, scanning from line index `from` (default: now). */
  waitFor (re, timeoutMs, from = this.lines.length) {
    for (let i = from; i < this.lines.length; i++) if (re.test(this.lines[i])) return Promise.resolve(this.lines[i])
    if (!this.proc) return Promise.reject(new Error('server is not running'))
    return new Promise((resolve, reject) => {
      const w = {
        check: line => { if (!re.test(line)) return false; clearTimeout(t); resolve(line); return true },
        fail: err => { clearTimeout(t); reject(err) }
      }
      const t = Number.isFinite(timeoutMs)
        ? setTimeout(() => { this.waiters = this.waiters.filter(x => x !== w); reject(new Error(`timed out after ${timeoutMs} ms waiting for ${re}`)) }, timeoutMs)
        : null
      this.waiters.push(w)
    })
  }

  mark () { return this.lines.length }
  console (command) {
    if (!this.proc) throw new Error('server is not running')
    this.log(`console> ${command}`)
    this.proc.stdin.write(command + '\n')
  }

  async stop (timeoutMs = 60000) {
    if (!this.proc) return
    const proc = this.proc
    try { this.console('stop') } catch {}
    const timer = setTimeout(() => proc.kill('SIGKILL'), timeoutMs)
    await this.exited
    clearTimeout(timer)
  }

  kill () { if (this.proc) this.proc.kill('SIGKILL') }
}

module.exports = { Server }
