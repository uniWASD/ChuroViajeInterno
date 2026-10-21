# Servicio WhatsApp/LLM de ChuroViaje

Infraestructura Node.js del servicio de monitoreo de surtidores (CU-06, HU-08 y T-091, issue #43). Su ubicación en el repositorio es `04_imp/servicio_whatsapp/`.

El servicio contiene su **propio SQLite** para mensajes de texto pendientes, configura un cliente HTTP hacia las dos rutas de monitoreo y ejecuta mantenimiento al iniciar y durante su ejecución. **Todavía no se conecta a WhatsApp/Baileys, no invoca un LLM ni consulta o modifica automáticamente los surtidores**: esas integraciones corresponden a las tareas de lectura y análisis posteriores. El cliente HTTP se usa solo si otro componente lo llama de forma explícita.

## Requisitos de ejecución

- Node.js **22.13.0 o posterior**, sin paquetes externos; se utiliza `node:sqlite` integrado en Node.js. Como decisión técnica, no se incorpora un controlador SQLite nativo adicional que obligue a compilar dependencias al desplegar en ARM.
- Se recomienda usar una versión LTS de Node.js mantenida y fijar la misma versión en el servidor y en la CI. Node 22 puede emitir `ExperimentalWarning` al cargar `node:sqlite`; no equivale a un fallo.
- Plataformas verificadas: **Linux x86_64 con Node.js 22.16.0** (pruebas y arranque) y **Windows con Node.js 22.18.0** (10 rondas aprobadas de 68 pruebas, demostración HTTP, cobertura y arranque). La prueba de permisos POSIX se omite normalmente en Windows. **Ubuntu Linux ARM queda pendiente de verificación física**.
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

`npm test` ejecuta las pruebas automáticas, incluido el arranque de un subproceso, pruebas de migración y la demostración con un doble HTTP local, usando solamente datos ficticios. `npm run demo` ejecuta **esa misma demostración por separado** cuando se desea ver su resultado; no es necesario ejecutarla dos veces para validar. No se requiere `npm install`. El doble HTTP escucha temporalmente en `127.0.0.1` y no es un backend Java real.

### Verificación de compatibilidad en Windows

Para reproducir 10 rondas de pruebas, la demostración y el informe de cobertura sin configuración real:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\verificar_windows_T91.ps1 -Rondas 10
```

La comprobación guarda los registros en `evidencias_T91_Windows/`. En Windows, las 68 pruebas deben dar **67 aprobadas, 0 fallidas y 1 omitida** (la prueba de permisos POSIX). Conviene extraer primero el ZIP en una carpeta local de ruta corta, por ejemplo `C:\T91\servicio_whatsapp`, para descartar interferencias de unidades sincronizadas o rutas extensas. Las pruebas de despliegue evitan `fs.cpSync` para prevenir un cierre nativo observado en Node.js 22.18.0 sobre Windows. Se verificaron 10 rondas satisfactorias desde una carpeta local; la ejecución original en una unidad sincronizada con ruta larga provocaba el cierre del proceso.

## Configuración y arranque

El servicio utiliza, por orden de prioridad, **variables de entorno**, un **JSON externo** señalado por `WHATSAPP_SERVICE_CONFIG_FILE` y valores predeterminados cuando existen. Para desarrollo también puede cargarse un `.env` dentro de la carpeta del servicio: el comando `npm start` incluye `--env-file-if-exists=.env`, por lo que **funciona con o sin dicho archivo**. Las variables existentes del entorno prevalecen sobre `.env`.

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

**El formato exacto del catálogo, del resultado del POST, de los valores de fila y de la cabecera de autenticación sigue pendiente del acuerdo con el responsable del backend Java** (D-13 y contrato de arquitectura `05_contratos.md`). El backend, no el servicio Node, valida los surtidores, combustibles y filas. La demostración usa un doble local con una forma de JSON meramente ilustrativa. Antes de integrar con Java, confirmar el contrato de `03_des/02_Arquitectura/05_contratos.md` con el equipo responsable del backend.

## Organización y tareas próximas

- `src/`: configuración, arranque, almacén SQLite, servicio, cliente HTTP, errores y registro estructurado.
- `test/`, `test-support/`: pruebas automatizadas con datos sintéticos y carpetas temporales.
- `demo/run-demo.js`: demostración local y doble HTTP; **no es** el simulador de tráfico de CU-07.

Los métodos `ingestText`, `listPending`, `markProcessed` y el temporizador preciso dejan preparada parte de la infraestructura de las tareas de lectura de grupos y análisis por lotes. `ingestText` devuelve `IGNORADO` para grupos no autorizados, sin escribir en SQLite. **No implementan todavía** conexión Baileys ni procesamiento LLM, ni la cola de resultados que exige RN-06.19 para reintentos de envío al backend.
