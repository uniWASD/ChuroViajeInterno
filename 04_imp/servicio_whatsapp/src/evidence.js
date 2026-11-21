import {clave, mencionaFrase} from './normalize.js';
import {DESCONOCIDO} from './catalog.js';
import {FILA_NO_MENCIONADA} from './llm-prompt.js';

/**
 * Segunda barrera, deliberadamente conservadora: el LLM propone valores y estas reglas exigen
 * corroboración textual positiva. Las formas no contempladas se DESCARTAN; nunca se completan
 * por adivinación. No se reutiliza contexto de otros mensajes (decisión de #59).
 *
 * Esto no demuestra la verdad de un mensaje: demuestra que la respuesta no contradice
 * las pistas explícitas comprobables de ESE texto. Se requieren pruebas de campo.
 */
const CONSULTA = /\?|¿|\b(?:alguien sabe|saben si|sabes si|donde hay|donde venden|avisen|avisa(?:me|nos)?|me avisan|informen|pregunta|consulta|quien sabe|quisiera saber|alguno sabe)\b/i;
const INCERTIDUMBRE = /\b(?:creo|parece|pareceria|quizas|quiza|tal vez|supuestamente|aparentemente|rumor|rumores|dicen|dijo|otro dice|otros dicen|me contaron|me dijeron|escuche|habria|debe haber|no se si|puede ser|por ahi)\b/;

export function esConsulta(texto) {
  return typeof texto === 'string' && (/[¿?]/.test(texto) || CONSULTA.test(clave(texto)));
}

const COMBUSTIBLE_PATRONES = Object.freeze({
  'Gasolina': /\b(?:gasolina|especial|clarita|blanca|blanquita|limpia|nafta)\b/,
  'Gasolina Premium': /\b(?:premium|plus|amarilla)\b/,
  'Diesel': /\b(?:diesel|gasoil)\b/,
  'Diesel ULS': /\b(?:uls|ultra bajo azufre)\b/
});
const NEGACION = /\b(?:no hay|ya no hay|no queda|ya no queda|no tienen|no venden|no despachan|se acabo|se termino|se agot[oó]|agotad[oa]s?|se quedaron sin|sin combustible|sin gasolina|sin diesel|se termino la venta)\b/;
const AFIRMACION = /\b(?:hay|tienen|llego|llegaron|vende|venden|vendiendo|despachando|despachan|disponible|disponibles|abasteciendo|cargando)\b/;
const FILAS = Object.freeze({
  SIN_FILA: /\b(?:sin fila|no hay fila|cero fila|fila cero|sin cola|no hay cola)\b/,
  CORTA: /\b(?:fila corta|poca fila|fila pequena|fila chiquita|cola corta|poca cola)\b/,
  MEDIA: /\b(?:fila media|fila mediana|cola media|cola mediana)\b/,
  LARGA: /\b(?:fila larga|mucha fila|bastante fila|filon|filota|harta fila|larguisima|cola larga|mucha cola|harta cola|bastante cola)\b/
});
// Lo que todavía no pasó no confirma que haya combustible («va a llegar gasolina a Vegas»).
const FUTURO = /\b(?:va a|van a|llegara|llegaran|vendra|traeran|esperando|esperan|manana|cuando)\b/;
const MENCIONA_FILA = /\b(?:fila|filas|filon|filota|cola|colas)\b/;

function fuelMentions(segmento, catalog) {
  const set = new Set();
  for (const nombre of catalog.combustibles) {
    const valor = clave(nombre);
    const patron = COMBUSTIBLE_PATRONES[nombre];
    if (patron ? patron.test(segmento) : mencionaFrase(segmento, nombre)) set.add(nombre);
  }
  // «gasolina premium» y «diésel ULS» no significan el combustible genérico.
  if (set.has('Gasolina Premium') && /\bgasolina\s+premium\b/.test(segmento)) set.delete('Gasolina');
  if (set.has('Diesel ULS') && /\bdiesel\s+uls\b/.test(segmento)) set.delete('Diesel');
  return set;
}

function states(segmento) {
  // «no hay fila» informa sobre cola, NO sobre falta de combustible.
  const depurado = segmento.replace(/\b(?:no hay fila|no hay cola|sin fila|sin cola)\b/g, ' ');
  const negativos = [...depurado.matchAll(new RegExp(NEGACION.source, 'g'))];
  let positivo = depurado.replace(new RegExp(NEGACION.source, 'g'), ' ');
  // Una fila, por sí sola, no confirma que haya combustible.
  positivo = positivo.replace(/\bhay (?:mucha |poca |bastante )?(?:fila|cola)\b/g, ' ');
  const positivos = [...positivo.matchAll(new RegExp(AFIRMACION.source, 'g'))];
  return {positivo: positivos.length > 0, negativo: negativos.length > 0};
}

function dividir(texto) {
  // Las cláusulas delimitan asociaciones para evitar atribuir un estado al surtidor equivocado.
  return texto.replace(/\s+y\s+(?=en\s+)/g, ', ')
    .split(/\s*(?:[,;.!]|\bpero\b|\ben cambio\b|\bmientras que\b)\s*/)
    .map(s => clave(s)).filter(Boolean);
}

