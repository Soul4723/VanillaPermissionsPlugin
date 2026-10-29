'use strict'
// Headless offline-mode player on top of minecraft-protocol (no mineflayer: we only need chat,
// commands, the command tree, game mode and teleports, on every protocol from 1.8 up).
const mc = require('minecraft-protocol')
const { flatten } = require('./text')

const GAME_MODES = ['survival', 'creative', 'adventure', 'spectator']

class Bot {
  constructor ({ host, port, username, version, log = () => {} }) {
    Object.assign(this, { host, port, username, version, log })
    this.messages = [] // { text }
    this.tree = null // latest declare_commands packet (1.13+)
    this.treeCount = 0
    this.gameMode = null
    this.teleports = 0
    this.ended = null // disconnect reason once the connection is gone
    this.client = null
    this.mcData = null
  }

  /** Resolve once the player has spawned; reject with the kick reason if refused. */
  connect (timeoutMs = 30000) {
    return new Promise((resolve, reject) => {
      const client = mc.createClient({
        host: this.host, port: this.port, username: this.username, auth: 'offline',
        version: this.version, hideErrors: true, checkTimeoutInterval: 120000
      })
      this.client = client
      this.mcData = require('minecraft-data')(client.version)
      let spawned = false
      const timer = setTimeout(() => fail(`no spawn within ${timeoutMs} ms`), timeoutMs)
      const fail = reason => {
        if (spawned) return
        spawned = true
        clearTimeout(timer)
        this.ended = this.ended || reason
        try { client.end() } catch {}
        reject(new Error(reason))
      }
      const kicked = packet => {
        this.ended = flatten(packet.reason)
        this.log(`${this.username} kicked: ${this.ended}`)
        fail(`kicked: ${this.ended}`)
      }
      client.on('disconnect', kicked)
      client.on('kick_disconnect', kicked)
      client.on('error', err => { this.log(`${this.username} error: ${err.message}`); fail(`error: ${err.message}`) })
      client.on('end', reason => { this.ended = this.ended || `connection ended (${reason || 'no reason'})`; fail(this.ended) })

      client.on('login', packet => {
        const gm = packet.worldState ? packet.worldState.gamemode : packet.gameMode
        this.gameMode = normaliseMode(gm)
      })
      client.on('respawn', packet => {
        const gm = packet.worldState ? packet.worldState.gamemode : (packet.gamemode ?? packet.gameMode)
        if (gm !== undefined) this.gameMode = normaliseMode(gm)
      })
      client.on('game_state_change', packet => {
        if (packet.reason === 'change_game_mode' || packet.reason === 3) this.gameMode = normaliseMode(packet.gameMode)
      })
      client.on('declare_commands', packet => { this.tree = packet; this.treeCount++ })
      client.on('position', packet => {
        this.teleports++
        if (packet.teleportId !== undefined && this.has('teleport_confirm')) client.write('teleport_confirm', { teleportId: packet.teleportId })
        if (!spawned) {
          spawned = true
          clearTimeout(timer)
          // 1.21.4+: the server waits for this (or a timeout) before treating the player as loaded.
          if (this.has('player_loaded')) client.write('player_loaded', {})
          resolve()
        }
      })

      const push = raw => {
        const text = flatten(raw)
        if (!text) return
        this.messages.push({ text })
        this.log(`${this.username} <- ${text}`)
      }
      client.on('chat', packet => push(packet.message)) // up to 1.18.2
      client.on('systemChat', m => push(m.formattedMessage)) // 1.19+, emitted by minecraft-protocol
      client.on('playerChat', m => push(m.formattedMessage || m.plainMessage))
    })
  }

  has (packetName, direction = 'toServer') {
    return Boolean(this.mcData.protocol.play[direction].types['packet_' + packetName])
  }

  get protocolVersion () { return this.mcData.version.version }

  /** Send a chat line or a /command, using the packet this protocol expects. */
  say (line) {
    this.log(`${this.username} -> ${line}`)
    if (typeof this.client._signedChat === 'function') this.client._signedChat(line) // 1.19+
    else this.client.write('chat', { message: line })
  }

  /** Right-click the block at `pos` with the main hand, on face `face` (1 = top), as the vanilla client does. */
  useBlock (pos, face = 1) {
    this.sequence = (this.sequence || 0) + 1
    const values = { hand: 0, location: pos, direction: face, cursorX: 0.5, cursorY: 0.5, cursorZ: 0.5, insideBlock: false, worldBorderHit: false, sequence: this.sequence }
    const fields = this.mcData.protocol.play.toServer.types.packet_block_place[1].map(f => f.name)
    this.log(`${this.username} -> right-click ${pos.x} ${pos.y} ${pos.z}`)
    this.client.write('block_place', Object.fromEntries(fields.map(f => [f, values[f] ?? 0])))
  }

  /** Ask the server for completions of `text` (a /command prefix). Resolves with match strings. */
  tabComplete (text, timeoutMs = 3000) {
    return new Promise((resolve, reject) => {
      const fields = this.mcData.protocol.play.toServer.types.packet_tab_complete[1].map(f => f.name)
      const id = Math.floor(Math.random() * 1e6)
      const packet = fields.includes('transactionId')
        ? { transactionId: id, text }
        : { text, assumeCommand: false, block: undefined, lookedAtBlock: undefined }
      const timer = setTimeout(() => { this.client.removeListener('tab_complete', on); reject(new Error(`no tab completion reply within ${timeoutMs} ms`)) }, timeoutMs)
      const on = reply => {
        if (reply.transactionId !== undefined && reply.transactionId !== id) return
        clearTimeout(timer)
        this.client.removeListener('tab_complete', on)
        resolve(reply.matches.map(m => (typeof m === 'string' ? m : m.match)).map(m => m.replace(/^\//, '')))
      }
      this.client.on('tab_complete', on)
      this.client.write('tab_complete', packet)
    })
  }

  /**
   * Does the latest command tree contain the literal path (e.g. ['gamemode', 'creative'])?
   * Returns null when the protocol has no command tree (before 1.13).
   */
  treeHas (pathParts) {
    if (!this.has('declare_commands', 'toClient')) return null
    if (!this.tree) return false
    const nodes = this.tree.nodes
    const name = n => (n.extraNodeData && n.extraNodeData.name) || n.name
    let node = nodes[this.tree.rootIndex]
    for (const part of pathParts) {
      const seen = new Set()
      while (node.children.length === 0 && node.redirectNode !== undefined && !seen.has(node.redirectNode)) {
        seen.add(node.redirectNode)
        node = nodes[node.redirectNode]
      }
      const next = node.children.map(i => nodes[i]).find(c => name(c) === part)
      if (!next) return false
      node = next
    }
    return true
  }

  end () {
    if (!this.client) return
    try { this.client.end('done') } catch {}
  }
}

function normaliseMode (gm) {
  if (typeof gm === 'string') return gm
  if (typeof gm === 'number') return GAME_MODES[Math.round(gm) & 3] ?? String(gm)
  return null
}

module.exports = { Bot }
