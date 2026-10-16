# T-091 · Estructura del servicio WhatsApp/LLM (issue #43)

**Ruta del repositorio:** `04_imp/servicio_whatsapp/`  
**Tecnología:** JavaScript ESM, `node:test`, SQLite mediante `node:sqlite` (Node.js >=22.13).  
**Alcance:** exclusivamente infraestructura solicitada en [issue #43](https://github.com/uniWASD/ChuroViajeInterno/issues/43); sin lector de chats, WhatsApp real, LLM, Python ni backend Java/PostgreSQL.

## Archivos de esta entrega

```text
04_imp/servicio_whatsapp/
├── .env.example                   Ejemplo de variables (sin secretos)
├── .gitignore                     Impide incluir bases, claves y logs
├── README.md                      Configuración, comandos y limitaciones
├── package.json                   Node.js y scripts
├── src/
│   ├── main.js                    Arranque y cierre del proceso
│   ├── config.js                  Configuración externa
│   ├── store.js                   MensajePendiente / SQLite (P-22, CP-26)
│   ├── datetime.js                Fechas y vencimientos
│   ├── backend-client.js          Cliente GET/POST con credencial
│   ├── service.js                 Inicio, expiración y ciclo del servicio
│   ├── logger.js                  Registros sin datos sensibles
│   └── errors.js                  Errores saneados
├── test/                          Pruebas de criterios del issue
├── test-support/helpers.js        Recursos de prueba temporales
└── simulator/run-demo.js          Doble HTTP efímero y SQLite solo del servicio
```

## Pruebas completas en un solo comando

En una terminal situada en `04_imp/servicio_whatsapp/`:

```powershell
npm.cmd run verify
```

Alternativas: `npm test` (tests automatizados), `npm run demo` (demostración autocontenida) y `npm start` (arranque del servicio con configuración real). **No hace falta `npm install` para pruebas.** Los comandos `test`, `demo` y `verify` usan exclusivamente datos ficticios y no requieren credenciales, Python, chats, WhatsApp ni LLM.

La prueba `demo` inicia un *doble HTTP temporal en 127.0.0.1* que **solo devuelve respuestas ficticias a GET/POST**. No implementa catálogos, estados de surtidores, bases de datos ni reglas de negocio del backend; el único SQLite de la demo es el del servicio. El doble se cierra después de probar y no se instala en producción.

## Configurar `npm start`

La configuración se obtiene de variables de entorno o un **archivo JSON fuera del repositorio**, cuya ruta se pasa con `WHATSAPP_SERVICE_CONFIG_FILE`. Las variables de entorno prevalecen sobre el JSON.

| Variable | Propósito | Requisito |
|---|---|---|
| `BACKEND_URL` | URL raíz del backend | Obligatoria |
| `SERVICE_TOKEN` | Credencial propia para GET/POST | Obligatoria |
| `ALLOWED_GROUP_IDS` | IDs separados por coma | Obligatoria |
| `LLM_API_KEY` | Credencial futura, se carga pero no se utiliza | Opcional |
| `SQLITE_PATH` | Archivo SQLite propio | Por defecto `./data/pendientes.sqlite` |
| `POLL_INTERVAL_MS` | Intervalo limpieza | Por defecto `300000` ms |
| `SERVICE_AUTH_HEADER` | Cabecera HTTP | Por defecto `Authorization` |
| `SERVICE_TOKEN_PREFIX` | Prefijo | Por defecto `Bearer ` |

Ejemplo **solo para prueba, sin credenciales reales** desde PowerShell:

```powershell
$env:BACKEND_URL='http://127.0.0.1:18080'
$env:SERVICE_TOKEN='TOKEN_FICTICIO_SOLO_PRUEBAS'
$env:ALLOWED_GROUP_IDS='grupo-ejemplo@g.us'
$env:SQLITE_PATH="$PWD\data\pendientes.sqlite"
npm.cmd start
```

El servicio base **no se conecta a grupos** y **no ejecuta GET/POST automáticamente**, dado que lectura, procesamiento y análisis corresponden a tareas posteriores. Para detenerlo, pulsa `Ctrl+C`. La URL puede apuntar a un backend todavía no disponible: al arrancar no hace llamadas HTTP.

En `MensajePendiente` se almacenan ID, grupo, fecha/hora, texto y estado; también marcas técnicas para vencimiento. No se guardan nombre, número ni otro identificador del remitente. Al marcar procesado/descartado se pone `texto=NULL`. Los pendientes con más de 30 minutos se descartan y se borra su texto al arrancar. Los logs se construyen con una lista blanca de eventos y recuentos, sin mensajes ni credenciales.

## Alcance y validación pendiente

- El caso **CP-26** y los demás criterios funcionales se verifican en las pruebas.
- Se validó previamente Windows con la versión R2, pero esta **versión depurada 1.0.2 requiere repetición en Windows**; Linux ARM también está pendiente de ejecución física.
- El aviso `ExperimentalWarning` es generado por `node:sqlite` en algunas versiones de Node.js y no significa fallo de test.
- El contrato definitivo de autenticación/respuesta Java debe acordarse antes de integrar con el backend real.
- Esta entrega no crea ramas automáticamente. Para subirla, crear `feature/T91-whatsapp-estructura-servicio` **desde `develop`** e incorporar únicamente esta carpeta del ZIP, sin sobrescribir otras partes del repositorio.
