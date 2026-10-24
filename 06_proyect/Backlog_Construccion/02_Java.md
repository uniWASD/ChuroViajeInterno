# Backlog de Construcción — Backend Java

[Índice del backlog](README.md)

Responsables: Jorge y Antonio; Franz mide la API de ANH y Jorge vincula sus estaciones con los surtidores. Código en `04_imp/backend_java`.

El backend es la única puerta de entrada del sistema y el lugar donde viven los objetos de dominio y sus reglas. También contiene el wrapper `MotorDeRutas`, la cara en Java del motor core ([D-15](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Antes de tomar un issue, lee [02_mvc.md](../../03_des/02_Arquitectura/02_mvc.md), [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md) y el archivo de reglas del caso de uso.

| Sprint | Issues |
| --- | --- |
| S1 (15/10–21/10) | Estructura base del backend · Base de datos, catálogos y zona de cobertura |
| S2 (22/10–28/10) | HU-10 Iniciar sesión con Google y emitir el JWT · Catálogos para la app · Wrapper `MotorDeRutas` |
| S3 (29/10–04/11) | HU-01 Registrar reportes de eventos viales · HU-03 Consultar eventos vigentes y vencerlos · HU-08 Recibir el análisis de WhatsApp · Medir la API de ANH y fijar el ciclo de consulta · Vincular las estaciones de ANH con los surtidores |
| S4 (05/11–11/11) | HU-11 Votar eventos · HU-05 Calcular la ruta · Mantener al día el tráfico del motor · HU-14 Consultar el estado de combustible desde ANH |
| S5 (12/11–18/11) | HU-07 Consultar surtidores cercanos · HU-12 Registrar confirmaciones de recarga · HU-13 Recibir telemetría y registrar congestión |
| S6 (19/11–25/11) | HU-06 Revisar la ruta activa · HU-09 Soporte para el simulador · Limpieza de datos vencidos |
| S7 (26/11–30/11) | Pruebas de punta a punta |

---

## Sprint 1

### [T-088][Java] Estructura base del backend

**Tipo:** Tarea de infraestructura (base para todos los issues de Java)
**Tecnología:** Java · **Sprint:** Sprint 1 (15/10–21/10)
**Depende de:** nada
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:java`

**Descripción:** Crear el proyecto Spring Boot con la organización de [02_mvc.md](../../03_des/02_Arquitectura/02_mvc.md), para que cada historia agregue sus piezas en el lugar correcto y nadie mezcle reglas en los controladores.

**Criterios de aceptación**
- [x] El proyecto compila y arranca con Java 21 y Spring Boot 3 en `04_imp/backend_java`.
- [x] El código está separado en paquetes de controladores REST, servicios del modelo, objetos de dominio, repositorios y motor (`MotorDeRutas`).
- [x] Hay tres perfiles de configuración: `dev`, `pruebas` y `prod`. Los secretos se leen de variables de entorno y ninguno está en el repositorio.
- [x] Todo rechazo responde con el código HTTP de la tabla «Respuestas de rechazo» de `05_contratos.md` y con un cuerpo `{codigo, mensaje}`, con el mensaje en español listo para mostrar al conductor.
- [x] Las fechas viajan en ISO 8601 con zona horaria y las ubicaciones como latitud y longitud en grados decimales, como dicen las convenciones de `05_contratos.md`.
- [x] La documentación OpenAPI se genera sola y se puede ver en el perfil `dev`.
- [x] Hay un endpoint de salud que solo responde dentro de la máquina, para que el servidor sepa si el backend está vivo; desde internet no se puede llamar (RNF-08).
- [x] Las pruebas (JUnit 5) corren con un solo comando, y el README de `04_imp/backend_java` explica cómo compilar, probar y ejecutar.
- [x] Dependabot está activo en el repositorio para las dependencias de Java, Flutter, Node.js y Python, y avisa de las actualizaciones de seguridad ([07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md), Servidor).

**Tareas**
- [x] Elegir Maven o Gradle y anotarlo en el README.
- [x] Crear el proyecto con Spring Web, Spring Security, Spring Data JPA, Validation y Actuator.
- [x] Crear los paquetes del MVC y una clase de ejemplo en cada uno.
- [x] Implementar el manejador global de errores con los códigos 400, 401, 403, 404, 409, 422, 429 y 503.
- [x] Configurar los perfiles y la lectura de variables de entorno.
- [x] Agregar springdoc-openapi.
- [x] Configurar una acción de GitHub que compile y corra las pruebas en cada Pull Request.
- [x] Activar Dependabot para las cinco carpetas de código.
- [x] Escribir el README.

**Casos de prueba**
- [x] CP-21 Caso de prueba: una petición con un cuerpo mal formado responde 400 con `{codigo, mensaje}` en español.

**Relacionado:** RNF-08 · [02_mvc.md](../../03_des/02_Arquitectura/02_mvc.md) · [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md) · D-01, D-02

Lo subes en una nueva rama llamada "feature/T88-java-estructura-base" (No olvides que el source de la rama es la de develop)

---

### [T-089][Java] Base de datos, catálogos y zona de cobertura

**Tipo:** Tarea de infraestructura (datos que usan CU-01, CU-02, CU-03, CU-05 y CU-06)
**Tecnología:** Java · **Sprint:** Sprint 1 (15/10–21/10)
**Depende de:** [T-088][Java] Estructura base del backend #40
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:java`

**Descripción:** Dejar lista la base de datos PostgreSQL con PostGIS, versionada con migraciones, con los catálogos cargados. También dibujar el polígono de la mancha urbana de Tarija, que usan el backend para validar ubicaciones y el motor para recortar la red vial. Cada historia agrega después sus propias tablas en su propia migración.

**Criterios de aceptación**
- [ ] Las migraciones (Flyway) crean la extensión PostGIS y las tablas `tipo_evento`, `tipo_combustible`, `surtidor` y `surtidor_alias` de [06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md).
- [ ] `tipo_evento` trae accidente, control policial, bloqueo y otro como reportables con vigencia de 60 minutos, y congestión como no reportable con 30 minutos (RN-02.3, D-04, D-10).
- [ ] `tipo_combustible` trae Gasolina, Diesel, Gasolina Premium, Diesel ULS y Desconocido.
- [ ] `surtidor` y `surtidor_alias` traen los surtidores de Tarija con su ubicación y los alias con que la gente los nombra en los grupos.
- [ ] El polígono de la mancha urbana está en `04_imp/datos/mancha_urbana_tarija.geojson` y el backend lo carga al arrancar.
- [ ] Un objeto `Ubicación` responde si un punto cae dentro de la mancha urbana (RN-01.6, RN-03.3).
- [ ] Una base vacía queda igual a la de producción con solo correr las migraciones.
- [ ] Las tablas declaran sus claves foráneas, y cada migración posterior también: todo evento, voto y estado apunta a un tipo, un surtidor o un conductor que existe ([06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md), «Lo que la base de datos asegura por su cuenta»).

**Tareas**
- [ ] Configurar Flyway y la conexión a PostgreSQL por perfil.
- [ ] Escribir la migración de los catálogos y sus datos iniciales.
- [ ] Armar la lista de surtidores con su ubicación y sus alias, partiendo del diccionario `SURTIDORES` de `05_pro/PruebaClasificacion/Clasificador_Surtidores.py` y revisándola con Franz.
- [ ] Dibujar el polígono de la mancha urbana (por ejemplo con geojson.io) y guardarlo en `04_imp/datos/`.
- [ ] Implementar el objeto de dominio `Ubicación` con su validación.
- [ ] Probar las migraciones sobre una base limpia.

**Casos de prueba**
- [ ] CP-22 Caso de prueba: un punto en la Plaza Luis de Fuentes está dentro de la mancha urbana; uno en San Lorenzo está fuera.
- [ ] CP-23 Caso de prueba: correr las migraciones sobre una base vacía crea las tablas y los catálogos completos.

**Relacionado:** RF-01 · RF-07 · CU-01 · CU-05 · CU-06 · [03_modelo_dominio.md](../../03_des/02_Arquitectura/03_modelo_dominio.md) · [06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md) · D-02, D-04, D-10

Lo subes en una nueva rama llamada "feature/T89-java-base-de-datos" (No olvides que el source de la rama es la de develop)

---

## Sprint 2

### [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT

**Caso de uso:** CU-08 — Iniciar Sesión
**Tecnología:** Java · **Sprint:** Sprint 2 (22/10–28/10)
**Depende de:** [T-089][Java] Base de datos, catálogos y zona de cobertura #41
**Etiquetas:** `caso-uso:CU-08` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor de la app, quiero iniciar sesión con mi cuenta de Google, para acceder de forma rápida y segura sin gestionar una contraseña adicional.

**Criterios de aceptación**
- [ ] `POST /sesion/google` valida el token de identidad con Google antes de emitir nada; si es inválido o expiró, responde 401 sin emitir token (RN-08.3).
- [ ] En el primer ingreso se crea el `Conductor`; en los siguientes se reutiliza. La base no permite dos conductores con la misma cuenta de Google (RN-08.4).
- [ ] La respuesta trae un JWT de 1 hora, su expiración y un token de renovación válido por 30 días (RN-08.5, RN-08.12).
- [ ] `POST /sesion/renovar` entrega un JWT nuevo sin pasar por Google mientras el token de renovación siga vigente; vencido, responde 401 (RN-08.12).
- [ ] Todas las rutas de la API exigen un JWT de conductor vigente, salvo las de `/sesion/*`, que no lo necesitan, y las de `/monitoreo/*`, que exigen la credencial de servicio. En las rutas de conductor, sin JWT o con uno vencido, responden 401. En `/monitoreo/*`, sin credencial o con una que no es válida, responden 401, y con el JWT de un conductor vigente, 403 (RN-08.11, RN-06.14, RNF-08).
- [ ] `POST /sesion/cerrar` invalida el token de renovación; después, `POST /sesion/renovar` con ese token responde 401 (RN-08.14).
- [ ] Nunca se guardan credenciales de Google; solo el identificador de la cuenta (RN-08.6).
- [ ] La respuesta de inicio de sesión llega en menos de 3 segundos con red normal (RNF-05).

**Tareas**
- [ ] Crear la migración de las tablas `conductor` y `sesion`.
- [ ] Implementar los objetos de dominio `Conductor` y `Sesión` con sus reglas.
- [ ] Validar el token de Google con la librería oficial, comprobando el Client ID de la app.
- [ ] Emitir y firmar el JWT con una clave que se lee de una variable de entorno.
- [ ] Configurar el filtro de Spring Security que exige el JWT en todas las demás rutas.
- [ ] Implementar la renovación y el cierre de sesión, y guardar los tokens de renovación.
- [ ] Escribir pruebas unitarias del dominio y pruebas de integración de los dos endpoints.
- [ ] Implementar la credencial de servicio, leída de una variable de entorno (el mismo valor que usa el servicio de WhatsApp), y exigirla en `/monitoreo/*` dentro del filtro de seguridad (D-13, contrato en `05_contratos.md`).

**Casos de prueba**
- [ ] CP-31 Caso de prueba: primer ingreso con una cuenta válida crea el conductor y devuelve el JWT; el segundo ingreso no crea otro.
- [ ] CP-32 Caso de prueba: un token de Google vencido o falso responde 401 y no emite JWT.
- [ ] CP-33 Caso de prueba: una petición a `GET /eventos` sin JWT, o con uno vencido, responde 401.
- [ ] CP-34 Caso de prueba: con un token de renovación vigente se obtiene un JWT nuevo; con uno de más de 30 días, 401.
- [ ] CP-35 Caso de prueba: después de cerrar sesión, el token de renovación ya no sirve.

**Relacionado:** RF-11 · RNF-05 · RNF-08 · CU-08 · RN-08.1 a RN-08.12, RN-08.14 · P-05, RN-06.14 · D-13

Lo subes en una nueva rama llamada "feature/HU10-CU08-java-inicio-sesion" (No olvides que el source de la rama es la de develop)

---

### [T-094][Java] Catálogos para la app

**Tipo:** Tarea de desarrollo (cubre CU-01, CU-02 y CU-05; no es una HU específica)
**Tecnología:** Java · **Sprint:** Sprint 2 (22/10–28/10)
**Depende de:** [T-089][Java] Base de datos, catálogos y zona de cobertura #41 · [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT #46
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:java`

**Descripción:** Entregar a la app los catálogos que necesita para no tener valores propios: tipos de evento con su color, su vigencia y si son reportables; tipos de combustible; valores de la fila; y los tiempos que ofrece el reporte. Así la app y el backend nunca usan valores distintos ([D-06](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).

**Criterios de aceptación**
- [ ] `GET /catalogos` devuelve los tipos de evento con nombre, color, vigencia en minutos y si son reportables (RN-01.3, RN-02.3, RN-02.7).
- [ ] Devuelve los tipos de combustible, y marca que Desconocido no se puede elegir al confirmar una recarga (RN-05.9).
- [ ] Devuelve los tiempos del reporte: ahora mismo, hace 5 minutos (marcado), hace 10 minutos y hace 30 minutos (RN-01.4, D-09).
- [ ] Devuelve los valores de la fila aproximada: sin fila, corta, media y larga (P-15).
- [ ] Exige un JWT vigente (RNF-08).

**Tareas**
- [ ] Implementar el servicio y el controlador de catálogos.
- [ ] Mientras el diseño no fije los colores de bloqueo, otro y congestión (P-06), usar valores provisionales y dejarlos anotados en el issue.
- [ ] Escribir la prueba de integración del endpoint.

**Casos de prueba**
- [ ] CP-36 Caso de prueba: `GET /catalogos` devuelve 5 tipos de evento (4 reportables), 5 tipos de combustible, 4 valores de fila y 4 tiempos de reporte.

**Relacionado:** RF-01 · RF-03 · CU-01 · CU-02 · CU-05 · D-06, D-09 · P-06, P-15

Lo subes en una nueva rama llamada "feature/T94-java-catalogos" (No olvides que el source de la rama es la de develop)

---

### [T-095][Java] Wrapper MotorDeRutas

**Tipo:** Tarea de desarrollo (cubre CU-03 y CU-04; no es una HU específica)
**Tecnología:** Java · **Sprint:** Sprint 2 (22/10–28/10) · **Apoyo:** Marcelo
**Depende de:** [T-088][Java] Estructura base del backend #40 · [T-090][C++] Estructura del núcleo y protocolo por pipe #42
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:java` · `tec:cpp`

**Descripción:** Construir el wrapper `MotorDeRutas`, la única forma que tiene el backend de llegar al núcleo C++. El wrapper arranca el núcleo como proceso hijo, le escribe una línea de JSON por petición en su entrada estándar y lee la respuesta de su salida estándar, como define [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md). El resto del backend solo ve métodos Java.

**Criterios de aceptación**
- [ ] `MotorDeRutas` ofrece `calcularRuta`, `revisarRuta`, `actualizarTrafico`, `analizarTelemetria` y `consultarEstado`, y ninguna otra clase del backend habla con el núcleo.
- [ ] Al iniciar el backend, el wrapper arranca el núcleo y espera a que `consultar_estado` diga que la red vial está cargada.
- [ ] Cada petición lleva un `id` y una `operacion`, y el wrapper solo acepta la respuesta con el mismo `id`.
- [ ] Las peticiones se atienden de a una; si llegan varias juntas, el wrapper las pone en fila.
- [ ] Si el núcleo no responde una petición a tiempo para cumplir los 3 segundos de RN-03.15, contados desde que se le escribió, el wrapper lo detiene, lo vuelve a arrancar y avisa al servicio para que el backend responda 503.
- [ ] Una petición que vence mientras espera en la fila responde «motor no disponible» sin reiniciar el núcleo.
- [ ] Si el núcleo termina por un error, el wrapper lo arranca de nuevo. Mientras arranca, las operaciones responden «motor no disponible».
- [ ] Lo que el núcleo escribe en su salida de errores pasa al registro del backend.
- [ ] El wrapper tiene un punto donde, cada vez que arranca el núcleo, se le envían los eventos vigentes. Ese envío lo completa el issue «Mantener al día el tráfico del motor».

**Tareas**
- [ ] Implementar el arranque del proceso con `ProcessBuilder`, con la ruta del ejecutable y del archivo de red vial por configuración.
- [ ] Implementar la escritura y lectura de líneas JSON con Jackson.
- [ ] Implementar la fila de peticiones, el tiempo máximo y el reinicio.
- [ ] Escribir un núcleo falso de prueba (un script que responde mensajes fijos) para probar el wrapper sin depender del C++.
- [ ] Escribir pruebas de integración: respuesta normal, demora, caída y reinicio.

**Casos de prueba**
- [ ] CP-41 Caso de prueba: con el núcleo falso, `consultarEstado` devuelve que la red está cargada.
- [ ] CP-42 Caso de prueba: si el núcleo no responde, el wrapper corta antes de los 3 segundos, lo reinicia y la siguiente petición funciona.
- [ ] CP-43 Caso de prueba: con 50 peticiones juntas y un núcleo falso lento, las que vencen en la fila responden «motor no disponible» y el núcleo no se reinicia.
- [ ] CP-44 Caso de prueba: si el proceso del núcleo muere, el wrapper lo vuelve a arrancar solo.

**Relacionado:** RNF-02 · CU-03 · CU-04 · RN-03.15 · D-14, D-15 · [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md)

Lo subes en una nueva rama llamada "feature/T95-java-wrapper-motor" (No olvides que el source de la rama es la de develop)

---

## Sprint 3

### [HU-01][CU-01][Java] Registrar reportes de eventos viales

**Caso de uso:** CU-01 — Reportar Evento Vial
**Tecnología:** Java · **Sprint:** Sprint 3 (29/10–04/11)
**Depende de:** [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT #46 · [T-094][Java] Catálogos para la app #47
**Etiquetas:** `caso-uso:CU-01` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor de la app, quiero reportar un evento vial (accidente, control policial, bloqueo u otro) desde el mapa, para que otros conductores lo vean y puedan evitarlo.

**Criterios de aceptación**
- [ ] `POST /eventos` recibe tipo, ubicación, hora de confirmación y, si el conductor eligió, los minutos. Sin tipo, ubicación u hora de confirmación rechaza el reporte entero con 400 (RN-01.2).
- [ ] Solo acepta tipos reportables; un reporte de congestión se rechaza con 422 (RN-01.3).
- [ ] El `EventoVial` calcula el momento en que ocurrió restando los minutos a la hora de confirmación; sin minutos, usa 5; nunca queda en el futuro (RN-01.4).
- [ ] Una ubicación fuera de la mancha urbana de Tarija se rechaza con 422 (RN-01.6).
- [ ] Un evento que ya superó la vigencia de su tipo no se registra y responde 422 con el aviso (RN-01.7).
- [ ] Un segundo evento del mismo conductor, del mismo tipo, a menos de 100 m y dentro de 10 minutos se rechaza con 409 (RN-01.8, P-03).
- [ ] El sexto reporte de un conductor en 10 minutos se rechaza con 429 (RN-01.9, P-04).
- [ ] El evento aceptado nace Activo, con origen Conductor y sin votos; si no puede guardarse, no queda nada a medias (RN-01.10, RN-01.11).
- [ ] La respuesta y cualquier consulta posterior nunca incluyen quién lo reportó (RN-01.12, RNF-10).

**Tareas**
- [ ] Crear la migración de `evento_vial`, con la ubicación como punto PostGIS.
- [ ] Implementar el objeto de dominio `EventoVial` con sus reglas de creación, vigencia y duplicado.
- [ ] Implementar el límite de 5 reportes en 10 minutos en `Conductor`.
- [ ] Implementar el servicio que registra eventos, en una sola transacción.
- [ ] Implementar el controlador REST de eventos con su validación de formato.
- [ ] Escribir pruebas unitarias de cada regla y pruebas de integración del endpoint.

**Casos de prueba**
- [ ] CP-52 Caso de prueba: un accidente «hace 5 minutos» dentro de Tarija se registra Activo y el momento en que ocurrió es 5 minutos antes de la confirmación.
- [ ] CP-53 Caso de prueba: un reporte sin ubicación responde 400 y no se guarda nada.
- [ ] CP-54 Caso de prueba: el mismo conductor reporta dos controles policiales a 50 m en 3 minutos; el segundo responde 409.
- [ ] CP-55 Caso de prueba: el sexto reporte en 10 minutos responde 429.
- [ ] CP-56 Caso de prueba: un reporte de «congestión» responde 422.

**Relacionado:** RF-01 · RNF-01 · RNF-06 · RNF-08 · RNF-09 · RNF-10 · CU-01 · RN-01.1 a RN-01.12 · P-03, P-04 · D-09, D-10

Lo subes en una nueva rama llamada "feature/HU01-CU01-java-registrar-reporte" (No olvides que el source de la rama es la de develop)

---

### [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos

**Caso de uso:** CU-02 — Visualizar Eventos Viales
**Tecnología:** Java · **Sprint:** Sprint 3 (29/10–04/11) · **Apoyo:** Jorge
**Depende de:** [HU-01][CU-01][Java] Registrar reportes de eventos viales #53
**Etiquetas:** `caso-uso:CU-02` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor de la app, quiero ver en el mapa los eventos viales activos reportados por otros conductores, para anticiparme a accidentes, controles, bloqueos o congestiones en mi ruta.

**Criterios de aceptación**
- [ ] `GET /eventos` devuelve solo los eventos Activos y Confirmados; los Retirados y Vencidos no salen nunca (RN-02.2).
- [ ] Un evento deja de salir en cuanto supera la vigencia de su tipo, contada desde que ocurrió: 30 minutos la congestión y 60 los demás (RN-02.3, RN-02.4).
- [ ] Con una marca de la última consulta, la respuesta trae solo los cambios y una marca nueva: los eventos nuevos, los modificados (los que pasaron a Confirmado y las congestiones con su momento actualizado) y los identificadores de los que dejaron de mostrarse, por vencidos o retirados (RN-02.6, RN-02.13, RN-02.14, RN-04.11).
- [ ] La vigencia se calcula al momento de cada consulta, sin esperar a la tarea periódica: un evento que venció hace segundos ya sale entre los que dejaron de estar vigentes (RN-02.4).
- [ ] De cada evento se entrega tipo, ubicación, cuándo ocurrió, si está confirmado y su origen; nunca quién lo reportó ni quién lo votó (RN-02.5, RNF-10).
- [ ] Una tarea periódica marca como Vencido todo evento que superó su vigencia (RN-02.4).
- [ ] La consulta responde en menos de 1 segundo con 500 eventos vigentes, para que el ciclo de 5 segundos de la app no se atrase (RNF-01).

**Tareas**
- [ ] Implementar la consulta en el repositorio y la respuesta de vigencia en `EventoVial`.
- [ ] Implementar la consulta por cambios con la marca de la última consulta.
- [ ] Implementar la tarea periódica de vencimiento (cada minuto).
- [ ] Implementar el controlador REST con su respuesta sin datos personales.
- [ ] Escribir pruebas unitarias, de integración y una prueba de tiempo con 500 eventos.

**Casos de prueba**
- [ ] CP-61 Caso de prueba: un control policial de hace 61 minutos ya no sale en `GET /eventos` y queda Vencido.
- [ ] CP-62 Caso de prueba: con la marca de una consulta anterior solo llegan los eventos nuevos, los que se confirmaron y los que dejaron de estar vigentes.
- [ ] CP-63 Caso de prueba: un evento que vence entre dos consultas llega como vencido en la segunda, aunque la tarea periódica no haya corrido todavía.
- [ ] CP-64 Caso de prueba: la respuesta no contiene ningún identificador de conductor.

**Relacionado:** RF-03 · RNF-01 · RNF-10 · CU-02 · RN-02.1 a RN-02.6, RN-02.14, RN-04.11

Lo subes en una nueva rama llamada "feature/HU03-CU02-java-consultar-eventos" (No olvides que el source de la rama es la de develop)

---

### [HU-08][CU-06][Java] Recibir el análisis de WhatsApp

**Caso de uso:** CU-06 — Monitorear Estado de Surtidores vía WhatsApp
**Tecnología:** Java · **Sprint:** Sprint 3 (29/10–04/11)
**Depende de:** [T-089][Java] Base de datos, catálogos y zona de cobertura #41 · [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT #46
**Etiquetas:** `caso-uso:CU-06` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como sistema, quiero extraer los mensajes de los grupos de WhatsApp monitoreados e interpretarlos por lotes, para mantener actualizado el estado de los surtidores sin depender de reportes manuales.

Este issue cubre la parte del backend: recibir, validar y guardar el resultado del análisis.
La credencial de servicio la implementa #46; aquí solo se usa para proteger los dos endpoints.

**Criterios de aceptación**
- [ ] `GET /monitoreo/surtidores` y `POST /monitoreo/resultados` solo aceptan la credencial de servicio; un JWT de conductor responde 403 (RN-06.14, D-13).
- [ ] `GET /monitoreo/surtidores` devuelve los nombres y alias de los surtidores, los tipos de combustible y los valores de la fila, para que el servicio se los pase al LLM.
- [ ] Cada aviso del lote se valida por separado: surtidor del catálogo por nombre o alias, tipo, estado y formato válidos. El aviso que falla se descarta y los demás siguen (RN-06.15).
- [ ] La fila, si viene, debe ser uno de los valores de P-15; si no lo es, el aviso se acepta sin fila.
- [ ] Un aviso válido se guarda como `EstadoCombustible` con origen WhatsApp y con la fecha del mensaje, no la del análisis (RN-06.16).
- [ ] Si ya hay un dato de WhatsApp más reciente para ese surtidor y tipo, el aviso no lo reemplaza; un dato de Conductores o de ANH no lo impide, y cuál de los tres se muestra lo decide #69. Si el lote trae varios del mismo surtidor y tipo, vale el más reciente (RN-06.17).
- [ ] La respuesta es siempre 200 con el número de avisos aceptados y la lista de descartados, cada uno con su posición en el lote y su motivo (`SURTIDOR_DESCONOCIDO`, `COMBUSTIBLE_INVALIDO`, `ESTADO_INVALIDO`, `FECHA_INVALIDA` o `FORMATO_INVALIDO`). Un aviso válido pero más viejo que el dato actual cuenta como aceptado. Un lote de más de 1000 avisos responde 400.
- [ ] La tabla `estado_combustible` acepta los tres orígenes (WhatsApp, Conductores y ANH) y ya tiene las columnas `litros` (no negativo, opcional), `estado_anh` (alto, medio o bajo, opcional) y `consultado_en`, que usa CU-09 ([06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md), RN-09.12, RN-09.13).

**Tareas**
- [ ] Crear la migración de `estado_combustible`, con un solo estado por surtidor, tipo y origen (WhatsApp, Conductores o ANH), y con las columnas `litros`, `estado_anh` y `consultado_en` para que CU-09 no necesite otra migración de esa tabla.
- [ ] Implementar la validación de `ResultadoAnálisis` y la actualización de `EstadoCombustible`.
- [ ] Implementar los dos endpoints de monitoreo.
- [ ] Escribir pruebas con lotes válidos, mezclados y repetidos.

**Casos de prueba**
- [ ] CP-68 Caso de prueba: un lote con un aviso de «Las Vegas» válido y otro de un surtidor que no existe actualiza solo el primero y reporta el segundo como descartado.
- [ ] CP-69 Caso de prueba: un aviso de las 15:00 no reemplaza un dato del mismo surtidor y tipo de las 15:10.
- [ ] CP-70 Caso de prueba: una petición con el JWT de un conductor responde 403.

**Relacionado:** RF-08 · RF-09 · RNF-04 · CU-06 · RN-06.14 a RN-06.17 · RN-09.12, RN-09.13 · D-13 · P-15 · [decision_conexion_whatsapp.md](../../05_pro/ComWhatsapp/decision_conexion_whatsapp.md)

Lo subes en una nueva rama llamada "feature/HU08-CU06-java-recibir-analisis" (No olvides que el source de la rama es la de develop)

---

### [T-117][Java] Medir la API de ANH y fijar el ciclo de consulta y la vigencia

**Tipo:** Tarea de pruebas (cubre CU-09)
**Tecnología:** Java · **Sprint:** Sprint 3 (29/10–04/11)
**Depende de:** [T-119][Servidor] Comprobar la salida del servidor hacia la API de ANH #?(orden 56)
**Etiquetas:** `tipo:prueba` · `fase:construccion` · `tec:java`

**Descripción:** Medir durante unos días cómo se comporta la API de ANH y, con eso, fijar los parámetros que quedaron pendientes: cada cuánto se consulta (P-29), cuánto vale un dato de ANH (P-30) y el tiempo de espera y los reintentos (P-32). También confirmar la zona horaria de `updated_at`. El resultado queda anotado en la arquitectura, para que el issue de la consulta (HU-14) parta de valores confirmados.

**Criterios de aceptación**
- [ ] Hay un registro de al menos 3 días seguidos de consultas desde la instancia, con la hora, el estado HTTP, el tiempo de respuesta, cuántas estaciones llegaron y la fecha de actualización de cada una. El registro no guarda el cuerpo de las respuestas.
- [ ] Se sabe cada cuánto cambian el saldo y la fecha de actualización de una estación, y con eso se fija P-29 sin consultar más de lo necesario (RN-09.1).
- [ ] Se fija P-30, la vigencia de un dato de ANH contada desde que el backend lo leyó, y queda dicho si se mantienen las 6 horas de RN-05.5 (RN-09.17).
- [ ] Se confirma si `updated_at` trae zona horaria y qué zona se toma cuando no la trae (RN-09.13).
- [ ] Se confirma o ajusta P-32 con los tiempos medidos y con lo que haga ANH ante las peticiones repetidas (429 o `Retry-After`) (RN-09.4).
- [ ] P-29, P-30 y P-32 quedan como Confirmada en [08_decisiones_parametros.md](../../03_des/02_Arquitectura/08_decisiones_parametros.md), y los valores iniciales de [03_modelo_dominio.md](../../03_des/02_Arquitectura/03_modelo_dominio.md) y de CU-09 quedan al día.

**Tareas**
- [ ] Escribir un script de medición, que puede partir del cliente de `05_pro/PruebaApiANH`, y dejarlo corriendo en la instancia.
- [ ] Analizar el registro y proponer los valores.
- [ ] Actualizar los documentos de arquitectura con los valores confirmados.

**Casos de prueba**
- [ ] CP-157 Caso de prueba: el registro de medición cubre 72 horas seguidas y cada fila trae hora, estado HTTP, tiempo de respuesta y cantidad de estaciones.
- [ ] CP-158 Caso de prueba: P-29, P-30 y P-32 figuran como Confirmada, con su valor, en `08_decisiones_parametros.md`.

**Relacionado:** RF-15 · RNF-04 · CU-09 · RN-09.1, RN-09.4, RN-09.13, RN-09.17 · RN-05.5 · D-29, D-32 · P-29, P-30, P-32

Lo subes en una nueva rama llamada "feature/T117-java-medir-api-anh" (No olvides que el source de la rama es la de develop)

---

### [T-118][Java] Vincular las estaciones de ANH con los surtidores del catálogo

**Tipo:** Tarea de desarrollo (cubre CU-09)
**Tecnología:** Java · **Sprint:** Sprint 3 (29/10–04/11)
**Depende de:** [T-089][Java] Base de datos, catálogos y zona de cobertura #41 · [HU-08][CU-06][Java] Recibir el análisis de WhatsApp #57
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:java`

**Descripción:** Darle a cada surtidor del catálogo el identificador de su estación en ANH, para que la consulta sepa a qué surtidor pertenece cada dato. ANH devuelve todo el departamento, así que solo se usan las estaciones que están en la mancha urbana y tienen un surtidor en el catálogo. Aquí también se fija la distancia máxima para vincular (P-31) y se deja lista la tabla `estado_combustible` para recibir datos de ANH.

**Criterios de aceptación**
- [ ] Una migración agrega `anh_id` a `surtidor`: número entero, opcional y único, de modo que ningún surtidor tiene dos y ningún identificador de ANH pertenece a dos surtidores ([06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md), RN-09.10).
- [ ] `estado_combustible` tiene `litros` (no negativo, opcional), `estado_anh` (alto, medio o bajo, opcional) y `consultado_en`, y acepta el origen ANH. Si la migración de #57 no los creó, la migración de esta tarea los agrega.
- [ ] Se comparan las estaciones de Tarija de ANH que están dentro de la mancha urbana con el catálogo, por nombre y por distancia, y con esos pares se fija P-31 (RN-09.10).
- [ ] Cada vínculo lo revisa a mano una persona del equipo antes de entrar en la migración de datos.
- [ ] Las estaciones de ANH dentro de la mancha urbana que no están en el catálogo quedan en una lista de candidatas, y el equipo decide si entran al catálogo (RN-09.9).
- [ ] P-31 queda como Confirmada en [08_decisiones_parametros.md](../../03_des/02_Arquitectura/08_decisiones_parametros.md).

**Tareas**
- [ ] Escribir la migración de `anh_id` y, si hace falta, la de las columnas de `estado_combustible`.
- [ ] Escribir un script que lea las estaciones de ANH y proponga los pares con su distancia.
- [ ] Revisar los pares con el equipo y cargar los vínculos confirmados.
- [ ] Escribir las pruebas de integridad de las dos tablas.

**Casos de prueba**
- [ ] CP-159 Caso de prueba: asignar el mismo `anh_id` a dos surtidores falla por la restricción de unicidad.
- [ ] CP-160 Caso de prueba: `estado_combustible` guarda un dato con origen ANH, 0 litros, estado «bajo» y `consultado_en`, y rechaza un dato con litros negativos.

**Relacionado:** RF-15 · CU-09 · RN-09.9, RN-09.10, RN-09.12, RN-09.13 · D-30, D-31 · P-31

Lo subes en una nueva rama llamada "feature/T118-java-vincular-estaciones-anh" (No olvides que el source de la rama es la de develop)

---

## Sprint 4

### [HU-11][CU-02][Java] Votar eventos

**Caso de uso:** CU-02 — Visualizar Eventos Viales
**Tecnología:** Java · **Sprint:** Sprint 4 (05/11–11/11)
**Depende de:** [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos #55
**Etiquetas:** `caso-uso:CU-02` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor de la app, quiero ver la lista de eventos activos y votar si cada uno es real o falso, para que el mapa muestre solo eventos confiables.

**Criterios de aceptación**
- [ ] `POST /eventos/{id}/votos` registra un voto «real» o «falso» solo sobre eventos Activos o Confirmados que reportó un conductor; la congestión registrada por telemetría no se vota y responde 422 (D-20). Un evento Retirado o Vencido responde 422, y uno que ya no existe, 404 (RN-02.10).
- [ ] Un segundo voto del mismo conductor sobre el mismo evento responde 409; la base tampoco lo permite (RN-02.11).
- [ ] El reportante no puede votar su propio evento: responde 422 (RN-02.12, P-17).
- [ ] Con 3 votos «falso», el evento pasa a Retirado (RN-02.13, P-01).
- [ ] En la consulta por cambios de `GET /eventos`, un evento retirado llega entre los que dejaron de mostrarse y uno confirmado entre los modificados, para que la app lo quite o lo marque (RN-02.6, RN-02.13, RN-02.14).
- [ ] Con 3 votos «real», pasa a Confirmado y ya no se retira por votos, solo vence (RN-02.14, P-11).
- [ ] La respuesta trae el estado del evento después del voto, sin datos de quién votó.

**Tareas**
- [ ] Crear la migración de `voto`, con un solo voto por conductor y evento.
- [ ] Implementar el objeto de dominio `Voto` y el recuento y cambio de estado en `EventoVial`.
- [ ] Implementar el servicio y el controlador de votos.
- [ ] Escribir pruebas de cada regla, incluida la de dos votos que llegan al mismo tiempo.

**Casos de prueba**
- [ ] CP-86 Caso de prueba: tres conductores votan «falso» un accidente; queda Retirado y no sale en la siguiente `GET /eventos`.
- [ ] CP-87 Caso de prueba: un evento con 3 votos «real» recibe después 3 «falso» y sigue Confirmado.
- [ ] CP-88 Caso de prueba: el mismo conductor vota dos veces; el segundo voto responde 409.
- [ ] CP-89 Caso de prueba: el reportante intenta votar su evento y recibe 422.

**Relacionado:** RF-12 · RNF-09 · CU-02 · RN-02.10 a RN-02.14 · D-20 · P-01, P-11, P-17

Lo subes en una nueva rama llamada "feature/HU11-CU02-java-votos" (No olvides que el source de la rama es la de develop)

---

### [HU-05][CU-03][Java] Calcular la ruta

**Caso de uso:** CU-03 — Calcular Ruta Óptima
**Tecnología:** Java · **Sprint:** Sprint 4 (05/11–11/11)
**Depende de:** [T-095][Java] Wrapper MotorDeRutas #49 · [HU-05][CU-03][C++] Calcular la ruta óptima con A* #58
**Etiquetas:** `caso-uso:CU-03` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor de la app, quiero calcular la ruta vehicular óptima hacia mi destino, para llegar por el camino más eficiente considerando datos viales vigentes.

**Criterios de aceptación**
- [ ] `POST /rutas` recibe origen y destino obligatorios y paradas opcionales en orden; sin origen o destino responde 400 (RN-03.2).
- [ ] Un punto fuera de la mancha urbana responde 200 con el estado «punto inválido» y el mensaje «Origen o destino inválido», sin llamar al motor; es la misma respuesta que da el motor cuando no puede ajustar un punto a la red (RN-03.3, RN-03.4).
- [ ] La ruta se pide a `MotorDeRutas` y se devuelve con su trazado, su distancia, su tiempo estimado, el nombre de la calle de cada tramo y los identificadores de sus tramos, que la app devuelve al revisar la ruta (RN-03.13, D-12).
- [ ] «Sin ruta» (RN-03.12) y «punto inválido» (RN-03.3, RN-03.4) son respuestas correctas, con código 200 y su mensaje; no son errores.
- [ ] Si el motor no responde a tiempo o se está reiniciando, el backend responde 503 y la app puede reintentar (RN-03.15).
- [ ] La ruta no se guarda en la base de datos y el cálculo no usa datos de otros conductores (RN-03.16, RN-03.17, D-05).
- [ ] Toda la respuesta llega en menos de 3 segundos (RNF-02).

**Tareas**
- [ ] Implementar el servicio del modelo que valida los puntos y llama a `MotorDeRutas`.
- [ ] Implementar el controlador REST de rutas.
- [ ] Traducir las respuestas del motor (`ENCONTRADA`, `SIN_RUTA`, `PUNTO_INVALIDO`, error) a la respuesta de la API.
- [ ] Escribir pruebas con el núcleo falso y una prueba de integración con el núcleo real.

**Casos de prueba**
- [ ] CP-92 Caso de prueba: de la Plaza Luis de Fuentes a la terminal se obtiene una ruta con distancia y tiempo en menos de 3 segundos.
- [ ] CP-93 Caso de prueba: un destino en San Lorenzo responde «Origen o destino inválido».
- [ ] CP-94 Caso de prueba: con el núcleo detenido, la respuesta es 503 y el siguiente intento, con el núcleo reiniciado, funciona.

**Relacionado:** RF-05 · RNF-02 · RNF-10 · CU-03 · RN-03.1 a RN-03.4, RN-03.12, RN-03.13, RN-03.15 a RN-03.17 · D-05, D-12, D-15

Lo subes en una nueva rama llamada "feature/HU05-CU03-java-calcular-ruta" (No olvides que el source de la rama es la de develop)

---

### [T-099][Java] Mantener al día el tráfico del motor

**Tipo:** Tarea de desarrollo (cubre CU-02, CU-03 y CU-04; no es una HU específica)
**Tecnología:** Java · **Sprint:** Sprint 4 (05/11–11/11)
**Depende de:** [T-095][Java] Wrapper MotorDeRutas #49 · [HU-11][CU-02][Java] Votar eventos #63 · [HU-05][CU-03][C++] Tráfico de los eventos en los tramos #66
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:java`

**Descripción:** Que el motor calcule siempre con el tráfico al día. El backend le avisa cada vez que un evento nace, cambia su vencimiento, se retira o vence ([D-14](../../03_des/02_Arquitectura/08_decisiones_parametros.md)) y, cada vez que el wrapper arranca el núcleo, le envía todos los eventos vigentes ([D-15](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).

**Criterios de aceptación**
- [ ] Al registrarse un evento, el motor recibe `actualizar_trafico` con su identificador, tipo, ubicación y vencimiento; si es una congestión, también su nivel.
- [ ] Cuando un evento cambia su vencimiento, el motor recibe el nuevo. Lo usa la congestión que se extiende (RN-04.11), que llega en el sprint 5.
- [ ] Al retirarse un evento por votos o al vencer, el motor recibe el aviso y el tramo queda con el nivel que le dan los eventos que le quedan, o fluido si no le queda ninguno (RN-02.15).
- [ ] Al arrancar o reiniciar el núcleo, el wrapper le envía todos los eventos vigentes antes de atender rutas.
- [ ] Si el aviso al motor falla, el evento igual queda registrado en la base y el aviso se reintenta; nunca se pierde.

**Tareas**
- [ ] Publicar un evento interno cuando un `EventoVial` nace, cambia su vencimiento, se retira o vence.
- [ ] Escuchar esos eventos y llamar a `MotorDeRutas.actualizarTrafico`.
- [ ] Completar el envío de eventos vigentes en el arranque del wrapper.
- [ ] Escribir pruebas con el núcleo falso que registren qué mensajes recibió.

**Casos de prueba**
- [ ] CP-99 Caso de prueba: un bloqueo reportado sobre la única calle entre dos puntos hace que la ruta entre ellos responda «sin ruta»; al retirarlo por votos, la ruta vuelve a existir.
- [ ] CP-100 Caso de prueba: se reinicia el núcleo con 3 eventos vigentes y, antes de la primera ruta, el núcleo ya recibió los 3.

**Relacionado:** RF-05 · RF-06 · CU-02 · CU-03 · CU-04 · RN-02.15, RN-03.9 · D-14, D-15, D-18

Lo subes en una nueva rama llamada "feature/T99-java-trafico-motor" (No olvides que el source de la rama es la de develop)

---

### [HU-14][CU-09][Java] Consultar el estado de combustible desde ANH

**Caso de uso:** CU-09 — Consultar Estado de Combustible desde ANH
**Tecnología:** Java · **Sprint:** Sprint 4 (05/11–11/11)
**Depende de:** [HU-08][CU-06][Java] Recibir el análisis de WhatsApp #57 · [T-117][Java] Medir la API de ANH y fijar el ciclo de consulta y la vigencia #?(orden 57) · [T-118][Java] Vincular las estaciones de ANH con los surtidores del catálogo #?(orden 58)
**Etiquetas:** `caso-uso:CU-09` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como sistema, quiero consultar periódicamente la API pública de ANH Abastecimiento, para mantener actualizado el estado de los surtidores con el saldo y el estado que informa ANH, además de los mensajes de WhatsApp.

Este proceso es automático: no tiene vista ni controlador REST. La consulta la hace solo el backend; la app, el simulador y el servicio de WhatsApp/LLM nunca llaman a ANH ([05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md), «Backend con la API de ANH»).

**Criterios de aceptación**
- [ ] Una tarea periódica, con el ciclo de P-29, pide a ANH el listado de estaciones de gasolina y el de diésel de Tarija. Antes busca a Tarija por nombre en el catálogo de departamentos y, si no hay exactamente una, no consulta (RN-09.1, RN-09.2).
- [ ] Toda llamada es un GET por HTTPS, sin credenciales, con el certificado verificado; una redirección fuera de ANH o a HTTP se detiene (RN-09.3).
- [ ] Cada petición espera lo que dice P-32 y se reintenta solo ante falta de conexión, tiempo agotado o respuesta 429, 502, 503 o 504; respeta `Retry-After` y deja una pausa entre peticiones (RN-09.4).
- [ ] Una respuesta solo se acepta si es 200, JSON de hasta 10 MiB y `decCodigo` igual a 1. Se descarta completa si trae otro departamento u otro combustible, estaciones repetidas, identificadores no enteros o paginación (RN-09.5, RN-09.6).
- [ ] Un saldo no válido o negativo se trata como «sin saldo», y los demás datos siguen su curso (RN-09.7).
- [ ] Se ignoran las estaciones fuera de la mancha urbana o sin vincular a un surtidor; las que están dentro de la mancha y sin vincular se anotan en el registro como candidatas (RN-09.8, RN-09.9).
- [ ] Los litros se traducen así: 0 es agotado, más de 0 es disponible y sin saldo es sin dato, que no crea ni cambia ningún estado. Los litros y el estado de ANH se guardan tal como llegan, y el estado nunca se convierte en agotado (RN-09.11, RN-09.12).
- [ ] El dato válido se guarda como `EstadoCombustible` con origen ANH, el tipo Gasolina o Diesel, la fecha `updated_at` (sin zona horaria se toma la de Tarija; sin fecha no se guarda) y el momento de la lectura en `consultado_en` (RN-09.13).
- [ ] Un dato más viejo no reemplaza a uno de ANH más reciente; con la misma fecha solo se actualiza `consultado_en`. La comparación es solo entre datos de ANH (RN-09.14).
- [ ] Si ANH falla, cada surtidor conserva su último dato de ANH con su fecha, y un fallo nunca se convierte en cero litros. Si falla un combustible, el otro se actualiza igual (RN-09.15, RN-09.16).
- [ ] La vigencia de P-30 se cuenta desde `consultado_en` (RN-09.17).
- [ ] No se guardan la fecha de última venta ni el indicador de venta, la dirección ni la zona de la estación, las respuestas completas ni las estaciones ignoradas. Cada ciclo deja en el registro un resumen con las rutas, el estado de cada una y cuántas estaciones llegaron, se aceptaron y se ignoraron (RN-09.18, RN-09.19).
- [ ] El dato de ANH sale en `GET /surtidores` con origen ANH, litros y estado de ANH (lo muestra #69).

**Tareas**
- [ ] Implementar el cliente de ANH con su tiempo de espera, reintentos, `Retry-After` y la lectura segura de la respuesta.
- [ ] Implementar `ReporteANH`: validación de la respuesta, filtro de zona y de catálogo y traducción de los litros.
- [ ] Implementar la tarea periódica y la actualización de `EstadoCombustible`.
- [ ] Escribir las pruebas con un servidor de ANH falso que responda con los casos de los criterios; el ejemplo de respuesta sale del informe técnico de ANH (T-115).

**Casos de prueba**
- [ ] CP-161 Caso de prueba: ANH responde con tres estaciones, una vinculada con 0 litros, otra con 150 litros fuera de la mancha urbana y otra sin vincular; solo la primera crea un estado agotado con origen ANH.
- [ ] CP-162 Caso de prueba: ANH responde 503 en todos los intentos; ningún estado cambia y cada surtidor conserva su último dato de ANH con su fecha.
- [ ] CP-163 Caso de prueba: la respuesta trae una estación de otro departamento; se descarta completa y no se actualiza ningún estado.
- [ ] CP-164 Caso de prueba: una estación llega con saldo «abc» y otra con 20 litros; la primera queda sin dato y la segunda queda disponible.
- [ ] CP-165 Caso de prueba: un dato de ANH con fecha anterior al ya guardado no lo reemplaza.

**Relacionado:** RF-15 · RNF-04 · RNF-07 · CU-09 · RN-09.1 a RN-09.19 · D-28 a D-32 · P-29, P-30, P-32

Lo subes en una nueva rama llamada "feature/HU14-CU09-java-consultar-anh" (No olvides que el source de la rama es la de develop)

---

## Sprint 5

### [HU-07][CU-05][Java] Consultar surtidores cercanos

**Caso de uso:** CU-05 — Consultar Disponibilidad de Combustible
**Tecnología:** Java · **Sprint:** Sprint 5 (12/11–18/11)
**Depende de:** [HU-08][CU-06][Java] Recibir el análisis de WhatsApp #57
**Etiquetas:** `caso-uso:CU-05` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor de la app, quiero consultar el estado de disponibilidad de combustible en surtidores cercanos, para evitar dirigirme a uno que esté agotado.

**Criterios de aceptación**
- [ ] `GET /surtidores` recibe la ubicación actual y devuelve los surtidores ordenados del más cercano al más lejano, con la búsqueda por cercanía en PostGIS (RN-05.2).
- [ ] Para cada surtidor y tipo de combustible sale un solo dato: el más reciente, venga de WhatsApp, de Conductores o de ANH (RN-05.3).
- [ ] Cada dato trae disponibilidad, tipo, origen y fecha; la fila solo si se conoce. Si el origen es ANH, trae también los litros y el estado de ANH (RN-05.4, RNF-07).
- [ ] Un dato de más de 6 horas no cuenta (en un dato de ANH, las horas se cuentan desde que el backend lo leyó), y un surtidor sin datos vigentes sale como «sin información reciente» (RN-05.5, RN-09.17, P-30).
- [ ] Un dato de WhatsApp sin tipo sale con el tipo Desconocido; los datos de ANH siempre traen el tipo del combustible consultado (RN-05.6).
- [ ] Nunca sale quién aportó el dato (RN-05.7).
- [ ] La respuesta llega en menos de 3 segundos (RNF-03).

**Tareas**
- [ ] Implementar en `EstadoCombustible` la elección del dato más reciente entre los tres orígenes y la vigencia de 6 horas (desde `consultado_en` en los datos de ANH).
- [ ] Implementar la consulta por cercanía en el repositorio.
- [ ] Implementar el servicio y el controlador de surtidores.
- [ ] Escribir pruebas con datos de los tres orígenes y datos vencidos.

**Casos de prueba**
- [ ] CP-102 Caso de prueba: un surtidor con Gasolina «agotado» por WhatsApp a las 10:00 y «disponible» por Conductores a las 10:30 muestra disponible, origen Conductores, 10:30.
- [ ] CP-103 Caso de prueba: un surtidor cuyo último dato es de hace 7 horas sale como «sin información reciente».
- [ ] CP-104 Caso de prueba: desde la Plaza, el primer surtidor de la lista es el más cercano.

**Relacionado:** RF-07 · RNF-03 · RNF-07 · CU-05 · RN-05.1 a RN-05.7 · RN-09.12, RN-09.17 · D-28, D-31 · P-30

Lo subes en una nueva rama llamada "feature/HU07-CU05-java-consultar-surtidores" (No olvides que el source de la rama es la de develop)

---

### [HU-12][CU-05][Java] Registrar confirmaciones de recarga

**Caso de uso:** CU-05 — Consultar Disponibilidad de Combustible
**Tecnología:** Java · **Sprint:** Sprint 5 (12/11–18/11) · **Apoyo:** Jorge
**Depende de:** [HU-07][CU-05][Java] Consultar surtidores cercanos #69
**Etiquetas:** `caso-uso:CU-05` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor de la app, quiero indicar que acabo de recargar combustible y de qué tipo, para que los demás conductores sepan que ese surtidor tiene ese combustible.

**Criterios de aceptación**
- [ ] `POST /surtidores/{id}/confirmaciones` recibe el tipo de combustible y la ubicación donde la app detectó la parada; con el tipo Desconocido responde 422 (RN-05.9).
- [ ] Solo se acepta si la ubicación está a menos de 50 m del surtidor; si no, responde 422 (RN-05.13, P-09).
- [ ] Una confirmación del mismo conductor, surtidor y tipo dentro de 60 minutos no se registra y responde 409 (RN-05.14, P-14).
- [ ] Una confirmación aceptada deja ese combustible disponible, con origen Conductores y la fecha de la confirmación (RN-05.11).
- [ ] Una confirmación nunca marca un surtidor como agotado (RN-05.12).
- [ ] Los demás conductores ven la confirmación solo si es más reciente que el último dato de WhatsApp de ese surtidor y tipo (RN-05.15).

**Tareas**
- [ ] Crear la migración de `confirmacion_recarga`.
- [ ] Implementar el objeto de dominio `ConfirmaciónRecarga` con sus reglas de cercanía y repetición.
- [ ] Implementar el servicio y el controlador.
- [ ] Escribir pruebas de cada regla.

**Casos de prueba**
- [ ] CP-111 Caso de prueba: un conductor a 20 m del surtidor confirma Diesel y el surtidor queda con Diesel disponible, origen Conductores.
- [ ] CP-112 Caso de prueba: la misma confirmación 10 minutos después responde 409.
- [ ] CP-113 Caso de prueba: una confirmación desde 500 m responde 422.

**Relacionado:** RF-13 · RNF-07 · RNF-09 · CU-05 · RN-05.9, RN-05.11 a RN-05.15 · P-09, P-14

Lo subes en una nueva rama llamada "feature/HU12-CU05-java-confirmar-recarga" (No olvides que el source de la rama es la de develop)

---

### [HU-13][CU-04][Java] Recibir telemetría y registrar congestión

**Caso de uso:** CU-04 — Recalcular Ruta
**Tecnología:** Java · **Sprint:** Sprint 5 (12/11–18/11)
**Depende de:** [T-099][Java] Mantener al día el tráfico del motor #67 · [HU-13][CU-04][C++] Detectar congestión con la telemetría #73
**Etiquetas:** `caso-uso:CU-04` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como sistema, quiero recibir la posición de los conductores mientras tienen la app abierta, para calcular su velocidad y detectar congestión sin depender solo de los reportes manuales.

**Criterios de aceptación**
- [ ] `POST /telemetria` solo acepta lecturas de conductores con sesión vigente (RN-04.4).
- [ ] Las lecturas pasan a `MotorDeRutas` con un identificador temporal de recorrido, sin datos del conductor, y no se guardan en la base (RN-04.12).
- [ ] El identificador de recorrido es un HMAC del conductor con una clave que solo vive en memoria y cambia cada día. La relación con el conductor no se guarda en ningún lado y se pierde al cambiar la clave; mientras tanto, el mismo conductor mantiene el mismo recorrido aunque renueve su JWT.
- [ ] Cuando el motor informa una congestión, el backend registra un `EventoVial` de tipo Congestión, con origen Telemetría, sin reportante y con el nivel que informó el motor (RN-04.10, D-04).
- [ ] Cada congestión creada o extendida se avisa al motor con su nivel y su vencimiento, para que pese ×2,5 o ×4,0 según P-12 (D-14).
- [ ] Si ese tramo ya tiene una congestión vigente, se actualiza su momento (y su nivel, si cambió) en lugar de crear otra (RN-04.11).
- [ ] La congestión creada aparece en `GET /eventos` y pesa en las rutas como cualquier evento.
- [ ] El endpoint responde «recibido» en menos de 300 ms, sin esperar a que el motor termine de analizar.

**Tareas**
- [ ] Crear la migración que agrega el nivel a `evento_vial`.
- [ ] Implementar el identificador de recorrido con HMAC y la clave diaria en memoria.
- [ ] Implementar el servicio que entrega las lecturas al motor y procesa las congestiones que devuelve.
- [ ] Implementar la creación o actualización del evento de congestión.
- [ ] Implementar el controlador REST de telemetría.
- [ ] Escribir pruebas con el núcleo falso que devuelve una congestión.

**Casos de prueba**
- [ ] CP-119 Caso de prueba: cuando el motor informa congestión en un tramo, aparece un evento Congestión con origen Telemetría en `GET /eventos`.
- [ ] CP-120 Caso de prueba: una segunda congestión en el mismo tramo no crea otro evento; mueve el momento del existente.
- [ ] CP-121 Caso de prueba: después de procesar las lecturas, la base no tiene ninguna fila de telemetría.

**Relacionado:** RF-14 · RNF-08 · RNF-10 · RNF-13 · CU-04 · RN-04.4, RN-04.10 a RN-04.12 · D-04, D-07, D-14 · P-12

Lo subes en una nueva rama llamada "feature/HU13-CU04-java-telemetria" (No olvides que el source de la rama es la de develop)

---

## Sprint 6

### [HU-06][CU-04][Java] Revisar la ruta activa

**Caso de uso:** CU-04 — Recalcular Ruta
**Tecnología:** Java · **Sprint:** Sprint 6 (19/11–25/11)
**Depende de:** [HU-05][CU-03][Java] Calcular la ruta #65 · [HU-06][CU-04][C++] Revisar la ruta activa #76
**Etiquetas:** `caso-uso:CU-04` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como conductor navegando una ruta activa, quiero que el sistema me proponga una ruta alternativa cuando un evento vial afecte mi camino, para evitar demoras sin tener que buscarla yo.

**Criterios de aceptación**
- [ ] `POST /rutas/revision` recibe la posición actual, los tramos que faltan, el destino, las paradas que faltan y los eventos ya avisados en esta ruta (D-12, 05_contratos).
- [ ] Responde «sin cambios», «sin alternativa» o una ruta alternativa con su costo. Las dos últimas traen el evento que las provocó, para que la app lo agregue a los ya avisados (RN-04.14, RN-04.16, RN-04.19).
- [ ] Si el motor responde que los tramos ya no existen porque se recargó la red vial, responde «ruta desactualizada» y la app pide la ruta de nuevo.
- [ ] La alternativa parte de la posición actual y conserva el destino y las paradas que faltan (RN-04.15).
- [ ] Solo se propone si cuesta menos que seguir por la ruta actual con el tráfico al día (RN-04.16).
- [ ] Un evento ya avisado en esta ruta no vuelve a provocar una propuesta, porque la app lo envía en la lista (RN-04.19).
- [ ] El servidor no guarda la ruta (D-05).
- [ ] La respuesta llega en menos de 3 segundos.

**Tareas**
- [ ] Implementar el servicio del modelo que llama a `MotorDeRutas.revisarRuta`.
- [ ] Implementar el controlador REST de revisión.
- [ ] Escribir pruebas de los tres resultados posibles.

**Casos de prueba**
- [ ] CP-134 Caso de prueba: con un accidente nuevo sobre un tramo que falta, la respuesta trae una alternativa más barata.
- [ ] CP-135 Caso de prueba: si no hay camino mejor, la respuesta es «sin alternativa», con el evento que la provocó.
- [ ] CP-136 Caso de prueba: el mismo evento, enviado como ya avisado, devuelve «sin cambios».
- [ ] CP-137 Caso de prueba: con tramos que no existen, la respuesta es «ruta desactualizada».

**Relacionado:** RF-06 · CU-04 · RN-04.13 a RN-04.16, RN-04.19 · D-05, D-12

Lo subes en una nueva rama llamada "feature/HU06-CU04-java-revisar-ruta" (No olvides que el source de la rama es la de develop)

---

### [HU-09][CU-07][Java] Soporte para el simulador

**Caso de uso:** CU-07 — Ejecutar Simulador de Tráfico
**Tecnología:** Java · **Sprint:** Sprint 6 (19/11–25/11), al inicio, porque el simulador lo usa en el mismo sprint
**Depende de:** [HU-13][CU-04][Java] Recibir telemetría y registrar congestión #74
**Etiquetas:** `caso-uso:CU-07` · `tipo:historia-usuario` · `fase:construccion` · `tec:java`

**Historia de usuario:** Como desarrollador del equipo, quiero ejecutar un simulador que genere «usuarios fantasma» con eventos y telemetría, para validar el rendimiento y la estabilidad del sistema bajo carga antes de un despliegue.

Este issue cubre la parte del backend: recibir a los usuarios fantasma como conductores simulados y separar sus datos de los reales.

**Criterios de aceptación**
- [ ] `POST /sesion/prueba` recibe las credenciales de prueba y el identificador de la prueba de carga, y entrega el JWT de un conductor simulado solo si el ambiente tiene habilitadas las credenciales de prueba; en producción responde 403 (RN-07.4, RN-07.5, RN-08.13, P-20).
- [ ] Todo evento y voto de un conductor simulado queda marcado como simulado (RN-01.17, RN-02.16, RN-07.6).
- [ ] La telemetría de un conductor simulado viaja al motor con la marca de simulado, y la congestión que el motor informa como simulada se registra marcada (RN-04.20, RN-07.6).
- [ ] Los datos simulados pasan por las mismas reglas que los reales (RN-07.7).
- [ ] Un comando de mantenimiento, disponible solo donde las credenciales de prueba están habilitadas, borra todas las filas simuladas sin tocar una real (RN-07.8, RN-07.9).
- [ ] Un conductor real ve datos simulados solo si el ambiente está en modo demostración (RN-07.13).
- [ ] Cada prueba de carga queda en los registros del backend con su identificador: cuándo entró el primer usuario fantasma, cuántos entraron y cuándo llegó la última petición, incluida la saturación si ocurre (RN-07.10, RN-07.11).

**Tareas**
- [ ] Agregar la marca de simulado a `conductor`, `evento_vial` y `voto` con una migración.
- [ ] Implementar la sesión de prueba y su habilitación por configuración.
- [ ] Propagar la marca de simulado al crear eventos, votos y congestiones.
- [ ] Implementar el filtro del modo demostración en las consultas.
- [ ] Implementar el comando de borrado de datos simulados.
- [ ] Pasar la marca de simulado en las lecturas que van a `MotorDeRutas`.
- [ ] Agregar el registro de cada prueba de carga por su identificador.

**Casos de prueba**
- [ ] CP-141 Caso de prueba: en el perfil `prod`, `POST /sesion/prueba` responde 403.
- [ ] CP-142 Caso de prueba: un evento simulado no aparece a un conductor real fuera del modo demostración.
- [ ] CP-143 Caso de prueba: después del borrado no queda ninguna fila simulada y las reales siguen intactas.

**Relacionado:** RF-10 · RNF-12 · CU-07 · RN-01.17, RN-02.16, RN-04.20, RN-07.4 a RN-07.11, RN-07.13, RN-08.13 · P-20

Lo subes en una nueva rama llamada "feature/HU09-CU07-java-soporte-simulador" (No olvides que el source de la rama es la de develop)

---

### [T-105][Java] Limpieza de datos vencidos

**Tipo:** Tarea de desarrollo (privacidad y mantenimiento; cubre CU-02 y CU-05)
**Tecnología:** Java · **Sprint:** Sprint 6 (19/11–25/11)
**Depende de:** [HU-12][CU-05][Java] Registrar confirmaciones de recarga #72
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:java`

**Descripción:** Borrar lo que el sistema ya no necesita, como define [06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md): eventos vencidos o retirados, sus votos y las confirmaciones de recarga con más de 30 días, y los tokens de renovación vencidos o invalidados al cerrar sesión.

**Criterios de aceptación**
- [ ] Una tarea diaria borra los eventos vencidos o retirados hace más de 30 días, junto con sus votos (P-21).
- [ ] La misma tarea borra las confirmaciones de recarga de más de 30 días y los tokens de renovación vencidos o invalidados al cerrar sesión (RN-08.14).
- [ ] Nada vigente se borra.

**Tareas**
- [ ] Implementar la tarea programada.
- [ ] Escribir una prueba con datos de distintas fechas.

**Casos de prueba**
- [ ] CP-144 Caso de prueba: un evento vencido hace 31 días y sus votos se borran; uno vencido hace 29 días se conserva.

**Relacionado:** RNF-10 · RNF-17 · CU-02 · CU-05 · P-21 · [06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md)

Lo subes en una nueva rama llamada "feature/T105-java-limpieza-datos" (No olvides que el source de la rama es la de develop)

---

## Sprint 7

### [T-107][Java] Pruebas de punta a punta

**Tipo:** Tarea de pruebas (cubre los nueve casos de uso)
**Tecnología:** Java · **Sprint:** Sprint 7 (26/11–30/11)
**Depende de:** todos los issues de Java
**Etiquetas:** `tipo:prueba` · `fase:construccion` · `tec:java`

**Descripción:** Comprobar que el backend cumple el recorrido completo de cada caso de uso con el núcleo C++ real, y corregir lo que falle antes del cierre de la Construcción.

**Criterios de aceptación**
- [ ] Hay una prueba de integración por caso de uso que recorre su flujo principal contra el backend con la base real y el núcleo real.
- [ ] Todas las pruebas de Java pasan en la acción de GitHub.
- [ ] Cada fallo encontrado tiene su issue `tipo:bug` con el CP que lo detectó.

**Tareas**
- [ ] Escribir las pruebas de los flujos de CU-01 a CU-09; en el de CU-09, la consulta usa un servidor de ANH falso.
- [ ] Revisar que cada endpoint de `05_contratos.md` está implementado y documentado en OpenAPI.
- [ ] Abrir los issues de los fallos y corregir los que sean del backend.

**Casos de prueba**
- [ ] CP-149 Caso de prueba: un evento reportado por un conductor llega a otro en su siguiente consulta, en menos de 10 segundos (RNF-01).
- [ ] CP-150 Caso de prueba: un bloqueo reportado cambia la ruta calculada después entre los mismos puntos.
- [ ] CP-166 Caso de prueba: con un ANH falso que informa 0 litros de Gasolina en un surtidor vinculado, `GET /surtidores` lo muestra agotado con origen ANH; si después ANH responde 503, el dato anterior sigue ahí.

**Relacionado:** RNF-01 · RNF-02 · RNF-03 · CU-01 a CU-09

Lo subes en una nueva rama llamada "feature/T107-java-pruebas-punta-a-punta" (No olvides que el source de la rama es la de develop)
