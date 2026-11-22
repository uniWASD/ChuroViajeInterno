#!/usr/bin/env bash
# Cambia la credencial de servicio (D-13) del backend y del servicio de WhatsApp/LLM (T-100, #68).
#
# Uso (como ubuntu):  sudo bash rotar-credencial-servicio.sh
#
# Genera un valor nuevo al azar, lo escribe en /etc/churoviaje/backend.env (SERVICE_CREDENTIAL) y, si existe,
# en /etc/churoviaje/whatsapp.env (SERVICE_TOKEN), y reinicia los dos servicios. El valor nunca aparece en
# pantalla ni en la línea de comandos de ningún proceso. La credencial anterior deja de valer en cuanto el
# backend reinicia.

main() {
  set -euo pipefail
  umask 077
  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0" >&2
    exit 1
  fi
  local backend="/etc/churoviaje/backend.env" whatsapp="/etc/churoviaje/whatsapp.env"
  NUEVA="$(openssl rand -hex 32)"
  export NUEVA
  reemplazar "$backend" SERVICE_CREDENTIAL
  [[ -f "$whatsapp" ]] && reemplazar "$whatsapp" SERVICE_TOKEN
  unset NUEVA

  echo "== Reiniciando el backend"
  systemctl restart churoviaje-backend
  local _
  for _ in $(seq 1 45); do
    sleep 2
    curl -sf http://127.0.0.1:8081/actuator/health | grep -q '"UP"' && break
  done
  echo "   salud: $(curl -s http://127.0.0.1:8081/actuator/health)"
  if [[ -f "$whatsapp" ]] && systemctl is-enabled --quiet churoviaje-whatsapp 2> /dev/null; then
    systemctl restart churoviaje-whatsapp
    echo "   servicio de WhatsApp/LLM: $(systemctl is-active churoviaje-whatsapp)"
  fi
  local a b
  a="$(grep -E '^SERVICE_CREDENTIAL=' "$backend" | cut -d= -f2- | sha256sum)"
  b="$( { grep -E '^SERVICE_TOKEN=' "$whatsapp" 2> /dev/null || echo "SERVICE_TOKEN=$(grep -E '^SERVICE_CREDENTIAL=' "$backend" | cut -d= -f2-)"; } | cut -d= -f2- | sha256sum)"
  echo "== Credencial cambiada. Backend y servicio usan la misma: $([[ "$a" == "$b" ]] && echo sí || echo NO)"
}

# Reemplaza la línea CLAVE=... del archivo con el valor de $NUEVA, conservando dueño y permisos.
reemplazar() {
  local archivo="$1" clave="$2" temporal
  temporal="$(mktemp "$archivo.XXXX")"
  CLAVE="$clave" awk 'BEGIN { hecho = 0 }
    index($0, ENVIRON["CLAVE"] "=") == 1 { print ENVIRON["CLAVE"] "=" ENVIRON["NUEVA"]; hecho = 1; next }
    { print }
    END { if (!hecho) print ENVIRON["CLAVE"] "=" ENVIRON["NUEVA"] }' "$archivo" > "$temporal"
  chown --reference="$archivo" "$temporal"
  chmod --reference="$archivo" "$temporal"
  mv -f "$temporal" "$archivo"
  echo "   $archivo: $clave nueva"
}

main "$@"
