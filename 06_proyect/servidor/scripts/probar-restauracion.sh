#!/usr/bin/env bash
# CP-129 (T-103, #79): restaura un respaldo en una base aparte y comprueba que tiene los mismos eventos y
# surtidores que la base tenía cuando se respaldó.
#
# Uso (como ubuntu):
#   sudo bash probar-restauracion.sh                 # el respaldo más reciente del bucket
#   sudo bash probar-restauracion.sh <AAAA-MM-DD>    # el de ese día (por ejemplo, el de ayer)
#   sudo bash probar-restauracion.sh <día> --conservar   # no borra la base restaurada al terminar
#
# Descarga el respaldo y su resumen del bucket, lo restaura en churoviaje_restaurada, calcula el mismo
# resumen sobre la base restaurada y los compara. No toca la base de producción ni muestra contraseñas.

main() {
  set -euo pipefail
  umask 077
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0 [día] [--conservar]" >&2
    exit 1
  fi
  # shellcheck source=/dev/null
  source /etc/churoviaje/respaldos.env
  local base="${BASE:-churoviaje}"
  local destino="${base}_restaurada"
  local dia="${1:-}" conservar="no"
  [[ "${2:-}" == "--conservar" || "${1:-}" == "--conservar" ]] && conservar="si"
  [[ "$dia" == "--conservar" ]] && dia=""
  local trabajo
  trabajo="$(mktemp -d)"
  chmod 755 "$trabajo"
  # shellcheck disable=SC2064  # la carpeta se fija ahora: al salir, la variable local ya no existe
  trap "rm -rf '$trabajo'" EXIT

  local objeto
  objeto="$("$OCI" os object list --auth instance_principal --bucket-name "$BUCKET" --prefix "respaldos/${base}_${dia}" --all \
    --query 'data[].name' | nombres | grep '\.dump$' | sort -r | head -n 1 || true)"
  if [[ -z "$objeto" ]]; then
    echo "!! No hay respaldos de ${dia:-ningún día} en el bucket $BUCKET" >&2
    exit 1
  fi
  echo "== Respaldo: $objeto"
  "$OCI" os object get --auth instance_principal --bucket-name "$BUCKET" --name "$objeto" --file "$trabajo/r.dump" > /dev/null
  "$OCI" os object get --auth instance_principal --bucket-name "$BUCKET" --name "${objeto%.dump}.resumen" \
    --file "$trabajo/esperado" > /dev/null
  chmod 644 "$trabajo/r.dump"

  echo "== Restaurando en la base aparte $destino"
  runuser -u postgres -- dropdb --if-exists "$destino" 2> /dev/null
  # Del usuario de la aplicación, como producción: desde PostgreSQL 15, solo el dueño de la base crea en «public».
  runuser -u postgres -- createdb --owner="${ROL_APP:-churoviaje_app}" "$destino"
  runuser -u postgres -- psql -q -d "$destino" -c "CREATE EXTENSION IF NOT EXISTS postgis;" 2> /dev/null
  # Las tablas quedan del usuario de la aplicación, como en producción, por si esta base reemplaza a la dañada.
  # El error de la extensión que ya existe no importa.
  runuser -u postgres -- pg_restore --no-owner --no-privileges --role="${ROL_APP:-churoviaje_app}" -d "$destino" \
    "$trabajo/r.dump" 2> "$trabajo/errores" || true
  # Lo que es de la extensión (su comentario y la tabla spatial_ref_sys) ya lo creó CREATE EXTENSION: esos avisos no importan.
  grep -v 'extension "postgis" already exists\|errors ignored on restore\|while PROCESSING TOC\|from TOC entry\|Command was: CREATE EXTENSION\|owner of extension postgis\|COMMENT ON EXTENSION postgis\|spatial_ref_sys\|^\s*$' \
    "$trabajo/errores" | sed 's/^/   /' || true

  echo "== Comparación"
  local obtenido
  obtenido="$(runuser -u postgres -- psql -d "$destino" -X -At -f "${HUELLA:-/usr/local/share/churoviaje/huella-respaldo.sql}")"
  echo "   en el respaldo: $(cat "$trabajo/esperado")"
  echo "   restaurado:     $obtenido"
  local resultado=0
  if [[ "$obtenido" == "$(cat "$trabajo/esperado")" ]]; then
    echo "   CP-129: la base restaurada tiene los mismos eventos y surtidores que el respaldo."
  else
    echo "!! La base restaurada no coincide con el respaldo." >&2
    resultado=1
  fi
  if [[ "$conservar" == "no" ]]; then
    runuser -u postgres -- dropdb "$destino"
    echo "== La base $destino se borró (para conservarla: --conservar)"
  fi
  return "$resultado"
}

# La CLI devuelve los nombres como una lista JSON (o nada, si no hay objetos): uno por línea.
nombres() {
  python3 -c 'import json, sys; texto = sys.stdin.read().strip(); print("\n".join(json.loads(texto) if texto else []))'
}

main "$@"
