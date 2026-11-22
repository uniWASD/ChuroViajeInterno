#!/usr/bin/env bash
# Prepara el ambiente de pruebas del simulador en churoviaje-server (T-106, #86).
#
# Uso (como ubuntu, con sudo):  sudo bash crear-ambiente-pruebas.sh
#
# Qué hace (se puede correr más de una vez; lo que ya existe no se toca):
#   1. Crea el usuario de sistema churoviaje-pruebas, que lee /opt/churoviaje por el grupo churoviaje
#      pero no puede leer los secretos de producción.
#   2. Crea en PostgreSQL el usuario churoviaje_pruebas_app (sin permisos de administrador) y la base
#      churoviaje_pruebas con PostGIS. Su contraseña se genera al azar y solo queda en el archivo de entorno.
#   3. Escribe /etc/churoviaje/backend-pruebas.env con secretos nuevos (JWT, credencial de servicio y
#      credencial de prueba del simulador), el perfil pruebas y los puertos 8090 y 8091.
#   4. Instala el servicio churoviaje-backend-pruebas (sin habilitarlo al arrancar la máquina).
#   5. Deja que deploy reinicie el backend de pruebas al desplegar, si está encendido.
# Nunca imprime un secreto.

main() {
  set -euo pipefail
  umask 077

  if [[ "$(id -u)" -ne 0 ]]; then
    echo "Correr con sudo: sudo bash $0" >&2
    exit 1
  fi
  local repo="/home/deploy/ChuroViajeInterno/06_proyect/servidor"
  local env_prod="/etc/churoviaje/backend.env"
  local env_pruebas="/etc/churoviaje/backend-pruebas.env"

  echo "== 1. Usuario del servicio"
  if ! id churoviaje-pruebas > /dev/null 2>&1; then
    adduser --system --group --no-create-home --home /opt/churoviaje churoviaje-pruebas > /dev/null
  fi
  usermod -aG churoviaje churoviaje-pruebas
  id churoviaje-pruebas

  echo "== 2. Base de datos churoviaje_pruebas"
  local clave_bd=""
  if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname = 'churoviaje_pruebas_app'" | grep -q 1; then
    clave_bd="$(openssl rand -hex 24)"
    # La contraseña va por la entrada estándar y no en la línea de comandos, que otros usuarios pueden ver.
    runuser -u postgres -- psql -q -v ON_ERROR_STOP=1 <<SQL
CREATE ROLE churoviaje_pruebas_app LOGIN PASSWORD '$clave_bd' NOSUPERUSER NOCREATEDB NOCREATEROLE;
SQL
    echo "   Usuario churoviaje_pruebas_app creado."
  else
    echo "   El usuario churoviaje_pruebas_app ya existía."
  fi
  if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname = 'churoviaje_pruebas'" | grep -q 1; then
    runuser -u postgres -- createdb --owner=churoviaje_pruebas_app churoviaje_pruebas
    echo "   Base churoviaje_pruebas creada."
  fi
  runuser -u postgres -- psql -q -d churoviaje_pruebas -c "CREATE EXTENSION IF NOT EXISTS postgis;"
  runuser -u postgres -- psql -tAc "SELECT 'PostGIS ' || postgis_lib_version()" -d churoviaje_pruebas

  echo "== 3. Archivo de entorno"
  if [[ -f "$env_pruebas" ]]; then
    echo "   $env_pruebas ya existe: no se toca. Para regenerarlo, borrarlo y volver a correr el script"
    echo "   (también hay que cambiar la contraseña de churoviaje_pruebas_app, porque solo vive ahí)."
  elif [[ -z "$clave_bd" ]]; then
    echo "!! El usuario de la base ya existía pero no hay archivo de entorno: no se conoce su contraseña." >&2
    echo "   Cambiarla con: sudo -u postgres psql -c \"\\password churoviaje_pruebas_app\" y escribir el archivo a mano." >&2
    exit 1
  else
    local gcid
    gcid="$(grep -E '^GOOGLE_CLIENT_ID=' "$env_prod" | cut -d= -f2-)"
    install -m 600 -o churoviaje-pruebas -g churoviaje-pruebas /dev/null "$env_pruebas"
    cat > "$env_pruebas" <<ENV
SPRING_PROFILES_ACTIVE=pruebas
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8090
MANAGEMENT_SERVER_PORT=8091
DB_URL=jdbc:postgresql://127.0.0.1:5432/churoviaje_pruebas
DB_USER=churoviaje_pruebas_app
DB_PASSWORD=$clave_bd
CHUROVIAJE_GEOJSON_PATH=/opt/churoviaje/datos/mancha_urbana_tarija.geojson
JWT_SECRET=$(openssl rand -base64 48)
GOOGLE_CLIENT_ID=$gcid
SERVICE_CREDENTIAL=$(openssl rand -hex 32)
SIMULACION_CREDENCIAL=$(openssl rand -hex 32)
SIMULACION_REGISTRO_PATH=/var/log/churoviaje-pruebas-carga/pruebas-carga.log
MOTOR_HABILITADO=true
MOTOR_PATH=/opt/churoviaje/actual/motor_nucleo
RED_VIAL_PATH=/opt/churoviaje/datos/red_vial_tarija.json
LUGARES_PATH=/opt/churoviaje/datos/lugares_tarija.json
ENV
    echo "   $env_pruebas escrito con secretos nuevos (permisos 600, dueño churoviaje-pruebas)."
  fi
  unset clave_bd

  echo "== 4. Servicio"
  install -m 644 -o root -g root "$repo/systemd/churoviaje-backend-pruebas.service" /etc/systemd/system/
  systemctl daemon-reload
  echo "   Instalado. Encender: sudo systemctl start churoviaje-backend-pruebas"

  echo "== 5. Permiso de deploy"
  local sudoers="/etc/sudoers.d/churoviaje-deploy-pruebas"
  local nuevo
  nuevo="$(mktemp)"
  echo 'deploy ALL=(root) NOPASSWD: /usr/bin/systemctl try-restart churoviaje-backend-pruebas' > "$nuevo"
  visudo -cf "$nuevo" > /dev/null
  install -m 440 -o root -g root "$nuevo" "$sudoers"
  rm -f "$nuevo"
  echo "   deploy puede reiniciar el backend de pruebas solo si ya está encendido (try-restart)."
  echo "== Listo"
}

main "$@"
