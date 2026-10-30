const ISO = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|([+-])(\d{2}):(\d{2}))$/;
export function zonedMilliseconds(value) {
  if (typeof value !== 'string') return null;
  const parts = ISO.exec(value);
  if (!parts) return null;
  const year=+parts[1], month=+parts[2], day=+parts[3];
  const hour=+parts[4], minute=+parts[5], second=+parts[6];
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28,31,30,31,30,31,31,30,31,30,31];
  if (year < 1 || month < 1 || month > 12 || day < 1 || day > days[month - 1] || hour > 23 || minute > 59 || second > 59) return null;
  if (parts[9] && (+parts[10] > 23 || +parts[11] > 59)) return null;
  const ms = Date.parse(value);
  return Number.isFinite(ms) ? ms : null;
}
export function requireDate(value) {
  const parsed = zonedMilliseconds(value);
  if (parsed === null) throw new TypeError('FECHA_INVALIDA: se exige ISO 8601 con zona horaria.');
  return parsed;
}
