# ChuroViaje — Guía de instalación, configuración y validación (Issue #59)

**Historia:** HU-08 / CU-06 — lectura autorizada de WhatsApp, análisis de lotes con Gemini y entrega a Java/PostgreSQL.
**Issue:** <https://github.com/uniWASD/ChuroViajeInterno/issues/59>
**Rama de trabajo:** `feature/HU08-CU06-llm-analisis-lotes` · **Base del PR:** `develop`.

> Esta guía contiene nombres de variables, ejemplos y comandos, **no credenciales reales**. No versionar sesiones WhatsApp, QR, JID de grupos, conversaciones, `.env`, SQLite, ficheros DPAPI ni autorizaciones privadas. El grupo debe estar autorizado por su administrador y los participantes informados. Para Gemini Free Tier, usar exclusivamente **mensajes sintéticos**, sin información privada; revisar las condiciones vigentes de Google antes de procesar mensajes de terceros.

## 1. Dependencias y versiones

- Windows PowerShell 5.1 o PowerShell 7 (comandos de esta guía: Windows).
- Git, Node.js `>=22.13.0`, npm, Java **JDK 21**, Docker Desktop con contenedores Linux, Internet para npm/Gemini y permisos de acceso al backend Java del issue #57.
- Servicio Node: `04_imp/servicio_whatsapp`.
- Backend Java: `04_imp/backend_java` si está incluido en la rama desplegada; si no, obtener del equipo la versión compatible de [issue #57](https://github.com/uniWASD/ChuroViajeInterno/issues/57). No copiar archivos Java arbitrarios entre ramas.

Verificar herramientas:

```powershell
git --version
node --version
npm.cmd --version
java -version
docker --version
docker compose version
docker info --format '{{.ServerVersion}}'
```

Instalar dependencias y probar Node:

```powershell
Set-Location '.\04_imp\servicio_whatsapp'
npm.cmd install --no-audit --no-fund
npm.cmd test
npm.cmd run demo
```

**Resultado observado en el laboratorio Windows del Issue #59 (20/11/2026):** 139 pruebas contabilizadas, 138 aprobadas, 0 fallidas, 1 omitida por permisos POSIX de Linux. La demo usa dobles/simuladores; no equivale a una prueba de WhatsApp, Gemini o Java reales.

## 2. Variables y cómo obtener cada dato

| Variable | Servicio | Obtención / uso | Secreto |
|---|---|---|---|
| `DB_PASSWORD` | PostgreSQL / Java | Solicitar al administrador de la BD existente. Para un volumen **nuevo y vacío** generar una contraseña de desarrollo; reutilizar exactamente la contraseña con la que se inicializó un volumen ya existente. | Sí |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | Java | Dirección, puerto, nombre y usuario de la base de desarrollo. Validar con el equipo y `docker compose config`. | No |
| `JWT_SECRET` | Java | Solicitar al equipo el secreto JWT compatible o generar uno criptográfico para un entorno nuevo. Mínimo 32 caracteres. No cambiar uno compartido sin coordinación. | Sí |
| `GOOGLE_CLIENT_ID` | Java | Cliente OAuth ya usado por la aplicación, en Google Cloud Console → APIs y servicios → Credenciales. Es un **ID OAuth**, no una API key de Gemini. | Configuración, no contraseña |
| `SERVICE_CREDENTIAL` | Java | Secreto de servicio acordado con el equipo, mínimo 32 caracteres. | Sí |
| `SERVICE_TOKEN` | Node | **Mismo valor exacto** que `SERVICE_CREDENTIAL` de Java. | Sí |
| `LLM_API_KEY` | Node/Gemini | Google AI Studio → API keys → seleccionar proyecto autorizado → crear o reutilizar clave: <https://aistudio.google.com/apikey>. | Sí |
| `LLM_MODEL` | Node | `gemini-3.1-flash-lite` si existe y tiene cuota para la clave del proyecto; ejecutar la prueba real. | No |
| `BACKEND_URL` | Node | Dirección base de la API Java, por ejemplo `http://127.0.0.1:8080` en desarrollo local. | No |
| `WHATSAPP_PHONE_NUMBER` | Node | Número completo con prefijo de país, **solo dígitos**, de la cuenta dedicada vinculada. | Privado |
| `ALLOWED_GROUP_IDS` | Node | JID técnico `...@g.us` del grupo específicamente autorizado. Obtener mediante el auxiliar con un mensaje sintético nuevo. | Privado |
| `WHATSAPP_AUTHORIZATIONS_FILE` | Node | Ruta absoluta a JSON privado de autorizaciones; no ubicarlo dentro del repositorio ni de un directorio Git. | Privado |
| `WHATSAPP_SESSION_PATH` | Node | Ruta de SQLite privada con la sesión Baileys. No versionar ni compartir. | Secreto de sesión |
| `SQLITE_PATH` | Node | Ruta de SQLite para mensajes pendientes y resultados sin texto. No versionar. | Privado |

### Generar secretos para un entorno nuevo (sin introducirlos en Git)

Solo si **no existe** un secreto asignado para ese entorno; no reemplazar valores de otros issues sin autorización:

```powershell
$Rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
  foreach ($Nombre in @('JWT_SECRET','SERVICE_CREDENTIAL')) {
    $Bytes = New-Object byte[] 48
    $Rng.GetBytes($Bytes)
    Write-Host ('{0}: {1}' -f $Nombre,[Convert]::ToBase64String($Bytes))
  }
} finally { $Rng.Dispose() }
```

Guárdalos en el gestor de secretos del equipo; no en el repo, capturas, issues o logs. No es necesario compartir la contraseña de PostgreSQL mediante GitHub. Para un servidor compartido, otorgar acceso a través del administrador/gestor de secretos.

### Dónde obtener Google OAuth y Gemini

1. `GOOGLE_CLIENT_ID`: abrir <https://console.cloud.google.com/apis/credentials>, elegir **el proyecto de la app**, inspeccionar el cliente OAuth ya usado (Android u otro según la app) y copiar el **Client ID**. Si hace falta un nuevo cliente Android, necesitarás el paquete y la huella SHA-1 reales de esa aplicación; no inventarlos.
2. `LLM_API_KEY`: entrar a <https://aistudio.google.com/apikey>, elegir el proyecto con API y cuota autorizadas, crear o reutilizar la clave. **No es el Client ID OAuth.**
3. Consultar disponibilidad, cuotas y condiciones de Gemini en <https://ai.google.dev/gemini-api/docs/rate-limits> y <https://ai.google.dev/gemini-api/terms>. La modalidad gratuita puede permitir que Google use el contenido enviado para mejorar servicios; en esta prueba utilizar textos ficticios y obtener consentimiento para el acceso al grupo.

Para introducir secretos sin mostrarlos en pantalla ni en el historial, define esta función **en la misma ventana de PowerShell** antes de los comandos Java o Node:

```powershell
function Leer-SecretoLocal([string]$Etiqueta) {
  $Seguro = Read-Host -Prompt $Etiqueta -AsSecureString
  $Ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Seguro)
  try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Ptr) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Ptr); $Seguro.Dispose() }
}
```

## 3. Iniciar el backend Java y PostgreSQL

Ejemplo para **una instalación limpia y autorizada** donde el backend está en `04_imp/backend_java`. Si la BD ya existe, no modificar ni resetear su contraseña.

```powershell
Set-Location '.\04_imp\backend_java'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5433'
$env:DB_NAME = 'churoviaje_dev'
$env:DB_USER = 'churoviaje'
$env:DB_PASSWORD = Leer-SecretoLocal 'DB_PASSWORD asignada a este volumen'
$env:JWT_SECRET = Leer-SecretoLocal 'JWT_SECRET existente (>=32 caracteres)'
$env:GOOGLE_CLIENT_ID = Read-Host 'Google OAuth Client ID del proyecto'
$env:SERVICE_CREDENTIAL = Leer-SecretoLocal 'SERVICE_CREDENTIAL existente (>=32 caracteres)'
docker compose up -d db
.\mvnw.cmd spring-boot:run
```

Conserva la consola Java abierta. En otra PowerShell verifica la **URL de salud efectiva** y las migraciones:

```powershell
Invoke-RestMethod 'http://127.0.0.1:8081/actuator/health'
docker compose ps
# Ajustar usuario/base a los nombres reales de la instalación:
docker compose exec -T db psql -U churoviaje -d churoviaje_dev -c 'SELECT version, success FROM public.flyway_schema_history ORDER BY installed_rank;'
```

**Puertos ocupados (Windows):** consultar primero `Get-NetTCPConnection` y `netsh interface portproxy show all`. No detener `iphlpsvc`, no quitar redirecciones de otras aplicaciones y no usar `docker compose down -v`. Si se necesita coexistir, usar **un `compose.override.yaml` exclusivamente local, no versionado**, para asignar otra IP/puerto al contenedor; configurar Java con `SPRING_APPLICATION_JSON` para conectar a la misma dirección y cambiar `management.server.port` a otro puerto libre. No ejecutar `flyway repair`, `flyway clean` ni borrar volúmenes para eludir discrepancias. Una base de desarrollo nueva y aislada es alternativa para pruebas, **no** migración de la base existente.

Ejemplo **ilustrativo** para un equipo donde `127.0.0.1:5433` y `8081` están ocupados:

```yaml
# compose.override.yaml LOCAL en la carpeta del backend (NO versionar)
services:
  db:
    ports: !override
      - "127.0.0.2:5433:5432"
```

```powershell
$env:SPRING_APPLICATION_JSON = '{"spring":{"datasource":{"url":"jdbc:postgresql://127.0.0.2:5433/churoviaje_issue59_dev"}},"management":{"server":{"port":18081}}}'
# La base churoviaje_issue59_dev debe EXISTIR y haber sido preparada previamente.
# Health de esta instancia: http://127.0.0.1:18081/actuator/health
```

## 4. Autorizar el grupo y vincular la cuenta

Solo con **autorización auténtica del administrador e integrantes informados**. Usar una cuenta WhatsApp dedicada. La biblioteca Baileys **no es la API oficial de WhatsApp**; considerar riesgos de compatibilidad/cuenta. No usar conversaciones reales para la validación Free Tier.

Los scripts auxiliares están en `04_imp/servicio_whatsapp/herramientas/`. El descubridor imprime el JID de **mensajes nuevos** (no su texto):

```powershell
# Desde 04_imp\servicio_whatsapp, con npm install ya ejecutado:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\herramientas\identificar_grupo.ps1
# En el teléfono (cuenta dedicada): Dispositivos vinculados → Vincular dispositivo.
# Un contacto autorizado envía un mensaje sintético nuevo al grupo.
# Anotar el JID en almacenamiento privado y detener descubridor con Ctrl+C.
```

La autorización se registra fuera de Git, por ejemplo `%LOCALAPPDATA%\ChuroViajeIssue59\autorizaciones.json`, **UTF-8 sin BOM**. Ejemplo de estructura (sustituir por datos reales y verificables, sin nombres ni números innecesarios):

```json
{"autorizaciones":[{"grupo":"<JID_REAL_DEL_GRUPO>@g.us","fecha":"<FECHA_ISO_8601_CON_ZONA>","solicitud":"<REFERENCIA_REAL_DE_LA_AUTORIZACION>","autorizadoPorAdmin":true}]}
```

No introducir `autorizadoPorAdmin: true` si no existe aprobación auténtica. El archivo debe estar fuera del repositorio; en Windows restringir ACL. Conservar la sesión `sesion.sqlite` en carpeta privada, no exportarla a Git.

## 5. Probar Gemini e iniciar Node (sin copiar claves al repositorio)

En una PowerShell, desde `04_imp/servicio_whatsapp`, define las variables **localmente**. Los ejemplos preguntan los secretos y no los almacenan en archivos Git:

```powershell
Set-Location '.\04_imp\servicio_whatsapp'
$env:BACKEND_URL = 'http://127.0.0.1:8080'
$env:SERVICE_AUTH_HEADER = 'Authorization'
$env:SERVICE_TOKEN_PREFIX = 'Bearer '
$env:SERVICE_TOKEN = Leer-SecretoLocal 'Mismo SERVICE_CREDENTIAL de Java'
$env:LLM_API_KEY = Leer-SecretoLocal 'Gemini API key del proyecto'
$env:LLM_MODEL = 'gemini-3.1-flash-lite'
$env:WHATSAPP_ENABLED = 'true'
$env:WHATSAPP_PHONE_NUMBER = Read-Host 'Numero dedicado con prefijo de pais, solo digitos'
$env:ALLOWED_GROUP_IDS = Read-Host 'JID autorizado terminado en @g.us'
$Privado = Join-Path $env:LOCALAPPDATA 'ChuroViajeIssue59'
$env:WHATSAPP_AUTHORIZATIONS_FILE = Join-Path $Privado 'autorizaciones.json'
$env:WHATSAPP_SESSION_PATH = Join-Path $Privado 'sesion.sqlite'
$env:SQLITE_PATH = Join-Path $Privado 'pendientes.sqlite'
$env:ANALYSIS_INTERVAL_MS = '300000'
$env:MAINTENANCE_INTERVAL_MS = '60000'
npm.cmd run smoke:gemini
# Solo si smoke:gemini = APROBADO:
npm.cmd start
```

Para **no repetir claves** en el mismo equipo Windows, almacenarlas una vez usando `ConvertFrom-SecureString` (DPAPI, fuera de Git) y recuperarlas con `ConvertTo-SecureString`; las credenciales no se transfieren entre usuarios/computadoras. El equipo debe suministrar el valor real a cada integrante a través de un gestor de secretos. Para una API key que se haya compartido públicamente, **rotar/revocar antes de producción** y actualizar el secreto de forma coordinada en el servicio.

Eventos de inicio: `SERVICIO_INICIADO` y `WHATSAPP_CONECTADO`. Si la cuenta no coincide con `WHATSAPP_PHONE_NUMBER`, `WHATSAPP_CUENTA_INVALIDA`: no desactivar esa protección.

## 6. Validaciones y evidencias del Issue #59

| Caso | Qué comprobar | Evidencia de laboratorio (20/11/2026) |
|---|---|---|
| CP-74 | Aviso claro sobre surtidor, gasolina y fila | Prueba automatizada y mensaje real de pruebas |
| CP-75 | Preguntas no producen avisos | Grupo real: 2 descartes, sin cambio de la fila SQL previa |
| CP-76 | LLM caído: pendientes para próximo ciclo | Simulador aprobado |
| CP-77 | Grupo → Gemini → Java → PostgreSQL en menos de 360 s | Real: 86,01 s, Las Vegas/GASOLINA/disponible/CORTA |
| CP-78 | Números de teléfono enmascarados antes del LLM | Pruebas sintéticas aprobadas |
| RN-06.19 | Backend caído: conservar avisos **sin texto** y reenviar | Caída real Java; resultado pendiente SQLite 1→0; El Portillo/GASOLINA/CORTA confirmado |

Consultar la tabla **de la base de pruebas del equipo** (ajustar nombre según entorno):

```powershell
docker exec backend_java-db-1 psql -X -U churoviaje -d churoviaje_issue59_dev -v ON_ERROR_STOP=1 -c "SELECT s.nombre, e.tipo_codigo, e.disponible, e.fila, e.origen, e.fecha_dato, e.actualizado_en FROM public.estado_combustible e JOIN public.surtidor s ON s.id=e.surtidor_id WHERE e.origen='WHATSAPP' ORDER BY e.actualizado_en DESC LIMIT 10;"
```

Los informes desidentificados están en `04_imp/servicio_whatsapp/evidencias_issue59/` si se incluyeron en el PR. Comprobar siempre que no contienen claves, QR, sesiones, JID, teléfonos, nombres de participantes ni textos de conversaciones reales.

## 7. Seguridad, entrega y límites

- No subir: `.env`, `*.sqlite`, `*.db`, `node_modules/`, `compose.override.yaml`, `*dpapi*`, sesión/QR/JID, mensajes privados, contraseñas, API keys ni JWT.
- `SERVICE_TOKEN` Node debe ser idéntico a `SERVICE_CREDENTIAL` Java. El OAuth Client ID y Gemini API key son **distintos**.
- El estado de un issue cerrado **no reemplaza** los casos reales y la aprobación de revisión. Solo actualizar un PR **abierto** de la rama exacta: nuevos commits y push a la rama HEAD; no abrir ni reabrir otro PR sin indicación del equipo.
- El producto no está certificado para producción solo por estas pruebas: rotación de secretos expuestos, revisión de privacidad/consentimiento, dependencias Baileys, CI e integración a `develop` siguen sujetos al equipo.

**Fuentes:** [Issue #59](https://github.com/uniWASD/ChuroViajeInterno/issues/59), [Google AI Studio API keys](https://aistudio.google.com/apikey), [Términos Gemini API](https://ai.google.dev/gemini-api/terms), [GitHub protección de secretos](https://docs.github.com/en/code-security/concepts/secret-security/command-line-push-protection).
