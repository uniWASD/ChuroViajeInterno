export class ErrorServicio extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ErrorServicio';
    this.code = code;
  }
}

export class BackendHttpError extends ErrorServicio {
  constructor(status) {
    super('BACKEND_HTTP', `Respuesta HTTP ${status} del backend.`);
    this.status = status;
  }
}
