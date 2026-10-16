/** Lista blanca: nunca guarda cuerpos HTTP, grupo, mensaje, autor ni tokens. */
const EVENTS = new Set(['SERVICIO_INICIADO','SERVICIO_DETENIDO','MANTENIMIENTO','ERROR_CONFIGURACION','ERROR_SQLITE','ERROR_MANTENIMIENTO']);
const METRICS = new Set(['caducados','pendientes']);
export function createLogger(write = line => process.stdout.write(`${line}\n`)) {
  return Object.freeze({
    event(evento, data = {}) {
      if (!EVENTS.has(evento)) throw new TypeError('EVENTO_NO_PERMITIDO');
      const safe = {evento};
      for (const [key, value] of Object.entries(data)) {
        if (METRICS.has(key) && Number.isSafeInteger(value) && value >= 0) safe[key] = value;
      }
      write(JSON.stringify(safe));
    }
  });
}