function estacionesMencionadas(segmento, catalog) {
  return new Set(catalog.surtidores.filter(s => [s.nombre, ...s.alias]
    .some(alias => mencionaFrase(segmento, alias))).map(s => s.nombre));
}

function ultimaResolucion(texto, estados) {
  if (estados.length === 0) return null;
  const positivos = estados.some(x => x === 'DISPONIBLE');
  const negativos = estados.some(x => x === 'AGOTADO');
  if (positivos && negativos) {
    // Solo se resuelven cambios inequívocos señalados en el mensaje, nunca un rumor.
    if (/\b(?:pero|al final|ahora|despues|luego|mentira|al toque)\b.*\b(?:se acabo|se termino|agotado|no hay|ya no hay|sin combustible)\b/.test(texto)) return 'AGOTADO';
    if (/\b(?:pero|al final|ahora|despues|luego|en realidad)\b.*\b(?:si hay|ya hay|ahora hay|llego|disponible|estan vendiendo)\b/.test(texto)) return 'DISPONIBLE';
    return null;
  }
  return negativos ? 'AGOTADO' : 'DISPONIBLE';
}

/**
 * Revisa si el texto respalda el aviso propuesto por el LLM. Devuelve {estado, fila}: {@code estado} dice si el texto
 * respalda el surtidor, el combustible y el estado; {@code fila} es la fila que el texto menciona para ese aviso
 * (NO_MENCIONADA si no dice nada, null si dice dos cosas distintas). La fila se revisa aparte para no perder un
 * «hay gasolina» solo porque la fila está dicha de una forma que estas reglas no conocen.
 */
export function respaldo(texto, a, catalog) {
  const sin = {estado: false, fila: null};
  if (typeof texto !== 'string' || !a || esConsulta(texto)) return sin;
  const normal = clave(texto);
  // Las fuentes indirectas o meramente conjeturales no confirman estados.
  if (INCERTIDUMBRE.test(normal)) return sin;
  const presentes = estacionesMencionadas(normal, catalog);
  if (!presentes.has(a.surtidor)) return sin;
  const allFuels = fuelMentions(normal, catalog);
  if (a.combustible === DESCONOCIDO ? allFuels.size !== 0 : !allFuels.has(a.combustible)) return sin;

  const clausulas = dividir(texto);
  const declaraciones = [];
  let implicitas = 0;
  const pistasFila = [];
  let estacionActual = presentes.size === 1 ? a.surtidor : null;
  let combustibleAnterior = null;
  for (const clausula of clausulas) {
    const estacionEnClausula = estacionesMencionadas(clausula, catalog);
    if (estacionEnClausula.size > 1) { estacionActual = null; continue; }
    if (estacionEnClausula.size === 1) estacionActual = [...estacionEnClausula][0];
    const combustibles = fuelMentions(clausula, catalog);
    const objetivo = a.combustible === DESCONOCIDO
      ? allFuels.size === 0
      : combustibles.has(a.combustible) || (combustibles.size === 0 &&
        (combustibleAnterior === a.combustible || allFuels.size === 1));
    const {positivo, negativo} = states(clausula);
    if (estacionActual === a.surtidor && objetivo) {
      if (positivo) declaraciones.push('DISPONIBLE');
      if (negativo) declaraciones.push('AGOTADO');
      // «Las Vegas con gasolina», «gasolina en vegas»: nombrar el surtidor y el combustible, sin negación, sin fila
      // y sin futuro, también es decir que hay. Solo con el combustible escrito en la misma frase, y solo si el
      // mensaje no dice nada más explícito (más abajo).
      if (!positivo && !negativo && combustibles.has(a.combustible) && !MENCIONA_FILA.test(clausula) &&
          !FUTURO.test(clausula)) implicitas++;
    }
    if (estacionActual === a.surtidor && (combustibles.has(a.combustible) ||
      (combustibles.size === 0 && (allFuels.size <= 1 || combustibleAnterior === a.combustible)))) {
      const fila = Object.entries(FILAS).filter(([, patron]) => patron.test(clausula)).map(([tipo]) => tipo);
      pistasFila.push(...fila);
    }
    if (combustibles.size === 1) combustibleAnterior = [...combustibles][0];
    else if (combustibles.size > 1) combustibleAnterior = null;
  }
  if (declaraciones.length === 0 && implicitas > 0) declaraciones.push('DISPONIBLE');
  if (ultimaResolucion(normal, declaraciones) !== a.estado) return sin;
  const filasUnicas = new Set(pistasFila);
  // Dos filas distintas sin resolución de tiempo: el estado vale, la fila no.
  if (filasUnicas.size > 1) return {estado: true, fila: null};
  return {estado: true, fila: [...filasUnicas][0] ?? FILA_NO_MENCIONADA};
}

/** El aviso completo (estado y fila) tiene respaldo en el texto. */
export function avisoRespaldado(texto, a, catalog) {
  const r = respaldo(texto, a, catalog);
  return r.estado && (a?.fila ?? FILA_NO_MENCIONADA) === r.fila;
}
