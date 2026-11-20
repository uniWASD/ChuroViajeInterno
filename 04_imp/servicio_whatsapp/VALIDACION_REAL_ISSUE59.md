# Validación y entrega — Issue #59, HU-08/CU-06, WhatsApp/LLM

**Alcance exclusivo:** https://github.com/uniWASD/ChuroViajeInterno/issues/59
**Rama de destino:** `feature/HU08-CU06-llm-analisis-lotes` → PR contra `develop`.
**Fecha de revisión documental:** 17/11/2026. No se declara que el PR esté creado o integrado.

## 1. Qué se ha ejecutado y qué no

- Ejecutado: suite local `npm test` sobre Node.js 22.16.0; pruebas Node:SQLite; llamadas HTTP reales a un servidor *simulado* en localhost; recorrido del servicio con mensajes sintéticos; simulación de indisponibilidad de Gemini, backend y reintentos; pruebas negativas de datos inventados y teléfonos.
- Pendiente: conexión a **WhatsApp real**, llamada a **Gemini real con clave**, comprobación con **Java/PostgreSQL reales** y medición real de **CP-77 (<360 segundos)**. Sin esas dependencias no se acredita el criterio E2E.
- Alcance del ZIP: servicio Node.js completo, conservando la compatibilidad con el lector de grupos existente (#51). **No contiene la implementación Java** de #57 ni datos de cuenta/sesión.

## 2. Dependencias verificadas en el repositorio público

| Referencia | Ruta real y función |
| --- | --- |
| Issue #59 | https://github.com/uniWASD/ChuroViajeInterno/issues/59 |
| Investigación #10 | `05_pro/PruebaAnalisisLLM/03des/HU08_CU06_investigacion_LLM.md`, en rama `feature/HU08-CU06-AnalisisLLM`: https://github.com/uniWASD/ChuroViajeInterno/blob/feature/HU08-CU06-AnalisisLLM/05_pro/PruebaAnalisisLLM/03des/HU08_CU06_investigacion_LLM.md |
| Backend Java #57 | `04_imp/backend_java/`, en rama `feature/HU08-CU06-java-recibir-analisis`: https://github.com/uniWASD/ChuroViajeInterno/blob/feature/HU08-CU06-java-recibir-analisis/04_imp/backend_java/README.md |
| Compilación Java | `04_imp/backend_java/pom.xml`: https://github.com/uniWASD/ChuroViajeInterno/blob/feature/HU08-CU06-java-recibir-analisis/04_imp/backend_java/pom.xml |
| Lectura/configuración raíz | `04_imp/readme.md` en rama de #59: https://github.com/uniWASD/ChuroViajeInterno/blob/feature/HU08-CU06-llm-analisis-lotes/04_imp/readme.md |

Los tres ZIP proporcionados incluyen solo el **subdirectorio** `servicio_whatsapp`. El ZIP rotulado como rama Java #57 no incluye `backend_java/`; su contenido es idéntico al ZIP del lector #51. **Descarga el backend Java desde su rama del repositorio**, no desde ese ZIP.

## 3. Preparar el código en Windows (PowerShell)

1. Instala Node.js LTS compatible (mínimo 22.13.0) y Git. Para WhatsApp real, tendrás que descargar las dependencias del `package.json` por internet.
2. Descomprime el ZIP entregado **conservando** la ruta `04_imp/servicio_whatsapp/`. Si ya tienes un clon del repo, copia el contenido de esta carpeta sobre la misma ruta en la rama del issue #59. Evita copiar los tres ZIP antiguos encima de la versión corregida.
3. Comprueba los archivos y ejecuta las pruebas:

```powershell
cd .\04_imp\servicio_whatsapp
node --version
npm.cmd test
npm.cmd run demo
```

4. Para la integración con WhatsApp real, instala las dependencias del `package.json`:

```powershell
npm.cmd install
```

La suite `npm test` no necesita acceder a internet. `npm install` sí necesita descargar Baileys y QR terminal. Conserva una copia de la salida de la suite desidentificada, con commit SHA y fecha.

## 4. Probar primero Gemini REAL sin WhatsApp (datos ficticios)

1. Entra en Google AI Studio y obtén la **clave de un proyecto propio** para Gemini Developer API. Verifica que el modelo `gemini-3.1-flash-lite` esté accesible y tenga cuota gratuita para ese proyecto. No guardes ni compartas la clave en el repositorio, capturas o chats.
2. En la misma terminal (ejemplo, introduce la clave sin compartir su valor), configura `LLM_API_KEY` y ejecuta:

```powershell
function SolicitarSecreto([string]$mensaje) {
  $secreto = Read-Host -Prompt $mensaje -AsSecureString
  $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secreto)
  try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$env:LLM_API_KEY = SolicitarSecreto 'LLM_API_KEY para esta sesión'
$env:LLM_MODEL = 'gemini-3.1-flash-lite'
npm.cmd run smoke:gemini
```

Resultado buscado: línea `GEMINI_LIVE_ISSUE_59` con `resultado: APROBADO` y `preguntaDescartada: true`. Esta prueba envía únicamente tres frases inventadas, comprueba la aceptación del JSON Schema y valida el resultado, pero **no demuestra CP-77**. Si aparece `LLM_CUOTA`, `LLM_NO_DISPONIBLE` o `LLM_FORMATO`, registra el código sin registrar cuerpo ni clave y corrige antes de continuar.

Los términos de Google (https://ai.google.dev/gemini-api/terms), en vigor desde el 23/03/2026, describen el uso de contenido bajo servicios gratuitos para mejorar productos. **El texto de grupos reales puede contener datos personales distintos de teléfonos; el enmascarado implementado no garantiza anonimización completa.** Realiza esta prueba solo con datos ficticios. La eventual integración con chats reales requiere autorización y revisión de privacidad del equipo.

## 5. Preparar Java y WhatsApp con tu número dedicado

**Backend (dependencia #57):** el README exige `SERVICE_CREDENTIAL` (el mismo valor de `SERVICE_TOKEN` en Node, **mínimo 32 caracteres**). Utiliza la rama `feature/HU08-CU06-java-recibir-analisis` del repositorio y la carpeta `04_imp/backend_java`. El proyecto identifica Java 21, Spring Boot, PostgreSQL/PostGIS, migraciones Flyway y Maven Wrapper. Sigue su README para las variables de conexión, credencial de servicio, despliegue y pruebas. Antes del ensayo verifica que `GET /monitoreo/surtidores` y `POST /monitoreo/resultados` están operativos y usan la **misma credencial exclusiva de servicio** que configurarás en Node.

**Número/grupo:** debes proporcionar el número internacional **solo dígitos y sin `+`**, pertenecer a la cuenta vinculada y obtener autorización expresa del administrador de cada grupo que se monitorizará (#51). No copies al ZIP ni subas a Git sesiones de WhatsApp, números privados ni las constancias. La conexión se establece mediante QR mostrado en la terminal. Se requiere acceso efectivo a WhatsApp y que el lector confirme `WHATSAPP_CONECTADO`.

Crea un directorio privado fuera de Git, por ejemplo `C:\ChuroViajePrivado`. Dentro coloca `autorizaciones.json`, con los campos requeridos por `src/authorizations.js`; reemplaza el siguiente esquema por **datos efectivos de un grupo autorizado**:

```json
{
  "autorizaciones": [
    {
      "grupo": "IDENTIFICADOR_DEL_GRUPO@g.us",
      "fecha": "2026-11-17T16:00:00-04:00",
      "solicitud": "REFERENCIA_DE_LA_AUTORIZACION_REAL",
      "autorizadoPorAdmin": true
    }
  ]
}
```

La fecha y el texto anteriores son **solo un ejemplo de formato**, no una autorización. `autorizadoPorAdmin` debe representar una aprobación auténtica. El código rechaza registros incompletos o que no estén fuera de Git. En Windows restringe las ACL de esa carpeta, la sesión SQLite y el JSON a la identidad del servicio; en Linux utiliza permisos `0700` para el directorio y `0600` para los archivos.

En una consola PowerShell donde vayas a ejecutar el servicio, define las **variables reales** (sustituye solo los marcadores):

```powershell
function SolicitarSecreto([string]$mensaje) {
  $secreto = Read-Host -Prompt $mensaje -AsSecureString
  $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secreto)
  try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
  finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$env:BACKEND_URL = 'http://127.0.0.1:8080'  # Solo si Java corre en el mismo equipo; remoto necesita HTTPS
$env:SERVICE_TOKEN = SolicitarSecreto 'Credencial de servicio Java'
$env:SERVICE_AUTH_HEADER = 'Authorization'
$env:SERVICE_TOKEN_PREFIX = 'Bearer '
$env:LLM_API_KEY = SolicitarSecreto 'Clave Gemini'
$env:LLM_MODEL = 'gemini-3.1-flash-lite'
$env:ALLOWED_GROUP_IDS = 'IDENTIFICADOR_REAL_DEL_GRUPO@g.us'
$env:WHATSAPP_ENABLED = 'true'
$env:WHATSAPP_PHONE_NUMBER = Read-Host 'Número dedicado internacional solo dígitos'
$env:WHATSAPP_AUTHORIZATIONS_FILE = 'C:\ChuroViajePrivado\autorizaciones.json'
$env:WHATSAPP_SESSION_PATH = 'C:\ChuroViajePrivado\session.sqlite'
$env:SQLITE_PATH = 'C:\ChuroViajePrivado\pendientes.sqlite'
$env:MAINTENANCE_INTERVAL_MS = '60000'
$env:ANALYSIS_INTERVAL_MS = '300000'
npm.cmd start
```

El ejemplo `BACKEND_URL` es solo para backend local. Comprueba en el servidor el puerto y la política de autenticación, no reutilices un token JWT de conductor. Si la contraseña o las variables se gestionan con un almacén de secretos, utiliza ese procedimiento en lugar de escribirlas manualmente.

## 6. Ejecutar CP-74, CP-75, CP-76 y CP-78

Las pruebas automatizadas (archivo `test/analisis-lotes.test.js` y refuerzos) cubren:

| Caso | Mensaje/escenario de prueba sintético | Resultado verificable |
| --- | --- | --- |
| CP-74 | `ya hay gasolina en Las Vegas, fila corta` | Aviso Las Vegas / Gasolina / DISPONIBLE / CORTA, fecha original |
| CP-75 | `¿alguien sabe si hay diésel en El Portillo?` | Descartar aunque el LLM invente un aviso |
| CP-76 | LLM simulado indisponible y restaurado | Mantener `PENDIENTE` y procesar al ciclo siguiente, si no caducó |
| CP-78 | `llamen al 71234567` | El payload enviado al doble Gemini contiene `[número]`, no el teléfono |

**Evidencia:** la salida de `npm test` contiene resultados con logs estructurados sin texto privado. El recorrido local completo de `test/issue59-refuerzo.test.js` NO usa Java ni WhatsApp real.

## 7. CP-77 extremo a extremo: WhatsApp real → Gemini real → Java real

1. Arranca Java con sus migraciones V8 y credencial de servicio. Revisa el catálogo efectivo: debe contener el surtidor y el combustible que usarás. No supongas nombres ni alias: primero consulta `GET /monitoreo/surtidores` desde tu entorno autorizado.
2. Arranca Node (`npm.cmd start`) y comprueba `WHATSAPP_CONECTADO`; espera que el reloj/servicio estén sincronizados. Ten habilitados el registro de servicio y el acceso de lectura autorizado a PostgreSQL.
3. Desde **otro miembro autorizado del grupo de prueba**, envía un texto ficticio claro, por ejemplo `ya hay gasolina en Las Vegas, fila corta` **solo si ese surtidor está en el catálogo que respondió Java**. Registra `t0` (hora real de envío del mensaje).
4. Espera el siguiente ciclo de cinco minutos; verifica en el log Node, **sin textos/números/nombres**, los eventos `LOTE_ANALIZADO` y `RESULTADO_ENVIADO`. Si hay errores de LLM/catálogo/token, conserva el código de error, no el contenido.
5. En PostgreSQL de Java, consulta la tabla sin modificarla: `SELECT * FROM estado_combustible LIMIT 20;` y ubica el aviso correspondiente por surtidor, combustible, origen WhatsApp y marca de fecha. Ajusta el filtro por los nombres **reales de columnas** de la migración V8 de ese backend. Verifica que la fila realmente esté persistida; HTTP 200 por sí solo no acredita esta condición.
6. Registra `t1` (hora en que se acredita la persistencia) y calcula **`t1 − t0 < 360 segundos`**. Guarda fecha/huso horario, commit SHA, versiones, log desidentificado y evidencia SQL desidentificada.
7. Si no aparece en la tabla o tarda 360 segundos o más, CP-77 queda **FALLIDO o BLOQUEADO**, no aprobado. Inspecciona catálogo, token, timing, cuota, sanitización, permisos y conectividad, y repite con datos sintéticos.

El script `demo/cp77-extremo-a-extremo.js` incluido en el servicio también se conserva para una integración parcial con Java: inyecta el texto mediante `ingestText` y simula Gemini. **No sustituye** el procedimiento de WhatsApp y Gemini reales de esta sección.

## 8. Control de cambios/PR

```powershell
# En tu clon del repositorio público:
git fetch origin
git switch feature/HU08-CU06-llm-analisis-lotes
# Copia SOBRE 04_imp/servicio_whatsapp/ los archivos del ZIP corregido.
git status --short
git diff --check
cd 04_imp/servicio_whatsapp
npm.cmd test
cd ../..
git add 04_imp/servicio_whatsapp
git commit -m "fix(HU08-CU06): validar evidencias y reintentos issue 59"
git push origin feature/HU08-CU06-llm-analisis-lotes
```

Crear PR desde `feature/HU08-CU06-llm-analisis-lotes` **hacia** `develop`, enlazar `Closes #59` solo si TODOS los criterios y CP-77 están acreditados; si quedan pendientes, usar `Refs #59`. No incluir `.env`, SQLite de mensajes, SQLite de sesión, claves, QR, mensajes reales ni logs con datos privados. Revisar diferencias de la rama respecto a `develop` antes de publicar.

## 9. Matriz de estado para cierre

| Criterio issue #59 | Prueba local | Estado sin credenciales reales |
| --- | --- | --- |
| RN-06.5: lote cronológico cada 5 minutos | Sí, timer y 501 mensajes en un lote | **Probado con simulación**; verificar capacidad y reloj reales |
| RN-06.11/12: caducidad y borrado | Sí, SQLite y caducidad durante LLM | **Probado con reloj simulado/SQLite real** |
| RN-06.6, CP-78: privacidad telefónica | Sí, varias variantes; HTTP local | **Probado con texto sintético** |
| RN-06.7/8/9: extracción y descarte | Sí, positivos y adversos con mock | **Probado con salida LLM simulada**; precisión live pendiente |
| RN-06.10: LLM caído | Sí, falla mock y recuperación | **Probado con simulación** |
| RN-06.18: POST tras análisis | Sí, servidor HTTP local | **Probado con backend simulado** |
| RN-06.19: borrado, cola y reenvío | Sí, SQLite real, fallas y recuperación mock | **Probado con backend simulado**; recuperación live pendiente |
| D-17: Free Tier + términos | README y modelo configurados | **Documentado**; llamada real bloqueada sin API key |
| CP-77: grupo real → tabla Java <6 min | No puede acreditarse aquí | **PENDIENTE** de número, grupo, Gemini y Java reales |

**Regla:** ningún criterio que exija una prueba real se marca cumplido por pasar simuladores. La función de validación textual es deliberadamente conservadora y no prueba que el contenido original del grupo sea verdadero.
