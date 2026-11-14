import {ErrorServicio} from './errors.js';
import {DESCONOCIDO} from './catalog.js';
import {FILA_NO_MENCIONADA} from './llm-prompt.js';
import {mencionaFrase} from './normalize.js';

/**
 * Validación determinista de la salida del LLM (investigación del issue #10, sección 9). El LLM propone; aquí solo
 * queda lo que se sostiene en el mensaje y en el catálogo. De cada aviso válido sale un aviso del contrato con la
 * fecha del mensaje, no una que diga el LLM (RN-06.7, RN-06.16).
 *
 * @param output   lo que devolvió el LLM
 * @param messages los mensajes del lote, en el mismo orden que viajaron («m1», «m2»…)
 * @returns {{avisos: object[], descartados: number, porValidador: number}}
 */
export function interpretOutput(output, messages, catalog) {
  if (!output || typeof output !== 'object' || !Array.isArray(output.avisos) || !Array.isArray(output.descartados)) {
    throw new ErrorServicio('LLM_FORMATO', 'El LLM no devolvió la estructura pedida.');
  }
  const porId = new Map(messages.map((m, i) => [`m${i + 1}`, m]));
  const surtidores = new Map(catalog.surtidores.map(s => [s.nombre, s]));
  const combustibles = new Set([...catalog.combustibles, DESCONOCIDO]);
  const filas = new Set(catalog.filas);
  const propuestos = new Map(); // mensaje|surtidor|combustible -> aviso, o null si se contradice
  let porValidador = 0;
  for (const a of output.avisos) {
    const mensaje = a && porId.get(a.mensaje);
    const surtidor = a && surtidores.get(a.surtidor);
    if (!mensaje || !surtidor || !combustibles.has(a.combustible) || !['DISPONIBLE', 'AGOTADO'].includes(a.estado)
        || (a.fila !== undefined && a.fila !== FILA_NO_MENCIONADA && !filas.has(a.fila))) {
      porValidador++;
      continue;
    }
    // RN-06.9: una pregunta nunca confirma un estado, aunque el LLM la haya tomado como aviso.
    if (esPregunta(mensaje.texto)) {
      porValidador++;
      continue;
    }
    // RN-06.9: el surtidor tiene que estar nombrado en el mensaje, por su nombre o un alias; si no, el LLM lo supuso.
    if (![surtidor.nombre, ...surtidor.alias].some(n => mencionaFrase(mensaje.texto, n))) {
      porValidador++;
      continue;
    }
    const llave = `${a.mensaje}|${a.surtidor}|${a.combustible}`;
    const aviso = {surtidor: a.surtidor, combustible: a.combustible, estado: a.estado,
      ...(a.fila && a.fila !== FILA_NO_MENCIONADA ? {fila: a.fila} : {}), fecha: mensaje.fecha};
    if (!propuestos.has(llave)) propuestos.set(llave, aviso);
    else if (propuestos.get(llave) !== null && propuestos.get(llave).estado !== a.estado) {
      propuestos.set(llave, null); // el mismo mensaje dice que hay y que no hay: contradictorio
      porValidador++;
    }
  }
  const avisos = [...propuestos.values()].filter(Boolean);
  const conAviso = new Set([...propuestos.entries()].filter(([, v]) => v).map(([k]) => k.split('|')[0]));
  return {avisos, descartados: messages.length - conAviso.size, porValidador};
}

/** Un mensaje con «¿» o que termina en «?» es una pregunta (investigación del issue #10, sección 9). */
export function esPregunta(texto) {
  return texto.includes('¿') || /\?\s*$/.test(texto);
}
