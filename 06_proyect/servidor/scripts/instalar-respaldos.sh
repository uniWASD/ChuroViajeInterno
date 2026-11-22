#!/usr/bin/env bash
# Instala el respaldo diario de la base de datos (T-103, #79).
#
# Uso (como ubuntu):  sudo bash instalar-respaldos.sh <nombre-del-bucket>
#
# Antes, en la consola de Oracle Cloud (sección «Respaldos» de la guía): el bucket privado, el grupo
# dinámico con la instancia y la política que le deja manejar los objetos de ese bucket.
#
# Qué hace:
#   1. Instala la CLI de Oracle Cloud en un entorno virtual (/opt/oci-cli), si no está.
#   2. Comprueba que la instancia se identifica sola (instance principal) y ve el bucket.
#   3. Escribe /etc/churoviaje/respaldos.env (nombre del bucket y ruta de la CLI; no hay llaves).
#   4. Copia el script del respaldo a /usr/local/sbin y el resumen SQL a /usr/local/share/churoviaje, de root:
#      el temporizador corre como root y no debe ejecutar archivos que deploy puede cambiar.
#   5. Instala y enciende el temporizador diario.

main() {
  set -euo pipefail
  if [[ "$(id -u)" -ne 0 || -z "${1:-}" ]]; then
    echo "Uso: sudo bash $0 <nombre-del-bucket>" >&2
    exit 1
  fi
  local bucket="$1"
  local repo="/home/deploy/ChuroViajeInterno/06_proyect/servidor"
  local oci="/opt/oci-cli/bin/oci"

  echo "== 1. CLI de Oracle Cloud"
  if [[ ! -x "$oci" ]]; then
    apt-get install -y -qq python3-venv > /dev/null
    python3 -m venv /opt/oci-cli
    /opt/oci-cli/bin/pip install -q --upgrade pip
    /opt/oci-cli/bin/pip install -q oci-cli
  fi
  "$oci" --version

  echo "== 2. Identidad de la instancia y bucket"
  echo "   espacio de nombres: $("$oci" os ns get --auth instance_principal --query data --raw-output)"
  "$oci" os bucket get --auth instance_principal --bucket-name "$bucket" \
    --query 'data.{nombre:name,acceso:"public-access-type",nivel:"storage-tier"}' --output table

  echo "== 3. Configuración"
  install -m 600 -o root -g root /dev/null /etc/churoviaje/respaldos.env
  printf 'BUCKET=%s\nOCI=%s\nBASE=churoviaje\n' "$bucket" "$oci" > /etc/churoviaje/respaldos.env

  echo "== 4. Scripts de root"
  install -m 750 -o root -g root "$repo/scripts/respaldar-bd.sh" /usr/local/sbin/churoviaje-respaldar-bd
  install -d -m 755 -o root -g root /usr/local/share/churoviaje
  install -m 644 -o root -g root "$repo/scripts/huella-respaldo.sql" /usr/local/share/churoviaje/huella-respaldo.sql

  echo "== 5. Temporizador"
  install -m 644 -o root -g root "$repo/systemd/churoviaje-respaldo.service" "$repo/systemd/churoviaje-respaldo.timer" \
    /etc/systemd/system/
  systemctl daemon-reload
  systemctl enable --now churoviaje-respaldo.timer
  systemctl list-timers churoviaje-respaldo.timer --no-pager
  echo "== Listo. Primer respaldo ahora: sudo systemctl start churoviaje-respaldo"
}

main "$@"
