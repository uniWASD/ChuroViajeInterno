import {ErrorServicio} from './errors.js';

export const GEMINI_URL = 'https://generativelanguage.googleapis.com';

/**
 * Cliente de la API de Gemini (D-17) con salida estructurada. Ante cualquier falla lanza un error sin el cuerpo de la
 * respuesta: el servicio deja los mensajes pendientes para el ciclo siguiente (RN-06.10).
 */
export class GeminiClient {
  constructor({apiKey, model, baseUrl = GEMINI_URL}, {fetchImpl = globalThis.fetch, timeoutMs = 60000} = {}) {
    if (typeof apiKey !== 'string' || !apiKey) throw new TypeError('Se requiere la clave del LLM.');
    if (typeof fetchImpl !== 'function') throw new TypeError('Se requiere fetch.');
    this.apiKey = apiKey; this.model = model; this.baseUrl = baseUrl;
    this.fetch = fetchImpl; this.timeoutMs = timeoutMs;
  }

  async analyze({system, user, schema}) {
    let response;
    try {
      response = await this.fetch(`${this.baseUrl}/v1beta/models/${encodeURIComponent(this.model)}:generateContent`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json', 'x-goog-api-key': this.apiKey},
        body: JSON.stringify({
          systemInstruction: {parts: [{text: system}]},
          contents: [{role: 'user', parts: [{text: user}]}],
          generationConfig: {temperature: 0, responseMimeType: 'application/json', responseJsonSchema: schema}
        }),
        signal: AbortSignal.timeout(this.timeoutMs), redirect: 'error'
      });
    } catch { throw new ErrorServicio('LLM_NO_DISPONIBLE', 'No se pudo conectar con el LLM.'); }
    if (response.status === 429) throw new ErrorServicio('LLM_CUOTA', 'El LLM rechazó la llamada por cuota.');
    if (!response.ok) throw new ErrorServicio('LLM_NO_DISPONIBLE', 'El LLM respondió con error.');
    let body;
    try { body = await response.json(); }
    catch { throw new ErrorServicio('LLM_FORMATO', 'El LLM no devolvió JSON válido.'); }
    const candidate = body?.candidates?.[0];
    const text = candidate?.content?.parts?.map(p => p?.text ?? '').join('');
    if (typeof text !== 'string' || !text.trim() || (candidate.finishReason && candidate.finishReason !== 'STOP')) {
      throw new ErrorServicio('LLM_FORMATO', 'El LLM no devolvió un resultado completo.');
    }
    try { return JSON.parse(text); }
    catch { throw new ErrorServicio('LLM_FORMATO', 'El LLM no devolvió JSON válido.'); }
  }
}
