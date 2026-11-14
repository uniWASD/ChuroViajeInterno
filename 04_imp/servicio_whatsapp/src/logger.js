/** Lista blanca estricta: jamás registrar cuerpos, remitentes, grupo ni credenciales. */
const EVENTS = new Set(['SERVICIO_INICIADO','SERVICIO_DETENIDO','MANTENIMIENTO',
  'ERROR_CONFIGURACION','ERROR_SQLITE','ERROR_MANTENIMIENTO','ERROR_INESPERADO',
  'WHATSAPP_CONECTADO','WHATSAPP_DESCONECTADO','WHATSAPP_REINTENTO',
  'WHATSAPP_SESION_CERRADA','WHATSAPP_CUENTA_INVALIDA','ERROR_LECTURA_WHATSAPP',
  'ANALISIS_DESACTIVADO','LOTE_ANALIZADO','LLM_FALLO','CATALOGO_NO_DISPONIBLE',
  'RESULTADO_ENVIADO','RESULTADO_GUARDADO','RESULTADO_RECHAZADO','RESULTADO_ESPERA_CREDENCIAL',
  'RESULTADO_VENCIDO','ERROR_ANALISIS']);
const METRICS = new Set(['caducados','pendientes','depurados','mensajes','avisos','descartados','descartadosValidador',
  'aceptados','descartadosBackend','estadoHttp','resultados']);
const CODES = new Set(['CONFIG_INVALIDA','AUTORIZACION_INVALIDA','SESION_INVALIDA','ESQUEMA_NO_PERMITIDO','SERVICIO_CERRADO','ERR_SQLITE_ERROR','ERR_SQLITE_CANTOPEN',
  'LLM_NO_DISPONIBLE','LLM_CUOTA','LLM_FORMATO','BACKEND_CONEXION','BACKEND_HTTP','BACKEND_FORMATO','CATALOGO_INVALIDO','RESULTADO_INVALIDO']);
const SYSTEM_CODE = /^E[A-Z0-9]+$/;
// Los únicos detalles registrables son frases fijas del código; nada externo.
const DETAILS = new Set([
  'Ruta externa de configuración inválida.', 'No se puede leer el archivo de configuración externa.',
  'El JSON de configuración debe estar fuera del servicio y del repositorio Git.',
  'El JSON externo no se puede leer o no es válido.', 'El JSON externo debe ser un objeto.',
  'ALLOWED_GROUP_IDS requiere identificadores sin espacios.', 'BACKEND_URL no es válida.',
  'BACKEND_URL contiene componentes no permitidos.', 'BACKEND_URL remoto requiere HTTPS.',
  'BACKEND_URL debe ser la URL base sin rutas.', 'SERVICE_AUTH_HEADER inválido.',
  'SERVICE_TOKEN_PREFIX debe terminar en espacio o estar vacío.', 'MAINTENANCE_INTERVAL_MS inválido.',
  'LLM_API_KEY debe ser una cadena.', 'SERVICE_TOKEN inválido.',
  'El esquema SQLite carece de columnas requeridas o incluye datos privados.',
  'El servicio está cerrado.',
  'POLL_INTERVAL_MS fue reemplazado por MAINTENANCE_INTERVAL_MS.',
  'Falta BACKEND_URL.', 'Falta SERVICE_TOKEN.', 'Falta ALLOWED_GROUP_IDS.',
  'Falta SERVICE_AUTH_HEADER.', 'Falta SQLITE_PATH.',
  'WHATSAPP_ENABLED debe ser true o false.', 'WHATSAPP_PHONE_NUMBER inválido.',
  'Falta WHATSAPP_SESSION_PATH.', 'WHATSAPP_AUTHORIZATIONS_FILE debe ser una ruta absoluta.',
  'WHATSAPP_RECONNECT_MS inválido.', 'WHATSAPP_SESSION_PATH no puede ser temporal.',
  'LLM_API_KEY inválido.', 'LLM_MODEL inválido.', 'LLM_BASE_URL no es válida.', 'ANALYSIS_INTERVAL_MS inválido.',
  'Falta configurar la cuenta, sesión o autorizaciones de WhatsApp.',
  'La sesión y los mensajes requieren archivos independientes.',
  'El registro externo de autorizaciones es inválido o incompleto.',
  'La sesión debe persistirse en un archivo privado.',
  'El directorio de sesión debe ser privado.'
]);
export function createLogger(write = line => process.stdout.write(`${line}\n`)) {
  return Object.freeze({
    event(evento, data = {}) {
      if (!EVENTS.has(evento)) throw new TypeError('EVENTO_NO_PERMITIDO');
      const safe = {evento};
      for (const [key, value] of Object.entries(data)) {
        if (METRICS.has(key) && Number.isSafeInteger(value) && value >= 0) safe[key] = value;
        else if (key === 'codigo' && (CODES.has(value) ||
          (typeof value === 'string' && SYSTEM_CODE.test(value)))) safe[key] = value;
        else if (key === 'detalle' && DETAILS.has(value)) safe[key] = value;
      }
      write(JSON.stringify(safe));
    }
  });
}
