import {ErrorServicio} from './errors.js';
import {DESCONOCIDO} from './catalog.js';
import {FILA_NO_MENCIONADA} from './llm-prompt.js';
import {respaldo, esConsulta} from './evidence.js';

/**
 * Validación determinista de la salida del LLM (investigación del issue #10, sección 9). El LLM propone; aquí solo
 * queda lo que se sostiene en el mensaje y en el catálogo. De cada aviso válido sale un aviso del contrato con la
 * fecha del mensaje, no una que diga el LLM (RN-06.7, RN-06.16).
 *
 * @param output   lo que devolvió el LLM
 * @param messages los mensajes del lote, en el mismo orden que viajaron («m1», «m2»…)
 * @returns {{avisos: object[], descartados: number, porValidador: number, filasQuitadas: number}}
 */
export function interpretOutput(output, messages, catalog, {vigentes = null} = {}) {
  if (!output || typeof output !== 'object' || !Array.isArray(output.avisos) || !Array.isArray(output.descartados)) {
    throw new ErrorServicio('LLM_FORMATO', 'El LLM no devolvió la estructura pedida.');
  }
  const porId = new Map(messages.map((m, i) => [`m${i + 1}`, m]));
  const surtidores = new Map(catalog.surtidores.map(s => [s.nombre, s]));
  const combustibles = new Set([...catalog.combustibles, DESCONOCIDO]);
  const filas = new Set(catalog.filas);
  const idsDescartados = new Set(output.descartados.filter(x => x && typeof x.mensaje === 'string').map(x => x.mensaje));
  const propuestos = new Map(); // mensaje|surtidor|combustible -> aviso, o null si se contradice
  let porValidador = 0;
  let filasQuitadas = 0;
  for (const a of output.avisos) {
    const mensaje = a && porId.get(a.mensaje);
    const surtidor = a && surtidores.get(a.surtidor);
    if (!mensaje || (vigentes && !vigentes.has(mensaje.id)) || idsDescartados.has(a.mensaje) || !surtidor ||
        !combustibles.has(a.combustible) || !['DISPONIBLE', 'AGOTADO'].includes(a.estado)
        || (a.fila !== undefined && a.fila !== FILA_NO_MENCIONADA && !filas.has(a.fila))) {
      porValidador++;
      continue;
    }
    // RN-06.9: una pregunta nunca confirma un estado, aunque el LLM la haya tomado como aviso.
    const r = respaldo(mensaje.texto, a, catalog);
    if (!r.estado) {
      porValidador++;
      continue;
    }
    // La fila solo viaja si el texto dice la misma; si no, el aviso sale sin fila en vez de perderse.
    const conFila = a.fila !== undefined && a.fila !== FILA_NO_MENCIONADA;
    if (conFila && a.fila !== r.fila) filasQuitadas++;
    const fila = conFila && a.fila === r.fila ? a.fila : null;
    const llave = `${a.mensaje}|${a.surtidor}|${a.combustible}`;
    const aviso = {surtidor: a.surtidor, combustible: a.combustible, estado: a.estado,
      ...(fila ? {fila} : {}), fecha: mensaje.fecha};
    if (!propuestos.has(llave)) propuestos.set(llave, aviso);
    else if (propuestos.get(llave) !== null &&
        (propuestos.get(llave).estado !== a.estado || propuestos.get(llave).fila !== aviso.fila)) {
      propuestos.set(llave, null); // el mismo mensaje dice que hay y que no hay: contradictorio
      porValidador++;
    }
  }
  const avisos = [...propuestos.values()].filter(Boolean);
  const conAviso = new Set([...propuestos.entries()].filter(([, v]) => v).map(([k]) => k.split('|')[0]));
  return {avisos, descartados: messages.length - conAviso.size, porValidador, filasQuitadas};
}

/** Un mensaje con «¿» o que termina en «?» es una pregunta (investigación del issue #10, sección 9). */
export function esPregunta(texto) {
  return esConsulta(texto);
}
