# Servicio WhatsApp/LLM de ChuroViaje

Servicio Node.js del monitoreo de surtidores (CU-06, HU-08; base de T-091, issue #43 y lectura de grupos del issue #51). Su ubicación en el repositorio es `04_imp/servicio_whatsapp/`.

El servicio contiene su **propio SQLite** para mensajes de texto pendientes, configura un cliente HTTP hacia las dos rutas de monitoreo y ejecuta mantenimiento al iniciar y durante su ejecución. **Incorpora conexión de solo lectura con Baileys al activar `WHATSAPP_ENABLED=true`. No invoca un LLM ni consulta o modifica automáticamente los surtidores**: el análisis corresponde a otro issue. El cliente HTTP se usa solo si otro componente lo llama de forma explícita.

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

La comprobación guarda los registros en `evidencias_T91_Windows/`. En Windows deben aprobarse **todas las pruebas, salvo la de permisos POSIX, que se omite en Windows** (0 fallidas). Se recomienda ejecutar desde una ruta local corta, por ejemplo `C:\T91\servicio_whatsapp`, evitando carpetas sincronizadas o rutas extensas. Las pruebas existentes de T-091 evitan `fs.cpSync` para prevenir un cierre nativo observado en Node.js 22.18.0 sobre Windows.

## Configuración y arranque

El servicio utiliza, por orden de prioridad, **variables de entorno**, un **JSON externo** señalado por `WHATSAPP_SERVICE_CONFIG_FILE` y valores predeterminados cuando existen. Para desarrollo también puede cargarse un `.env` dentro de la carpeta del servicio: el comando `npm start` incluye `--env-file-if-exists=.env`, por lo que **funciona con o sin dicho archivo**. Las variables existentes del entorno prevalecen sobre `.env`. Si no existe `.env`, Node puede mostrar `.env not found. Continuing without it.`; es un aviso informativo, no un error de arranque.

Para desarrollo local en Linux/macOS:

```bash
cp .env.example .env
npm start
```

En PowerShell:

```powershell
Copy-Item .env.example .env
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

**El formato exacto del catálogo, del resultado del POST, de los valores de fila y de la cabecera de autenticación sigue pendiente del acuerdo con el responsable del backend Java** (D-13 y contrato de arquitectura `05_contratos.md`). El backend, no el servicio Node, valida los surtidores, combustibles y filas. La demostración usa un doble local con una forma de JSON meramente ilustrativa. Antes de integrar con Java, confirmar el contrato de `03_des/02_Arquitectura/05_contratos.md` con el equipo responsable del backend.

## Organización y tareas próximas

- `src/`: configuración, arranque, almacén SQLite, servicio, cliente HTTP, errores y registro estructurado.
- `test/`, `test-support/`: pruebas automatizadas con datos sintéticos y carpetas temporales.
- `demo/run-demo.js`: demostración local y doble HTTP; **no es** el simulador de tráfico de CU-07.

Los métodos `ingestText`, `listPending`, `markProcessed` y el temporizador preciso dejan preparada parte de la infraestructura de las tareas de lectura de grupos y análisis por lotes. `ingestText` devuelve `IGNORADO` para grupos no autorizados, sin escribir en SQLite. **La lectura Baileys se implementa en `whatsapp-reader.js`** con filtrado y deduplicación. No hay procesamiento LLM ni cola de resultados para el backend en el alcance del issue #51.


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
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .erificar_HU08_windows.ps1 -Rondas 10
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
