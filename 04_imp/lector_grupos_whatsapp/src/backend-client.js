import {ErrorServicio, BackendHttpError} from './errors.js';
import {zonedMilliseconds} from './datetime.js';
function invalid() { throw new ErrorServicio('RESULTADO_INVALIDO', 'El lote HTTP contiene datos no permitidos.'); }

export function validateBatch(batch) {
  if (!batch || typeof batch !== 'object' || Array.isArray(batch) || Object.keys(batch).sort().join(',') !== 'avisos,lote') invalid();
  if (zonedMilliseconds(batch.lote) === null || !Array.isArray(batch.avisos) || batch.avisos.length > 1000) invalid();
  for (const item of batch.avisos) {
    if (!item || typeof item !== 'object' || Array.isArray(item)) invalid();
    const keys = Object.keys(item);
    if (keys.some(x => !['surtidor','combustible','estado','fila','fecha'].includes(x))) invalid();
    if (typeof item.surtidor !== 'string' || !item.surtidor.trim() || item.surtidor.length > 256 ||
        typeof item.combustible !== 'string' || !item.combustible.trim() || item.combustible.length > 256) invalid();
    if (!['DISPONIBLE','AGOTADO'].includes(item.estado)) invalid();
    if (item.fila !== undefined && (typeof item.fila !== 'string' || !item.fila.trim() || item.fila.length > 128)) invalid();
    if (zonedMilliseconds(item.fecha) === null) invalid();
  }
  return batch;
}
export class BackendClient {
  constructor(config, {fetchImpl = globalThis.fetch, timeoutMs = 10000} = {}) {
    if (typeof fetchImpl !== 'function') throw new TypeError('Se requiere fetch.');
    if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || timeoutMs > 120000) throw new RangeError('Timeout inválido.');
    this.config = config; this.fetch = fetchImpl; this.timeoutMs = timeoutMs;
  }
  async #request(method, path, payload) {
    const headers = {Accept: 'application/json', [this.config.serviceAuthHeader]: `${this.config.serviceTokenPrefix}${this.config.serviceToken}`};
    if (payload !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await this.fetch(`${this.config.backendUrl}${path}`, {
        method, headers, ...(payload === undefined ? {} : {body: JSON.stringify(payload)}),
        signal: AbortSignal.timeout(this.timeoutMs), redirect: 'error'
      });
    } catch { throw new ErrorServicio('BACKEND_CONEXION', 'No se pudo conectar con el backend.'); }
    if (!response.ok) throw new BackendHttpError(response.status);
    if (method === 'POST' && response.status === 204) return null;
    let json;
    try { json = await response.json(); }
    catch { throw new ErrorServicio('BACKEND_FORMATO', 'El backend no devolvió JSON válido.'); }
    if (!json || typeof json !== 'object' || Array.isArray(json)) {
      throw new ErrorServicio('BACKEND_FORMATO', 'El backend devolvió una estructura inválida.');
    }
    return json;
  }
  getSurtidores() { return this.#request('GET', '/monitoreo/surtidores'); }
  postResultados(batch) { return this.#request('POST', '/monitoreo/resultados', validateBatch(batch)); }
}
