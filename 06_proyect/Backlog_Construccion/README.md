# Backlog de Construcción de ChuroViaje

Este backlog reúne los issues de la fase de Construcción, separados por tecnología y por sprint. Sale de la arquitectura de [03_des/02_Arquitectura](../../03_des/02_Arquitectura/README.md): cada criterio de aceptación cita la regla (RN), la decisión (D) o el parámetro (P) que lo origina.

Reemplaza al backlog de la fase de Elaboración, que queda guardado en [Backlog_Elaboracion](../Backlog_Elaboracion/Backlog_Backend_Java.md) solo como referencia. Ese backlog proponía WebSocket y una comunicación por red con el motor, dos cosas que la arquitectura cambió: la app consulta cada 5 segundos y el backend habla con el núcleo C++ por pipe (D-15).

## Archivos

| Archivo | Tecnología | Responsables | Issues |
| --- | --- | --- | --- |
| [01_Flutter.md](01_Flutter.md) | App Flutter | Alex; Franz en los modos sin conexión y la política de privacidad | 14 |
| [02_Java.md](02_Java.md) | Backend Java | Jorge y Antonio | 18 |
| [03_Cpp.md](03_Cpp.md) | Núcleo C++ del motor core | Marcelo | 7 |
| [04_WhatsApp_LLM.md](04_WhatsApp_LLM.md) | Servicio de WhatsApp/LLM (Node.js) | Franz | 4 |
| [05_Simulador.md](05_Simulador.md) | Simulador (Python) | Antonio | 3 |
| [06_Servidor.md](06_Servidor.md) | Servidor y despliegue (Oracle Cloud) | Marcelo | 7 |
| | | **Total** | **53** |

Son 29 issues de historia de usuario y 24 tareas técnicas.

## Sprints

La Construcción va del 2 de octubre al 30 de noviembre de 2026. Del 2 al 14 de octubre se hicieron el documento de arquitectura y este backlog; el desarrollo va en siete sprints de una semana, de jueves a miércoles, y el último es corto.

| Sprint | Fechas | Objetivo |
| --- | --- | --- |
| Sprint 1 | 15/10 – 21/10 | Bases: proyectos, base de datos, servidor y protocolo del núcleo |
| Sprint 2 | 22/10 – 28/10 | Inicio de sesión, red vial de Tarija, wrapper del motor y lectura de WhatsApp |
| Sprint 3 | 29/10 – 04/11 | Reportar y ver eventos, A*, análisis con LLM y primer despliegue |
| Sprint 4 | 05/11 – 11/11 | Votos, ruta en el backend, tráfico en el motor y modos sin conexión |
| Sprint 5 | 12/11 – 18/11 | Ruta en la app, telemetría, congestión y surtidores |
| Sprint 6 | 19/11 – 25/11 | Recálculo de ruta, pregunta de recarga, simulador y política de privacidad |
| Sprint 7 | 26/11 – 30/11 | Pruebas de carga, pruebas de punta a punta, despliegue final y correcciones |

Cada sprint es un Milestone de GitHub con el nombre «Sprint N», como pide [CONTRIBUTING.md](../CONTRIBUTING.md). Los fallos que aparezcan en el sprint 7 se abren como issues `tipo:bug` en ese momento.

## Issues por sprint

En esta tabla los títulos van abreviados; el título completo es el encabezado de cada issue en su archivo.

