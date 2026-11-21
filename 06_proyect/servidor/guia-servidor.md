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
| Ambiente de pruebas del simulador (T-106, #86) | Preparado en el repositorio: sección 11. Falta correrlo en el servidor y CP-145 |
| Respaldos diarios y registros de 14 días (T-103, #79) | Preparado en el repositorio: sección 12. Faltan el bucket y la política en la consola, correrlo en el servidor y CP-129 |
| Servicio de WhatsApp/LLM (T-100, #68) | Preparado en el repositorio: sección 13. Falta rotar la credencial de servicio, desplegarlo, vincular el número y CP-101 |

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
LUGARES_PATH=/opt/churoviaje/datos/lugares_tarija.json
EOF
unset GCID DBPASS
```

- Si se cambia la contraseña de `churoviaje_app` (`sudo -u postgres psql -c "\password churoviaje_app"`), hay que cambiarla también aquí. Conviene que no tenga comillas ni barras invertidas, porque systemd las interpreta.
- `SERVICE_CREDENTIAL` es el mismo valor que el servicio de WhatsApp usa como `SERVICE_TOKEN` (D-13). Se copia desde este archivo cuando se despliegue ese servicio (T-100).
- Para cambiar un valor: `sudo nano /etc/churoviaje/backend.env` y `sudo systemctl restart churoviaje-backend`.
- `LUGARES_PATH` es el JSON de la búsqueda de destinos ([D-47](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Se genera con `preparar_lugares.py`, del mismo extracto que la red vial, y se sube como `deploy` a `/opt/churoviaje/datos`, igual que la red vial. Al reiniciar, el backend lo carga solo si cambió la fecha del extracto.

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

### 11. Ambiente de pruebas del simulador

T-106 (#86). Un segundo backend, con su propio núcleo C++ y su propia base, para correr el simulador sin tocar producción ([RN-07.12](../../03_des/02_Arquitectura/04_reglas/CU-07_EjecutarSimuladorTrafico.md)). Con 12 GB de memoria, la instancia aguanta los dos.

| Qué | Producción | Pruebas |
|---|---|---|
| Servicio | `churoviaje-backend` | `churoviaje-backend-pruebas`, no arranca solo con la máquina |
| Usuario del servicio | `churoviaje` | `churoviaje-pruebas`, en el grupo `churoviaje` para leer `/opt/churoviaje`; no puede leer los secretos de producción |
| Perfil | `prod` | `pruebas`: sesiones de prueba y modo demostración ([P-20](../../03_des/02_Arquitectura/08_decisiones_parametros.md), [D-48](../../03_des/02_Arquitectura/08_decisiones_parametros.md)) |
| Puertos (solo `127.0.0.1`) | 8080 y 8081 (salud) | 8090 y 8091 (salud) |
| Base | `churoviaje`, usuario `churoviaje_app` | `churoviaje_pruebas`, usuario `churoviaje_pruebas_app` |
| Secretos | `/etc/churoviaje/backend.env` | `/etc/churoviaje/backend-pruebas.env`, con un JWT, una credencial de servicio y una credencial de prueba propios |
| Dirección | `https://churoviaje.orelqu.com` | `https://pruebas.churoviaje.orelqu.com` |
| Versión | `/opt/churoviaje/actual` | La misma: al desplegar, si está encendido, pasa a la versión nueva |
| Recursos | Sin límite | Como mucho 60 % de CPU (de 200 % con 2 OCPU) y 1,5 GB, contando el núcleo, y cede la CPU primero (`Nice=5`) |
| Registro de las pruebas de carga | — | `/var/log/churoviaje-pruebas-carga/pruebas-carga.log`, que no se borra a los 14 días |

**Dominio.** En Cloudflare, **DNS → Records**, un registro más, también con la nube gris (el certificado gratis de Cloudflare no cubre dos niveles de subdominio, y además el certificado tiene que ser el de Let's Encrypt):

| Tipo | Nombre | Valor | Proxy |
|---|---|---|---|
| `A` | `pruebas.churoviaje` | `<IP_PUBLICA>` | DNS only (nube gris) |

**Preparar el ambiente.** Como `ubuntu`, después de traer la rama al clon de `deploy`:

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/crear-ambiente-pruebas.sh
```

El script crea el usuario, la base con PostGIS, el archivo de entorno con secretos nuevos (la contraseña de la base se genera al azar y solo queda en ese archivo), instala el servicio y deja que `deploy` lo reinicie al desplegar solo si está encendido (`try-restart`). Se puede correr más de una vez: lo que ya existe no lo toca. No imprime ningún secreto.

**Nginx y certificado.**

```bash
sudo cp /home/deploy/ChuroViajeInterno/06_proyect/servidor/nginx/churoviaje-pruebas.conf /etc/nginx/sites-available/churoviaje-pruebas
sudo ln -s /etc/nginx/sites-available/churoviaje-pruebas /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d pruebas.churoviaje.orelqu.com --redirect -m <CORREO> --agree-tos --no-eff-email
```

**Encender y apagar.** Apagado no gasta memoria ni CPU, y encenderlo o apagarlo no toca producción:

```bash
sudo systemctl start churoviaje-backend-pruebas
curl -s http://127.0.0.1:8091/actuator/health
sudo systemctl stop churoviaje-backend-pruebas
```

La primera vez, Flyway crea las tablas en `churoviaje_pruebas`. Con la base vacía, el núcleo puede arrancar antes de que existan y el registro muestra un error de `evento_vial`; el backend lo reinicia solo a los 2 segundos. Si se quiere que arranque con la máquina: `sudo systemctl enable churoviaje-backend-pruebas`.

**Correr el simulador en el servidor.** Así la credencial de prueba no sale del servidor: el script la lee del archivo de entorno y corre el simulador como `deploy` contra `http://127.0.0.1:8090`.

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/simular.sh --usuarios 10 --navegando 4 \
     --eventos-por-minuto 4 --duracion 5 --prueba cp146 --reporte /home/deploy/simulaciones/cp146.json
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/simular.sh --borrar
sudo tail -n 20 /var/log/churoviaje-pruebas-carga/pruebas-carga.log
```

### 12. Respaldos y registros

T-103 (#79). Que un error no haga perder los datos y que los registros no llenen el disco ni guarden datos de los conductores ([06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md)).

**Respaldos.** Todos los días a las 04:00 de Tarija, después de la limpieza diaria del backend (03:30, T-105), `churoviaje-respaldo.timer` respalda la base `churoviaje` con `pg_dump` y lo sube al almacenamiento de objetos de Oracle Cloud, fuera de la instancia. En el bucket quedan los 7 más recientes; en `/var/backups/churoviaje`, solo de root, los 2 últimos. Junto a cada respaldo va un `.resumen`: cuántos eventos, votos, surtidores, estados y conductores había y una huella del contenido de eventos y surtidores, calculados en la misma instantánea que `pg_dump`. Con eso, la prueba de restauración compara de verdad.

El servidor sube los respaldos con su propia identidad (*instance principal*): no hay ninguna llave de Oracle Cloud guardada en el disco. Para eso, una sola vez en la consola de Oracle Cloud:

1. **Bucket.** Storage → Buckets → *Create Bucket*, en el compartimento de la instancia: nombre `churoviaje-respaldos`, nivel Standard, **sin acceso público**. El nivel Always Free incluye 20 GB; los respaldos pesan poco.
2. **Grupo dinámico.** Identity & Security → Domains → `Default` → Dynamic groups → *Create*: nombre `churoviaje-servidor`, regla `instance.id = '<OCID de churoviaje-server>'` (el OCID está en la página de la instancia).
3. **Política.** Identity & Security → Policies, en el mismo compartimento → *Create Policy*: nombre `churoviaje-respaldos`, con:

```text
Allow dynamic-group 'Default'/'churoviaje-servidor' to read buckets in compartment <compartimento> where target.bucket.name = 'churoviaje-respaldos'
Allow dynamic-group 'Default'/'churoviaje-servidor' to manage objects in compartment <compartimento> where target.bucket.name = 'churoviaje-respaldos'
```

La política solo deja tocar ese bucket. Puede tardar un par de minutos en aplicarse.

Después, como `ubuntu`:

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/instalar-respaldos.sh churoviaje-respaldos
sudo systemctl start churoviaje-respaldo                  # el primer respaldo, ahora
sudo journalctl -u churoviaje-respaldo -n 20 --no-pager
```

El instalador pone la CLI de Oracle Cloud en `/opt/oci-cli`, comprueba que la instancia ve el bucket, y copia el script del respaldo a `/usr/local/sbin` como archivo de root: el temporizador corre como root y no debe ejecutar algo que `deploy` puede cambiar. Si se cambia el script en el repositorio, hay que volver a correr el instalador.

**Restaurar.** El respaldo se restaura en una base aparte, `churoviaje_restaurada`; producción no se toca. El script calcula el mismo resumen sobre la base restaurada y lo compara con el del respaldo:

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/probar-restauracion.sh             # el más reciente
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/probar-restauracion.sh 2026-11-22  # el de ese día
```

Para una restauración real (perder la base de producción), con el servicio detenido:

```bash
sudo systemctl stop churoviaje-backend
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/probar-restauracion.sh <día> --conservar
sudo -u postgres psql -c "ALTER DATABASE churoviaje RENAME TO churoviaje_danada;"
sudo -u postgres psql -c "ALTER DATABASE churoviaje_restaurada RENAME TO churoviaje;"
sudo systemctl start churoviaje-backend
```

La base restaurada y sus tablas ya son del usuario de la aplicación (`churoviaje_app`), como las de producción. Cuando todo funcione, `churoviaje_danada` se puede borrar.

**Registros.** Una sola vez, y otra vez después de pedir un certificado nuevo con certbot (certbot agrega bloques `server` nuevos):

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/configurar-registros.sh --borrar-registros-viejos
```

| Registro | Dónde | Cuánto dura | Qué no guarda |
|---|---|---|---|
| Backend, núcleo y servicio de WhatsApp/LLM | journald | 14 días, como mucho 1 GB ([registros/journald-churoviaje.conf](registros/journald-churoviaje.conf)) | Ubicaciones ni conductores (no los registran); textos de mensajes (el servicio solo registra eventos de una lista fija) |
| Accesos a Nginx | `/var/log/nginx/access.log` | 14 días, un archivo por día ([registros/logrotate-nginx](registros/logrotate-nginx)) | La consulta de las URL (con la ubicación en `GET /surtidores` y `GET /lugares`), la IP completa ni el referer ([registros/nginx-formato.conf](registros/nginx-formato.conf)) |
| Errores de Nginx | `/var/log/nginx/error.log` | 14 días | Solo los críticos: en los niveles más bajos Nginx copia la línea de la petición con su consulta |
| Pruebas de carga | `/var/log/churoviaje-pruebas-carga` | Hasta revisarlos | Nada de un conductor: solo números de cada prueba (RN-07.11) |

`--borrar-registros-viejos` borra los registros de Nginx que ya estaban escritos, porque tienen la consulta con la ubicación. Si `nginx -t` falla, el script deja Nginx como estaba.

### 13. Servicio de WhatsApp/LLM

T-100 (#68). El servicio de `04_imp/servicio_whatsapp` lee los grupos autorizados con el número dedicado, analiza los mensajes cada 5 minutos con Gemini y entrega el resultado al backend ([CU-06](../../03_des/02_Arquitectura/04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [D-13](../../03_des/02_Arquitectura/08_decisiones_parametros.md), [D-17](../../03_des/02_Arquitectura/08_decisiones_parametros.md), [D-36](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).

| Ruta | Qué hay | Dueño |
|---|---|---|
| `/opt/churoviaje-whatsapp/releases/<fecha>-<commit>/` | Una versión: `src`, `package.json`, `package-lock.json` y `node_modules`. Se guardan las 3 más recientes | `deploy`, grupo `churoviaje-wa` (carpeta con setgid) |
| `/opt/churoviaje-whatsapp/actual` | Enlace a la versión que corre | `deploy` |
| `/etc/churoviaje/whatsapp.env` | Credencial de servicio (`SERVICE_TOKEN`, la misma que `SERVICE_CREDENTIAL` del backend), clave de Gemini, número, grupos y rutas | `churoviaje-wa`, `600` |
| `/etc/churoviaje/whatsapp/autorizaciones.json` | El registro de la autorización de cada administrador de grupo ([RN-06.1](../../03_des/02_Arquitectura/04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md)) | `churoviaje-wa`, carpeta `700`, archivo `600` |
| `/var/lib/churoviaje-whatsapp/` | La sesión del número (`sesion/session.sqlite`) y los mensajes pendientes (`pendientes.sqlite`) | `churoviaje-wa`, `700` |

- **Servicio.** [systemd/churoviaje-whatsapp.service](systemd/churoviaje-whatsapp.service): arranca con la máquina después del backend, se reinicia a los 15 segundos si se cae, usa como mucho 512 MB, solo lee su versión y solo escribe en `/var/lib/churoviaje-whatsapp`.
- **Dentro de la máquina.** Habla con el backend por `http://127.0.0.1:8080`, sin pasar por internet ni por Nginx.
- **Sin puertos.** El servicio no escucha nada: solo abre conexiones de salida hacia WhatsApp y Gemini.
- **Registro.** En journald. El servicio solo registra eventos de una lista fija, sin textos, números ni grupos, y journald los borra a los 14 días (sección 12).

**Puesta en marcha, en este orden.** Todo como `ubuntu`, salvo el despliegue:

1. **Rotar la credencial de servicio.** Se publicó en el PR #173, así que antes de que el servicio la use se cambia. El script genera una nueva, la escribe en `backend.env` (y en `whatsapp.env` si ya existe) y reinicia el backend; nunca la muestra:

   ```bash
   sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/rotar-credencial-servicio.sh
   ```

2. **Usuario, entorno y servicio.** Pide la clave de Gemini (sin mostrarla), el número dedicado y los IDs de los grupos; la credencial la copia del backend:

   ```bash
   sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/crear-servicio-whatsapp.sh
   sudo install -m 600 -o churoviaje-wa -g churoviaje-wa /dev/null /etc/churoviaje/whatsapp/autorizaciones.json
   sudo nano /etc/churoviaje/whatsapp/autorizaciones.json
   ```

   El formato del registro de autorizaciones está en [04_imp/servicio_whatsapp/README.md](../../04_imp/servicio_whatsapp/README.md#configuración-de-la-cuenta-y-autorizaciones). Un grupo sin su registro no se lee.

3. **Desplegar**, como `deploy`:

   ```bash
   bash ~/ChuroViajeInterno/06_proyect/servidor/scripts/desplegar-whatsapp.sh
   ```

   Copia el servicio sin las pruebas ni las demostraciones, instala las dependencias con `npm ci` (las versiones de `package-lock.json`), cambia el enlace y lo reinicia. Si en 30 segundos no sigue activo, vuelve a la versión anterior.

4. **Vincular el número**, con el teléfono del número dedicado en la mano:

   ```bash
   sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/vincular-whatsapp.sh
   ```

   Detiene el servicio y lo corre en la terminal, como `churoviaje-wa` y con el mismo entorno, para mostrar el QR. En el teléfono: **WhatsApp → Dispositivos vinculados → Vincular un dispositivo**. Cuando aparezca `{"evento":"WHATSAPP_CONECTADO"}`, Ctrl+C: el script vuelve a encender el servicio con la sesión guardada.

5. **Que arranque con la máquina:** `sudo systemctl enable --now churoviaje-whatsapp`.

**Si se pierde la sesión** (el registro muestra `WHATSAPP_SESION_CERRADA`, o se desvinculó desde el teléfono): repetir el paso 4. Si la sesión quedó dañada o se cambió de número, `sudo bash .../vincular-whatsapp.sh --borrar-sesion`, y si cambió el número, también `WHATSAPP_PHONE_NUMBER` en `whatsapp.env`. Mientras tanto el backend sigue funcionando: los surtidores muestran el último dato hasta que vence (6 horas).

**Cambiar la clave de Gemini:** `sudo nano /etc/churoviaje/whatsapp.env` (`LLM_API_KEY`) y `sudo systemctl restart churoviaje-whatsapp`. **Cambiar la credencial de servicio:** el script del paso 1, que cambia las dos a la vez.

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

CP-101: se manda un mensaje al grupo de prueba (por ejemplo «Las Vegas hay gasolina, fila corta»), se esperan 6 minutos y se corre el script. Muestra el estado del servicio, que no escucha ningún puerto, los permisos de sus secretos, sus últimos eventos y los datos de WhatsApp de la tabla `estado_combustible`, con la hora del mensaje, la hora en que se guardó y los minutos entre las dos, que deben ser menos de 6. No muestra secretos, IPs internas ni textos de mensajes:

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/verificar-whatsapp.sh
```

La comprobación en la app se hace con la pestaña Surtidores (#71): el surtidor debe mostrar el dato con origen WhatsApp.

CP-129: el respaldo de ayer se restaura en una base aparte y tiene los mismos eventos y surtidores (la salida no tiene IPs, llaves ni contraseñas):

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/probar-restauracion.sh "$(date -d yesterday +%F)"
systemctl list-timers churoviaje-respaldo.timer --no-pager
sudo /opt/oci-cli/bin/oci os object list --auth instance_principal --bucket-name churoviaje-respaldos --query 'data[].name' --all
```

El registro de acceso no debe tener `?`: `sudo grep -c '?' /var/log/nginx/access.log` debe dar 0.

CP-145 (ambiente de pruebas encendido): `POST /sesion/prueba` debe dar 200 con un JWT en `pruebas.churoviaje.orelqu.com` y 403 `ACCESO_DENEGADO` en `churoviaje.orelqu.com`. El script no muestra la credencial ni el JWT, y borra al final el conductor simulado que creó:

```bash
sudo bash /home/deploy/ChuroViajeInterno/06_proyect/servidor/scripts/verificar-ambiente-pruebas.sh
```

Desde un teléfono, `http://churoviaje.orelqu.com` debe pasar solo a `https://` y abrir sin aviso de certificado.

La nota de HTTPS se revisa en [SSL Labs](https://www.ssllabs.com/ssltest/analyze.html?d=churoviaje.orelqu.com&hideResults=on) (con `hideResults=on`, el resultado no sale en la lista pública). Debe ser A o mejor.
