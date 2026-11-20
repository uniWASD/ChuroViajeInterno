/**
 * Smoke test REAL de Gemini para issue #59. Solo envía frases SINTÉTICAS,
 * sin WhatsApp, grupos, personas ni backend. Requiere LLM_API_KEY en el entorno.
 * No imprime la clave, prompt, texto ni respuesta completa del modelo.
 */
import {GeminiClient} from '../src/gemini-client.js';
import {validateCatalog} from '../src/catalog.js';
import {userContent, systemPrompt, outputSchema} from '../src/llm-prompt.js';
import {interpretOutput} from '../src/analysis.js';

if (!process.env.LLM_API_KEY) {
  process.stderr.write('GEMINI_SMOKE: BLOQUEADO (falta LLM_API_KEY).\n');
  process.exit(2);
}
const catalogo = validateCatalog({
  surtidores: [{nombre: 'Las Vegas', alias: ['vegas']}, {nombre: 'El Portillo', alias: ['portillo']}],
  combustibles: ['Gasolina', 'Diesel', 'Gasolina Premium', 'Diesel ULS'],
  filas: ['SIN_FILA', 'CORTA', 'MEDIA', 'LARGA']
});
const mensajes = [
  {id: 'sintetico-1', fecha: '2026-11-17T12:00:00-04:00', texto: 'ya hay gasolina en Las Vegas, fila corta'},
  {id: 'sintetico-2', fecha: '2026-11-17T12:01:00-04:00', texto: '¿alguien sabe si hay diésel en El Portillo?'},
  {id: 'sintetico-3', fecha: '2026-11-17T12:02:00-04:00', texto: 'llamen al 71234567, ya hay gasolina en Vegas'}
];
const cliente = new GeminiClient({apiKey: process.env.LLM_API_KEY,
  model: process.env.LLM_MODEL || 'gemini-3.1-flash-lite'});
try {
  const output = await cliente.analyze({system: systemPrompt(catalogo), user: userContent(mensajes),
    schema: outputSchema(catalogo, mensajes.length)});
  const result = interpretOutput(output, mensajes.map(m => ({...m, texto: m.texto})), catalogo);
  const valido = result.avisos.some(a => a.surtidor === 'Las Vegas' && a.combustible === 'Gasolina' &&
    a.estado === 'DISPONIBLE' && a.fila === 'CORTA' && a.fecha === mensajes[0].fecha);
  const preguntaExcluida = !result.avisos.some(a => a.surtidor === 'El Portillo');
  const passed = valido && preguntaExcluida;
  process.stdout.write(JSON.stringify({prueba: 'GEMINI_LIVE_ISSUE_59',
    resultado: passed ? 'APROBADO' : 'FALLIDO',
    avisosValidos: result.avisos.length, esquemaAceptado: true, preguntaDescartada: preguntaExcluida}) + '\n');
  if (!passed) process.exitCode = 1;
} catch (error) {
  process.stderr.write(JSON.stringify({prueba: 'GEMINI_LIVE_ISSUE_59', resultado: 'FALLIDO',
    codigo: ['LLM_NO_DISPONIBLE','LLM_CUOTA','LLM_FORMATO'].includes(error?.code) ? error.code : 'ERROR_NO_CLASIFICADO'}) + '\n');
  process.exitCode = 1;
}
