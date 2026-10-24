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
| D-13 | El servicio de WhatsApp/LLM se identifica ante el backend con una credencial de servicio propia: un valor fijo y aleatorio, guardado como variable de entorno en el backend y en el servicio y enviado como `Authorization: Bearer`. El detalle está en [05_contratos.md](05_contratos.md) | El backend solo atiende peticiones autenticadas (RNF-08) | Confirmada el 23/10/2026 |
| D-14 | El backend avisa al motor core cada vez que un evento nace, cambia su vencimiento, se retira o vence | El motor calcula siempre con el tráfico al día | Propuesta |
| D-15 | El motor core tiene una cara en Java: el wrapper `MotorDeRutas`, dentro del backend. El wrapper arranca el núcleo C++ como proceso hijo, le habla por pipe con un mensaje JSON por línea y, cada vez que lo arranca, le envía los eventos vigentes | Recomendación del docente: el resto del backend solo ve Java, el núcleo no necesita servidor web ni abre puertos, y el backend controla su arranque y su reinicio. Se descartaron HTTP local, que suma un servidor web al C++, y JNI, porque un fallo del C++ tumbaría el backend | Confirmada el 12/10/2026 |
| D-16 | Todo el sistema corre en la instancia `churoviaje-server` de Oracle Cloud: Ubuntu sobre procesador ARM (Ampere A1), 2 OCPU y 12 GB de memoria, dentro del nivel Always Free, con IP pública reservada | Ya existe, tiene más memoria que un VPS básico y no tiene costo mensual | Confirmada el 12/10/2026 |
| D-17 | El análisis de mensajes usa la capa gratuita de la API de Gemini. Sus términos permiten a Google usar lo enviado para mejorar sus productos y que lo lean revisores humanos; por eso solo viajan el texto y la fecha, con los números de teléfono enmascarados, y la política de privacidad lo informa | Sin costo para un proyecto académico. Reemplaza el criterio anterior de elegir un proveedor que no entrene con los datos enviados por API | Confirmada el 12/10/2026 |
| D-18 | Un evento que reporta un conductor afecta a los dos sentidos de la calle donde cae; la congestión por telemetría, solo al sentido donde se midió | Un accidente o un bloqueo frena o cierra la calle entera, no un solo sentido | Confirmada el 12/10/2026 |
| D-19 | Si el conductor no responde al aviso de ruta alternativa, el aviso se cierra solo y sigue la ruta original | No distraer al conductor mientras maneja (CU-04 sección 7) | Confirmada el 13/10/2026 |
| D-20 | La congestión registrada por telemetría no se vota; solo se votan los eventos que reportan los conductores | Si 3 votos «falso» la retiraran, la siguiente lectura lenta la volvería a crear y aparecería y desaparecería del mapa | Confirmada el 13/10/2026 |
| D-21 | Sin conexión, la app entra al mapa mientras el token de renovación siga vigente (30 días), aunque el JWT haya vencido; al volver la red, renueva el JWT | El JWT dura 1 hora y sin red no se puede renovar | Confirmada el 13/10/2026 |
| D-22 | La vista Navegación activa muestra la calle que sigue y el sentido del giro (izquierda o derecha); la app lo calcula con el trazado y los nombres de calle que trae la ruta | El contrato de la ruta no trae instrucciones de giro y no hace falta agregarlas al motor | Confirmada el 13/10/2026 |
| D-23 | Con la vista Surtidores o el Mapa principal abiertos, la app vuelve a consultar los surtidores cada cierto tiempo, para la lista y para los marcadores | Para que un dato nuevo de WhatsApp se vea en la pantalla en menos de 6 minutos (RNF-04) | Confirmada el 13/10/2026 |
| D-24 | El buscador de lugares queda fuera de la app hasta que se decida P-19; el destino se marca en el mapa o se elige un surtidor | No hay servicio de búsqueda elegido | Confirmada el 13/10/2026 |
| D-25 | Los surtidores también se muestran como marcadores en el mapa, además de la lista | CU-05 paso 4 habla de «listado/mapa» | Confirmada el 13/10/2026 |
| D-26 | El proyecto tiene un dominio propio (unos 15 dólares al año) que apunta a la IP reservada del servidor, con certificado de Let's Encrypt | HTTPS necesita un dominio (Visión 4.4 y 6.3) | Confirmada el 13/10/2026 |
| D-27 | La versión de Node.js del servidor y de las pruebas del servicio de WhatsApp/LLM queda fija en la 24 hasta el 30/11/2026; después se decide si se pasa a una más nueva | Todos prueban con la misma versión, y la 24 tiene soporte de seguridad hasta abril de 2028, más allá del fin del proyecto | Confirmada el 23/10/2026 |
| D-28 | ANH Abastecimiento es una fuente complementaria de combustible. Su dato entra como un `EstadoCombustible` con origen ANH, y el monitoreo de WhatsApp y las confirmaciones de los conductores se mantienen | ANH da el saldo en litros y el estado de cada estación, pero no publica condiciones de uso, cuotas ni disponibilidad garantizada; con tres fuentes, el sistema sigue funcionando si una falla | Propuesta |
| D-29 | La consulta a ANH la hace el backend Java con una tarea periódica; la app nunca llama a ANH. El cliente en Python de `05_pro/PruebaApiANH` queda como prototipo de referencia | Una sola consulta centralizada evita que cada app llame a ANH, y el prototipo ya probó las rutas, la validación y los reintentos | Propuesta |
| D-30 | Cada estación de ANH se vincula a un surtidor del catálogo por su identificador. Las estaciones que no están en el catálogo o que caen fuera de la mancha urbana se ignoran | ANH devuelve todo el departamento: en la prueba llegaron 31 estaciones, también de Bermejo y Yacuiba | Propuesta |
| D-31 | Un saldo de 0 litros es agotado, uno mayor que 0 es disponible y sin saldo es sin dato. Se guardan los litros y el estado de ANH (alto, medio o bajo) tal como llegan, y el estado no se convierte en agotado | El estado «bajo» no tiene un umbral definido: en la prueba, 37 litros y 0 litros salieron los dos como «bajo». Convertirlo en agotado perdería información | Propuesta |
| D-32 | Si ANH falla, el surtidor conserva el último dato de ANH con su fecha; un fallo nunca se convierte en cero litros, y el dato vence por vigencia | Que ANH no responda no significa que no haya combustible | Propuesta |

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
| P-23 | Distancia máxima para ajustar un punto o un evento a la red vial | [RN-03.4](04_reglas/CU-03_CalcularRutaOptima.md), [RN-03.9](04_reglas/CU-03_CalcularRutaOptima.md) | 100 metros | Propuesta |
| P-24 | Distancia máxima entre una lectura de telemetría y su tramo | [RN-04.8](04_reglas/CU-04_RecalcularRuta.md) | 30 metros | Propuesta |
| P-25 | Tiempo que el motor recuerda un recorrido sin lecturas nuevas | [RN-04.12](04_reglas/CU-04_RecalcularRuta.md) | 5 minutos | Propuesta |
| P-26 | Tiempo que el aviso de ruta alternativa espera una respuesta | [RN-04.21](04_reglas/CU-04_RecalcularRuta.md) | 15 segundos | Confirmado |
| P-27 | Cada cuánto se actualizan la lista y los marcadores de surtidores con su vista abierta | [RN-05.17](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md) | 60 segundos | Confirmado |
| P-28 | Cuánto se conservan, sin texto, las filas terminadas del servicio de WhatsApp/LLM | [RN-06.12](04_reglas/CU-06_MonitorearSurtidoresWhatsApp.md), [06_base_de_datos.md](06_base_de_datos.md) | 24 horas, para no repetir mensajes recibidos al reconectarse; después se borran en el siguiente mantenimiento | Confirmado el 23/10/2026 |
| P-29 | Cada cuánto el backend consulta a ANH | [RN-09.1](04_reglas/CU-09_ConsultarEstadoANH.md) | Se fija con la tarea T-117 | Pendiente |
| P-30 | Vigencia del dato de ANH | [RN-05.5](04_reglas/CU-05_ConsultarDisponibilidadCombustible.md), [RN-09.17](04_reglas/CU-09_ConsultarEstadoANH.md) | Hoy los datos valen 6 horas (RN-05.5); se fija con la tarea T-117 | Pendiente |
| P-31 | Distancia máxima para vincular una estación de ANH con un surtidor del catálogo | [RN-09.10](04_reglas/CU-09_ConsultarEstadoANH.md) | Se fija con la tarea T-118 | Pendiente |
| P-32 | Tiempo de espera y reintentos de la consulta a ANH | [RN-09.4](04_reglas/CU-09_ConsultarEstadoANH.md) | Partir de los del cliente de prueba: 12 segundos por petición, 1 reintento y 1 segundo entre peticiones | Propuesta |

P-07, P-08 y P-10 se cerraron con las decisiones D-09, D-08 y D-12. P-16 quedó sin uso.
