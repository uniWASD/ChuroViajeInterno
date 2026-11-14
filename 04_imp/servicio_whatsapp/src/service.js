import {MessageStore} from './store.js';
import {BackendClient} from './backend-client.js';
import {GeminiClient} from './gemini-client.js';
import {createLogger} from './logger.js';
import {ErrorServicio, BackendHttpError} from './errors.js';
import {validateCatalog} from './catalog.js';
import {systemPrompt, outputSchema, userContent} from './llm-prompt.js';
import {interpretOutput} from './analysis.js';

/** El contrato acepta hasta 1000 avisos por lote; un análisis más grande se entrega en varios. */
export const MAX_AVISOS_POR_LOTE = 1000;
/** Mensajes por llamada al LLM. Con los volúmenes medidos en el issue #10 un ciclo nunca llega a esto. */
export const MAX_MENSAJES_POR_LOTE = 500;

const TARIJA_MS = -4 * 60 * 60 * 1000;
/** La fecha del lote en la hora de Bolivia, como en el contrato. */
export function horaTarija(ms) {
  return new Date(ms + TARIJA_MS).toISOString().replace(/\.\d{3}Z$/, '-04:00');
}

/**
 * Servicio de WhatsApp/LLM (CU-06). Guarda los mensajes de texto de los grupos autorizados, descarta los que pasan
 * 30 minutos sin analizar y, cada 5 minutos, analiza los pendientes con el LLM y entrega el resultado al backend.
 */
