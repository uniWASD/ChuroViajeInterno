# Estimación del backlog de Construcción

[Índice del backlog](README.md)

Tamaño y responsable de cada issue. Las tallas son una propuesta inicial: el equipo las revisa en conjunto (cada uno muestra su talla y se conversan las diferencias) y las corrige antes del sprint 1.

Este archivo es donde se cambian la talla y el responsable de un issue. Después del cambio se corre [sincronizar_issues.py](sincronizar_issues.py), que asigna cada issue en GitHub a su responsable, carga los campos «Talla» y «Puntos» del tablero y recalcula la tabla de carga de más abajo.

Si alguien ve que no llega con lo que tiene asignado en un sprint, lo avisa y el issue pasa a otra persona.

## Escala

| Talla | Trabajo de una persona | Puntos para sumar carga |
| --- | --- | --- |
| XS | Hasta 2 horas | 1 |
| S | De 2 a 5 horas | 2 |
| M | De 5 a 10 horas | 3 |
| L | De 10 a 20 horas | 5 |
| XL | Más de 20 horas: no entra cómodo en un sprint y conviene dividirlo | 8 |

Las horas incluyen las pruebas, la revisión del Pull Request y lo que haga falta aprender. Los puntos solo sirven para sumar la carga de cada persona; la talla es lo que se acuerda.

## Usuarios de GitHub

| Persona | Usuario de GitHub |
| --- | --- |
| Alex | NuzEstrada |
| Antonio | antoniocuellarca19-byte |
| Franz | CarlosCaceresAuca |
| Jorge | jorgecabreraz |
| Marcelo | MarcelQuiroF |

Cuando el responsable tiene usuario, el issue se le asigna y la línea «Responsable sugerido» sale del cuerpo; si había alguien de apoyo, queda como «Apoyo». Antonio ya tiene usuario (está en la tabla), así que sus issues se le asignan al correr el script. «Pruebas de punta a punta», que comparten Jorge y Antonio, se asigna a los dos.

## Estimación por issue

