#!/usr/bin/env bash
# Despliega una versión nueva del servicio de WhatsApp/LLM en churoviaje-server (T-100, #68).
#
# Uso (como deploy):  bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/desplegar-whatsapp.sh [rama]
#
# Qué hace:
#   1. Trae la rama (por omisión develop) al clon ~/ChuroViajeInterno.
#   2. Copia 04_imp/servicio_whatsapp a /opt/churoviaje-whatsapp/releases/<fecha>-<commit>/, sin las pruebas,
#      las demostraciones ni las evidencias, e instala sus dependencias con npm ci, sin las de desarrollo.
#   3. Mueve el enlace /opt/churoviaje-whatsapp/actual y reinicia el servicio.
#   4. Si en 30 segundos el servicio no sigue activo, vuelve a la versión anterior.
#   5. Deja las 3 versiones más recientes.

main() {
  set -euo pipefail
  local rama="${1:-develop}"
  local repo="$HOME/ChuroViajeInterno"
  local base="/opt/churoviaje-whatsapp"
  if [[ "$(id -un)" != "deploy" ]]; then
    echo "Este script se corre como deploy (sudo -iu deploy)." >&2
    exit 1
  fi

  echo "== Trayendo $rama de GitHub"
  git -C "$repo" fetch --quiet origin "$rama"
  git -C "$repo" checkout --quiet --detach FETCH_HEAD
  local commit version nueva anterior=""
  commit="$(git -C "$repo" rev-parse --short HEAD)"
  version="$(date +%Y%m%d-%H%M%S)-$commit"
  nueva="$base/releases/$version"
  [[ -L "$base/actual" ]] && anterior="$(readlink -f "$base/actual")"

  echo "== Armando la versión $version"
  mkdir -p "$nueva"
  cp -r "$repo/04_imp/servicio_whatsapp/src" "$repo/04_imp/servicio_whatsapp/package.json" \
    "$repo/04_imp/servicio_whatsapp/package-lock.json" "$nueva/"
  (cd "$nueva" && npm ci --omit=dev --ignore-scripts --no-audit --no-fund --loglevel=error)
  chmod -R g+rX,o-rwx "$nueva"

  activar "$base" "$nueva"
  if ! sigue_activo; then
    echo "!! La versión $version no quedó corriendo." >&2
    sudo /usr/bin/journalctl -u churoviaje-whatsapp -n 40 --no-pager >&2 || true
    if [[ -n "$anterior" && -d "$anterior" ]]; then
      echo "== Volviendo a $(basename "$anterior")" >&2
      activar "$base" "$anterior"
    fi
    exit 1
  fi
  echo "== Listo: corre la versión $version"
  sudo /usr/bin/journalctl -u churoviaje-whatsapp -n 40 --no-pager | grep -o '{"evento":"[A-Z_]*"[^}]*}' | tail -n 5 | sed 's/^/   /' || true
  find "$base/releases" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -rn | tail -n +4 | cut -d' ' -f2- |
    while read -r vieja; do
      if [[ "$vieja" != "$nueva" && "$vieja" != "$anterior" ]]; then
        rm -rf -- "$vieja"
        echo "   Versión vieja borrada: $(basename "$vieja")"
      fi
    done
}

activar() {
  local base="$1" destino="$2"
  ln -sfn "$destino" "$base/actual.nuevo"
  mv -Tf "$base/actual.nuevo" "$base/actual"
  sudo /usr/bin/systemctl restart churoviaje-whatsapp
}

# Activo durante 30 segundos seguidos: si la configuración falla, systemd lo reinicia y deja de estar activo.
sigue_activo() {
  local _
  for _ in $(seq 1 15); do
    sleep 2
    sudo /usr/bin/systemctl is-active churoviaje-whatsapp > /dev/null || return 1
  done
  return 0
}

main "$@"