| Sprint | Flutter | Java | C++ | WhatsApp/LLM | Simulador | Servidor |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | Estructura base de la app | Estructura base del backend · Base de datos, catálogos y zona de cobertura | Estructura del núcleo y protocolo por pipe | Estructura del servicio | — | Preparar la instancia de Oracle Cloud |
| S2 | HU-10 Iniciar sesión · Mapa base con Mapbox | HU-10 Iniciar sesión y JWT · Catálogos para la app · Wrapper `MotorDeRutas` | Red vial desde OpenStreetMap | HU-08 Leer los grupos autorizados | — | Dominio, Nginx y HTTPS |
| S3 | HU-03 Ver los eventos en el mapa · HU-01 Reportar un evento vial | HU-01 Registrar reportes · HU-03 Consultar y vencer eventos · HU-08 Recibir el análisis de WhatsApp | HU-05 Calcular la ruta óptima con A* | HU-08 Analizar los mensajes por lotes | — | Desplegar el backend con el núcleo C++ |
| S4 | HU-11 Lista de eventos y votos · HU-02 Reportes sin conexión · HU-04 Eventos sin conexión | HU-11 Votar eventos · HU-05 Calcular la ruta · Mantener al día el tráfico del motor | HU-05 Tráfico de los eventos en los tramos | — | — | Desplegar el servicio de WhatsApp/LLM |
| S5 | HU-05 Cómo llegar y navegación activa · HU-13 Enviar telemetría · HU-07 Consultar surtidores | HU-07 Consultar surtidores · HU-12 Confirmaciones de recarga · HU-13 Telemetría y congestión | HU-13 Detectar congestión · HU-06 Revisar la ruta activa | Prueba con mensajes reales | Estructura y parámetros | Respaldos y registros |
| S6 | HU-12 Preguntar por la recarga · HU-06 Aviso de ruta alternativa · Política de privacidad | HU-06 Revisar la ruta activa · HU-09 Soporte para el simulador · Limpieza de datos vencidos | — | — | HU-09 Usuarios fantasma | Ambiente de pruebas |
| S7 | — | Pruebas de punta a punta | Rendimiento con la red real | — | Pruebas de carga y reporte | Despliegue final y revisión de seguridad |

## De historia de usuario a issues

Cada historia de usuario se reparte en issues por tecnología, y algunas de sus reglas se cumplen en issues de otra historia o en tareas técnicas. Una historia se marca como Completada en TRAZABILIDAD.md cuando están en «Hecho» todos los issues de las dos columnas.

| HU | Caso de uso | Issues de la historia | Otros issues que cumplen sus reglas | Reglas |
| --- | --- | --- | --- | --- |
| HU-01 | CU-01 | Java S3 · Flutter S3 | HU-09 Java S6 (RN-01.17) | RN-01.1 a RN-01.12, RN-01.16, RN-01.17 |
| HU-02 | CU-01 | Flutter S4 | — | RN-01.13 a RN-01.15 |
| HU-03 | CU-02 | Java S3 · Flutter S3 | — | RN-02.1 a RN-02.8 |
| HU-04 | CU-02 | Flutter S4 | — | RN-02.9 |
| HU-05 | CU-03 | C++ S3 y S4 · Java S4 · Flutter S5 | Red vial C++ S2 (RN-03.4 a RN-03.7) · Wrapper Java S2 (RN-03.15) | RN-03.1 a RN-03.18 |
| HU-06 | CU-04 | C++ S5 · Java S6 · Flutter S6 | — | RN-04.13 a RN-04.19 |
| HU-07 | CU-05 | Java S5 · Flutter S5 | — | RN-05.1 a RN-05.7 |
| HU-08 | CU-06 | WhatsApp/LLM S2 y S3 · Java S3 | — | RN-06.1 a RN-06.18 |
| HU-09 | CU-07 | Java S6 · Simulador S6 | Ambiente de pruebas S6 (RN-07.12) | RN-07.1 a RN-07.13 |
| HU-10 | CU-08 | Java S2 · Flutter S2 | HU-09 Java S6 (RN-08.13) | RN-08.1 a RN-08.13 |
| HU-11 | CU-02 | Java S4 · Flutter S4 | Mantener al día el tráfico del motor S4 (RN-02.15) · HU-09 Java S6 (RN-02.16) | RN-02.10 a RN-02.16 |
| HU-12 | CU-05 | Java S5 · Flutter S6 | — | RN-05.8 a RN-05.16 |
| HU-13 | CU-04 | C++ S5 · Java S5 · Flutter S5 | HU-09 Java S6 (RN-04.20, junto con C++ S5) | RN-04.1 a RN-04.12, RN-04.20 |

