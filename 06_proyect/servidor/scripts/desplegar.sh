#!/usr/bin/env bash
# Despliega en churoviaje-server una versión nueva del backend y del núcleo C++ (T-098, #60).
#
# Uso (como deploy):  bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/desplegar.sh [rama]
#   rama: la rama de GitHub que se despliega. Por omisión, develop.
#
# Qué hace:
#   1. Trae la rama de GitHub al clon ~/ChuroViajeInterno.
#   2. Compila el núcleo C++ (sin pruebas) y el backend (sin pruebas: ya corren en la CI).
#   3. Arma /opt/churoviaje/releases/<fecha>-<commit>/ con backend.jar y motor_nucleo.
#   4. Mueve el enlace /opt/churoviaje/actual a esa versión y reinicia el servicio.
#   5. Espera a que la salud responda UP y el núcleo esté corriendo. Si no pasa en
#      90 segundos, vuelve solo a la versión anterior.
#   6. Borra las versiones viejas y deja las 5 más recientes.
#
# Para volver a una versión anterior a mano: volver.sh, en esta misma carpeta.

main() {
  set -euo pipefail

  local rama="${1:-develop}"
  local repo="$HOME/ChuroViajeInterno"
  local base="/opt/churoviaje"
  local releases="$base/releases"
  local conservar=5

  if [[ "$(id -un)" != "deploy" ]]; then
    echo "Este script se corre como deploy (sudo -iu deploy)." >&2
    exit 1
  fi

  echo "== Trayendo $rama de GitHub"
  git -C "$repo" fetch --quiet origin "$rama"
  git -C "$repo" checkout --quiet --detach FETCH_HEAD
  local commit
  commit="$(git -C "$repo" rev-parse --short HEAD)"
  echo "   commit $commit: $(git -C "$repo" log -1 --format=%s)"

  echo "== Compilando el núcleo C++"
  cmake -S "$repo/04_imp/motor_cpp" -B "$repo/04_imp/motor_cpp/build" -DBUILD_TESTING=OFF > /dev/null
  cmake --build "$repo/04_imp/motor_cpp/build" -j2 --target motor_nucleo > /dev/null

  echo "== Compilando el backend"
  (cd "$repo/04_imp/backend_java" && bash mvnw -q -DskipTests package)
  local jar
  jar="$(ls "$repo"/04_imp/backend_java/target/backend-java-*.jar | head -n 1)"

  local version
  version="$(date +%Y%m%d-%H%M%S)-$commit"
  local nueva="$releases/$version"
  echo "== Armando la versión $version"
  mkdir -p "$nueva"
  cp "$jar" "$nueva/backend.jar"
  cp "$repo/04_imp/motor_cpp/build/motor_nucleo" "$nueva/motor_nucleo"

  local anterior=""
  if [[ -L "$base/actual" ]]; then
    anterior="$(readlink -f "$base/actual")"
  fi

  activar "$base" "$nueva"
  if ! esperar_salud; then
    echo "!! La versión $version no arrancó bien." >&2
    sudo /usr/bin/journalctl -u churoviaje-backend -n 80 --no-pager >&2 || true
    if [[ -n "$anterior" && -d "$anterior" ]]; then
      echo "== Volviendo a $(basename "$anterior")" >&2
      activar "$base" "$anterior"
      if esperar_salud; then
        echo "   Se volvió a $(basename "$anterior"); el despliegue falló." >&2
      else
        echo "!! Tampoco arrancó la versión anterior. Revisar el servicio a mano." >&2
      fi
    fi
    exit 1
  fi

  echo "== Listo: corre la versión $version"
  limpiar "$releases" "$conservar" "$nueva" "$anterior"
}

# Mueve el enlace actual a la carpeta indicada y reinicia el servicio.
# El enlace se cambia con mv -T para que nunca quede a medio cambiar.
activar() {
  local base="$1" destino="$2"
  ln -sfn "$destino" "$base/actual.nuevo"
  mv -Tf "$base/actual.nuevo" "$base/actual"
  sudo /usr/bin/systemctl restart churoviaje-backend
}

# Espera hasta 90 segundos a que la salud responda UP y el núcleo esté corriendo.
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

# Borra las versiones más viejas y deja las más recientes. Nunca borra la actual ni la anterior.
limpiar() {
  local releases="$1" conservar="$2" actual="$3" anterior="$4"
  local carpeta
  ls -1dt "$releases"/*/ | tail -n +"$((conservar + 1))" | while read -r carpeta; do
    carpeta="${carpeta%/}"
    if [[ "$carpeta" != "$actual" && "$carpeta" != "$anterior" ]]; then
      rm -rf -- "$carpeta"
      echo "   Versión vieja borrada: $(basename "$carpeta")"
    fi
  done
}

# Todo el script está dentro de funciones: bash lo lee completo antes de ejecutarlo,
# así que el git checkout puede cambiar este archivo sin romper la ejecución en curso.
main "$@"
