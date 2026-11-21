#!/usr/bin/env bash
# Registros del servidor (T-103, #79): se borran a los 14 días y no guardan ubicaciones ni la consulta de las URL.
#
# Uso (como ubuntu):
#   sudo bash configurar-registros.sh                           # configura
#   sudo bash configurar-registros.sh --borrar-registros-viejos # además borra los registros de Nginx ya escritos,
#                                                               # que tienen la consulta con la ubicación
#
# Qué hace:
#   1. journald (backend, núcleo y servicio de WhatsApp/LLM): conserva 14 días y como mucho 1 GB.
#   2. Nginx: formato de acceso sin la consulta de las URL ni la IP completa, y errores solo críticos. Agrega el
#      include a cada bloque server de los sitios de ChuroViaje. Si «nginx -t» falla, deja todo como estaba.
#   3. logrotate de Nginx: un archivo por día, 14 días.
# Los registros de las pruebas de carga (/var/log/churoviaje-pruebas-carga) no se tocan: se guardan hasta revisarlos.

main() {
  set -euo pipefail
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0 [--borrar-registros-viejos]" >&2
    exit 1
  fi
  local repo="/home/deploy/ChuroViajeInterno/06_proyect/servidor"

  echo "== 1. journald: 14 días"
  install -d -m 755 /etc/systemd/journald.conf.d
  install -m 644 "$repo/registros/journald-churoviaje.conf" /etc/systemd/journald.conf.d/churoviaje.conf
  systemctl restart systemd-journald
  journalctl --vacuum-time=14d 2>&1 | tail -n 1 | sed 's/^/   /'

  echo "== 2. Nginx sin la consulta de las URL"
  local respaldo
  respaldo="$(mktemp -d)"
  cp -a /etc/nginx/sites-available "$respaldo/"
  install -m 644 "$repo/registros/nginx-formato.conf" /etc/nginx/conf.d/churoviaje-formato.conf
  install -m 644 "$repo/registros/nginx-sitio.conf" /etc/nginx/snippets/churoviaje-registro.conf
  local sitio
  for sitio in /etc/nginx/sites-available/churoviaje /etc/nginx/sites-available/churoviaje-pruebas; do
    [[ -f "$sitio" ]] || continue
    # Después de cada server_name que todavía no lo tiene, también en los bloques que certbot agregue después.
    python3 - "$sitio" <<'PY'
import sys
ruta = sys.argv[1]
lineas = open(ruta, encoding="utf-8").read().split("\n")
salida = []
for i, linea in enumerate(lineas):
    salida.append(linea)
    siguiente = lineas[i + 1] if i + 1 < len(lineas) else ""
    if linea.strip().startswith("server_name ") and "churoviaje-registro.conf" not in siguiente:
        salida.append("    include snippets/churoviaje-registro.conf;")
open(ruta, "w", encoding="utf-8").write("\n".join(salida))
PY
    echo "   $(basename "$sitio"): $(grep -c 'snippets/churoviaje-registro.conf' "$sitio") bloques server con el formato nuevo"
  done
  if nginx -t 2> "$respaldo/nginx-t"; then
    systemctl reload nginx
    echo "   nginx -t correcto; Nginx recargado"
  else
    sed 's/^/   /' "$respaldo/nginx-t" >&2
    cp -a "$respaldo/sites-available/." /etc/nginx/sites-available/
    rm -f /etc/nginx/conf.d/churoviaje-formato.conf /etc/nginx/snippets/churoviaje-registro.conf
    echo "!! nginx -t falló: se dejó todo como estaba" >&2
    exit 1
  fi
  rm -rf "$respaldo"

  echo "== 3. logrotate de Nginx: 14 días"
  install -m 644 "$repo/registros/logrotate-nginx" /etc/logrotate.d/nginx
  logrotate --debug /etc/logrotate.d/nginx 2>&1 | grep -E 'rotating pattern|log files older than' | sed 's/^/   /' || true

  if [[ "${1:-}" == "--borrar-registros-viejos" ]]; then
    echo "== Registros viejos de Nginx (tenían la consulta con la ubicación)"
    rm -f /var/log/nginx/access.log.* /var/log/nginx/error.log.*
    truncate -s 0 /var/log/nginx/access.log /var/log/nginx/error.log
    echo "   borrados"
  fi
  echo "== Listo"
}

main "$@"
