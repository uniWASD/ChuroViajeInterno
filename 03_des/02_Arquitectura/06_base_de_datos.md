# Datos y persistencia

[Índice de la arquitectura](README.md)

El sistema guarda solo lo que necesita para cumplir sus reglas. Las rutas y la telemetría no se guardan, y el texto de los mensajes de WhatsApp vive como máximo 30 minutos. De ANH solo se guardan el saldo y el estado de las estaciones que están en el catálogo.

## Qué se guarda, dónde y por cuánto tiempo

| Dato | Dónde vive | Cuánto dura | Fuente |
| --- | --- | --- | --- |
| Conductores | PostgreSQL | Mientras exista la cuenta | CU-08 |
| Tokens de renovación de sesión | PostgreSQL | 30 días | [P-05](08_decisiones_parametros.md) |
| Catálogos: tipos de evento, tipos de combustible, surtidores y sus alias | PostgreSQL | Permanente | CU-02, CU-05, CU-06 |
| Eventos viales y sus votos | PostgreSQL, con la ubicación en PostGIS | Mientras están vigentes, y 30 días más | [P-21](08_decisiones_parametros.md) |
| Estado de combustible | PostgreSQL | El último dato por surtidor, tipo y origen | CU-05 |
| Datos de ANH | PostgreSQL, en el estado de combustible: litros, estado de ANH, fecha del dato y momento de la consulta. Solo de las estaciones vinculadas a un surtidor | El último dato por surtidor y tipo, que se reemplaza en cada consulta; vence según [P-30](08_decisiones_parametros.md) | [RN-09.13](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.17](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.18](04_reglas/CU-09_ConsultarEstadoANH.md) |
| Confirmaciones de recarga | PostgreSQL | 30 días | [RN-05.14](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md), [P-21](08_decisiones_parametros.md) |
| Lecturas de telemetría | Memoria del motor core, solo mientras se analizan | Minutos | [RN-04.12](04_reglas/CU-04_RecalcularRuta.md) |
| Rutas | La app, mientras dura la navegación | Hasta llegar o cancelar | [D-05](08_decisiones_parametros.md) |
| Red vial | Archivo del extracto de OpenStreetMap en el VPS, cargado en la memoria del motor core | Hasta que se actualice el extracto | [D-08](08_decisiones_parametros.md) |
| Mensajes pendientes de WhatsApp | Almacenamiento propio del servicio de WhatsApp/LLM | Hasta analizarse, o 30 minutos | [RN-06.11](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [RN-06.12](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [P-22](08_decisiones_parametros.md) |
| Sesión del número de WhatsApp | Archivo del servicio de WhatsApp/LLM, con acceso restringido | Mientras el número siga vinculado | CU-06 sección 7 |
| JWT, token de renovación, cola de reportes, últimos eventos y catálogos | El teléfono del conductor | Hasta cerrar sesión o hasta que se reemplazan | HU-02, HU-04, [RN-08.7](04_reglas/CU-08_IniciarSesion.md) |
| Registros de las pruebas de carga | Archivos de registro del backend | Hasta revisarlos | [RN-07.11](04_reglas/CU-07_EjecutarSimuladorTrafico.md) |
| Registros de las consultas a ANH | Archivos de registro del backend: hora, rutas consultadas, estado HTTP, cuántas estaciones llegaron, se aceptaron e ignoraron y el motivo de cada falla; sin el cuerpo de la respuesta | 14 días, como los demás registros del servidor ([D-16](08_decisiones_parametros.md)) | [RN-09.19](04_reglas/CU-09_ConsultarEstadoANH.md) |
| Registros del servidor (Nginx, backend, núcleo y servicio de WhatsApp/LLM) | Archivos del servidor, sin ubicaciones, sin la consulta de las URL y sin textos de mensajes | 14 días, salvo los de pruebas de carga | [D-16](08_decisiones_parametros.md) |
| Respaldos de la base de datos | Almacenamiento de objetos de Oracle Cloud, fuera de la instancia | 7 días. Lo que borra [P-21](08_decisiones_parametros.md) desaparece de los respaldos 7 días después | [D-16](08_decisiones_parametros.md) |

## Tablas principales

| Tabla | Columnas principales | Objeto de dominio |
| --- | --- | --- |
| `conductor` | Identificador, identificador de Google, fecha de alta, simulado | Conductor |
| `sesion` | Conductor, token de renovación, emisión, expiración | Sesión |
| `tipo_evento` | Código, nombre, color, vigencia en minutos, reportable | TipoEvento |
| `evento_vial` | Identificador, tipo, ubicación, momento en que ocurrió, momento de registro, reportante, origen, nivel (solo congestión), estado, votos «real», votos «falso», simulado | EventoVial, Ubicación |
| `voto` | Evento, conductor, valor, fecha, simulado | Voto |
| `surtidor` | Identificador, nombre, ubicación, `anh_id` (el identificador de la estación en ANH; único y opcional) | Surtidor |
| `surtidor_alias` | Surtidor, alias | Surtidor |
| `tipo_combustible` | Código, nombre | TipoCombustible |
| `estado_combustible` | Surtidor, tipo, origen (WhatsApp, Conductores o ANH), disponible, fila, fecha del dato, `litros` (no negativo y opcional), `estado_anh` (alto, medio o bajo; opcional) y `consultado_en` (cuándo lo leyó el backend; solo en ANH) | EstadoCombustible |
| `confirmacion_recarga` | Conductor, surtidor, tipo, fecha | ConfirmaciónRecarga |

En un evento de origen Telemetría, la columna del reportante queda vacía. `RedVial`, `Tramo`, `Ruta`, `Parada` y `LecturaTelemetría` no tienen tabla: viven en el motor core. `MensajePendiente` vive en el almacenamiento del servicio de WhatsApp/LLM. `ReporteANH` tampoco tiene tabla: es lo que llega de ANH en cada consulta, y de él solo se guarda lo que dice `estado_combustible`.

Las columnas `litros`, `estado_anh` y `consultado_en` solo se llenan cuando el origen es ANH. La fecha del dato de ANH es su `updated_at`, la que informa la estación, y no el momento en que el backend la leyó: ese momento va en `consultado_en`, y de él se cuenta la vigencia ([RN-09.17](04_reglas/CU-09_ConsultarEstadoANH.md)). El estado de ANH se guarda como llega y no cambia el campo `disponible`, que sale solo de los litros ([RN-09.11](04_reglas/CU-09_ConsultarEstadoANH.md), [RN-09.12](04_reglas/CU-09_ConsultarEstadoANH.md)).

## Lo que la base de datos asegura por su cuenta

Las reglas las aplican los objetos de dominio. La base de datos repite seis de ellas como segunda barrera, por si dos peticiones llegan a la vez:

- Un solo conductor por cuenta de Google ([RN-08.4](04_reglas/CU-08_IniciarSesion.md)).
- Un solo voto por conductor y evento ([RN-02.11](04_reglas/CU-02_VisualizarEventosViales.md)).
- Un solo estado por surtidor, tipo de combustible y origen ([RN-05.3](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md)).
- Todo evento, voto y estado apunta a un tipo, un surtidor o un conductor que existe.
- Un identificador de ANH pertenece a un solo surtidor ([RN-09.10](04_reglas/CU-09_ConsultarEstadoANH.md)).
- Los litros de ANH no pueden ser negativos ([RN-09.7](04_reglas/CU-09_ConsultarEstadoANH.md)).

## Datos simulados

La marca de simulado está en el conductor, en sus eventos y en sus votos. Borrar los datos de una prueba es borrar las filas con esa marca, sin tocar ninguna otra ([RN-07.8](04_reglas/CU-07_EjecutarSimuladorTrafico.md)).