| Orden | Issue | Título | Sprint | Responsable | Talla | Por qué |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | #39 | [T-087][Flutter] Estructura base de la app | 1 | Alex | M | Proyecto, gestión de estado, cliente HTTP con errores, ambientes y navegación |
| 2 | #40 | [T-088][Java] Estructura base del backend | 1 | Jorge | M | Spring Security base, manejador de errores, perfiles, OpenAPI, CI y Dependabot |
| 3 | #41 | [T-089][Java] Base de datos, catálogos y zona de cobertura | 1 | Antonio | M | Flyway con PostGIS, lista de surtidores con alias y polígono dibujado a mano |
| 4 | #42 | [T-090][C++] Estructura del núcleo y protocolo por pipe | 1 | Marcelo | M | Bucle simple, pero CMake para Linux ARM y Windows y el marco de pruebas llevan tiempo |
| 5 | #43 | [T-091][WhatsApp/LLM] Estructura del servicio | 1 | Franz | S | Proyecto Node, SQLite, configuración y cliente del backend |
| 6 | #44 | [T-092][Servidor] Preparar la instancia de Oracle Cloud | 1 | Marcelo | S | La instancia ya existe; falta endurecerla e instalar las herramientas |
| 7 | #45 | [T-093][Flutter] Mapa base con Mapbox y ubicación | 2 | Alex | M | El SDK de Mapbox en Flutter, los permisos y el ciclo de vida suelen dar guerra |
| 8 | #46 | [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT | 2 | Antonio | L | Validar Google, JWT, renovación y cierre, filtro de seguridad y credencial de servicio |
| 9 | #47 | [T-094][Java] Catálogos para la app | 2 | Antonio | S | Un endpoint de lectura con su prueba |
| 10 | #48 | [HU-10][CU-08][Flutter] Iniciar sesión con Google | 2 | Alex | L | Configurar Google en Android e iOS, entrada sin conexión, renovación automática, 401 y cierre |
| 11 | #49 | [T-095][Java] Wrapper MotorDeRutas | 2 | Jorge | L | Procesos, fila, tiempo máximo y reinicio: concurrencia difícil de probar |
| 12 | #50 | [T-096][C++] Cargar la red vial en el núcleo | 2 | Marcelo | M | Carga del archivo en C++, índice espacial y ajuste de puntos a la red |
| 13 | #51 | [HU-08][CU-06][WhatsApp/LLM] Leer los grupos autorizados | 2 | Franz | M | Baileys, filtros y reconexión; además, conseguir la autorización de los administradores |
| 14 | #52 | [T-097][Servidor] Dominio, Nginx y HTTPS | 2 | Marcelo | S | Dominio, Nginx y certbot |
| 56 | #?(orden 56) | [T-119][Servidor] Comprobar la salida del servidor hacia la API de ANH | 2 | Marcelo | XS | Pruebas con curl y con Java desde la instancia y una nota en la guía |
| 54 | #93 | [T-111][C++] Preparar la red vial de Tarija desde OpenStreetMap | 2 | Franz | M | Recortar y filtrar el extracto, sentidos, tipo de vía y formato acordado con Marcelo |
| 15 | #53 | [HU-01][CU-01][Java] Registrar reportes de eventos viales | 3 | Jorge | L | EventoVial con muchas reglas, PostGIS, duplicados, límite y transacción |
| 16 | #54 | [HU-01][CU-01][Flutter] Reportar un evento vial | 3 | Alex | M | Vista, captura de ubicación con ajuste manual y rechazos |
| 17 | #55 | [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos | 3 | Antonio | L | La consulta por cambios con marca y bajas es lo delicado; más la tarea periódica y la prueba con 500 eventos |
| 18 | #56 | [HU-03][CU-02][Flutter] Ver los eventos en el mapa | 3 | Alex | M | Ciclo de 5 segundos, dibujo por color y manejo de errores |
| 19 | #57 | [HU-08][CU-06][Java] Recibir el análisis de WhatsApp | 3 | Antonio | M | Validación por aviso, regla del dato más reciente y respuesta con aceptados y descartados |
| 20 | #58 | [HU-05][CU-03][C++] Calcular la ruta óptima con A* | 3 | Marcelo | M | El algoritmo ya existe en Python; se porta con paradas y tiempo estimado |
| 21 | #59 | [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado | 3 | Franz | L | Salida estructurada de Gemini, enmascarado, ciclos, reintentos y reenvío |
| 22 | #60 | [T-098][Servidor] Desplegar el backend con el núcleo C++ | 3 | Marcelo | M | Servicio del sistema, compilación ARM, secretos y script de despliegue |
| 57 | #?(orden 57) | [T-117][Java] Medir la API de ANH y fijar el ciclo de consulta y la vigencia | 3 | Franz | S | Script de medición, tres días de registro y análisis; Franz ya conoce la API por T-115 |
| 58 | #?(orden 58) | [T-118][Java] Vincular las estaciones de ANH con los surtidores del catálogo | 3 | Jorge | S | Dos migraciones, un script que propone los pares por nombre y distancia y la revisión a mano |
| 23 | #61 | [HU-02][CU-01][Flutter] Guardar y reintentar reportes sin conexión | 4 | Franz | M | Cola con sqflite, detección de red, vigencia y reintentos |
| 24 | #62 | [HU-04][CU-02][Flutter] Ver eventos guardados sin conexión | 4 | Franz | S | Caché local y marca de desactualizado |
| 25 | #63 | [HU-11][CU-02][Java] Votar eventos | 4 | Antonio | M | Voto, recuento, cambios de estado y concurrencia |
| 26 | #64 | [HU-11][CU-02][Flutter] Lista de eventos y votos | 4 | Alex | S | Vista de lista sobre datos que ya existen y envío del voto |
| 27 | #65 | [HU-05][CU-03][Java] Calcular la ruta | 4 | Jorge | S | Capa delgada sobre el wrapper y traducción de respuestas |
| 28 | #66 | [HU-05][CU-03][C++] Tráfico de los eventos en los tramos | 4 | Marcelo | M | Asignar evento a calle en los dos sentidos, niveles, cierre y vencimiento |
| 29 | #67 | [T-099][Java] Mantener al día el tráfico del motor | 4 | Jorge | M | Eventos internos, reintento sin pérdida y envío al arrancar |
| 30 | #68 | [T-100][Servidor] Desplegar el servicio de WhatsApp/LLM | 4 | Marcelo | S | Servicio, secretos y vincular el número |
| 59 | #?(orden 59) | [HU-14][CU-09][Java] Consultar el estado de combustible desde ANH | 4 | Antonio | L | Cliente con reintentos y `Retry-After`, validación de la respuesta, filtros, traducción de litros y fallos parciales |
| 31 | #69 | [HU-07][CU-05][Java] Consultar surtidores cercanos | 5 | Antonio | M | Búsqueda por cercanía en PostGIS, dato más reciente y vigencia |
| 32 | #70 | [HU-05][CU-03][Flutter] Cómo llegar | 5 | Alex | L | Vista con paradas, destino por mapa o por surtidor y dibujo de la ruta |
| 33 | #71 | [HU-07][CU-05][Flutter] Consultar surtidores cercanos | 5 | Franz | M | Lista, marcadores en el mapa, refresco cada 60 s y presentación del origen |
| 34 | #72 | [HU-12][CU-05][Java] Registrar confirmaciones de recarga | 5 | Antonio | M | Cercanía, repetición y regla frente al dato de WhatsApp |
| 35 | #73 | [HU-13][CU-04][C++] Detectar congestión con la telemetría | 5 | Marcelo | L | Portar el cálculo, asignar lecturas al tramo y sentido, estado por recorrido y simulados |
| 36 | #74 | [HU-13][CU-04][Java] Recibir telemetría y registrar congestión | 5 | Jorge | M | HMAC diario, respuesta asíncrona y alta o extensión de la congestión |
| 37 | #75 | [HU-13][CU-04][Flutter] Enviar telemetría con la app abierta | 5 | Franz | M | Lecturas atadas al ciclo de vida, envío agrupado y medición de batería |
| 38 | #76 | [HU-06][CU-04][C++] Revisar la ruta activa | 5 | Marcelo | M | Reutiliza el cálculo de ruta; compara costos y arma las tres respuestas |
| 39 | #77 | [T-101][WhatsApp/LLM] Prueba con mensajes reales | 5 | Franz | M | Anotar a mano 100 mensajes, script de comparación y ajuste del prompt |
| 40 | #78 | [T-102][Simulador] Estructura y parámetros del simulador | 5 | Antonio | S | Parámetros, validación y elegir entre Locust o propio |
| 55 | #94 | [HU-05][CU-03][Flutter] Navegación activa | 5 | Alex | M | Calle que sigue y sentido del giro calculados con el trazado, llegada y cancelación |
| 41 | #79 | [T-103][Servidor] Respaldos de la base de datos y registros | 6 | Marcelo | M | Respaldo al almacenamiento de objetos, restauración probada y rotación de registros |
| 42 | #80 | [HU-12][CU-05][Flutter] Preguntar por la recarga | 6 | Alex | M | Detectar la parada junto a un surtidor sin molestar y la lógica de silencio |
| 43 | #81 | [T-104][Flutter] Política de privacidad | 6 | Franz | S | Redacción, revisión del equipo y publicación |
| 44 | #82 | [HU-06][CU-04][Java] Revisar la ruta activa | 6 | Jorge | S | Capa delgada sobre el motor |
| 45 | #83 | [HU-06][CU-04][Flutter] Aviso de ruta alternativa | 6 | Alex | M | Aviso no intrusivo, cierre a los 15 s, eventos avisados y casos borde |
| 46 | #84 | [HU-09][CU-07][Java] Soporte para el simulador | 6 | Jorge | L | Sesión de prueba, marca de simulado en todo, modo demostración, borrado y registros |
| 47 | #85 | [T-105][Java] Limpieza de datos vencidos | 6 | Antonio | XS | Una tarea programada con su prueba |
| 48 | #86 | [T-106][Servidor] Ambiente de pruebas para el simulador | 6 | Marcelo | S | Segunda base, segundo servicio y subdominio |
| 49 | #87 | [HU-09][CU-07][Simulador] Usuarios fantasma que envían eventos y telemetría | 6 | Antonio | L | Comportamiento de los usuarios fantasma, rutas, concurrencia y resumen |
| 50 | #88 | [T-107][Java] Pruebas de punta a punta | 7 | Jorge y Antonio | L | Nueve flujos contra el sistema real y corregir lo que falle |
| 51 | #89 | [T-108][Simulador] Pruebas de carga y reporte | 7 | Antonio | M | Tres escenarios, medición y reporte |
| 52 | #90 | [T-109][C++] Rendimiento con la red real de Tarija | 7 | Marcelo | S | Banco de medición y revisión de parámetros |
| 53 | #91 | [T-110][Servidor] Despliegue final y revisión de seguridad | 7 | Marcelo | M | Revisión punto por punto, escaneo de puertos y búsqueda de secretos |

