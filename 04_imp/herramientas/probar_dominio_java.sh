#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
DIRECTORIO_TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$DIRECTORIO_TEMPORAL"' EXIT
mapfile -d '' CODIGOS < <(find backend_java/src/main/java/bo/edu/ucb/churoviaje/dominio -name '*.java' -print0)
javac --release 21 -encoding UTF-8 -d "$DIRECTORIO_TEMPORAL" "${CODIGOS[@]}" herramientas/PruebasDominioT089.java
java -cp "$DIRECTORIO_TEMPORAL" PruebasDominioT089
