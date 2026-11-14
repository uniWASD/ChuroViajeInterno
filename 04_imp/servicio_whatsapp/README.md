# Servicio WhatsApp/LLM de ChuroViaje

Servicio Node.js del monitoreo de surtidores (CU-06, HU-08; base de T-091, issue #43, lectura de grupos del issue #51 y análisis por lotes del issue #59). Su ubicación en el repositorio es `04_imp/servicio_whatsapp/`.

El servicio contiene su **propio SQLite** para mensajes de texto pendientes, configura un cliente HTTP hacia las dos rutas de monitoreo y ejecuta mantenimiento al iniciar y durante su ejecución. **Incorpora conexión de solo lectura con Baileys al activar `WHATSAPP_ENABLED=true`.** Con `LLM_API_KEY` configurada, cada 5 minutos analiza los pendientes con Gemini y entrega el resultado al backend (sección del issue #59). Sin esa clave no se llama al LLM y los mensajes vencen a los 30 minutos sin salir de la máquina.

## Requisitos de ejecución

- Node.js **22.13.0 o posterior**, con Baileys y qrcode-terminal para activar la conexión real; las pruebas automatizadas no necesitan dependencias descargadas. Se utiliza `node:sqlite` integrado en Node.js. Como decisión técnica, no se incorpora un controlador SQLite nativo adicional que obligue a compilar dependencias al desplegar en ARM.
- Se recomienda usar una versión LTS de Node.js mantenida y fijar la misma versión en el servidor y en la CI. Node 22 puede emitir `ExperimentalWarning` al cargar `node:sqlite`; no equivale a un fallo.
- Plataformas verificadas: **Linux x86_64 con Node.js 22.16.0** (pruebas y arranque) y **Windows con Node.js 22.18.0** (10 rondas aprobadas, demostración HTTP, cobertura y arranque). En Windows se omite la prueba de permisos POSIX, pero las demás deben aprobarse. **Ubuntu Linux ARM queda pendiente de verificación física**.
- La ejecución necesita permisos de escritura sobre el SQLite privado. El servicio fuerza `0600` para el archivo SQLite en POSIX y crea `data/` con permisos restringidos; en Windows se debe aplicar la ACL correspondiente.

## Pruebas y demostración local

Desde `04_imp/servicio_whatsapp/`:

```bash
npm test
npm run demo
```

En PowerShell:

```powershell
npm.cmd test
npm.cmd run demo
```

`npm test` ejecuta las pruebas automáticas, incluido el arranque de un subproceso, pruebas de migración y la demostración con un doble HTTP local, usando solamente datos ficticios. `npm run demo` ejecuta **esa misma demostración por separado** cuando se desea ver su resultado; no es necesario ejecutarla dos veces para validar. No se requiere `npm install` para las pruebas simuladas; para la conexión real sí, ejecutar `npm install`. El doble HTTP escucha temporalmente en `127.0.0.1` y no es un backend Java real.

### Verificación de compatibilidad en Windows

Para reproducir 10 rondas de pruebas, la demostración y el informe de cobertura sin configuración real:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\verificar_windows.ps1 -Rondas 10
```

La comprobación guarda los registros en `evidencias_T91_Windows/`. En Windows deben aprobarse **todas las pruebas, salvo la de permisos POSIX, que se omite en Windows** (0 fallidas). Se recomienda ejecutar desde una ruta local corta, por ejemplo `C:\T91\servicio_whatsapp`, evitando carpetas sincronizadas o rutas extensas. Las pruebas de despliegue evitan `fs.cpSync` para prevenir un cierre nativo observado en Node.js 22.18.0 sobre Windows.

## Configuración y arranque

El servicio utiliza, por orden de prioridad, **variables de entorno**, un **JSON externo** señalado por `WHATSAPP_SERVICE_CONFIG_FILE` y valores predeterminados cuando existen. Para desarrollo también puede cargarse un `.env` dentro de la carpeta del servicio: el comando `npm start` incluye `--env-file-if-exists=.env`, por lo que **funciona con o sin dicho archivo**. Las variables existentes del entorno prevalecen sobre `.env`. Si no existe `.env`, Node puede mostrar `.env not found. Continuing without it.`; es un aviso informativo, no un error de arranque.

Para desarrollo local en Linux/macOS:

```bash
cp .env.example .env
# Editar los valores FICTICIOS de .env para su entorno de prueba
npm start
```

En PowerShell:

```powershell
Copy-Item .env.example .env
# Editar .env
npm.cmd start
```

En producción, preferir las variables protegidas del gestor de servicios (p. ej. systemd) o un JSON externo **fuera del directorio del servicio y de cualquier repositorio Git**. No colocar tokens reales en `.env` versionado ni en documentación. Ejemplo de archivo externo (`/etc/churoviaje/whatsapp.json`, ruta orientativa):

```json
{
  "BACKEND_URL": "https://backend.example.invalid",
  "SERVICE_TOKEN": "VALOR_FICTICIO_REEMPLAZAR",
  "ALLOWED_GROUP_IDS": ["grupo-autorizado@g.us"],
  "SQLITE_PATH": "/var/lib/churoviaje/pendientes.sqlite"
}
```

Ejemplo de invocación Linux con esa ruta (el archivo debe existir y tener permisos restringidos):

```bash
WHATSAPP_SERVICE_CONFIG_FILE=/etc/churoviaje/whatsapp.json npm start
```

| Variable | Uso | Valor predeterminado |
| --- | --- | --- |
| `BACKEND_URL` | URL base del backend; HTTPS para hosts remotos | Obligatoria |
| `SERVICE_TOKEN` | Credencial exclusiva del servicio | Obligatoria |
| `ALLOWED_GROUP_IDS` | Lista de grupos separados por comas, o arreglo en JSON | Obligatoria |
| `WHATSAPP_SERVICE_CONFIG_FILE` | Ruta de archivo JSON fuera del servicio y de repositorios Git | Ninguno |
| `SQLITE_PATH` | SQLite privado; una ruta relativa se interpreta desde el directorio de este servicio, independientemente del directorio de ejecución | `./data/pendientes.sqlite` |
| `MAINTENANCE_INTERVAL_MS` | Mantenimiento de pendientes y depuración de filas (no ejecuta análisis LLM) | `300000` (5 minutos) |
| `SERVICE_AUTH_HEADER` | Cabecera de autenticación provisional hasta acordar Java | `Authorization` |
| `SERVICE_TOKEN_PREFIX` | Prefijo de la credencial; debe terminar en espacio, o ser vacío | `Bearer ` |
| `LLM_API_KEY` | Reservada para una tarea posterior; actualmente no se usa | Cadena vacía |

`POLL_INTERVAL_MS` ya no se admite: utilizar `MAINTENANCE_INTERVAL_MS`. `npm start` no envía solicitudes al backend al iniciar, por lo que la ausencia del backend Java todavía no impide probar el arranque. Detener el servicio con `Ctrl+C`.

### Diagnósticos de arranque

El proceso emite líneas JSON por una lista blanca. Por ejemplo, si falta la URL:

```json
{"evento":"ERROR_CONFIGURACION","codigo":"CONFIG_INVALIDA","detalle":"Falta BACKEND_URL."}
```

Los errores nativos no incluyen su mensaje de excepción en el registro, para evitar que una ruta o un cuerpo contenga secretos. `ERROR_SQLITE` identifica problemas de SQLite o acceso al archivo, mientras que `ERROR_INESPERADO` identifica otros fallos. No se registran texto, remitente, grupo, URL con secretos ni tokens.

## Persistencia y privacidad: MensajePendiente

`src/store.js` implementa el almacén identificado por el modelo de dominio como `MensajePendiente`. Aplica **RN-06.3 y RN-06.4** al aceptar exclusivamente `id`, `group`, `date` y `text`. Solo se persisten ID, grupo, fecha/hora, texto y estado, más las columnas técnicas `fecha_ms` (fecha declarada del mensaje), `recibido_ms` (hora de recepción efectiva) y `finalizado_ms` (fin de tratamiento). Nunca se guardan nombres, números ni identificadores de remitentes: **CP-26**.

El vencimiento estricto de **RN-06.11** usa `min(fecha_ms, recibido_ms)`; una fecha futura no puede prolongar la vida del texto más allá de los **30 minutos desde la recepción**. En el instante exacto de 30 minutos aún está pendiente; en el siguiente milisegundo se descarta y se borra el texto (`RN-06.12`). La limpieza se ejecuta al arrancar y se reprograma para el siguiente vencimiento, además del mantenimiento periódico. Al marcar un mensaje como procesado o descartado, `texto` pasa a `NULL`. El almacén activa `PRAGMA secure_delete=ON` y evita el modo WAL persistente.

Las filas finalizadas permanecen **24 horas** sin texto para deduplicar mensajes recibidos durante reconexiones. Pasado ese período se eliminan en el siguiente mantenimiento. Esta retención es una **decisión local propuesta**, revisable por el equipo; no se deduce de P-21 (que trata otros datos del backend). Los estados de las filas son `PENDIENTE`, `PROCESADO` y `DESCARTADO`.

El esquema tiene `PRAGMA user_version=1`, admite columnas técnicas y tablas auxiliares sin almacenar remitentes y migra la tabla original de siete columnas. Durante esa migración, los pendientes antiguos con fecha futura se descartan preventivamente porque la fecha real de recepción no existía en el esquema anterior.

## Límites del contrato HTTP

`src/backend-client.js` prepara solicitudes `GET /monitoreo/surtidores` y `POST /monitoreo/resultados`, usando la credencial de servicio y protecciones de tiempo de espera, HTTPS remoto y saneamiento de errores. Un `POST` aceptado con `204 No Content` devuelve `null`; las respuestas con cuerpo deben ser objetos JSON.

El formato del catálogo y del resultado es el de `03_des/02_Arquitectura/05_contratos.md`, sección «Servicio de WhatsApp/LLM con el backend», que implementa el backend en el issue #57. El servicio filtra la salida del LLM antes de enviarla, pero quien decide qué se guarda es el backend, que vuelve a validar cada aviso (RN-06.15). La demostración `npm run demo` usa un doble local.

## Organización y tareas próximas

- `src/`: configuración, arranque, almacén SQLite, servicio, cliente HTTP, errores y registro estructurado.
- `test/`, `test-support/`: pruebas automatizadas con datos sintéticos y carpetas temporales.
- `demo/run-demo.js`: demostración local y doble HTTP; **no es** el simulador de tráfico de CU-07.

Los métodos `ingestText`, `listPending`, `markProcessed` y el temporizador preciso dejan preparada parte de la infraestructura de las tareas de lectura de grupos y análisis por lotes. `ingestText` devuelve `IGNORADO` para grupos no autorizados, sin escribir en SQLite. **La lectura Baileys se implementa en `whatsapp-reader.js`** con filtrado y deduplicación. El análisis con el LLM y la cola de resultados para el backend se agregaron en el issue #59.

## HU-08 / issue #51: lectura de grupos autorizados

La conexión real está desactivada por defecto para conservar el arranque y la demostración sin una cuenta real. `WHATSAPP_ENABLED=true` exige un número de uso exclusivo del proyecto, los IDs reales de grupos aprobados, el registro de autorizaciones y el acceso a una sesión SQLite privada. Esta funcionalidad no envía mensajes: el código no llama a `sendMessage` ni a métodos de envío hacia grupos. La biblioteca Baileys efectúa el intercambio técnico indispensable para mantener la sesión.

### Configuración de la cuenta y autorizaciones

1. Obtener un número dedicado controlado por el equipo. Su propietario debe vincularlo a WhatsApp y escanear el QR al iniciar la conexión. La conexión se rechaza si el número autenticado no coincide con `WHATSAPP_PHONE_NUMBER` (código de país seguido de dígitos, sin `+`, espacios ni guiones).
2. Solicitar la autorización expresa al administrador de **cada** grupo y mantener registro del grupo, la fecha y el medio de solicitud. No incorporar un grupo sin autorización confirmada.
3. Guardar el registro en un JSON externo **fuera del repositorio**. Se espera el siguiente formato, sustituyendo los datos por registros verídicos y autorizados:

```json
{
  "autorizaciones": [
    {
      "grupo": "1234567890-1234567890@g.us",
      "fecha": "2026-10-29T17:00:00-04:00",
      "solicitud": "Medio de solicitud y constancia de aceptación del administrador",
      "autorizadoPorAdmin": true
    }
  ]
}
```

Los valores son ilustrativos y **no constituyen autorización**. Cada grupo incluido en `ALLOWED_GROUP_IDS` debe tener un registro con `autorizadoPorAdmin=true`; de lo contrario se impide iniciar la lectura. El lector rechaza el registro dentro del servicio, dentro de cualquier árbol Git y, en POSIX, archivos legibles por otros usuarios. En Windows, restringir las ACL a la cuenta que ejecuta el proceso; en POSIX usar directorio `0700` y archivo `0600`. Evitar almacenar nombres, números o datos personales del administrador en la constancia.

| Variable | Uso |
| --- | --- |
| `WHATSAPP_ENABLED` | `true` para abrir la conexión, `false` para solo mantenimiento local (valor predeterminado). |
| `WHATSAPP_PHONE_NUMBER` | Número dedicado en formato internacional solo dígitos, sin `+`. Obligatorio al activar. |
| `WHATSAPP_AUTHORIZATIONS_FILE` | Ruta absoluta del JSON externo de autorizaciones. Obligatoria al activar. |
| `WHATSAPP_SESSION_PATH` | SQLite de credenciales y claves Signal; archivo diferente al de mensajes. Predeterminado `./data/session.sqlite`. |
| `WHATSAPP_RECONNECT_MS` | Intervalo fijo para reintento tras una desconexión transitoria, `15000` ms predeterminado (entre 1000 y 300000). |

Las variables anteriores también se pueden configurar en el JSON externo señalado por `WHATSAPP_SERVICE_CONFIG_FILE`. Los grupos se indican mediante `ALLOWED_GROUP_IDS` como antes. Los IDs de grupos deben ser los IDs reales de WhatsApp terminados en `@g.us`. La tabla de mensajes utiliza un ID SHA-256 derivado del grupo y del identificador del mensaje: evita colisiones entre grupos, no contiene el identificador del remitente y conserva deduplicación tras reiniciar el proceso.

### Arranque y comprobación en Windows (PowerShell)

Desde la carpeta `servicio_whatsapp`:

```powershell
node --version
npm.cmd install
npm.cmd test
npm.cmd run demo
node --test --experimental-test-coverage
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\verificar_HU08_windows.ps1 -Rondas 10
```

La suite cubre CP-48, CP-49 y CP-50 con **dobles simulados** de Baileys; las pruebas de red no se ejecutan contra WhatsApp ni pueden demostrar el acceso a grupos reales sin la cuenta dedicada. Para el ensayo real controlado, configurar variables de entorno y el JSON externo con permisos adecuados, poner `WHATSAPP_ENABLED=true` y arrancar:

```powershell
$env:WHATSAPP_ENABLED = 'true'
$env:WHATSAPP_PHONE_NUMBER = 'NUMERO_DEDICADO_SOLO_DIGITOS'
$env:WHATSAPP_AUTHORIZATIONS_FILE = 'C:\ChuroViajePrivado\autorizaciones.json'
$env:WHATSAPP_SESSION_PATH = 'C:\ChuroViajePrivado\session.sqlite'
$env:ALLOWED_GROUP_IDS = 'ID_REAL_DEL_GRUPO@g.us'
npm.cmd start
```

Antes de conectar en Windows, crear la carpeta privada con `New-Item -ItemType Directory -Force C:\ChuroViajePrivado` y restringir su ACL (ajustar la identidad a la cuenta de servicio real):

```powershell
icacls 'C:\ChuroViajePrivado' /inheritance:r /grant:r "$($env:USERDOMAIN)\$($env:USERNAME):(OI)(CI)F" /T
```

Aplicar ACL privada también al JSON de autorizaciones y a la sesión creada. Mantener `BACKEND_URL` y `SERVICE_TOKEN` como los exige la configuración T-091; no se usa el backend durante la lectura. El QR se muestra únicamente en la terminal local para vincular la cuenta; no copiarlo a registros ni capturas. Tras vincular, verificar CP-48 (texto, imagen, audio: solo texto pendiente), CP-49 (un grupo excluido no se guarda), y CP-50 (desconectar red durante 2 minutos; el servicio registra desconexión y reintentos, y recibe mensajes nuevos al recuperar la red). Revisar solo el recuento de pendientes, **no exportar el texto**. Detener con `Ctrl+C`. Para volver a conectarse, la sesión debe permanecer intacta; un código 401 implica una sesión revocada y exige intervención para vincular de nuevo.

### Responsabilidad y alcance

La verificación de autorizaciones es documental: el software exige un registro externo afirmativo, pero solo el equipo puede comprobar que la autorización fue concedida verdaderamente por cada administrador. La integración con WhatsApp es mediante una librería no oficial, con riesgo de bloqueo o cambios externos. Los ensayos simulados verifican la lógica implementada pero no sustituyen la prueba de conectividad real. Ningún análisis LLM, actualización de combustible ni envío a grupos está incluido en este issue.

## HU-08 / issue #59: análisis por lotes y entrega al backend

### Ciclo

- **Cada minuto** (`MAINTENANCE_INTERVAL_MS`, 60000) se descartan los mensajes con más de 30 minutos y se borra su texto. Además, cada mensaje tiene su propio aviso al vencer, así que nunca espera al ciclo (RN-06.11, RN-06.12).
- **Cada 5 minutos** (`ANALYSIS_INTERVAL_MS`, 300000) corre `analyzeCycle()` (RN-06.5): primero reenvía los resultados que el backend no recibió (RN-06.19); después, si hay pendientes, pide el catálogo con `GET /monitoreo/surtidores`, arma un solo lote ordenado por fecha y lo manda al LLM. Sin pendientes no se llama al LLM ni al backend. Dos ciclos nunca se superponen.
- Si el LLM falla (sin conexión, error, cuota o una respuesta que no cumple el esquema), los mensajes siguen pendientes para el ciclo siguiente (RN-06.10). Como vencen a los 30 minutos, un mensaje tiene unos cinco intentos.
- Si el backend no entrega el catálogo, se usa el último que se leyó; si nunca se leyó uno, el lote espera.
- Analizado el lote, en **una sola transacción** de SQLite los mensajes quedan `PROCESADO` sin texto y el resultado queda en la tabla `resultado_pendiente`, que solo guarda los avisos del contrato (surtidor, combustible, estado, fila y fecha) (RN-06.12, RN-06.19). Enseguida se envía con `POST /monitoreo/resultados` (RN-06.18). Con 200 se borra; con 400 o 422 se registra y se suelta; con 401, 403, 5xx o sin conexión se conserva y se reenvía en el ciclo siguiente. Un resultado que no se pudo entregar en 24 horas se suelta. Más de 1000 avisos se entregan en varios lotes.

### Qué viaja al LLM

Solo el texto y la fecha de cada mensaje, con un identificador que es su posición en el lote (`m1`, `m2`…), no el de WhatsApp; no viajan el grupo ni nada del remitente. Antes de enviarlo, `src/phone-mask.js` reemplaza por `[número]` toda secuencia de 7 a 15 dígitos, con o sin `+` y con espacios, puntos o guiones (RN-06.6). Junto al lote viaja el catálogo del backend: surtidores con sus alias, combustibles y filas.

El prompt (`src/llm-prompt.js`) es el de la investigación del issue #10 (rama `feature/HU08-CU06-AnalisisLLM`, `05_pro/PruebaAnalisisLLM/03des/HU08_CU06_investigacion_LLM.md`, sección 8.2), con dos cambios: el catálogo sale del backend y no de una lista fija, y cada mensaje se analiza solo, sin ventanas de contexto. Se pide salida estructurada (`responseJsonSchema`) con los surtidores, combustibles y filas del catálogo como valores cerrados. El LLM no devuelve fechas: la fecha del aviso es siempre la del mensaje (RN-06.16).

### Validación antes de enviar

`src/analysis.js` aplica la validación determinista de la sección 9 de la investigación. Se descarta un aviso si: el mensaje no existe en el lote; el surtidor, combustible, estado o fila no son del catálogo; el surtidor no aparece en el texto por su nombre o un alias; el mensaje es una pregunta (lleva «¿» o termina en «?»); o el mismo mensaje dice que hay y que no hay del mismo surtidor y combustible (RN-06.7 a RN-06.9). Un combustible no mencionado va como `Desconocido` (RN-06.8). El registro cuenta los descartes del LLM y los del validador, nunca su contenido.

### Gemini y sus términos (D-17)

Se usa la API de Gemini, modelo `gemini-3.1-flash-lite` (`LLM_MODEL`), con la clave en la cabecera `x-goog-api-key`, nunca en la URL. **La capa gratuita es un «servicio no pagado»: según los términos adicionales de la API de Gemini, Google usa lo enviado y las respuestas para mejorar sus productos, personas pueden leerlo y Google pide no enviar información personal o confidencial.** Por eso se enmascaran los teléfonos y no viajan grupos ni remitentes; la investigación del issue #10 recomienda activar la facturación antes de procesar conversaciones reales. Términos: <https://ai.google.dev/terms> (vigentes desde el 23 de marzo de 2026), revisados el 14 de noviembre de 2026.

### Configuración

| Variable | Por defecto | Uso |
| --- | --- | --- |
| `LLM_API_KEY` | vacía | Sin ella no se programa el análisis (`ANALISIS_DESACTIVADO`) |
| `LLM_MODEL` | `gemini-3.1-flash-lite` | Modelo de Gemini |
| `LLM_BASE_URL` | `https://generativelanguage.googleapis.com` | Solo para pruebas con un LLM simulado: HTTPS, o HTTP en `127.0.0.1`/`localhost` |
| `ANALYSIS_INTERVAL_MS` | `300000` | Ciclo de análisis (RN-06.5) |
| `MAINTENANCE_INTERVAL_MS` | `60000` | Revisión de vencidos (RN-06.11) |

### Pruebas

`test/analisis-lotes.test.js` y `test/llm.test.js` usan un LLM y un backend simulados: CP-74 (aviso de Las Vegas, Gasolina, disponible, fila corta), CP-75 (la pregunta se descarta), CP-76 (LLM caído y reintento), CP-78 (el número llega enmascarado), backend caído y reenvío, 400 y 403 del backend, validador, catálogo caído, lotes de más de 1000 avisos y ciclos superpuestos. Las pruebas imprimen el registro del servicio como evidencia; no contiene textos, números ni nombres. CP-77 (de punta a punta con el backend real) se comprobó con `demo/cp77-extremo-a-extremo.js`; ver su encabezado.

