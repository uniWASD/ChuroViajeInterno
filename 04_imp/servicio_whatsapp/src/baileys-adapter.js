import {createAuthState} from './auth-state.js';

export async function createBaileysAdapters() {
  const baileys = await import('@whiskeysockets/baileys');
  const qr = await import('qrcode-terminal');
  return {
    makeAuth: filename => createAuthState(filename, baileys),
    makeSocket: auth => baileys.default({auth, markOnlineOnConnect: false, syncFullHistory: false, logger: {
      level: 'silent', child() { return this; }, trace() {}, debug() {}, info() {}, warn() {}, error() {}, fatal() {}
    }}),
    showQR: value => (qr.default ?? qr).generate(value, {small: true}),
    loggedOutCode: baileys.DisconnectReason.loggedOut
  };
}
