import {MessageStore} from './store.js';
import {BackendClient} from './backend-client.js';
import {createLogger} from './logger.js';
import {T091Error} from './errors.js';
/** Base T-091: no conecta WhatsApp, no ejecuta LLM, no emite POST por sí misma. */
export class WhatsappService {
  constructor(config,{store,backend,logger=createLogger(),clock=Date.now,
    schedule=(callback,delay)=>setInterval(callback,delay),unschedule=clearInterval,
    scheduleExpiry=(callback,delay)=>setTimeout(callback,delay),unscheduleExpiry=clearTimeout}={}) {
    this.config=config;this.clock=clock;this.logger=logger;
    this.store=store??new MessageStore(config.sqlitePath,{clock});this.ownsStore=!store;
    this.backend=backend??new BackendClient(config); // se expone para invocación explícita, nunca automática
    this.schedule=schedule;this.unschedule=unschedule;
    this.scheduleExpiry=scheduleExpiry;this.unscheduleExpiry=unscheduleExpiry;
    this.interval=null;this.expiry=null;this.closed=false;
  }
  start() {
    if (this.closed) throw new T091Error('SERVICIO_CERRADO','El servicio está cerrado.');
    if (this.interval!==null) return;
    this.logger.event('MANTENIMIENTO',{caducados:this.store.expireOldPending()});
    this.#armExpiry();
    this.interval=this.schedule(()=>{
      try {this.logger.event('MANTENIMIENTO',{caducados:this.store.expireOldPending()});this.#armExpiry();}
      catch {this.logger.event('ERROR_MANTENIMIENTO');}
    },this.config.pollIntervalMs);
    this.logger.event('SERVICIO_INICIADO');
  }
  ingestText(message) {
    if (!message || !this.config.allowedGroupIds.includes(message.group)) throw new T091Error('GRUPO_NO_AUTORIZADO','El grupo no está autorizado.');
    const result=this.store.savePending(message);
    if (this.interval!==null && result.inserted) this.#armExpiry();
    return result;
  }
  #armExpiry() {
    if (this.expiry!==null) {this.unscheduleExpiry(this.expiry);this.expiry=null;}
    const due=this.store.nextExpirationMs();
    if (due===null || this.closed) return;
    const delay=Math.max(1,Math.min(2147483647,due-this.clock()));
    this.expiry=this.scheduleExpiry(()=>{
      this.expiry=null;
      try {this.logger.event('MANTENIMIENTO',{caducados:this.store.expireOldPending()});}
      catch {this.logger.event('ERROR_MANTENIMIENTO');}
      if (!this.closed) this.#armExpiry();
    },delay);
  }
  stop() {
    if (this.closed) return;
    this.closed=true;
    if (this.interval!==null) {this.unschedule(this.interval);this.interval=null;}
    if (this.expiry!==null) {this.unscheduleExpiry(this.expiry);this.expiry=null;}
    if (this.ownsStore) this.store.close();
    this.logger.event('SERVICIO_DETENIDO');
  }
}
