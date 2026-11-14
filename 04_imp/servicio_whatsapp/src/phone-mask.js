/**
 * RN-06.6 y D-17: antes de enviar un texto al LLM se enmascaran los números de teléfono. Se toma como teléfono
 * cualquier secuencia de 7 a 15 dígitos, con o sin «+» y con espacios, puntos o guiones entre ellos: cubre los
 * celulares de Bolivia (71234567, 712-34567, +591 71234567) y los fijos (6641234). Una hora, una fecha con barras
 * o una cantidad corta («1000 litros») no llegan a 7 dígitos seguidos y quedan como están.
 */
export const MASCARA = '[número]';
const TELEFONO = /\+?\d(?:[ .-]?\d){6,14}(?!\d)/g;

export function maskPhones(text) {
  if (typeof text !== 'string') throw new TypeError('Se requiere texto.');
  return text.replace(TELEFONO, MASCARA);
}
