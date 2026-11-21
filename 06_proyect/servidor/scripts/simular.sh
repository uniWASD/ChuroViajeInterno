#!/usr/bin/env bash
# Corre el simulador (CU-07, #87) en el servidor contra el backend de pruebas, sin que la credencial
# de prueba salga del servidor (T-106, #86).
#
# Uso (como ubuntu, con sudo, con el backend de pruebas encendido):
#   sudo bash simular.sh --usuarios 10 --navegando 4 --eventos-por-minuto 4 --duracion 5 --prueba carga-1
#   sudo bash simular.sh --borrar                     # borra los datos simulados al terminar
#
# Lee SIMULACION_CREDENCIAL del archivo de entorno del backend de pruebas y corre el simulador como deploy,
# desde su clon del repositorio, contra http://127.0.0.1:8090. Los reportes que pidas con --reporte quedan
# en /home/deploy/simulaciones.

main() {
  set -euo pipefail
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0 ..." >&2
    exit 1
  fi
  local repo="/home/deploy/ChuroViajeInterno"
  SIMULACION_CREDENCIAL="$(grep -E '^SIMULACION_CREDENCIAL=' /etc/churoviaje/backend-pruebas.env | cut -d= -f2-)"
  export SIMULACION_CREDENCIAL
  install -d -o deploy -g deploy -m 750 /home/deploy/simulaciones
  cd "$repo/04_imp/simulador"
  # runuser deja pasar solo esa variable al proceso del simulador; no va en la línea de comandos.
  runuser -u deploy -w SIMULACION_CREDENCIAL -- python3 -m simulador --backend http://127.0.0.1:8090 "$@"
}

main "$@"