export class WhatsappService {
  constructor(config, {store, backend, llm, logger = createLogger(), clock = Date.now,
    schedule = (callback, delay) => setInterval(callback, delay), unschedule = clearInterval,
    scheduleExpiry = (callback, delay) => setTimeout(callback, delay), unscheduleExpiry = clearTimeout,
    scheduleAnalysis = (callback, delay) => setInterval(callback, delay), unscheduleAnalysis = clearInterval} = {}) {
    this.config = config; this.clock = clock; this.logger = logger;
    this.store = store ?? new MessageStore(config.sqlitePath, {clock}); this.ownsStore = !store;
    this.backend = backend ?? new BackendClient(config);
    // Sin clave del LLM no hay análisis: los mensajes vencen a los 30 minutos sin salir de la máquina.
    this.llm = llm !== undefined ? llm : (config.llmApiKey
      ? new GeminiClient({apiKey: config.llmApiKey, model: config.llmModel, baseUrl: config.llmBaseUrl}) : null);
    this.schedule = schedule; this.unschedule = unschedule;
    this.scheduleExpiry = scheduleExpiry; this.unscheduleExpiry = unscheduleExpiry;
    this.scheduleAnalysis = scheduleAnalysis; this.unscheduleAnalysis = unscheduleAnalysis;
    this.interval = null; this.expiry = null; this.analysis = null; this.closed = false;
    this.running = null; this.catalog = null;
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
    if (this.llm) {
      this.analysis = this.scheduleAnalysis(() => { this.analyzeCycle(); }, this.config.analysisIntervalMs);
    } else {
      this.logger.event('ANALISIS_DESACTIVADO');
    }
    this.logger.event('SERVICIO_INICIADO');
  }
  ingestText(message) {
    // RN-06.1: ignorar sin error el tráfico habitual de grupos no autorizados.
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

  /**
   * Un ciclo de análisis (CU-06 pasos 2 a 4). Nunca se superpone con otro: si el anterior sigue, devuelve el mismo.
   * Nunca lanza: las fallas quedan en el registro y lo pendiente se reintenta en el ciclo siguiente.
   */
  analyzeCycle() {
    if (this.closed) return Promise.resolve();
    if (this.running) return this.running;
    this.running = this.#cycle().catch(() => { this.logger.event('ERROR_ANALISIS'); })
      .finally(() => { this.running = null; });
    return this.running;
  }
  async #cycle() {
    // RN-06.11: primero se descarta lo vencido, para no mandarlo al LLM.
    this.#maintenance();
    // RN-06.19: lo que no llegó en un ciclo anterior se reenvía antes de analizar más.
    if (!(await this.#sendSaved())) return;
    const pending = this.store.listPending(MAX_MENSAJES_POR_LOTE);
    if (pending.length === 0) return; // RN-06.5: sin pendientes no se llama al LLM
    const catalog = await this.#catalog();
    if (!catalog || this.closed) return;
    let output;
    try {
      output = await this.llm.analyze({system: systemPrompt(catalog), user: userContent(pending), schema: outputSchema(catalog)});
    } catch (error) {
      // RN-06.10: los mensajes siguen pendientes y se reintentan en el ciclo siguiente.
      this.logger.event('LLM_FALLO', {codigo: error?.code, mensajes: pending.length});
      return;
    }
    let result;
    try { result = interpretOutput(output, pending, catalog); }
    catch (error) {
      this.logger.event('LLM_FALLO', {codigo: error?.code, mensajes: pending.length});
      return;
    }
    if (this.closed) return;
    const lote = horaTarija(this.clock());
    const lotes = [];
    for (let i = 0; i < result.avisos.length; i += MAX_AVISOS_POR_LOTE) {
      lotes.push({lote, avisos: result.avisos.slice(i, i + MAX_AVISOS_POR_LOTE)});
    }
    // RN-06.12 y RN-06.19: los mensajes quedan sin texto y el resultado queda guardado, en la misma transacción.
    this.store.finishBatch(pending.map(m => m.id), lotes);
    this.logger.event('LOTE_ANALIZADO', {mensajes: pending.length, avisos: result.avisos.length,
      descartados: result.descartados, descartadosValidador: result.porValidador});
    // RN-06.18: se entrega apenas termina el análisis.
    await this.#sendSaved();
  }
  async #catalog() {
    try {
      this.catalog = validateCatalog(await this.backend.getSurtidores());
    } catch (error) {
      // Con el backend caído se analiza con el último catálogo que se leyó; sin ninguno, se espera.
      this.logger.event('CATALOGO_NO_DISPONIBLE', {codigo: error?.code, ...(error instanceof BackendHttpError ? {estadoHttp: error.status} : {})});
    }
    return this.catalog;
  }
  /** Envía los resultados guardados, del más viejo al más nuevo. Devuelve falso si el backend no los está recibiendo. */
  async #sendSaved() {
    const vencidos = this.store.purgeOldResults();
    if (vencidos > 0) this.logger.event('RESULTADO_VENCIDO', {resultados: vencidos});
    for (const saved of this.store.listResults()) {
      if (this.closed) return false;
      try {
        const answer = await this.backend.postResultados({lote: saved.lote, avisos: saved.avisos});
        if (this.closed) return false; // se reenvía al volver a arrancar; el backend tolera repetidos (RN-06.17)
        this.store.deleteResult(saved.id);
        this.logger.event('RESULTADO_ENVIADO', {avisos: saved.avisos.length, aceptados: answer?.aceptados,
          descartadosBackend: Array.isArray(answer?.descartados) ? answer.descartados.length : undefined});
      } catch (error) {
        if (this.closed) return false;
        const status = error instanceof BackendHttpError ? error.status : undefined;
        if (status === 400 || status === 422 || error?.code === 'RESULTADO_INVALIDO') {
          // Reenviarlo lo atascaría para siempre: se registra y se suelta.
          this.store.deleteResult(saved.id);
          this.logger.event('RESULTADO_RECHAZADO', {codigo: error?.code, estadoHttp: status, avisos: saved.avisos.length});
          continue;
        }
        this.logger.event(status === 401 || status === 403 ? 'RESULTADO_ESPERA_CREDENCIAL' : 'RESULTADO_GUARDADO',
          {codigo: error?.code, estadoHttp: status, avisos: saved.avisos.length});
        return false;
      }
    }
    return true;
  }
  stop() {
    if (this.closed) return;
    this.closed = true;
    if (this.interval !== null) { this.unschedule(this.interval); this.interval = null; }
    if (this.expiry !== null) { this.unscheduleExpiry(this.expiry); this.expiry = null; }
    if (this.analysis !== null) { this.unscheduleAnalysis(this.analysis); this.analysis = null; }
    if (this.ownsStore) this.store.close();
    this.logger.event('SERVICIO_DETENIDO');
  }
}