Las 131 reglas de `04_reglas` están citadas en al menos un issue.

## Cómo pasar un issue a GitHub

1. **Numerar.** Los issues van con `T-XXX` y `CP-XX` a propósito. Al crearlos en GitHub, en el orden de los sprints, se reemplaza cada `T-XXX` por el siguiente número libre y cada `CP-XX` por el siguiente caso de prueba. Al 12/10/2026 la última tarea es T-084 y el último caso de prueba es CP-19; confírmalo en [TRAZABILIDAD.md](../TRAZABILIDAD.md) antes de empezar. En la rama, `TXX` toma el mismo número (T-085 → `feature/T85-...`).
2. **Título y cuerpo.** El título es el encabezado del issue, sin el `###`. El cuerpo es todo lo que sigue hasta la línea horizontal.
3. **Dependencias.** Cuando el issue del que depende ya existe en GitHub, se agrega su número junto al título (por ejemplo, «[HU-10][CU-08][Java] … #41»).
4. **Milestone y etiquetas.** El Milestone es «Sprint N» y las etiquetas son las de la línea «Etiquetas».
5. **Tablero.** El issue entra a la columna Backlog y pasa a «Por hacer» al empezar su sprint.

Las etiquetas `tec:flutter`, `tec:java`, `tec:cpp`, `tec:whatsapp-llm`, `tec:simulador` y `tec:servidor` son nuevas. Sirven para filtrar el tablero por tecnología y hay que crearlas en el repositorio antes del sprint 1. Si el equipo prefiere no agregarlas, se borran de la línea «Etiquetas» y la tecnología queda en el título.

## Cómo se ordenaron los issues

- Un issue nunca depende de otro de un sprint posterior.
- Cuando depende de otro del mismo sprint, avanza con datos falsos que siguen [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md): el wrapper de Java usa un núcleo falso y la app usa respuestas falsas. Se conectan de verdad al fusionarse el otro issue.
- El camino crítico es el del motor: estructura del núcleo (S1) → red vial (S2) → A* (S3) → tráfico (S4) → congestión y revisión de ruta (S5) → recálculo en Java y en la app (S6). Un retraso ahí mueve CU-03 y CU-04 completos, así que los Pull Requests de ese camino tienen prioridad en las revisiones.
- Desde el sprint 3, cada sprint termina con lo fusionado en `develop` desplegado en el servidor, para probar la app contra el sistema real.

## Cuándo un issue está terminado

- [ ] Se cumplen todos sus criterios de aceptación.
- [ ] Sus pruebas automáticas pasan y sus casos de prueba están anotados en `05_pro` o en las pruebas del código.
- [ ] El Pull Request contra `develop` fue revisado y aprobado por Marcelo, o por otro integrante cuando el autor es Marcelo.
- [ ] Si el issue cambió algo de la arquitectura (un endpoint, una regla, un valor), el archivo de `03_des/02_Arquitectura` está actualizado en el mismo Pull Request.
- [ ] La fila de su historia en TRAZABILIDAD.md tiene el issue, la rama y los casos de prueba.

## Cambios que este backlog hizo en la arquitectura

Al escribir el backlog aparecieron huecos en la arquitectura. Se cerraron el 12/10/2026 en `03_des/02_Arquitectura`, para que los issues y los documentos digan lo mismo:

| Cambio | Dónde |
| --- | --- |
| El servidor es la instancia `churoviaje-server` de Oracle Cloud (ARM, 2 OCPU, 12 GB) | D-16 · 01, 07 |
| Se usa la capa gratuita de Gemini; los números de teléfono se enmascaran y la política de privacidad lo informa | D-17 · 07 · decisión de WhatsApp v1.4 |
| Un evento reportado afecta a los dos sentidos de la calle; la congestión, solo al suyo | D-18 · RN-03.9 · 05_contratos |
| La congestión guarda su nivel (×2,5 o ×4,0) y el motor lo recibe; también recibe el vencimiento nuevo cuando una congestión se extiende | D-14 · 03, 05_contratos, 06 |
| La telemetría viaja al núcleo con la marca de simulado | 05_contratos |
| Cerrar sesión (`POST /sesion/cerrar`) | 05_contratos |
| La ruta trae el identificador y la calle de cada tramo; la revisión recibe los eventos ya avisados y puede responder «ruta desactualizada» | 05_contratos |
| El tiempo máximo del wrapper se cuenta desde que la petición se escribe en el pipe | 05_contratos |
| El puerto 80 se abre solo para redirigir a HTTPS y validar el certificado | 07 |
| Registros del servidor por 14 días, sin ubicaciones; respaldos de 7 días fuera de la instancia | 06 |
| Distancias y tiempos que faltaban: ajuste a la red (100 m), lectura a tramo (30 m), memoria de un recorrido (5 min) | P-23, P-24, P-25 |

## Decisiones de detalle que tomó este backlog

Estos detalles no cambian la arquitectura y quedaron dentro de los issues. Si el equipo no está de acuerdo, conviene cambiarlos antes del sprint en que se usan.

| Tema | Cómo quedó | Issues |
| --- | --- | --- |
| Límite de la mancha urbana | Un polígono GeoJSON en `04_imp/datos/`, compartido por el backend y la red vial | Java S1, C++ S2 |
| Lista de surtidores y alias | Sale del diccionario de `05_pro/PruebaClasificacion` y la revisa Franz | Java S1 |
| Identificador de recorrido de la telemetría | HMAC del conductor con una clave en memoria que cambia cada día | Java S5 |
| Códigos de respuesta | Las reglas que rompe un voto o una confirmación responden 422; «sin ruta» y «punto inválido» responden 200 con su estado | Java S4 y S5 |
| Destino de la ruta (P-19) | Mientras no haya buscador, se toca el mapa o se elige un surtidor | Flutter S5 |
| Fin de la ruta | Termina a menos de 30 m del destino o con «Cancelar» | Flutter S5 |
| Colores de bloqueo, otro y congestión (P-06) | Valores provisionales hasta que el diseño los fije | Java S2 |
| Frecuencia de la telemetría (P-13) | Lectura cada 3 segundos, envío cada 15 | Flutter S5 |
| Datos simulados | Un comando del backend los borra, solo donde están habilitadas las credenciales de prueba | Java S6 |
| Carga normal del simulador | Propuesta: 50 usuarios fantasma, 10 de ellos navegando | Simulador S5 |
| Precisión del LLM | El equipo fija el mínimo aceptable antes de medirla | WhatsApp/LLM S5 |

## Riesgos

| Riesgo | Qué hacer |
| --- | --- |
| WhatsApp bloquea el número dedicado | Número de respaldo y grupo de prueba propio, como dice la decisión de WhatsApp |
| La red vial de OpenStreetMap tiene errores (calles mal marcadas de un sentido) | Revisar en el sprint 2 las calles del centro con alguien que maneje en Tarija |
| La instancia de Oracle Cloud se termina o cambia de IP | IP reservada desde el sprint 1 y no terminar la instancia hasta el 30/11 |
| Marcelo lleva el camino crítico del motor, el servidor y las revisiones de todos los Pull Requests | En los sprints 4 y 5, otro integrante revisa los Pull Requests que no son de C++ ni del servidor |
| Antonio reparte su tiempo entre Java y el simulador en los sprints 5 y 6 | Jorge toma «Limpieza de datos vencidos» si el simulador se atrasa |
| Alex tiene tres issues en el sprint 5 | Franz puede tomar «Consultar surtidores cercanos» si termina antes la prueba del LLM |
| Probar la app en iOS requiere una Mac | Si el equipo no tiene una, las pruebas se hacen en Android y se anota |
| Con la capa gratuita de Gemini, Google puede leer y usar los mensajes enviados (D-17) | Solo viajan texto y fecha, con los números enmascarados, y la política de privacidad lo dice |
