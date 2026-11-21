#!/usr/bin/env bash
# Prepara el servicio de WhatsApp/LLM en churoviaje-server (T-100, #68).
#
# Uso (como ubuntu):  sudo bash crear-servicio-whatsapp.sh
#
# Qué hace (se puede correr más de una vez; lo que ya existe no se toca):
#   1. Crea el usuario de sistema churoviaje-wa y las carpetas /opt/churoviaje-whatsapp (de deploy, grupo
#      churoviaje-wa) y /etc/churoviaje/whatsapp (0700, para el registro de autorizaciones).
#   2. Escribe /etc/churoviaje/whatsapp.env (0600, churoviaje-wa). Pide en la terminal, sin mostrarlos, la clave
#      de Gemini, el número dedicado y los grupos autorizados. La credencial de servicio la copia de
#      /etc/churoviaje/backend.env: es el mismo valor (D-13) y no se ve en pantalla.
#   3. Instala el servicio (sin encenderlo) y deja que deploy lo reinicie y vea su registro al desplegar.
# Nunca imprime un secreto.

main() {
  set -euo pipefail
  umask 077
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0" >&2
    exit 1
  fi
  local repo="/home/deploy/ChuroViajeInterno/06_proyect/servidor"
  local env="/etc/churoviaje/whatsapp.env"

  echo "== 1. Usuario y carpetas"
  if ! id churoviaje-wa > /dev/null 2>&1; then
    adduser --system --group --no-create-home --home /var/lib/churoviaje-whatsapp churoviaje-wa > /dev/null
  fi
  # setgid (2750): lo que deploy copia en releases queda del grupo churoviaje-wa, que así lo puede leer.
  install -d -o deploy -g churoviaje-wa -m 2750 /opt/churoviaje-whatsapp /opt/churoviaje-whatsapp/releases
  install -d -o churoviaje-wa -g churoviaje-wa -m 700 /etc/churoviaje/whatsapp
  id churoviaje-wa

  echo "== 2. Archivo de entorno"
  if [[ -f "$env" ]]; then
    echo "   $env ya existe: no se toca (para cambiar un valor: sudo nano $env)."
  else
    local clave numero grupos credencial
    read -rsp "   Clave de Gemini (LLM_API_KEY, no se muestra): " clave; echo
    read -rp "   Número dedicado, solo dígitos con el código de país (591...): " numero
    read -rp "   IDs de los grupos autorizados, separados por comas (...@g.us): " grupos
    credencial="$(grep -E '^SERVICE_CREDENTIAL=' /etc/churoviaje/backend.env | cut -d= -f2-)"
    if [[ -z "$credencial" ]]; then
      echo "!! /etc/churoviaje/backend.env no tiene SERVICE_CREDENTIAL" >&2
      exit 1
    fi
    install -m 600 -o churoviaje-wa -g churoviaje-wa /dev/null "$env"
    cat > "$env" <<ENV
BACKEND_URL=http://127.0.0.1:8080
SERVICE_TOKEN=$credencial
ALLOWED_GROUP_IDS=$grupos
SQLITE_PATH=/var/lib/churoviaje-whatsapp/pendientes.sqlite
WHATSAPP_ENABLED=true
WHATSAPP_PHONE_NUMBER=$numero
WHATSAPP_AUTHORIZATIONS_FILE=/etc/churoviaje/whatsapp/autorizaciones.json
WHATSAPP_SESSION_PATH=/var/lib/churoviaje-whatsapp/sesion/session.sqlite
LLM_API_KEY=$clave
ENV
    unset clave credencial
    echo "   $env escrito (0600, churoviaje-wa)."
  fi
  if [[ ! -f /etc/churoviaje/whatsapp/autorizaciones.json ]]; then
    echo "   Falta el registro de autorizaciones de los administradores de cada grupo:"
    echo "     sudo install -m 600 -o churoviaje-wa -g churoviaje-wa /dev/null /etc/churoviaje/whatsapp/autorizaciones.json"
    echo "     sudo nano /etc/churoviaje/whatsapp/autorizaciones.json   (formato en 04_imp/servicio_whatsapp/README.md)"
  fi

  echo "== 3. Servicio"
  install -m 644 -o root -g root "$repo/systemd/churoviaje-whatsapp.service" /etc/systemd/system/
  systemctl daemon-reload
  local sudoers="/etc/sudoers.d/churoviaje-deploy-whatsapp" nuevo
  nuevo="$(mktemp)"
  printf '%s\n' 'deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart churoviaje-whatsapp, /usr/bin/systemctl is-active churoviaje-whatsapp, /usr/bin/journalctl -u churoviaje-whatsapp -n 40 --no-pager' > "$nuevo"
  visudo -cf "$nuevo" > /dev/null
  install -m 440 -o root -g root "$nuevo" "$sudoers"
  rm -f "$nuevo"
  echo "   Instalado, sin encender. Siguiente: desplegar como deploy y vincular el número (guía, sección 13)."
}

main "$@"
