#!/usr/bin/env bash
# Vuelve a una versión anterior del backend y del núcleo C++ en churoviaje-server (T-098, #60).
#
# Uso (como deploy):
#   bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/volver.sh            # la versión anterior a la actual
#   bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/volver.sh <versión>  # una versión concreta
#   bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/volver.sh --lista    # muestra las versiones guardadas
#
# No compila nada: solo mueve el enlace /opt/churoviaje/actual a una versión que ya está en
# /opt/churoviaje/releases y reinicia el servicio. Si la versión elegida no arranca, deja la que había.

main() {
  set -euo pipefail

  local base="/opt/churoviaje"
  local releases="$base/releases"

  if [[ "$(id -un)" != "deploy" ]]; then
    echo "Este script se corre como deploy (sudo -iu deploy)." >&2
    exit 1
  fi

  local actual
  actual="$(readlink -f "$base/actual")"

  if [[ "${1:-}" == "--lista" ]]; then
    echo "Versiones guardadas (la más reciente primero):"
    local carpeta
    ls -1dt "$releases"/*/ | while read -r carpeta; do
      carpeta="${carpeta%/}"
      if [[ "$carpeta" == "$actual" ]]; then
        echo "  $(basename "$carpeta")   <- actual"
      else
        echo "  $(basename "$carpeta")"
      fi
    done
    exit 0
  fi

  local destino
  if [[ -n "${1:-}" ]]; then
    destino="$releases/$1"
    if [[ ! -d "$destino" ]]; then
      echo "No existe la versión $1. Ver las que hay con --lista." >&2
      exit 1
    fi
  else
    # La versión inmediatamente más vieja que la actual.
    destino="$(ls -1dt "$releases"/*/ | sed 's#/$##' | grep -A1 -xF "$actual" | sed -n 2p || true)"
    if [[ -z "$destino" ]]; then
      echo "No hay una versión anterior a $(basename "$actual")." >&2
      exit 1
    fi
  fi

  if [[ "$destino" == "$actual" ]]; then
    echo "Esa versión ya es la actual."
    exit 0
  fi

  echo "== Volviendo de $(basename "$actual") a $(basename "$destino")"
  activar "$base" "$destino"
  if esperar_salud; then
    echo "== Listo: corre la versión $(basename "$destino")"
  else
    echo "!! $(basename "$destino") no arrancó bien; se deja $(basename "$actual")." >&2
    sudo /usr/bin/journalctl -u churoviaje-backend -n 80 --no-pager >&2 || true
    activar "$base" "$actual"
    esperar_salud || echo "!! Tampoco arrancó $(basename "$actual"). Revisar el servicio a mano." >&2
    exit 1
  fi
}

activar() {
  local base="$1" destino="$2"
  ln -sfn "$destino" "$base/actual.nuevo"
  mv -Tf "$base/actual.nuevo" "$base/actual"
  sudo /usr/bin/systemctl restart churoviaje-backend
}

esperar_salud() {
  local intento
  for intento in $(seq 1 45); do
    sleep 2
    if curl -sf http://127.0.0.1:8081/actuator/health | grep -q '"UP"' \
       && pgrep -x motor_nucleo > /dev/null; then
      echo "   Salud UP y núcleo corriendo (en unos $((intento * 2)) s)."
      return 0
    fi
  done
  return 1
}

main "$@"
