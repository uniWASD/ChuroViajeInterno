#!/usr/bin/env bash
# T-117: ejecutar en Oracle desde la carpeta copiada por el script Windows.
# No necesita sudo, Maven, Gradle ni abrir puertos.
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ "${1:-}" != "--consulta-real" && "${1:-}" != "--solo-simulacion" ]]; then
  echo "Uso: bash scripts/oracle_prueba_corta.sh --consulta-real|--solo-simulacion" >&2
  exit 2
fi
if ! command -v java >/dev/null 2>&1; then
  echo "FALTA JAVA 17+: consultar con Marcelo antes de instalar nada." >&2
  exit 3
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "FALTA PYTHON3: no se pueden correr las pruebas simuladas en Oracle." >&2
  exit 3
fi
java -version 2>&1
python3 --version 2>&1
printf '\n== Pruebas funcionales contra HTTP LOCAL SIMULADO en Oracle ==\n'
python3 -m unittest discover -s tests -v 2>&1 | tee pruebas-funcionales-oracle.txt
printf '\n== 72 h de calendario virtual, sin peticiones reales ==\n'
python3 scripts/simular_72h.py --salida datos-simulacion-virtual
if [[ "${1:-}" == "--solo-simulacion" ]]; then
  echo "Terminado: solo pruebas simuladas, sin consultar ANH."
  exit 0
fi
# Protección adicional frente a ejecuciones paralelas y repeticiones recientes
if ! command -v flock >/dev/null 2>&1; then
  echo "No existe flock: se omite la consulta real para evitar carreras entre ejecuciones." >&2
  exit 3
fi
exec 9>>"$HOME/.t117-anh-servidor.lock"
if ! flock -n 9; then
  echo "Ya hay otra consulta diagnóstica de T-117 en Oracle. No se ejecutará en paralelo." >&2
  exit 4
fi
if pgrep -f '[j]ava.*medicion-anh[.]jar' >/dev/null 2>&1; then
  echo "Ya hay un medidor Java T-117 ejecutándose en este servidor: no lanzar otro." >&2
  exit 4
fi
ultimo="$HOME/.t117-anh-ultima-ronda-utc"
ahora="$(date -u +%s)"
if [[ -f "$ultimo" ]]; then
  anterior="$(cat "$ultimo")"
  if [[ "$anterior" =~ ^[0-9]+$ ]] && (( ahora - anterior < 600 )); then
    echo "Protección de frecuencia: esperar $((600 - ahora + anterior)) s antes de otra ronda real." >&2
    exit 4
  fi
fi
# Reservar antes de intentar. Así, si el proceso falla, no se reintenta inmediatamente.
printf '%s\n' "$ahora" > "$ultimo"
printf '\n== Una única ronda real desde Oracle (3 GET, con pausas) ==\n'
java -jar ./medicion-anh.jar --origen oracle --horas 1 --salida ./datos-oracle-corto --una-vez > ./medicion-oracle-corto.log 2>&1 || {
  cat ./medicion-oracle-corto.log
  exit 5
}
cat ./medicion-oracle-corto.log
printf '\nDatos CSV y estado guardados en %s/datos-oracle-corto\n' "$PWD"
printf 'La ejecución de 1 ronda NO cumple las 24 horas reales de CP-157.\n'
