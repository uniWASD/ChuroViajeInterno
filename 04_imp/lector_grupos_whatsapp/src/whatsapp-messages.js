import {createHash} from 'node:crypto';

function textFromContent(message) {
  for (let i = 0; i < 3; i++) {
    if (!message || typeof message !== 'object' || Array.isArray(message)) return null;
    const types = Object.keys(message).filter(k => k !== 'messageContextInfo' && k !== 'senderKeyDistributionMessage');
    if (types.length === 1 && types[0] === 'ephemeralMessage') {
      message = message.ephemeralMessage?.message;
      continue;
    }
    if (types.length !== 1) return null;
    if (types[0] === 'conversation') return message.conversation;
    if (types[0] === 'extendedTextMessage') return message.extendedTextMessage?.text;
    return null;
  }
  return null;
}

function timestampMillis(timestamp, clock) {
  let seconds = timestamp;
  if (typeof seconds === 'bigint') seconds = Number(seconds);
  else if (seconds && typeof seconds === 'object' && typeof seconds.toNumber === 'function') seconds = seconds.toNumber();
  if (!Number.isSafeInteger(seconds) || seconds < 0 || seconds * 1000 > 8640000000000000) return clock();
  return seconds * 1000;
}

export function extractGroupText(message, allowedGroups, clock = Date.now) {
  if (!message || message.key?.fromMe === true) return null;
  const group = message.key?.remoteJid;
  const messageId = message.key?.id;
  if (typeof group !== 'string' || !allowedGroups.has(group) || !group.endsWith('@g.us') ||
      typeof messageId !== 'string' || !messageId || messageId.length > 512) return null;
  const text = textFromContent(message.message);
  if (typeof text !== 'string' || !text.trim() || text.length > 100000) return null;
  const time = timestampMillis(message.messageTimestamp, clock);
  if (!Number.isFinite(time)) return null;
  const id = createHash('sha256').update(group).update('\0').update(messageId).digest('hex');
  return {id, group, date: new Date(time).toISOString(), text};
}
