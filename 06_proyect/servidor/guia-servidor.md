# Guía del servidor de ChuroViaje

Guía de la instancia de Oracle Cloud donde se despliega ChuroViaje. Cubre los issues T-092 (#44), T-119 (#111), T-097 (#52) y T-098 (#60). Se actualiza a medida que se avanza.

Última actualización: 12 de noviembre de 2026.

> Esta guía no lleva contraseñas, llaves ni la IP del servidor. La IP pública se comparte por el chat del equipo. Donde aparece `<IP_PUBLICA>`, se reemplaza por esa IP.

## Estado

| Paso | Estado |
|---|---|
| IP pública reservada y asignada a la instancia | Hecho |
| Sistema actualizado y reiniciado | Hecho |
| Usuario `deploy` con una llave por integrante | Hecho. Marcelo, Franz, Alex y Jorge ya tienen su llave y Franz ya entró con la suya. La de Antonio se agrega más adelante (T-103 y T-106) |
| SSH solo con llave, sin root | Hecho y verificado |
| Reglas de la VCN y del firewall de Ubuntu | Hecho. Confirmado el 23/10/2026 en la consola: la instancia no tiene ningún NSG asignado. El 12/11/2026 se abrieron el 80 y el 443 (T-097) |
| Actualizaciones automáticas de seguridad | Hecho (ya venían activas; se comprobó la configuración) |
| Java 21 y herramientas de C++ (ARM) | Hecho. OpenJDK 21.0.12, g++ 13.3 y CMake 3.28 sobre aarch64 |
| Node.js LTS | Hecho. Node.js 24.22 (LTS) y npm 11.19, desde el repositorio de NodeSource |
| Git en el servidor | Hecho. Instalado; comprobado con `git --version` el 23/10/2026 |
| PostgreSQL y PostGIS, solo local | Hecho. PostgreSQL 16 con PostGIS 3.4; solo escucha en 127.0.0.1 |
| Base de datos `churoviaje` y su usuario sin permisos de administrador | Hecho. Usuario `churoviaje_app`, sin atributos de administrador |
| CP-27 (desde fuera solo responde SSH) | Hecho el 20/10/2026. Desde el 12/11/2026 también responden el 80 y el 443, que atiende Nginx (T-097) |
| CP-28 (desde fuera el puerto de PostgreSQL no responde) | Hecho el 20/10/2026, con PostgreSQL instalado y corriendo |
| Salida hacia la API de ANH con el certificado verificado | Hecho el 29/10/2026 (T-119). Detalle en la sección «Salida hacia la API de ANH» |
| CP-156 (desde la instancia, las estaciones de Tarija de gasolina responden con el certificado verificado) | Hecho el 29/10/2026 |
| Dominio `churoviaje.orelqu.com` apuntando a la IP reservada | Hecho el 12/11/2026 (T-097) |
| Nginx como proxy inverso hacia el backend | Hecho el 12/11/2026. Nginx 1.24 |
| Certificado de Let's Encrypt con renovación automática | Hecho el 12/11/2026. Vence el 10/02/2027 y se renueva solo; la renovación de prueba pasó |
| Nota de SSL Labs | A, el 12/11/2026, con TLS 1.3 |
| CP-51 (HTTP redirige a HTTPS y el certificado es válido en un teléfono) | Hecho el 12/11/2026 |
| Backend y núcleo C++ como servicio del sistema | Hecho el 12/11/2026 (T-098). Servicio `churoviaje-backend`, corre como el usuario `churoviaje` |
| Secretos del backend en un archivo de entorno | Hecho el 12/11/2026. `/etc/churoviaje/backend.env`, solo lo lee `churoviaje` |
| Script de despliegue y de vuelta atrás | Hecho el 12/11/2026. `scripts/desplegar.sh` y `scripts/volver.sh` |
| Carga de la red vial en el servidor | 244 ms, el 12/11/2026 (criterio de T-096) |
| CP-79 (tras reiniciar, el backend y el núcleo vuelven solos) | Hecho el 12/11/2026. Falta probar el inicio de sesión desde la app |
| CP-80 (al matar el núcleo, el backend lo vuelve a arrancar) | Hecho el 12/11/2026 |

## Datos del servidor

- Nombre de la instancia: `churoviaje-server`
- Proveedor y región: Oracle Cloud, `us-chicago-1`
- Imagen: Ubuntu 24.04 LTS, arquitectura ARM (aarch64)
- Tamaño: 2 OCPU y 12 GB de RAM (capa gratuita)
- IP pública: reservada, no cambia al reiniciar
- Dominio: `churoviaje.orelqu.com`, un subdominio de `orelqu.com` (dominio de Marcelo, registrado en Cloudflare) ([D-26](../../03_des/02_Arquitectura/08_decisiones_parametros.md))
- Puerto del backend: 8080, solo para la propia máquina (`SERVER_ADDRESS=127.0.0.1`). La salud del backend está en `127.0.0.1:8081`, que tampoco se ve desde fuera. Desde internet se llega por Nginx, con HTTPS. El servicio de WhatsApp lo llama en `127.0.0.1:8080` ([contrato](../../03_des/02_Arquitectura/05_contratos.md))

## Usuarios y acceso

| Usuario | Para qué | Quién entra |
|---|---|---|
| `ubuntu` | Administrar el servidor (tiene `sudo`) | Marcelo, con su llave `proyectos-key` |
| `deploy` | Desplegar la aplicación (sin `sudo`, salvo reiniciar el backend y ver su registro; sin contraseña) | Cada integrante que despliega, con su propia llave |
| `churoviaje` | Correr el backend y el núcleo (usuario de sistema, sin login ni llave) | Nadie: lo usa systemd |

Solo se entra con llave. No hay acceso con contraseña ni como root.

### Generar la llave (en la laptop de cada integrante, no en el servidor)

```powershell
ssh-keygen -t ed25519 -f $HOME\.ssh\churoviaje-deploy -C "nombre"
type $HOME\.ssh\churoviaje-deploy.pub
```

Se comparte solo lo que muestra el `type` (la línea que empieza con `ssh-ed25519`). El archivo sin `.pub` es la llave privada y no se comparte con nadie.

### Conectarse

```powershell
ssh -i $HOME\.ssh\churoviaje-deploy deploy@<IP_PUBLICA>
```

### Agregar o quitar la llave de alguien

Desde el usuario `ubuntu`:

```bash
# agregar (reemplazar por la línea .pub de la persona)
echo 'ssh-ed25519 AAAA... nombre' | sudo tee -a /home/deploy/.ssh/authorized_keys

# quitar: borrar la línea de esa persona (se reconoce por el nombre del final)
sudo nano /home/deploy/.ssh/authorized_keys
```

## Cómo se configuró

### 1. IP pública reservada

En la consola de Oracle Cloud se reservó una IP pública y se asignó a `churoviaje-server`. Desde ese momento la IP no cambia. La primera vez que se entra con la IP nueva, SSH avisa que no conoce el servidor; la huella es la misma de antes, así que es normal responder `yes`.

### 2. Actualizar el sistema

```bash
sudo apt update && sudo apt upgrade -y
sudo reboot
```

### 3. Usuario de despliegue

```bash
sudo adduser --disabled-password --gecos "" deploy
sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
# pegar en authorized_keys la llave pública de cada integrante, una por línea
sudo nano /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

El usuario no tiene contraseña ni `sudo`. Solo puede reiniciar el backend y ver su registro, para desplegar (sección 10).

### 4. SSH solo con llave

Se creó un archivo de configuración propio. Lleva el prefijo `00-` para que tenga prioridad sobre el `50-cloud-init.conf` que trae la imagen, porque SSH usa el primer valor que encuentra.

```bash
sudo tee /etc/ssh/sshd_config.d/00-hardening.conf > /dev/null <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
AllowUsers ubuntu deploy
MaxAuthTries 3
EOF
sudo sshd -t && sudo systemctl reload ssh
sudo sshd -T | grep -E 'passwordauthentication|kbdinteractiveauthentication|permitrootlogin|allowusers'
```

Antes de cerrar la sesión de administración se prueba el acceso desde otra ventana. Si algo sale mal, la sesión abierta permite corregirlo.

### 5. Puertos y firewall

Hay dos capas y las dos dejan abiertos solo los puertos 22, 80 y 443:

- **Red de Oracle (VCN):** la instancia está en `public subnet-proyectos-vcn`, que usa la `Default Security List for proyectos-vcn`. Esa lista permite TCP 22, 80 y 443 desde internet, y los dos ICMP de siempre (descubrimiento de MTU y errores internos). No hay regla para el 5432. La `security list for private subnet-proyectos-vcn` es de la otra subred y no se toca.
- **Firewall del servidor:** la imagen de Oracle trae reglas de `iptables` que dejan pasar solo SSH, las respuestas a conexiones ya abiertas, ICMP y el tráfico local, y rechazan el resto con una regla `REJECT` al final. El 80 y el 443 se agregaron justo antes de esa regla (sección 9). Se guardan con `netfilter-persistent`, así que sobreviven a los reinicios. No se instaló `ufw`: duplicaría las reglas y podría pisarlas. No se tocan las reglas `InstanceServices`, que Oracle necesita para que el servidor hable con sus servicios internos.

Para abrir un puerto más adelante hay que abrirlo en los dos lados.

Se apagó `rpcbind` (puerto 111), que no se usa y estaba escuchando en todas las interfaces:

```bash
sudo systemctl disable --now rpcbind.service rpcbind.socket
```

Hacia afuera solo escuchan SSH y Nginx.

### 6. Actualizaciones automáticas de seguridad

`unattended-upgrades` ya viene instalado y activo en la imagen. Se comprobó que:

- `/etc/apt/apt.conf.d/20auto-upgrades` tiene `Update-Package-Lists` y `Unattended-Upgrade` en `"1"`.
- El temporizador `apt-daily-upgrade.timer` está activo.
- La configuración incluye los orígenes de seguridad de Ubuntu (`-security`, `-apps-security` e `-infra-security`).
- El reinicio automático no está activado (es el valor por defecto). Cuando una actualización lo necesite aparece el archivo `/var/run/reboot-required`, y el reinicio se hace a mano a una hora que no moleste.

### 7. PostgreSQL y PostGIS

```bash
sudo apt install -y postgresql postgresql-contrib postgresql-16-postgis-3
sudo -u postgres psql -c "SHOW listen_addresses;"
sudo ss -tlnp | grep 5432
```

Ubuntu 24.04 instala PostgreSQL 16. Por defecto solo escucha en la propia máquina: `listen_addresses` dice `localhost` y el puerto 5432 aparece solo en `127.0.0.1`. No hubo que tocar el firewall ni la VCN.

Usuario de la aplicación y base de datos:

```bash
sudo -u postgres createuser --pwprompt --no-superuser --no-createdb --no-createrole churoviaje_app
sudo -u postgres createdb --owner=churoviaje_app churoviaje
sudo -u postgres psql -d churoviaje -c "CREATE EXTENSION postgis;"
```

La contraseña se escribe en la terminal cuando la pide y se guarda en un gestor de contraseñas; no va en el repositorio ni en el chat. La aplicación la lee de una variable de entorno. La extensión PostGIS la crea el administrador de PostgreSQL porque lo requiere; el usuario de la aplicación solo la usa.

Verificación:

```bash
sudo -u postgres psql -c "\du churoviaje_app"
sudo -u postgres psql -d churoviaje -c "SELECT postgis_version();"
```

`\du` no debe mostrar ningún atributo (`Superuser`, `Create role`, `Create DB`), y la consulta debe devolver la versión de PostGIS.

### 8. Node.js

Se instaló la versión 24, una versión LTS que pasó a mantenimiento el 20 de octubre de 2026 y recibe correcciones de seguridad hasta el 30 de abril de 2028, desde el repositorio de NodeSource para ARM. El equipo mantiene esa versión fija hasta el 30 de noviembre de 2026 ([D-27](../../03_des/02_Arquitectura/08_decisiones_parametros.md)), para que todos prueben con la misma. La instalación se hizo paso a paso, sin ejecutar el script `curl | bash`, para ver qué se agrega al sistema.

```bash
sudo apt-get install -y ca-certificates curl gnupg
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | sudo gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_24.x nodistro main" | sudo tee /etc/apt/sources.list.d/nodesource.list
sudo apt-get update
apt-cache policy nodejs
sudo apt-get install -y nodejs
node --version
npm --version
```

El `apt-cache policy` debe mostrar una versión `24.x` con origen `deb.nodesource.com` (Ubuntu también ofrece una versión 18 antigua, que no se usa).

Las actualizaciones automáticas de seguridad no cubren este repositorio, solo los paquetes de Ubuntu. Node se actualiza a mano:

```bash
sudo apt-get update && sudo apt-get upgrade
```

Para cambiar a otra versión mayor (por ejemplo la 26) se cambia el número en `node_24.x` dentro de `/etc/apt/sources.list.d/nodesource.list` y se actualiza.

### 9. Dominio, Nginx y HTTPS

Hecho el 12 de noviembre de 2026 (T-097, #52). Nginx recibe las peticiones de internet en el 80 y el 443 y las pasa al backend, que solo escucha en `127.0.0.1:8080`.

**Dominio.** `orelqu.com` está registrado en Cloudflare. En su panel, **DNS → Records**, hay un registro:

| Tipo | Nombre | Valor | Proxy |
|---|---|---|---|
| `A` | `churoviaje` | `<IP_PUBLICA>` | DNS only (nube gris) |

El proxy de Cloudflare queda apagado a propósito: con la nube naranja, Cloudflare se pondría delante del servidor con su propio certificado, y el certificado de Let's Encrypt de Nginx dejaría de ser el que ve la app.

**Puertos.** En la VCN se agregaron a la `Default Security List for proyectos-vcn` dos reglas de entrada: TCP 80 y TCP 443, desde `0.0.0.0/0`. En el servidor se agregaron antes de la regla `REJECT`, que estaba en la posición 5:

```bash
sudo iptables -L INPUT -n --line-numbers   # ver en qué línea está el REJECT
sudo iptables -I INPUT 5 -p tcp -m state --state NEW --dport 443 -j ACCEPT
sudo iptables -I INPUT 5 -p tcp -m state --state NEW --dport 80 -j ACCEPT
sudo netfilter-persistent save
```

Quedan en orden 22, 80, 443 y el `REJECT` al final.

**Nginx y certbot.**

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
```

El sitio está en `/etc/nginx/sites-available/churoviaje`, enlazado desde `sites-enabled`; el sitio `default` de Nginx se quitó. La copia del archivo, tal como quedó después de certbot, está en [nginx/churoviaje.conf](nginx/churoviaje.conf). Antes de certbot solo tenía el bloque `server` del puerto 80 con el `location /` que pasa al backend.

```bash
sudo ln -s /etc/nginx/sites-available/churoviaje /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

Para que Nginx no muestre su versión en ninguna respuesta, se activó `server_tokens off` en `/etc/nginx/nginx.conf`, que vale para todos los sitios:

```bash
sudo sed -i 's/# server_tokens off;/server_tokens off;/' /etc/nginx/nginx.conf
sudo nginx -t && sudo systemctl reload nginx
```

El certificado se pidió con el complemento de Nginx de certbot, que además agrega el bloque del 443 y la redirección de HTTP a HTTPS:

```bash
sudo certbot --nginx -d churoviaje.orelqu.com --redirect -m <CORREO> --agree-tos --no-eff-email
```

`<CORREO>` es el correo donde Let's Encrypt avisa si la renovación falla.

**Renovación.** Certbot instala el temporizador `certbot.timer`, que revisa dos veces al día y renueva cuando faltan menos de 30 días. Para comprobarlo:

```bash
sudo certbot renew --dry-run
systemctl list-timers | grep certbot
```

El 80 queda abierto solo para redirigir a HTTPS y para la validación de Let's Encrypt al renovar.

### 10. Backend y núcleo C++ como servicio

Hecho el 12 de noviembre de 2026 (T-098, #60). El backend corre como un servicio de systemd y arranca el núcleo C++ como proceso hijo ([D-15](../../03_des/02_Arquitectura/08_decisiones_parametros.md)), así que un solo servicio cubre los dos.

**Dónde está cada cosa**

| Ruta | Qué hay | Dueño |
|---|---|---|
| `/opt/churoviaje/releases/<fecha>-<commit>/` | Una versión: `backend.jar` y `motor_nucleo`. Se guardan las 5 más recientes | `deploy` |
| `/opt/churoviaje/actual` | Enlace a la versión que corre | `deploy` |
| `/opt/churoviaje/datos/` | `red_vial_tarija.json` (fuera de Git, D-33) y `mancha_urbana_tarija.geojson` | `deploy` |
| `/etc/churoviaje/backend.env` | Secretos y configuración del backend | `churoviaje`, permisos `600` |
| `/etc/systemd/system/churoviaje-backend.service` | El servicio. Copia en [systemd/churoviaje-backend.service](systemd/churoviaje-backend.service) | `root` |
| `/home/deploy/ChuroViajeInterno` | Clon del repositorio desde el que se compila | `deploy` |

`/opt/churoviaje` y sus carpetas son de `deploy` con el grupo `churoviaje` (permisos `750`): `deploy` despliega y `churoviaje` solo lee.

**Usuario del servicio.** El servicio corre como `churoviaje`, un usuario de sistema sin login, que es el único que puede leer los secretos. `deploy` compila y despliega, pero no los ve.

```bash
sudo adduser --system --group --no-create-home --home /opt/churoviaje churoviaje
sudo install -d -o deploy -g churoviaje -m 750 /opt/churoviaje /opt/churoviaje/releases /opt/churoviaje/datos
sudo install -d -o root -g root -m 755 /etc/churoviaje
```

**Archivo de entorno.** Tiene el perfil `prod`, la base, la ruta del GeoJSON, los secretos ([D-34](../../03_des/02_Arquitectura/08_decisiones_parametros.md)) y el núcleo ([D-35](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). La contraseña de `churoviaje_app` y el Client ID web de Google se escriben en la terminal con `read`, sin que queden en pantalla ni en el historial; la clave del JWT y la credencial de servicio se generan al azar ahí mismo.

```bash
read -rp "GOOGLE_CLIENT_ID: " GCID
read -rsp "Contraseña de churoviaje_app: " DBPASS; echo
sudo install -m 600 -o churoviaje -g churoviaje /dev/null /etc/churoviaje/backend.env
sudo tee /etc/churoviaje/backend.env > /dev/null <<EOF
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
DB_URL=jdbc:postgresql://127.0.0.1:5432/churoviaje
DB_USER=churoviaje_app
DB_PASSWORD=$DBPASS
CHUROVIAJE_GEOJSON_PATH=/opt/churoviaje/datos/mancha_urbana_tarija.geojson
JWT_SECRET=$(openssl rand -base64 48)
GOOGLE_CLIENT_ID=$GCID
SERVICE_CREDENTIAL=$(openssl rand -hex 32)
MOTOR_HABILITADO=true
MOTOR_PATH=/opt/churoviaje/actual/motor_nucleo
RED_VIAL_PATH=/opt/churoviaje/datos/red_vial_tarija.json
EOF
unset GCID DBPASS
```

- Si se cambia la contraseña de `churoviaje_app` (`sudo -u postgres psql -c "\password churoviaje_app"`), hay que cambiarla también aquí. Conviene que no tenga comillas ni barras invertidas, porque systemd las interpreta.
- `SERVICE_CREDENTIAL` es el mismo valor que el servicio de WhatsApp usa como `SERVICE_TOKEN` (D-13). Se copia desde este archivo cuando se despliegue ese servicio (T-100).
- Para cambiar un valor: `sudo nano /etc/churoviaje/backend.env` y `sudo systemctl restart churoviaje-backend`.

**Servicio.** El archivo está en [systemd/churoviaje-backend.service](systemd/churoviaje-backend.service). Arranca con la máquina, se reinicia a los 5 segundos si el backend se cae, y está endurecido: solo lee `/opt/churoviaje`, escribe en su `/tmp` privado y no puede ganar privilegios.

```bash
sudo cp /home/deploy/ChuroViajeInterno/06_proyect/servidor/systemd/churoviaje-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now churoviaje-backend
```

Si el núcleo se cae, el backend lo vuelve a arrancar solo (`motor.espera-reinicio`). Si se cae el backend, lo vuelve a arrancar systemd.

**Permiso de `deploy`.** Para desplegar, `deploy` puede reiniciar el backend y ver su registro, y nada más:

```bash
echo 'deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart churoviaje-backend, /usr/bin/journalctl -u churoviaje-backend -n 80 --no-pager' > /tmp/churoviaje-deploy
sudo visudo -cf /tmp/churoviaje-deploy && sudo install -m 440 -o root -g root /tmp/churoviaje-deploy /etc/sudoers.d/churoviaje-deploy
rm /tmp/churoviaje-deploy
```

`visudo -cf` revisa el archivo antes de instalarlo: un error en `sudoers.d` dejaría sin `sudo` al servidor.

**Desplegar una versión nueva.** Como `deploy`, desde `develop` (o desde otra rama, pasando su nombre):

```bash
bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/desplegar.sh
```

El script trae la rama, compila el núcleo y el backend sin pruebas (ya corren en la CI), arma la versión en `releases`, mueve `actual` a ella y reinicia el servicio. Después espera hasta 90 segundos a que la salud responda `UP` y el núcleo esté corriendo; si no pasa, vuelve solo a la versión anterior y termina con error. La primera compilación del backend tarda unos minutos porque Maven descarga sus dependencias.

Desde el sprint 4, cada sprint termina con lo fusionado en `develop` desplegado.

**Volver a una versión anterior.**

```bash
bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/volver.sh --lista      # ver las versiones
bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/volver.sh              # la anterior a la actual
bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/volver.sh <versión>    # una concreta
```

No compila nada: mueve `actual` y reinicia. Si la versión elegida no arranca, deja la que había.

**Revisar el servicio** (como `ubuntu`):

```bash
systemctl status churoviaje-backend --no-pager
sudo journalctl -u churoviaje-backend -n 100 --no-pager
curl -s http://127.0.0.1:8081/actuator/health
```

Los mensajes del núcleo aparecen en el registro del backend con el nivel `WARN`, aunque sean normales, porque el wrapper registra así todo lo que el núcleo escribe en su salida de errores.

## Salida hacia la API de ANH

Antes de que el backend dependa de ANH, el 29 de octubre de 2026 se comprobó desde la instancia que las consultas llegan y que el certificado se verifica. Cubre el issue T-119 (#111) y el caso de prueba CP-156. El informe técnico de ANH y el cliente de prueba están en `05_pro/PruebaApiANH`.

| Prueba | Resultado |
|---|---|
| Catálogo de departamentos, con `curl` | 200, `decCodigo` 1, certificado verificado, 0,67 s |
| Estaciones de Tarija de gasolina, con `curl` | 200, certificado verificado, con estaciones y saldo. Entre 0,56 y 0,90 s |
| Cliente Python, con el almacén de certificados del sistema | Certificado verificado. 31 estaciones de Tarija con su saldo |
| Cliente Python, con `certifi` | Mismo resultado |
| Java 21, con el almacén de certificados de la instancia | 200 en las dos consultas (catálogo 808 ms, estaciones 289 ms) |

- **Certificados:** el almacén del sistema y el de Java validaron el certificado de ANH sin instalar nada. No faltó ningún certificado y la verificación no se desactivó en ningún momento.
- **Límites de ANH:** se hicieron 10 consultas seguidas, una por segundo, y todas dieron 200 en menos de 0,75 s. ANH no rechazó ni frenó las peticiones que llegan desde la IP de Oracle. No se probó con una frecuencia mayor, así que esto no descarta un límite más estricto.
- **Puertos:** la salida no abre ningún puerto de entrada. Después de las pruebas, hacia afuera seguía escuchando solo el 22, y PostgreSQL solo en `127.0.0.1`.
- **Cliente Python en Linux ARM:** funciona sin cambios. Para correrlo hay que instalar `python3-venv` y crear un entorno virtual:

```bash
sudo apt install -y python3-venv
cd ~/PruebaApiANH
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python anh_tarija.py --productos 0 --mostrar-ubicacion
```

- **Reinicio pendiente:** al instalar `python3-venv` quedaron tres servicios con el reinicio diferido (`dbus`, `systemd-logind` y `unattended-upgrades`). No es urgente; se aplican en el próximo reinicio, que conviene hacer a una hora que no moleste.

## Cómo verificarlo

Desde la laptop (no desde el servidor), cada comando por separado:

```powershell
# debe entrar normal
ssh -i $HOME\.ssh\churoviaje-deploy deploy@<IP_PUBLICA>

# debe rechazar con "Permission denied (publickey)"
ssh -o PubkeyAuthentication=no -o PreferredAuthentications=password deploy@<IP_PUBLICA>

# debe rechazar
ssh -i $HOME\.ssh\churoviaje-deploy root@<IP_PUBLICA>

# el 22, el 80 y el 443 deben dar True; el 111 y el 5432, False
22,80,443,111,5432 | ForEach-Object { Test-NetConnection <IP_PUBLICA> -Port $_ -WarningAction SilentlyContinue | Select-Object RemotePort, TcpTestSucceeded }

# debe responder 301 con Location: https://churoviaje.orelqu.com/ y "Server: nginx" sin versión
curl.exe -I http://churoviaje.orelqu.com

# debe responder sin error de certificado
curl.exe -I https://churoviaje.orelqu.com

# la salud no se ve desde fuera: debe dar HTTP 404
curl.exe -s -w "`nHTTP %{http_code}`n" https://churoviaje.orelqu.com/actuator/health

# un token de Google falso: debe dar TOKEN_GOOGLE_INVALIDO y HTTP 401, sin emitir ningún JWT
'{"tokenGoogle":"falso"}' | Out-File -Encoding ascii body.json
curl.exe -s -w "`nHTTP %{http_code}`n" -X POST https://churoviaje.orelqu.com/sesion/google -H "Content-Type: application/json" --data "@body.json"
```

En el servidor, como `ubuntu`:

```bash
# el backend y el núcleo corren, y la salud responde UP
systemctl is-active churoviaje-backend nginx postgresql
pgrep -a motor_nucleo
curl -s http://127.0.0.1:8081/actuator/health

# el 8080 y el 8081 escuchan solo en 127.0.0.1
sudo ss -tlnp | grep -E ':8080|:8081'

# CP-80: al matar el núcleo, aparece otro con un PID distinto en unos segundos
sudo pkill -x motor_nucleo; sleep 5; pgrep -a motor_nucleo

# deploy no puede leer los secretos: debe dar Permission denied
sudo -u deploy cat /etc/churoviaje/backend.env
```

CP-79: después de `sudo reboot`, los mismos comandos deben dar lo mismo sin tocar nada.

Desde un teléfono, `http://churoviaje.orelqu.com` debe pasar solo a `https://` y abrir sin aviso de certificado.

La nota de HTTPS se revisa en [SSL Labs](https://www.ssllabs.com/ssltest/analyze.html?d=churoviaje.orelqu.com&hideResults=on) (con `hideResults=on`, el resultado no sale en la lista pública). Debe ser A o mejor.
