# Decisiones y parámetros

[Índice de la arquitectura](README.md)

Este archivo reúne las decisiones de arquitectura y los valores de los parámetros que usan las reglas. Lo marcado como propuesta vale mientras el equipo no decida otra cosa.

## Decisiones

| Código | Decisión | Motivo | Estado |
| --- | --- | --- | --- |
| D-01 | Un solo MVC para todo el sistema: vista en Flutter, controladores en Flutter y en el backend, modelo en el backend y el motor core | Las reglas quedan en un solo lugar | Confirmada el 06/10/2026 |
| D-02 | Backend con Spring Boot y base de datos PostgreSQL con PostGIS | Propuesta del PR #30; PostGIS resuelve las búsquedas por cercanía | Confirmada el 06/10/2026 |
| D-03 | El backend es la única puerta de entrada; el motor core y la base de datos no se exponen a internet | Menos superficie que proteger en el VPS (Visión 6.3) | Propuesta |
| D-04 | La congestión solo la registra el sistema a partir de la telemetría, como un `EventoVial` de tipo Congestión con origen Telemetría. El conductor no la reporta | Un solo concepto con las mismas reglas de vigencia, mapa y rutas | Confirmada el 06/10/2026 |
| D-05 | Las rutas no se guardan en la base de datos | La Visión pide no conservar historial de rutas más de lo necesario | Propuesta |
| D-06 | La app toma los tiempos de vigencia del catálogo que le entrega el backend | Evita que la app y el backend usen valores distintos | Propuesta |
| D-07 | El análisis de telemetría corre en el motor core; el backend solo recibe las lecturas y las reenvía | Así lo define la Visión 4.1 | Según la Visión |
| D-08 | El motor core carga la red vial de un extracto de OpenStreetMap, filtrado a vías vehiculares, y la mantiene en memoria. Mapbox queda para el mapa de la app | El cálculo no depende de un servicio externo en cada ruta | Confirmada el 06/10/2026 |
| D-09 | En el reporte, «Hace 30 minutos» reemplaza a «Hace 1 hora» | Con vigencias de 60 minutos, un evento de hace una hora nacía vencido | Confirmada el 06/10/2026 |
| D-10 | Un conductor puede reportar cuatro tipos de evento: accidente, control policial, bloqueo y otro | Son los tipos del diseño de la app | Confirmada el 06/10/2026 |
| D-11 | La app pregunta por la recarga cuando el conductor se detiene junto a un surtidor con la app abierta | No le exige ningún paso extra al conductor | Confirmada el 06/10/2026 |
| D-12 | Mientras navega, la app envía al backend lo que le falta de la ruta para que el modelo revise si hay congestión | Permite recalcular sin guardar rutas (D-05) | Propuesta |
| D-13 | El servicio de WhatsApp/LLM se identifica ante el backend con una credencial de servicio propia | El backend solo atiende peticiones autenticadas (RNF-08) | Propuesta |
| D-14 | El backend avisa al motor core cada vez que un evento nace, se retira o vence | El motor calcula siempre con el tráfico al día | Propuesta |

## Parámetros

| Código | Qué se define | Dónde se usa | Valor | Estado |
| --- | --- | --- | --- | --- |
| P-01 | Votos «falso» que retiran un evento | [RN-02.13](04_reglas/CU-02_VisualizarEventosViales.md) | 3 votos | Confirmado |
| P-02 | Tiempo y velocidad que cuentan como congestión | [RN-04.9](04_reglas/CU-04_RecalcularRuta.md), [RN-04.10](04_reglas/CU-04_RecalcularRuta.md) | 2 minutos seguidos por debajo de la mitad de la velocidad esperada de la vía; muy congestionado por debajo de la cuarta parte | Confirmado |
| P-03 | Distancia y tiempo que definen un reporte duplicado | [RN-01.8](04_reglas/CU-01_ReportarEventoVial.md) | Mismo tipo, a menos de 100 metros y dentro de 10 minutos | Confirmado |
| P-04 | Límite de reportes por conductor | [RN-01.9](04_reglas/CU-01_ReportarEventoVial.md) | 5 reportes cada 10 minutos | Confirmado |
| P-05 | Duración de la sesión | [RN-08.12](04_reglas/CU-08_IniciarSesion.md) | JWT de 1 hora, renovable durante 30 días | Confirmado |
| P-06 | Colores de bloqueo, otro y congestión | [RN-02.7](04_reglas/CU-02_VisualizarEventosViales.md) | Los fija el diseño de la app | Pendiente |
| P-09 | Cercanía y tiempo detenido para preguntar por la recarga | [RN-05.8](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md), [RN-05.13](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) | A menos de 50 metros del surtidor y detenido 3 minutos | Propuesta |
| P-11 | Votos «real» que confirman un evento | [RN-02.14](04_reglas/CU-02_VisualizarEventosViales.md) | 3 votos. Un evento confirmado ya no se retira por votos | Confirmado |
| P-12 | Cuánto encarece un tramo cada tipo de evento | [RN-03.9](04_reglas/CU-03_CalcularRutaOptima.md) | Accidente, por 4,0. Control policial y otro, por 1,5. Bloqueo, cierra el tramo. Congestión, por 2,5 o por 4,0 según su nivel | Confirmado |
| P-13 | Frecuencia de la telemetría | [RN-04.1](04_reglas/CU-04_RecalcularRuta.md) | Lectura del GPS cada 3 segundos, como en `05_pro/CalculoVelocidad`, enviada en grupos cada 15 segundos | Propuesta |
| P-14 | Tiempo mínimo entre confirmaciones del mismo surtidor y tipo | [RN-05.14](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) | 60 minutos | Propuesta |
| P-15 | Valores de la fila aproximada | [RN-05.4](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md), [RN-06.7](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md) | Sin fila, corta, media y larga, como en la investigación del LLM | Propuesta |
| P-17 | Voto del conductor sobre su propio evento | [RN-02.12](04_reglas/CU-02_VisualizarEventosViales.md) | No puede votarlo | Propuesta |
| P-18 | Límites del simulador | [RN-07.1](04_reglas/CU-07_EjecutarSimuladorTrafico.md) | Se fijan con las primeras pruebas de carga | Pendiente |
| P-19 | Búsqueda de destino por nombre | CU-03 | El buscador necesita un servicio de búsqueda de lugares. Mientras no se elija, el destino se marca en el mapa o se toma del catálogo de surtidores | Pendiente |
| P-20 | Credenciales de prueba y modo demostración | [RN-07.5](04_reglas/CU-07_EjecutarSimuladorTrafico.md), [RN-07.13](04_reglas/CU-07_EjecutarSimuladorTrafico.md) | Se habilitan por configuración del ambiente | Propuesta |
| P-21 | Cuánto se conservan los eventos vencidos o retirados, sus votos y las confirmaciones de recarga | [06_base_de_datos.md](06_base_de_datos.md) | 30 días, y después se borran | Propuesta |
| P-22 | Dónde guarda el servicio de WhatsApp/LLM los mensajes pendientes | [06_base_de_datos.md](06_base_de_datos.md) | Un archivo SQLite propio del servicio | Propuesta |

P-07, P-08 y P-10 se cerraron con las decisiones D-09, D-08 y D-12.
