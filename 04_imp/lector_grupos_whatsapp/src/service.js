import {MessageStore} from './store.js';
import {BackendClient} from './backend-client.js';
import {createLogger} from './logger.js';
import {ErrorServicio} from './errors.js';

export class WhatsappService {
  constructor(config, {store, backend, logger = createLogger(), clock = Date.now,
    schedule = (callback, delay) => setInterval(callback, delay), unschedule = clearInterval,
    scheduleExpiry = (callback, delay) => setTimeout(callback, delay), unscheduleExpiry = clearTimeout} = {}) {
    this.config = config; this.clock = clock; this.logger = logger;
    this.store = store ?? new MessageStore(config.sqlitePath, {clock}); this.ownsStore = !store;
    this.backend = backend ?? new BackendClient(config);
    this.schedule = schedule; this.unschedule = unschedule;
    this.scheduleExpiry = scheduleExpiry; this.unscheduleExpiry = unscheduleExpiry;
    this.interval = null; this.expiry = null; this.closed = false;
  }
  #maintenance() {
    const caducados = this.store.expireOldPending();
    const depurados = this.store.purgeFinished();
    this.logger.event('MANTENIMIENTO', {caducados, depurados});
  }
  start() {
    if (this.closed) throw new ErrorServicio('SERVICIO_CERRADO', 'El servicio está cerrado.');
    if (this.interval !== null) return;
    this.#maintenance();
    this.#armExpiry();
    this.interval = this.schedule(() => {
      try { this.#maintenance(); this.#armExpiry(); }
      catch { this.logger.event('ERROR_MANTENIMIENTO'); }
    }, this.config.maintenanceIntervalMs);
    this.logger.event('SERVICIO_INICIADO');
  }
  ingestText(message) {
    if (!message || !this.config.allowedGroupIds.includes(message.group)) {
      return {inserted: false, status: 'IGNORADO'};
    }
    const result = this.store.savePending(message);
    if (this.interval !== null && result.inserted) this.#armExpiry();
    return result;
  }
  #armExpiry() {
    if (this.expiry !== null) { this.unscheduleExpiry(this.expiry); this.expiry = null; }
    const due = this.store.nextExpirationMs();
    if (due === null || this.closed) return;
    const delay = Math.max(1, Math.min(2147483647, due - this.clock()));
    this.expiry = this.scheduleExpiry(() => {
      this.expiry = null;
      try { this.#maintenance(); }
      catch { this.logger.event('ERROR_MANTENIMIENTO'); }
      if (!this.closed) this.#armExpiry();
    }, delay);
  }
  stop() {
    if (this.closed) return;
    this.closed = true;
    if (this.interval !== null) { this.unschedule(this.interval); this.interval = null; }
    if (this.expiry !== null) { this.unscheduleExpiry(this.expiry); this.expiry = null; }
    if (this.ownsStore) this.store.close();
    this.logger.event('SERVICIO_DETENIDO');
  }
}
