#!/usr/bin/env bash
# Vincula de nuevo el número dedicado de WhatsApp (T-100, #68), por ejemplo si se perdió la sesión.
#
# Uso (como ubuntu, con el teléfono del número dedicado a mano):  sudo bash vincular-whatsapp.sh [--borrar-sesion]
#
# Detiene el servicio y lo corre en esta terminal, como churoviaje-wa y con el mismo archivo de entorno, para que
# muestre el código QR. En el teléfono: WhatsApp → Dispositivos vinculados → Vincular un dispositivo, y se escanea.
# Cuando el registro muestre {"evento":"WHATSAPP_CONECTADO"}, Ctrl+C: el script vuelve a encender el servicio.
# Con --borrar-sesion borra antes la sesión guardada (si quedó dañada o se cambió de número).

main() {
  set -euo pipefail
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0 [--borrar-sesion]" >&2
    exit 1
  fi
  systemctl stop churoviaje-whatsapp
  if [[ "${1:-}" == "--borrar-sesion" ]]; then
    rm -f /var/lib/churoviaje-whatsapp/sesion/session.sqlite*
    echo "== Sesión anterior borrada"
  fi
  echo "== Escanear el QR con el teléfono del número dedicado. Ctrl+C cuando diga WHATSAPP_CONECTADO."
  # Mismo usuario, mismo entorno y misma carpeta de estado que el servicio; la terminal solo muestra el QR y el registro.
  systemd-run --pty --wait --collect --uid=churoviaje-wa --gid=churoviaje-wa \
    --property=EnvironmentFile=/etc/churoviaje/whatsapp.env \
    --property=StateDirectory=churoviaje-whatsapp --property=StateDirectoryMode=0700 --property=UMask=0077 \
    --working-directory=/opt/churoviaje-whatsapp/actual /usr/bin/node src/main.js || true
  systemctl start churoviaje-whatsapp
  echo "== Servicio encendido de nuevo: $(systemctl is-active churoviaje-whatsapp)"
}

main "$@"
