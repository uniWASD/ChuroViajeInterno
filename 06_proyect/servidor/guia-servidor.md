# Guía del servidor de ChuroViaje

Guía de la instancia de Oracle Cloud donde se despliega ChuroViaje. Cubre los issues T-092 (#44) y T-119 (#111). Se actualiza a medida que se avanza.

Última actualización: 29 de octubre de 2026.

> Esta guía no lleva contraseñas, llaves ni la IP del servidor. La IP pública se comparte por el chat del equipo. Donde aparece `<IP_PUBLICA>`, se reemplaza por esa IP.

## Estado

| Paso | Estado |
|---|---|
| IP pública reservada y asignada a la instancia | Hecho |
| Sistema actualizado y reiniciado | Hecho |
| Usuario `deploy` con una llave por integrante | Hecho. Marcelo, Franz, Alex y Jorge ya tienen su llave y Franz ya entró con la suya. La de Antonio se agrega más adelante (T-103 y T-106) |
| SSH solo con llave, sin root | Hecho y verificado |
| Reglas de la VCN y del firewall de Ubuntu | Hecho. Confirmado el 23/10/2026 en la consola: la instancia no tiene ningún NSG asignado |
| Actualizaciones automáticas de seguridad | Hecho (ya venían activas; se comprobó la configuración) |
| Java 21 y herramientas de C++ (ARM) | Hecho. OpenJDK 21.0.12, g++ 13.3 y CMake 3.28 sobre aarch64 |
| Node.js LTS | Hecho. Node.js 24.22 (LTS) y npm 11.19, desde el repositorio de NodeSource |
| Git en el servidor | Hecho. Instalado; comprobado con `git --version` el 23/10/2026 |
| PostgreSQL y PostGIS, solo local | Hecho. PostgreSQL 16 con PostGIS 3.4; solo escucha en 127.0.0.1 |
| Base de datos `churoviaje` y su usuario sin permisos de administrador | Hecho. Usuario `churoviaje_app`, sin atributos de administrador |
| CP-27 (desde fuera solo responde SSH) | Hecho el 20/10/2026 |
| CP-28 (desde fuera el puerto de PostgreSQL no responde) | Hecho el 20/10/2026, con PostgreSQL instalado y corriendo |
| Salida hacia la API de ANH con el certificado verificado | Hecho el 29/10/2026 (T-119). Detalle en la sección «Salida hacia la API de ANH» |
| CP-156 (desde la instancia, las estaciones de Tarija de gasolina responden con el certificado verificado) | Hecho el 29/10/2026 |

## Datos del servidor

- Nombre de la instancia: `churoviaje-server`
- Proveedor y región: Oracle Cloud, `us-chicago-1`
- Imagen: Ubuntu 24.04 LTS, arquitectura ARM (aarch64)
- Tamaño: 2 OCPU y 12 GB de RAM (capa gratuita)
- IP pública: reservada, no cambia al reiniciar
- Puerto del backend: 8080, solo para la propia máquina. El servicio de WhatsApp lo llama en `127.0.0.1:8080` ([contrato](../../03_des/02_Arquitectura/05_contratos.md))

## Usuarios y acceso

| Usuario | Para qué | Quién entra |
|---|---|---|
| `ubuntu` | Administrar el servidor (tiene `sudo`) | Marcelo, con su llave `proyectos-key` |
| `deploy` | Desplegar la aplicación (sin `sudo`, sin contraseña) | Cada integrante que despliega, con su propia llave |

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

El usuario no tiene contraseña ni `sudo`. Si más adelante necesita reiniciar un servicio, se le da ese permiso puntual.

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

Hay dos capas y las dos dejan abierto solo el puerto 22:

- **Red de Oracle (VCN):** las Security Lists de `proyectos-vcn` solo permiten TCP 22 y los dos ICMP de siempre (descubrimiento de MTU y errores internos). No hay reglas para 80, 443 ni 5432. HTTP y HTTPS se abren en el sprint 2, en la VCN y en el servidor.
- **Firewall del servidor:** la imagen de Oracle trae reglas de `iptables` que dejan pasar solo SSH, las respuestas a conexiones ya abiertas, ICMP y el tráfico local, y rechazan el resto. Se guardan con `netfilter-persistent`, así que sobreviven a los reinicios. No se instaló `ufw`: duplicaría las reglas y podría pisarlas. No se tocan las reglas `InstanceServices`, que Oracle necesita para que el servidor hable con sus servicios internos.

Para abrir un puerto más adelante hay que abrirlo en los dos lados.

Se apagó `rpcbind` (puerto 111), que no se usa y estaba escuchando en todas las interfaces:

```bash
sudo systemctl disable --now rpcbind.service rpcbind.socket
```

Ahora hacia afuera solo escucha SSH.

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
- **Puertos:** la salida no abre ningún puerto de entrada. Después de las pruebas, hacia afuera sigue escuchando solo el 22, y PostgreSQL solo en `127.0.0.1`.
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

# el 22 debe dar True y todos los demás False
22,80,443,111,5432 | ForEach-Object { Test-NetConnection <IP_PUBLICA> -Port $_ -WarningAction SilentlyContinue | Select-Object RemotePort, TcpTestSucceeded }
```