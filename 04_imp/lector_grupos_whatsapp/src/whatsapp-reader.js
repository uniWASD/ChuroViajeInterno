import {extractGroupText} from './whatsapp-messages.js';
import {loadAuthorizations} from './authorizations.js';
import {ErrorServicio} from './errors.js';

export class WhatsappReader {
  constructor(config, service, {makeSocket, makeAuth, showQR = () => {}, clock = Date.now,
    schedule = setTimeout, unschedule = clearTimeout, logger = service.logger, loggedOutCode = 401} = {}) {
    if (typeof makeSocket !== 'function' || typeof makeAuth !== 'function') throw new TypeError('Faltan adaptadores de WhatsApp.');
    this.config = config;
    this.service = service;
    this.makeSocket = makeSocket;
    this.makeAuth = makeAuth;
    this.showQR = showQR;
    this.clock = clock;
    this.schedule = schedule;
    this.unschedule = unschedule;
    this.logger = logger;
    this.loggedOutCode = loggedOutCode;
    this.socket = null;
    this.auth = null;
    this.timer = null;
    this.running = false;
    this.connected = false;
    this.groups = null;
    this.generation = 0;
  }
  async start() {
    if (this.running) return;
    if (!this.config.whatsappPhoneNumber || !this.config.whatsappSessionPath || !this.config.whatsappAuthorizationsFile) {
      throw new ErrorServicio('CONFIG_INVALIDA', 'Falta configurar la cuenta, sesión o autorizaciones de WhatsApp.');
    }
    if (this.config.whatsappSessionPath === this.config.sqlitePath) {
      throw new ErrorServicio('CONFIG_INVALIDA', 'La sesión y los mensajes requieren archivos independientes.');
    }
    this.groups = loadAuthorizations(this.config.whatsappAuthorizationsFile, this.config.allowedGroupIds);
    this.auth = await this.makeAuth(this.config.whatsappSessionPath);
    this.running = true;
    await this.#connect();
  }
  async #connect() {
    if (!this.running) return;
    const generation = ++this.generation;
    this.connected = false;
    try {
      const socket = await this.makeSocket(this.auth.state);
      if (!this.running || generation !== this.generation) { socket?.ws?.close?.(); return; }
      this.socket = socket;
      socket.ev.on('creds.update', () => {
        try {
          Promise.resolve(this.auth.saveCreds()).catch(() => this.#disconnect(generation, true));
        } catch { this.#disconnect(generation, true); }
      });
      socket.ev.on('messages.upsert', event => {
        if (!this.running || !this.connected || generation !== this.generation || event?.type !== 'notify' || !Array.isArray(event.messages)) return;
        for (const message of event.messages ?? []) {
          const item = extractGroupText(message, this.groups, this.clock);
          if (!item) continue;
          try { this.service.ingestText(item); }
          catch { this.logger.event('ERROR_LECTURA_WHATSAPP'); }
        }
      });
      socket.ev.on('connection.update', update => {
        if (!this.running || generation !== this.generation) return;
        if (update?.qr) {
          try { this.showQR(update.qr); } catch { this.logger.event('ERROR_LECTURA_WHATSAPP'); }
        }
        if (update?.connection === 'open') {
          const ids = [socket.user?.id, this.auth.state.creds?.me?.id];
          const numbers = ids.filter(id => typeof id === 'string' && id.endsWith('@s.whatsapp.net'))
            .map(id => id.split('@')[0].split(':')[0]);
          if (!numbers.length || numbers.some(number => number !== this.config.whatsappPhoneNumber)) {
            this.logger.event('WHATSAPP_CUENTA_INVALIDA');
            this.stop();
            return;
          }
          this.connected = true;
          this.logger.event('WHATSAPP_CONECTADO');
        } else if (update?.connection === 'close') {
          const code = update.lastDisconnect?.error?.output?.statusCode;
          this.#disconnect(generation, code === this.loggedOutCode);
        }
      });
    } catch {
      this.#disconnect(generation, false);
    }
  }
  #disconnect(generation, terminal) {
    if (!this.running || generation !== this.generation) return;
    this.connected = false;
    this.generation++;
    this.socket?.ev?.removeAllListeners?.('connection.update');
    this.socket?.ev?.removeAllListeners?.('messages.upsert');
    this.socket?.ev?.removeAllListeners?.('creds.update');
    this.socket?.ws?.close?.();
    this.socket = null;
    this.logger.event(terminal ? 'WHATSAPP_SESION_CERRADA' : 'WHATSAPP_DESCONECTADO');
    if (terminal) { this.stop(); return; }
    this.timer = this.schedule(() => {
      this.timer = null;
      this.logger.event('WHATSAPP_REINTENTO');
      void this.#connect();
    }, this.config.whatsappReconnectMs);
  }
  stop() {
    if (!this.running && !this.auth) return;
    this.running = false;
    this.connected = false;
    this.generation++;
    if (this.timer !== null) this.unschedule(this.timer);
    this.timer = null;
    this.socket?.ev?.removeAllListeners?.('connection.update');
    this.socket?.ev?.removeAllListeners?.('messages.upsert');
    this.socket?.ev?.removeAllListeners?.('creds.update');
    this.socket?.ws?.close?.();
    this.socket = null;
    this.auth?.close?.();
    this.auth = null;
  }
}
