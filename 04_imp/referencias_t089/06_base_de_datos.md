# Datos y persistencia

[Índice de la arquitectura](README.md)

El sistema guarda solo lo que necesita para cumplir sus reglas. Las rutas y la telemetría no se guardan, y el texto de los mensajes de WhatsApp vive como máximo 30 minutos.

## Qué se guarda, dónde y por cuánto tiempo

| Dato | Dónde vive | Cuánto dura | Fuente |
| --- | --- | --- | --- |
| Conductores | PostgreSQL | Mientras exista la cuenta | CU-08 |
| Tokens de renovación de sesión | PostgreSQL | 30 días | [P-05](08_decisiones_parametros.md) |
| Catálogos: tipos de evento, tipos de combustible, surtidores y sus alias | PostgreSQL | Permanente | CU-02, CU-05, CU-06 |
| Eventos viales y sus votos | PostgreSQL, con la ubicación en PostGIS | Mientras están vigentes, y 30 días más | [P-21](08_decisiones_parametros.md) |
| Estado de combustible | PostgreSQL | El último dato por surtidor, tipo y origen | CU-05 |
| Confirmaciones de recarga | PostgreSQL | 30 días | [RN-05.14](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md), [P-21](08_decisiones_parametros.md) |
| Lecturas de telemetría | Memoria del motor core, solo mientras se analizan | Minutos | [RN-04.12](04_reglas/CU-04_RecalcularRuta.md) |
| Rutas | La app, mientras dura la navegación | Hasta llegar o cancelar | [D-05](08_decisiones_parametros.md) |
| Red vial | Archivo del extracto de OpenStreetMap en el VPS, cargado en la memoria del motor core | Hasta que se actualice el extracto | [D-08](08_decisiones_parametros.md) |
| Mensajes pendientes de WhatsApp | Almacenamiento propio del servicio de WhatsApp/LLM | Hasta analizarse, o 30 minutos | [RN-06.11](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [RN-06.12](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [P-22](08_decisiones_parametros.md) |
| Sesión del número de WhatsApp | Archivo del servicio de WhatsApp/LLM, con acceso restringido | Mientras el número siga vinculado | CU-06 sección 7 |
| JWT, cola de reportes, últimos eventos y catálogos | El teléfono del conductor | Hasta cerrar sesión o hasta que se reemplazan | HU-02, HU-04, [RN-08.7](04_reglas/CU-08_IniciarSesion.md) |
| Registros de las pruebas de carga | Archivos de registro del backend | Hasta revisarlos | [RN-07.11](04_reglas/CU-07_EjecutarSimuladorTrafico.md) |

## Tablas principales

| Tabla | Columnas principales | Objeto de dominio |
| --- | --- | --- |
| `conductor` | Identificador, identificador de Google, fecha de alta, simulado | Conductor |
| `sesion` | Conductor, token de renovación, emisión, expiración | Sesión |
| `tipo_evento` | Código, nombre, color, vigencia en minutos, reportable | TipoEvento |
| `evento_vial` | Identificador, tipo, ubicación, momento en que ocurrió, momento de registro, reportante, origen, estado, votos «real», votos «falso», simulado | EventoVial, Ubicación |
| `voto` | Evento, conductor, valor, fecha, simulado | Voto |
| `surtidor` | Identificador, nombre, ubicación | Surtidor |
| `surtidor_alias` | Surtidor, alias | Surtidor |
| `tipo_combustible` | Código, nombre | TipoCombustible |
| `estado_combustible` | Surtidor, tipo, origen, disponible, fila, fecha del dato | EstadoCombustible |
| `confirmacion_recarga` | Conductor, surtidor, tipo, fecha | ConfirmaciónRecarga |

En un evento de origen Telemetría, la columna del reportante queda vacía. `RedVial`, `Tramo`, `Ruta`, `Parada` y `LecturaTelemetría` no tienen tabla: viven en el motor core. `MensajePendiente` vive en el almacenamiento del servicio de WhatsApp/LLM.

## Lo que la base de datos asegura por su cuenta

Las reglas las aplican los objetos de dominio. La base de datos repite cuatro de ellas como segunda barrera, por si dos peticiones llegan a la vez:

- Un solo conductor por cuenta de Google ([RN-08.4](04_reglas/CU-08_IniciarSesion.md)).
- Un solo voto por conductor y evento ([RN-02.11](04_reglas/CU-02_VisualizarEventosViales.md)).
- Un solo estado por surtidor, tipo de combustible y origen ([RN-05.3](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)).
- Todo evento, voto y estado apunta a un tipo, un surtidor o un conductor que existe.

## Datos simulados

La marca de simulado está en el conductor, en sus eventos y en sus votos. Borrar los datos de una prueba es borrar las filas con esa marca, sin tocar ninguna otra ([RN-07.8](04_reglas/CU-07_EjecutarSimuladorTrafico.md)).
