/** La misma clave que usa el backend para los alias (ClaveAlias): minúsculas, sin tildes ni signos. */
export function clave(texto) {
  if (typeof texto !== 'string') return '';
  return texto.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase()
    .replace(/[^a-z0-9 ]/g, ' ').trim().replace(/\s+/g, ' ');
}

/** Si {@code frase} aparece en {@code texto} como palabras completas, comparando las claves. */
export function mencionaFrase(texto, frase) {
  const t = clave(texto), f = clave(frase);
  return f !== '' && ` ${t} `.includes(` ${f} `);
}
