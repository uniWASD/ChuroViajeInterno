import {DESCONOCIDO} from './catalog.js';
import {maskPhones} from './phone-mask.js';

/** El estado que no menciona la fila (05_contratos: si el campo no está, el mensaje no la menciona). */
export const FILA_NO_MENCIONADA = 'NO_MENCIONADA';
export const MOTIVOS_DESCARTE = Object.freeze(['PREGUNTA', 'AMBIGUO', 'CONTRADICTORIO', 'SIN_SURTIDOR', 'NO_RELEVANTE']);

/**
 * Prompt de la investigación del issue #10 (05_pro/PruebaAnalisisLLM, sección 8.2), con el catálogo que entrega el
 * backend en lugar de uno fijo: así hay una sola lista de surtidores y combustibles en todo el sistema (05_contratos).
 */
export function systemPrompt(catalog) {
  const surtidores = catalog.surtidores
    .map(s => `- ${s.nombre}${s.alias.length ? `: ${s.alias.join(', ')}` : ''}`).join('\n');
  return `Eres un clasificador de mensajes de grupos de WhatsApp de Tarija, Bolivia, sobre surtidores de combustible.
Tu tarea es EXTRAER HECHOS, no conversar. Entiendes español informal, abreviaturas, errores de ortografía y regionalismos.

REGLAS:
1. No inventes surtidores, combustibles, filas ni estados. Usa solo los valores del catálogo.
2. Cada mensaje se analiza solo, sin relacionarlo con otros mensajes del lote.
3. Una pregunta o pedido sobre combustible ("dónde hay", "avisen", "alguien sabe si hay") NO confirma nada: descártalo como PREGUNTA.
4. Si el mensaje afirma claramente que hay combustible, estado=DISPONIBLE. Si afirma que se acabó o no hay, estado=AGOTADO.
5. Expresiones vagas ("está normal", "ahí sigue", "dicen", "creo", "parece") no bastan: descártalo como AMBIGUO.
6. Si el mismo mensaje dice que hay y que no hay sin una secuencia clara ("hay, pero otro dice que no"), descártalo como CONTRADICTORIO. Si cuenta una secuencia inequívoca ("llegó pero se acabó al toque"), vale el estado final.
7. Si no nombra un surtidor del catálogo (por su nombre o un alias), descártalo como SIN_SURTIDOR.
8. Si no habla de combustible ni de surtidores, descártalo como NO_RELEVANTE.
9. Si no dice qué combustible, usa "${DESCONOCIDO}". Gasolina especial, "clarita", "blanca", "blanquita", "limpia" o "especial" es Gasolina; "plus", "premium" o "amarilla" es Gasolina Premium; "ULS" o "ultra bajo azufre" es Diesel ULS; diésel o diesel sin más es Diesel.
10. Un mensaje puede dar varios avisos (por ejemplo, de dos combustibles o dos surtidores). Usa un aviso por surtidor y combustible.
11. La fila solo si el mensaje la menciona: sin fila o no hay fila es SIN_FILA; poca fila es CORTA; fila mediana es MEDIA; mucha fila, fila larga o filón es LARGA. Si no la menciona, ${FILA_NO_MENCIONADA}.
12. Todo mensaje del lote aparece una vez: en avisos (uno o más) o en descartados.
13. Responde exclusivamente con el JSON del esquema.

SURTIDORES DEL CATÁLOGO Y SUS ALIAS:
${surtidores}

COMBUSTIBLES: ${[...catalog.combustibles, DESCONOCIDO].join(', ')}.
FILAS: ${[...catalog.filas, FILA_NO_MENCIONADA].join(', ')}.`;
}

/** Salida estructurada que se le pide al LLM (JSON Schema). La fecha no la da el LLM: es la del mensaje (RN-06.16). */
export function outputSchema(catalog) {
  const ref = {type: 'string'};
  return {
    type: 'object',
    properties: {
      avisos: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            mensaje: ref,
            surtidor: {type: 'string', enum: catalog.surtidores.map(s => s.nombre)},
            combustible: {type: 'string', enum: [...catalog.combustibles, DESCONOCIDO]},
            estado: {type: 'string', enum: ['DISPONIBLE', 'AGOTADO']},
            fila: {type: 'string', enum: [...catalog.filas, FILA_NO_MENCIONADA]}
          },
          required: ['mensaje', 'surtidor', 'combustible', 'estado', 'fila']
        }
      },
      descartados: {
        type: 'array',
        items: {
          type: 'object',
          properties: {mensaje: ref, motivo: {type: 'string', enum: [...MOTIVOS_DESCARTE]}},
          required: ['mensaje', 'motivo']
        }
      }
    },
    required: ['avisos', 'descartados']
  };
}

/**
 * RN-06.6: al LLM solo viajan el texto, con los teléfonos enmascarados, y la fecha. El identificador es la posición
 * en el lote («m1», «m2»…), no el de WhatsApp, y no viaja el grupo.
 */
export function userContent(messages) {
  return JSON.stringify({mensajes: messages.map((m, i) => ({id: `m${i + 1}`, fecha: m.fecha, texto: maskPhones(m.texto)}))});
}
