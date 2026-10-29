'use strict'
// Chat component -> plain text. Handles JSON strings (1.8–1.20.2), plain objects and the
// simplified NBT form minecraft-protocol emits from 1.20.3. Translatable messages are rendered
// in English for the keys scenarios care about, and as `key(args)` otherwise, so scenario
// regexes can match either the English text or the translation key.
const nbt = require('prismarine-nbt')

const EN = {
  'command.unknown.command': 'Unknown or incomplete command, see below for error',
  'command.unknown.argument': 'Incorrect argument for command',
  'command.context.here': '<--[HERE]',
  'commands.gamemode.success.self': 'Set own game mode to %s',
  'commands.gamemode.success.other': "Set %s's game mode to %s",
  'commands.teleport.success.location.single': 'Teleported %s to %s, %s, %s',
  'commands.teleport.success.location.multiple': 'Teleported %s entities to %s, %s, %s',
  'commands.teleport.success.entity.single': 'Teleported %s to %s',
  'commands.time.set': 'Set the time to %s',
  'commands.time.query': 'The time is %s',
  'commands.op.success': 'Made %s a server operator',
  'commands.deop.success': 'Made %s no longer a server operator',
  'multiplayer.disconnect.server_full': 'The server is full!',
  'multiplayer.disconnect.not_whitelisted': 'You are not white-listed on this server!',
  'gameMode.survival': 'Survival Mode',
  'gameMode.creative': 'Creative Mode',
  'gameMode.adventure': 'Adventure Mode',
  'gameMode.spectator': 'Spectator Mode',
  'argument.entity.selector.not_allowed': 'Selector not allowed',
  'commands.help.failed': 'Unknown command or insufficient permissions'
}

function flatten (component) {
  if (component === undefined || component === null) return ''
  if (typeof component === 'string') {
    const s = component.trim()
    if (s.startsWith('{') || s.startsWith('[') || s.startsWith('"')) {
      try { return flatten(JSON.parse(s)) } catch {}
    }
    return stripLegacy(component)
  }
  if (typeof component === 'number' || typeof component === 'boolean') return String(component)
  if (Array.isArray(component)) return component.map(flatten).join('')
  if (component.type && component.value !== undefined && typeof component.type === 'string') {
    // Raw (unsimplified) NBT, e.g. a 1.20.3+ disconnect reason.
    try { return flatten(nbt.simplify(component)) } catch {}
  }
  let out = ''
  if (component[''] !== undefined) out += flatten(component['']) // NBT root text shorthand
  if (component.text !== undefined) out += stripLegacy(String(component.text))
  if (component.translate !== undefined) {
    const args = (component.with || component.args || []).map(flatten)
    const fmt = EN[component.translate] ?? component.fallback
    if (fmt !== undefined) {
      let i = 0
      out += fmt.replace(/%(?:(\d+)\$)?s/g, (_, n) => (n ? args[n - 1] : args[i++]) ?? '')
    } else {
      out += args.length ? `${component.translate}(${args.join(', ')})` : component.translate
    }
  }
  if (component.extra) out += component.extra.map(flatten).join('')
  return out
}

function stripLegacy (s) { return s.replace(/§./g, '') }

module.exports = { flatten }
