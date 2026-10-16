/** Errores con códigos constantes. No adjuntar al Error tokens, texto o bodies. */
export class T091Error extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'T091Error';
    this.code = code;
  }
}
export class BackendHttpError extends T091Error {
  constructor(status) {
    super('BACKEND_HTTP', `Respuesta HTTP ${status} del backend.`);
    this.status = status;
  }
}
