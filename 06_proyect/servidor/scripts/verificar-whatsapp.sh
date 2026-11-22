#!/usr/bin/env bash
# CP-101 (T-100, #68): el servicio corre, no abre puertos, sus secretos son privados y un mensaje del grupo de prueba
# queda en estado_combustible en menos de 6 minutos (RNF-04).
#
# Uso (como ubuntu):  sudo bash verificar-whatsapp.sh [minutos]
#   minutos: cuánto hacia atrás buscar datos de WhatsApp en estado_combustible (por omisión 30).
#
# Se manda un mensaje al grupo de prueba (por ejemplo «Las Vegas hay gasolina, fila corta»), se esperan 6 minutos
# y se corre. No muestra secretos, IPs internas ni textos de mensajes: solo el surtidor, el tipo y las horas.

main() {
  set -euo pipefail
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0 [minutos]" >&2
    exit 1
  fi
  local minutos="${1:-30}"
  echo "== Servicio"
  echo "   churoviaje-whatsapp: $(systemctl is-active churoviaje-whatsapp) · arranca con la máquina: $(systemctl is-enabled churoviaje-whatsapp)"
  echo "   se reinicia si se cae: $(systemctl show -p Restart --value churoviaje-whatsapp)"
  echo "== Puertos que escucha el servicio (debe ser ninguno)"
  local pid
  pid="$(systemctl show -p MainPID --value churoviaje-whatsapp)"
  if ss -ltnupH | grep -q "pid=$pid,"; then
    ss -ltnupH | grep "pid=$pid," | awk '{print "   " $1 " " $5}'
  else
    echo "   ninguno"
  fi
  echo "== Quién puede leer los secretos y la sesión (solo churoviaje-wa)"
  stat -c '   %A %U:%G %n' /etc/churoviaje/whatsapp.env /etc/churoviaje/whatsapp /etc/churoviaje/whatsapp/autorizaciones.json \
    /var/lib/churoviaje-whatsapp /var/lib/churoviaje-whatsapp/* 2> /dev/null || true
  echo "== Habla con el backend dentro de la máquina"
  grep -E '^BACKEND_URL=' /etc/churoviaje/whatsapp.env | sed 's/^/   /'
  echo "== Últimos eventos del servicio (lista blanca: sin textos ni números)"
  journalctl -u churoviaje-whatsapp -n 300 --no-pager -o cat | grep -o '{"evento":"[A-Z_]*"[^}]*}' | tail -n 8 | sed 's/^/   /' || true
  echo "== Datos de WhatsApp en estado_combustible de los últimos $minutos minutos"
  runuser -u postgres -- psql -d churoviaje -X -P footer=off -c "
    SELECT s.nombre AS surtidor, e.tipo_codigo AS tipo, CASE WHEN e.disponible THEN 'hay' ELSE 'no hay' END AS estado,
           to_char(e.fecha_dato AT TIME ZONE 'America/La_Paz', 'HH24:MI:SS') AS mensaje,
           to_char(e.actualizado_en AT TIME ZONE 'America/La_Paz', 'HH24:MI:SS') AS guardado,
           round(extract(epoch FROM e.actualizado_en - e.fecha_dato) / 60, 1) AS minutos
    FROM estado_combustible e JOIN surtidor s ON s.id = e.surtidor_id
    WHERE e.origen = 'WHATSAPP' AND e.actualizado_en > now() - make_interval(mins => $minutos::int)
    ORDER BY e.actualizado_en DESC LIMIT 10;" | sed 's/^/   /'
  echo "   CP-101 pide que «minutos» (del mensaje a la tabla) sea menor que 6."
}

main "$@"
