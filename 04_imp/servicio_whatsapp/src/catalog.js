import {ErrorServicio} from './errors.js';

/** Lo que entrega GET /monitoreo/surtidores (05_contratos). «Desconocido» es un valor fijo de RN-06.8. */
export const DESCONOCIDO = 'Desconocido';

function invalid() { throw new ErrorServicio('CATALOGO_INVALIDO', 'El catálogo del backend no tiene la forma esperada.'); }
const texto = x => typeof x === 'string' && x.trim() !== '' && x.length <= 256;

export function validateCatalog(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) invalid();
  const {surtidores, combustibles, filas} = raw;
  if (!Array.isArray(surtidores) || surtidores.length === 0 || surtidores.length > 2000) invalid();
  for (const s of surtidores) {
    if (!s || typeof s !== 'object' || !texto(s.nombre) || !Array.isArray(s.alias) || !s.alias.every(texto)) invalid();
  }
  if (!Array.isArray(combustibles) || combustibles.length === 0 || !combustibles.every(texto)) invalid();
  if (!Array.isArray(filas) || !filas.every(texto)) invalid();
  return Object.freeze({
    surtidores: Object.freeze(surtidores.map(s => Object.freeze({nombre: s.nombre, alias: Object.freeze([...s.alias])}))),
    combustibles: Object.freeze([...combustibles]),
    filas: Object.freeze([...filas])
  });
}
