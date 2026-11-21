/**
 * RN-06.6: oculta teléfonos bolivianos/internacionales antes de cualquier petición al LLM.
 * Admite prefijo +, separadores y paréntesis: +591 (712) 34567, (04) 6641234.
 * Evita ocultar fechas de uso común. No es una eliminación universal de datos personales.
 */
export const MASCARA = '[número]';
// Solo se mira que no haya un dígito justo antes: un número pegado a letras («cel71234567») también se oculta.
const TELEFONO_CANDIDATO = /(?<!\d)(?:\+?\d|\(\d)[\d \t().-]{5,34}\d(?!\d)/gu;
const FECHA = /^(?:\d{4}[-.]\d{1,2}[-.]\d{1,2}|\d{1,2}[.]\d{1,2}[.]\d{4})$/;

export function maskPhones(text) {
  if (typeof text !== 'string') throw new TypeError('Se requiere texto.');
  return text.replace(TELEFONO_CANDIDATO, candidate => {
    const digits = candidate.replace(/\D/g, '');
    if (digits.length < 7 || digits.length > 15 || FECHA.test(candidate.trim())) return candidate;
    return MASCARA;
  });
}