En total: 59 issues, 2 XS, 15 S, 30 M y 12 L. No queda ningún XL.

## Carga por persona y sprint (en puntos)

| Persona | S1 | S2 | S3 | S4 | S5 | S6 | S7 | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Alex | 3 | 8 | 6 | 2 | 8 | 6 | 0 | 33 |
| Jorge | 3 | 5 | 7 | 5 | 3 | 7 | 2,5 | 32,5 |
| Antonio | 3 | 7 | 8 | 8 | 8 | 6 | 5,5 | 45,5 |
| Marcelo | 5 | 6 | 6 | 5 | 8 | 5 | 5 | 40 |
| Franz | 2 | 6 | 7 | 5 | 9 | 2 | 0 | 31 |
| **Sprint** | 16 | 32 | 34 | 25 | 36 | 26 | 13 | 182 |

«Pruebas de punta a punta» se reparte por mitades entre Jorge y Antonio. La carga cuenta solo al responsable; no cuenta las revisiones de Pull Requests, que recaen sobre todo en Marcelo. Como referencia, un punto equivale a unas 2 o 3 horas.

## Cambios acordados el 13/10/2026

La primera estimación mostró picos de carga. Para repartirla se acordó:

- #71 (surtidores en Flutter) y #75 (telemetría en Flutter) pasan de Alex a Franz.
- #50 (red vial) se divide: Franz prepara el extracto de OpenStreetMap con un script en Python (#93) y Marcelo carga la red en el núcleo (#50).
- #70 se divide en «Cómo llegar» (#70) y «Navegación activa» (#94).
- #46 (inicio de sesión en Java) pasa de Jorge a Antonio.
- #79 (respaldos y registros) pasa del sprint 5 al sprint 6.
- #84 (soporte para el simulador en Java) pasa de Antonio a Jorge.

## Cambios del 23/10/2026

Al incorporar la API de ANH (T-116) se agregaron cuatro issues. Franz mide la API (T-117) porque ya la investigó en T-115; Jorge vincula las estaciones con el catálogo (T-118); Marcelo comprueba la salida del servidor (T-119), y Antonio hace la consulta periódica (HU-14) en el sprint 4, que era su sprint con menos carga (3 puntos). Con los cuatro issues la carga total pasa de 172 a 182 puntos.
