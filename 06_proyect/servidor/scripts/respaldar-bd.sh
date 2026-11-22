#!/usr/bin/env bash
# Respaldo diario de la base de datos de ChuroViaje (T-103, #79).
#
# Lo corre el temporizador churoviaje-respaldo.timer como root, desde /usr/local/sbin (lo copia
# instalar-respaldos.sh). A mano:  sudo /usr/local/sbin/churoviaje-respaldar-bd
#
# Qué hace:
#   1. Abre una transacción en la base churoviaje y exporta su instantánea. pg_dump respalda esa
#      instantánea y, en la misma, se calcula un resumen (cuántos eventos y surtidores hay y una huella
#      de cada tabla). Así, al restaurar, se puede comprobar que el respaldo tiene exactamente lo mismo.
#   2. Sube el respaldo y su resumen al bucket del almacenamiento de objetos de Oracle Cloud, fuera de la
#      instancia, con la identidad de la instancia (instance principal): no hay llaves guardadas en el disco.
#   3. Deja en el bucket los 7 respaldos más recientes y borra los anteriores (06_base_de_datos.md).
#   4. Deja en /var/backups/churoviaje solo los 2 más recientes, por si hace falta uno sin descargarlo.
# Nunca imprime una contraseña: pg_dump entra como postgres por el socket local.

main() {
  set -euo pipefail
  umask 077
  # shellcheck source=/dev/null
  source /etc/churoviaje/respaldos.env   # BUCKET, OCI y, opcional, BASE
  local base="${BASE:-churoviaje}"
  local local_dir="/var/backups/churoviaje"
  HUELLA="${HUELLA:-/usr/local/share/churoviaje/huella-respaldo.sql}"
  local fecha nombre dump resumen snapshot
  fecha="$(date -u +%Y-%m-%d_%H%M)"
  nombre="respaldos/${base}_${fecha}"
  install -d -m 700 "$local_dir"
  dump="$local_dir/${base}_${fecha}.dump"
  resumen="$local_dir/${base}_${fecha}.resumen"

  echo "== Respaldo de $base ($fecha UTC)"
  coproc PSQL { runuser -u postgres -- psql -d "$base" -X -q -At; }
  echo "BEGIN ISOLATION LEVEL REPEATABLE READ; SELECT pg_export_snapshot();" >&"${PSQL[1]}"
  read -r snapshot <&"${PSQL[0]}"
  runuser -u postgres -- pg_dump --snapshot="$snapshot" -Fc -d "$base" > "$dump"
  # Los descriptores del coproc no existen dentro de una tubería (subshell): primero se arma la consulta.
  local consulta
  consulta="$(grep -v '^--' "$HUELLA" | tr '\n' ' ')"
  echo "$consulta" >&"${PSQL[1]}"
  read -r linea <&"${PSQL[0]}"
  echo "COMMIT;" >&"${PSQL[1]}"
  echo '\q' >&"${PSQL[1]}"
  wait "$PSQL_PID" || true
  echo "$linea" > "$resumen"
  echo "   $(du -h "$dump" | cut -f1) · $linea"

  echo "== Subiendo al bucket $BUCKET"
  oci_os object put --bucket-name "$BUCKET" --file "$dump" --name "$nombre.dump" --force > /dev/null
  oci_os object put --bucket-name "$BUCKET" --file "$resumen" --name "$nombre.resumen" --force > /dev/null
  echo "   $nombre.dump y $nombre.resumen"

  echo "== Se conservan los 7 más recientes"
  local viejos
  viejos="$(oci_os object list --bucket-name "$BUCKET" --prefix "respaldos/${base}_" --all \
    --query 'data[].name' | nombres | grep '\.dump$' | sort -r | tail -n +8 || true)"
  for objeto in $viejos; do
    oci_os object delete --bucket-name "$BUCKET" --object-name "$objeto" --force > /dev/null
    oci_os object delete --bucket-name "$BUCKET" --object-name "${objeto%.dump}.resumen" --force > /dev/null || true
    echo "   borrado $objeto"
  done
  find "$local_dir" -maxdepth 1 -name "${base}_*.dump" -printf '%f\n' | sort -r | tail -n +3 | while read -r viejo; do
    rm -f -- "$local_dir/$viejo" "$local_dir/${viejo%.dump}.resumen"
  done
  echo "== Listo"
}

# El almacenamiento de objetos con la identidad de la instancia (sin llaves en el disco).
oci_os() {
  "$OCI" os "$@" --auth instance_principal
}

# La CLI devuelve los nombres como una lista JSON (o nada, si no hay objetos): uno por línea.
nombres() {
  python3 -c 'import json, sys; texto = sys.stdin.read().strip(); print("\n".join(json.loads(texto) if texto else []))'
}

main "$@"
