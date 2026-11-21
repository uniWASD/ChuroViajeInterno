#!/usr/bin/env bash
# CP-145 (T-106, #86): POST /sesion/prueba funciona en el ambiente de pruebas y responde 403 en producción.
#
# Uso (como ubuntu, con sudo, con el backend de pruebas encendido):  sudo bash verificar-ambiente-pruebas.sh
#
# Lee la credencial de prueba del archivo de entorno sin mostrarla, y no imprime el JWT, ni IPs, ni llaves.
# Al final borra el conductor simulado que creó la comprobación.

main() {
  set -euo pipefail
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0" >&2
    exit 1
  fi
  local pruebas="https://pruebas.churoviaje.orelqu.com"
  local produccion="https://churoviaje.orelqu.com"
  local credencial cuerpo respuesta codigo
  credencial="$(grep -E '^SIMULACION_CREDENCIAL=' /etc/churoviaje/backend-pruebas.env | cut -d= -f2-)"
  cuerpo="$(mktemp)"
  respuesta="$(mktemp)"
  trap 'rm -f "$cuerpo" "$respuesta"' EXIT
  printf '{"credencial":"%s","pruebaId":"verificacion-cp145","usuario":1}' "$credencial" > "$cuerpo"

  echo "== Servicios"
  systemctl is-active churoviaje-backend churoviaje-backend-pruebas
  echo "   CPU máxima del backend de pruebas: $(systemctl show -p CPUQuotaPerSecUSec --value churoviaje-backend-pruebas) por segundo"
  echo "   Memoria máxima: $(systemctl show -p MemoryMax --value churoviaje-backend-pruebas) bytes"
  echo "== Puertos (deben estar solo en 127.0.0.1)"
  ss -tln | awk 'NR == 1 || /:809[01] /' | awk '{print "   " $4}'

  echo "== $pruebas/sesion/prueba (debe dar 200 y un JWT)"
  codigo="$(curl -s -o "$respuesta" -w '%{http_code}' -X POST "$pruebas/sesion/prueba" \
    -H 'Content-Type: application/json' --data-binary "@$cuerpo")"
  if grep -q '"jwt":"' "$respuesta"; then echo "   HTTP $codigo, JWT recibido: sí"; else echo "   HTTP $codigo, JWT recibido: no"; fi

  echo "== $produccion/sesion/prueba con la misma credencial (debe dar 403)"
  codigo="$(curl -s -o "$respuesta" -w '%{http_code}' -X POST "$produccion/sesion/prueba" \
    -H 'Content-Type: application/json' --data-binary "@$cuerpo")"
  echo "   HTTP $codigo, $(grep -o '"codigo":"[A-Z_]*"' "$respuesta" || echo 'sin código')"

  echo "== Limpieza: se borra el conductor simulado de la comprobación"
  printf '{"credencial":"%s"}' "$credencial" > "$cuerpo"
  curl -s -X POST "http://127.0.0.1:8090/simulacion/borrar" -H 'Content-Type: application/json' \
    --data-binary "@$cuerpo" | sed 's/^/   /'
  echo
  unset credencial
}

main "$@"
