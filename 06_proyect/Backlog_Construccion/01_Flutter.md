# Backlog de Construcción — App Flutter

[Índice del backlog](README.md)

Responsables: Alex, con Franz en los modos sin conexión (HU-02 y HU-04). Código en `04_imp/app_flutter`.

La app es la vista y el controlador de pantalla del MVC: muestra, captura toques y GPS, y habla con el backend. No decide reglas de negocio; la única que repite es la vigencia de un reporte que espera en la cola sin conexión, y lo hace con los tiempos que le da el backend ([02_mvc.md](../../03_des/02_Arquitectura/02_mvc.md), reglas de reparto). Antes de tomar un issue, lee la sección «App y simulador con el backend» de [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md) y las vistas de `02_mvc.md`.

Cuando el endpoint del backend todavía no está listo, la app avanza con respuestas falsas que siguen el contrato, y se conecta al backend real en cuanto se fusiona su issue.

| Sprint | Issues |
| --- | --- |
| S1 (15/10–21/10) | Estructura base de la app |
| S2 (22/10–28/10) | HU-10 Iniciar sesión con Google · Mapa base con Mapbox y ubicación |
| S3 (29/10–04/11) | HU-03 Ver los eventos en el mapa · HU-01 Reportar un evento vial |
| S4 (05/11–11/11) | HU-11 Lista de eventos y votos · HU-02 Guardar y reintentar reportes sin conexión · HU-04 Ver eventos guardados sin conexión |
| S5 (12/11–18/11) | HU-05 Cómo llegar y navegación activa · HU-13 Enviar telemetría con la app abierta · HU-07 Consultar surtidores cercanos |
| S6 (19/11–25/11) | HU-12 Preguntar por la recarga · HU-06 Aviso de ruta alternativa · Política de privacidad |

---

## Sprint 1

### [T-087][Flutter] Estructura base de la app

**Tipo:** Tarea de infraestructura (base para todos los issues de Flutter)
**Tecnología:** Flutter · **Sprint:** Sprint 1 (15/10–21/10) · **Responsable sugerido:** Alex
**Depende de:** nada
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:flutter`

**Descripción:** Crear el proyecto de la app con la separación entre vistas y controladores de pantalla de [02_mvc.md](../../03_des/02_Arquitectura/02_mvc.md), el cliente que habla con el backend y la navegación principal.

**Criterios de aceptación**
- [ ] La app compila y corre en Android desde `04_imp/app_flutter`; en iOS, si el equipo tiene una Mac para probarla.
- [ ] Las pantallas (vistas) no llaman al backend: lo hacen sus controladores, uno por pantalla o flujo.
- [ ] Está elegida y anotada en el README la forma de manejar el estado de las pantallas (Provider, Riverpod o BLoC).
- [ ] Un único cliente HTTP agrega el JWT a cada petición, entiende el cuerpo de error `{codigo, mensaje}` y muestra el mensaje en español que manda el backend.
- [ ] La dirección del backend se elige por ambiente (desarrollo, pruebas, producción) sin tocar el código.
- [ ] La barra inferior tiene Mapa, Eventos y Surtidores, cada una con una pantalla vacía por ahora.
- [ ] Toda la interfaz está en español (RNF-15).
- [ ] Las pruebas de widgets corren con un solo comando.

**Tareas**
- [ ] Crear el proyecto y la estructura de carpetas (vistas, controladores, servicios de API, almacenamiento local).
- [ ] Elegir la gestión de estado y escribir un ejemplo en una pantalla.
- [ ] Implementar el cliente HTTP con el manejo de errores del contrato.
- [ ] Configurar los ambientes.
- [ ] Armar la navegación con la barra inferior.
- [ ] Escribir el README: cómo correr, probar y cambiar de ambiente.

**Casos de prueba**
- [ ] CP-20 Caso de prueba: con el backend apagado, una petición muestra un mensaje de error de red sin cerrar la app.

**Relacionado:** RNF-08 · RNF-15 · [02_mvc.md](../../03_des/02_Arquitectura/02_mvc.md) · [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md) · D-01

Lo subes en una nueva rama llamada "feature/T87-flutter-estructura-base" (No olvides que el source de la rama es la de develop)

---

## Sprint 2

### [HU-10][CU-08][Flutter] Iniciar sesión con Google

**Caso de uso:** CU-08 — Iniciar Sesión
**Tecnología:** Flutter · **Sprint:** Sprint 2 (22/10–28/10) · **Responsable sugerido:** Alex
**Depende de:** [T-087][Flutter] Estructura base de la app #39 · [HU-10][CU-08][Java] Iniciar sesión con Google y emitir el JWT #46 · [T-094][Java] Catálogos para la app #47
**Etiquetas:** `caso-uso:CU-08` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero iniciar sesión con mi cuenta de Google, para acceder de forma rápida y segura sin gestionar una contraseña adicional.

**Criterios de aceptación**
- [ ] Sin sesión, la única vista disponible es Inicio de sesión, con el botón «Iniciar sesión con Google» (RN-08.1, RN-08.2).
- [ ] Al elegir la cuenta, la app envía el token de identidad a `POST /sesion/google` y abre el mapa con la sesión iniciada en menos de 3 segundos con red normal (RNF-05).
- [ ] Si el conductor cancela el selector o rechaza los permisos, vuelve a la vista de inicio sin sesión (RN-08.8).
- [ ] Si el backend rechaza el token, la app muestra el error y deja reintentar (RN-08.3).
- [ ] Si se pierde la conexión durante el inicio, avisa del error de red y deja reintentar (RN-08.9).
- [ ] El JWT y el token de renovación se guardan en el almacenamiento seguro del teléfono (RN-08.7).
- [ ] Con un token de renovación vigente guardado, la app entra directo al mapa, incluso sin conexión y con el JWT vencido; al volver la red, renueva el JWT (RN-08.10, D-21).
- [ ] Antes de que venza el JWT, la app lo renueva con `POST /sesion/renovar`; si la renovación falla o el backend responde 401, lleva al inicio de sesión y después reintenta lo que el conductor estaba haciendo (RN-08.11, RN-08.12).
- [ ] Al iniciar sesión, la app trae `GET /catalogos` y los guarda en el teléfono; las demás pantallas y la cola sin conexión toman de ahí los tipos, colores, vigencias, tiempos y valores de fila (D-06).
- [ ] «Cerrar sesión», en el menú de la cuenta del Mapa principal, llama a `POST /sesion/cerrar` y borra del teléfono el JWT, el token de renovación, la cola de reportes y los datos guardados ([06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md)).

**Tareas**
- [ ] Configurar el proyecto de Google Cloud y los Client ID de Android e iOS.
- [ ] Implementar la vista Inicio de sesión y su controlador de sesión con `google_sign_in`.
- [ ] Guardar los tokens con `flutter_secure_storage`.
- [ ] Implementar la entrada directa con sesión guardada.
- [ ] Implementar la renovación automática y el manejo del 401 en el cliente HTTP.
- [ ] Cargar y guardar los catálogos al iniciar sesión.
- [ ] Implementar «Cerrar sesión».
- [ ] Escribir pruebas del controlador de sesión con el backend simulado.

**Casos de prueba**
- [ ] CP-37 Caso de prueba: inicio de sesión con una cuenta de Google real abre el mapa en menos de 3 segundos.
- [ ] CP-38 Caso de prueba: cancelar el selector de cuentas deja al conductor en la vista de inicio.
- [ ] CP-39 Caso de prueba: con sesión vigente y el teléfono en modo avión, la app abre directo en el mapa.
- [ ] CP-40 Caso de prueba: después de cerrar sesión, la app vuelve a Inicio de sesión y no queda ningún token guardado.

**Relacionado:** RF-11 · RNF-05 · RNF-08 · CU-08 · RN-08.1 a RN-08.12, RN-08.14 · D-06, D-21 · P-05

Lo subes en una nueva rama llamada "feature/HU10-CU08-flutter-inicio-sesion" (No olvides que el source de la rama es la de develop)

---

### [T-093][Flutter] Mapa base con Mapbox y ubicación

**Tipo:** Tarea de desarrollo (base de CU-01 a CU-05)
**Tecnología:** Flutter · **Sprint:** Sprint 2 (22/10–28/10) · **Responsable sugerido:** Alex
**Depende de:** [T-087][Flutter] Estructura base de la app #39
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:flutter`

**Descripción:** Mostrar el mapa base de Mapbox en la vista Mapa principal y obtener la ubicación del conductor con su permiso. Mapbox solo da el mapa: las rutas las calcula el motor ([D-08](../../03_des/02_Arquitectura/08_decisiones_parametros.md)).

**Criterios de aceptación**
- [ ] La vista Mapa principal muestra Tarija con el SDK de Mapbox, y el token de Mapbox no está en el repositorio.
- [ ] La app pide el permiso de ubicación y muestra la posición del conductor en el mapa.
- [ ] La ubicación solo se obtiene con la app abierta en pantalla; al pasar a segundo plano se deja de pedir (RNF-13).
- [ ] Sin permiso de ubicación, el mapa y lo que no depende de la ubicación siguen funcionando (RN-04.3).
- [ ] Si el mapa base no carga, la app muestra un error de servicio externo y sugiere reintentar (RN-03.18).
- [ ] El mapa permite tocar un punto y devolver sus coordenadas, para el ajuste manual del reporte y para elegir destino.
- [ ] El mapa muestra la atribución de Mapbox y OpenStreetMap que piden sus licencias (RNF-19).

**Tareas**
- [ ] Crear la cuenta de Mapbox del proyecto y revisar los límites del uso gratuito.
- [ ] Integrar el SDK de Mapbox para Flutter con el token por configuración.
- [ ] Implementar el servicio de ubicación con `geolocator`, atado al ciclo de vida de la app.
- [ ] Implementar la selección de un punto en el mapa.

**Casos de prueba**
- [ ] CP-29 Caso de prueba: sin conexión a Mapbox, la app muestra el mensaje de error de servicio externo.
- [ ] CP-30 Caso de prueba: al minimizar la app, el ícono de ubicación del sistema operativo se apaga.

**Relacionado:** RNF-13 · RNF-19 · CU-02 · CU-03 · RN-03.18, RN-04.1, RN-04.3 · D-08

Lo subes en una nueva rama llamada "feature/T93-flutter-mapa-base" (No olvides que el source de la rama es la de develop)

---

## Sprint 3

### [HU-03][CU-02][Flutter] Ver los eventos en el mapa

**Caso de uso:** CU-02 — Visualizar Eventos Viales
**Tecnología:** Flutter · **Sprint:** Sprint 3 (29/10–04/11) · **Responsable sugerido:** Alex
**Depende de:** [T-093][Flutter] Mapa base con Mapbox y ubicación #45 · [HU-10][CU-08][Flutter] Iniciar sesión con Google #48 · [T-094][Java] Catálogos para la app #47 · [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos #55
**Etiquetas:** `caso-uso:CU-02` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero ver en el mapa los eventos viales activos reportados por otros conductores, para anticiparme a accidentes, controles, bloqueos o congestiones en mi ruta.

**Criterios de aceptación**
- [ ] Con el mapa abierto, la app consulta `GET /eventos` cada 5 segundos, aunque el conductor se desplace o haga zoom, y pide solo los cambios desde su última consulta (RN-02.6).
- [ ] Cada evento se dibuja con el color de su tipo, tomado de los catálogos guardados (RN-02.7), y los confirmados llevan su marca (RN-02.14).
- [ ] Al tocar un evento se ven su tipo y hace cuánto ocurrió (RN-02.7).
- [ ] Un evento que el backend informa como retirado o vencido desaparece del mapa en la siguiente actualización (RN-02.4, RN-02.13).
- [ ] Si el backend no responde o tarda, el mapa conserva lo último que mostró, avisa «No se pudo actualizar eventos» y reintenta en el siguiente ciclo (RN-02.8).
- [ ] La consulta se detiene con la app en segundo plano y no traba el mapa (HU-03, rendimiento).

**Tareas**
- [ ] Implementar el controlador de mapa con el ciclo de consulta de 5 segundos.
- [ ] Dibujar los eventos con sus colores e íconos, y la ficha de detalle.
- [ ] Implementar el aviso de error de actualización.
- [ ] Escribir pruebas del controlador con respuestas falsas.

**Casos de prueba**
- [ ] CP-65 Caso de prueba: un evento reportado desde otro teléfono aparece en el mapa en menos de 10 segundos (RNF-01).
- [ ] CP-66 Caso de prueba: con el backend apagado, el mapa conserva los eventos y muestra «No se pudo actualizar eventos».
- [ ] CP-67 Caso de prueba: un evento que vence mientras se mira desaparece en la siguiente actualización.

**Relacionado:** RF-03 · RNF-01 · RNF-10 · CU-02 · RN-02.4, RN-02.6 a RN-02.8, RN-02.13, RN-02.14

Lo subes en una nueva rama llamada "feature/HU03-CU02-flutter-ver-eventos" (No olvides que el source de la rama es la de develop)

---

### [HU-01][CU-01][Flutter] Reportar un evento vial

**Caso de uso:** CU-01 — Reportar Evento Vial
**Tecnología:** Flutter · **Sprint:** Sprint 3 (29/10–04/11) · **Responsable sugerido:** Alex
**Depende de:** [T-093][Flutter] Mapa base con Mapbox y ubicación #45 · [HU-10][CU-08][Flutter] Iniciar sesión con Google #48 · [HU-01][CU-01][Java] Registrar reportes de eventos viales #53
**Etiquetas:** `caso-uso:CU-01` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero reportar un evento vial (accidente, control policial, bloqueo u otro) desde el mapa, para que otros conductores lo vean y puedan evitarlo.

**Criterios de aceptación**
- [ ] Desde el mapa, el reporte se completa en 3 toques: «Reportar evento», el tipo y confirmar (RNF-06).
- [ ] La vista Reportar evento ofrece los tipos reportables del catálogo: accidente, control policial, bloqueo y otro; la congestión no aparece (RN-01.3).
- [ ] Los tiempos son «Ahora mismo», «Hace 5 minutos» (marcado), «Hace 10 minutos» y «Hace 30 minutos» (RN-01.4, D-09).
- [ ] Al confirmar, la app toma la ubicación del GPS; si el GPS no responde, deja ajustar el punto en el mapa antes de enviar (RN-01.5).
- [ ] La app envía tipo, ubicación, hora de confirmación y minutos elegidos; nunca calcula el momento del evento por su cuenta (05_contratos).
- [ ] Muestra la confirmación, o el mensaje del backend cuando el reporte es duplicado, supera el límite, está fuera de Tarija o ya venció.
- [ ] Si el JWT venció, conserva el reporte y renueva la sesión sin que el conductor intervenga; solo si no puede renovarla, pide iniciar sesión. Después reintenta el envío (RN-01.16).
- [ ] Sin conexión, la app avisa que no hay red y conserva lo que el conductor eligió en la pantalla. Guardarlo en la cola es parte de HU-02.

**Tareas**
- [ ] Implementar la vista Reportar evento y su controlador.
- [ ] Implementar la captura de la ubicación con el ajuste manual de respaldo.
- [ ] Mostrar los rechazos del backend con su mensaje.
- [ ] Implementar la renovación de la sesión y el reintento del envío.
- [ ] Escribir pruebas del controlador.

**Casos de prueba**
- [ ] CP-57 Caso de prueba: reportar un control policial con «Hace 5 minutos» toma 3 toques y aparece en el mapa de otro teléfono.
- [ ] CP-58 Caso de prueba: con el GPS apagado, el conductor ajusta el punto en el mapa y el reporte se envía con ese punto.
- [ ] CP-59 Caso de prueba: con el JWT vencido y el token de renovación vigente, el reporte se envía sin pedir iniciar sesión.
- [ ] CP-60 Caso de prueba: con el token de renovación también vencido, la app pide iniciar sesión y el reporte se envía después sin volver a llenarlo.

**Relacionado:** RF-01 · RNF-06 · RNF-08 · CU-01 · RN-01.3 a RN-01.5, RN-01.16 · D-09, D-10

Lo subes en una nueva rama llamada "feature/HU01-CU01-flutter-reportar-evento" (No olvides que el source de la rama es la de develop)

---

## Sprint 4

### [HU-11][CU-02][Flutter] Lista de eventos y votos

**Caso de uso:** CU-02 — Visualizar Eventos Viales
**Tecnología:** Flutter · **Sprint:** Sprint 4 (05/11–11/11) · **Responsable sugerido:** Alex
**Depende de:** [HU-03][CU-02][Flutter] Ver los eventos en el mapa #56 · [HU-11][CU-02][Java] Votar eventos #63
**Etiquetas:** `caso-uso:CU-02` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero ver la lista de eventos activos y votar si cada uno es real o falso, para que el mapa muestre solo eventos confiables.

**Criterios de aceptación**
- [ ] La pestaña Eventos muestra los eventos vigentes con su tipo y hace cuánto ocurrieron, con los mismos datos que el mapa (HU-11 c.1).
- [ ] Votar «real» o «falso» toma un solo toque (HU-11, usabilidad).
- [ ] Las congestiones registradas por telemetría aparecen en la lista sin botones de voto (RN-02.10, D-20).
- [ ] Después de votar, los botones de ese evento quedan desactivados; si el backend responde que ya votó o que es su propio evento, la app muestra el mensaje (RN-02.11, RN-02.12).
- [ ] Un evento retirado desaparece de la lista en la siguiente actualización, y uno confirmado muestra su marca (RN-02.13, RN-02.14).

**Tareas**
- [ ] Implementar la vista Lista de eventos y su controlador, compartiendo los datos con el mapa.
- [ ] Implementar el envío del voto y sus mensajes.
- [ ] Escribir pruebas del controlador.

**Casos de prueba**
- [ ] CP-90 Caso de prueba: tres teléfonos votan «falso» el mismo evento y desaparece de la lista y del mapa de un cuarto.
- [ ] CP-91 Caso de prueba: un segundo voto sobre el mismo evento muestra el mensaje del backend y no cambia nada.

**Relacionado:** RF-12 · RNF-09 · CU-02 · RN-02.10 a RN-02.14 · D-20

Lo subes en una nueva rama llamada "feature/HU11-CU02-flutter-votos" (No olvides que el source de la rama es la de develop)

---

### [HU-02][CU-01][Flutter] Guardar y reintentar reportes sin conexión

**Caso de uso:** CU-01 — Reportar Evento Vial
**Tecnología:** Flutter · **Sprint:** Sprint 4 (05/11–11/11) · **Responsable sugerido:** Franz
**Depende de:** [HU-01][CU-01][Flutter] Reportar un evento vial #54
**Etiquetas:** `caso-uso:CU-01` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero que mi reporte se guarde localmente cuando no tengo conexión a internet, para no perder la información y que se envíe automáticamente al recuperar la conexión.

**Criterios de aceptación**
- [ ] Sin conexión, el reporte confirmado se guarda en el teléfono con su hora de confirmación y sus minutos (RN-01.13).
- [ ] Al volver la red, la app reenvía los reportes guardados sin intervención del conductor y le avisa cuando se enviaron (RN-01.13).
- [ ] Antes de reenviar, comprueba la vigencia con los tiempos del catálogo guardado al iniciar sesión; si ya venció, lo saca de la cola y avisa que no se envió por haber perdido vigencia (RN-01.14).
- [ ] Si el reenvío falla varias veces seguidas, el reporte sigue en la cola y se muestra como pendiente; nunca se descarta en silencio (RN-01.15).
- [ ] La cola sobrevive a cerrar y abrir la app.

**Tareas**
- [ ] Implementar la cola local con `sqflite`.
- [ ] Detectar la vuelta de la conexión.
- [ ] Implementar el reenvío con la comprobación de vigencia y los reintentos.
- [ ] Mostrar los reportes pendientes y los avisos.
- [ ] Escribir pruebas de la cola con el reloj simulado.

**Casos de prueba**
- [ ] CP-81 Caso de prueba: en modo avión se reporta un accidente; al quitar el modo avión se envía solo y aparece el aviso.
- [ ] CP-82 Caso de prueba: un control policial guardado hace 70 minutos no se envía y se avisa que perdió vigencia.
- [ ] CP-83 Caso de prueba: con el backend caído, el reporte queda como pendiente después de varios intentos.

**Relacionado:** RF-02 · CU-01 · RN-01.13 a RN-01.15 · D-06

Lo subes en una nueva rama llamada "feature/HU02-CU01-flutter-cola-sin-conexion" (No olvides que el source de la rama es la de develop)

---

### [HU-04][CU-02][Flutter] Ver eventos guardados sin conexión

**Caso de uso:** CU-02 — Visualizar Eventos Viales
**Tecnología:** Flutter · **Sprint:** Sprint 4 (05/11–11/11) · **Responsable sugerido:** Franz
**Depende de:** [HU-03][CU-02][Flutter] Ver los eventos en el mapa #56
**Etiquetas:** `caso-uso:CU-02` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero seguir viendo los últimos eventos conocidos cuando no tengo conexión a internet, para no quedarme sin información aunque esté desactualizada.

**Criterios de aceptación**
- [ ] Cada consulta exitosa guarda en el teléfono los eventos recibidos; los catálogos ya los guarda el inicio de sesión.
- [ ] Al abrir la app sin conexión, el mapa y la lista muestran los últimos eventos guardados (RN-02.9).
- [ ] Mientras se muestran datos guardados, una marca visible avisa que pueden estar desactualizados (RN-02.9).
- [ ] Al volver la red, la app consulta al backend y reemplaza lo guardado por los eventos vigentes (RN-02.9).

**Tareas**
- [ ] Implementar la caché local de eventos.
- [ ] Implementar la marca de datos desactualizados.
- [ ] Escribir pruebas del controlador de mapa sin conexión.

**Casos de prueba**
- [ ] CP-84 Caso de prueba: se cierra la app con 3 eventos en el mapa, se abre en modo avión y se ven los 3 con la marca de desactualizados.
- [ ] CP-85 Caso de prueba: al volver la red, la marca desaparece y el mapa muestra los vigentes.

**Relacionado:** RF-04 · CU-02 · RN-02.9 · RN-08.10

Lo subes en una nueva rama llamada "feature/HU04-CU02-flutter-eventos-sin-conexion" (No olvides que el source de la rama es la de develop)

---

## Sprint 5

### [HU-05][CU-03][Flutter] Cómo llegar y navegación activa

**Caso de uso:** CU-03 — Calcular Ruta Óptima
**Tecnología:** Flutter · **Sprint:** Sprint 5 (12/11–18/11) · **Responsable sugerido:** Alex
**Depende de:** [T-093][Flutter] Mapa base con Mapbox y ubicación #45 · [HU-05][CU-03][Java] Calcular la ruta #65 · [HU-07][CU-05][Java] Consultar surtidores cercanos #69 (para elegir un surtidor como destino)
**Etiquetas:** `caso-uso:CU-03` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero calcular la ruta vehicular óptima hacia mi destino, para llegar por el camino más eficiente considerando datos viales vigentes.

**Criterios de aceptación**
- [ ] La vista Cómo llegar toma la posición actual como origen, o un punto que el conductor marca, y deja elegir el destino tocando el mapa o eligiendo un surtidor del catálogo; no hay buscador de lugares (P-19, D-24).
- [ ] Se pueden agregar paradas intermedias y su orden se respeta (RN-03.2).
- [ ] La ruta se dibuja con su tiempo y su distancia, visibles antes de confirmar (RN-03.13).
- [ ] Un punto inválido muestra «Origen o destino inválido» y pide otro; «No se encontró una ruta válida» se muestra como respuesta, no como error (RN-03.3, RN-03.12).
- [ ] Si el backend responde 503, la app informa el fallo y deja reintentar sin quedar a medias (RN-03.15).
- [ ] Al tocar «Iniciar», la ruta pasa a Activa y se abre la vista Navegación activa, con la posición del conductor, la calle que sigue con el sentido del giro (izquierda o derecha), que la app calcula con el trazado y los nombres de calle de la ruta, y la distancia y el tiempo que faltan (RN-03.17, D-22).
- [ ] Al llegar al destino, a menos de 30 m, o al tocar «Cancelar», la ruta pasa a Terminada y se borra del teléfono; nunca se guarda en el servidor (D-05).

**Tareas**
- [ ] Implementar la vista Cómo llegar y su controlador de ruta.
- [ ] Implementar la elección de destino por mapa y por surtidor.
- [ ] Dibujar la ruta con Mapbox.
- [ ] Implementar la vista Navegación activa y su controlador, que guarda los tramos de la ruta para revisarla después (D-12).
- [ ] Escribir pruebas de los controladores con respuestas falsas.

**Casos de prueba**
- [ ] CP-105 Caso de prueba: de la posición actual a un punto del mapa, la ruta aparece con tiempo y distancia en menos de 3 segundos.
- [ ] CP-106 Caso de prueba: un destino fuera de Tarija muestra «Origen o destino inválido».
- [ ] CP-107 Caso de prueba: al tocar «Iniciar», la vista Navegación activa sigue la posición del conductor.

**Relacionado:** RF-05 · RNF-02 · CU-03 · RN-03.2, RN-03.3, RN-03.12, RN-03.13, RN-03.15, RN-03.17, RN-03.18 · D-05, D-12, D-22, D-24 · P-19

Lo subes en una nueva rama llamada "feature/HU05-CU03-flutter-como-llegar" (No olvides que el source de la rama es la de develop)

---

### [HU-13][CU-04][Flutter] Enviar telemetría con la app abierta

**Caso de uso:** CU-04 — Recalcular Ruta
**Tecnología:** Flutter · **Sprint:** Sprint 5 (12/11–18/11) · **Responsable sugerido:** Alex
**Depende de:** [T-093][Flutter] Mapa base con Mapbox y ubicación #45 · [HU-13][CU-04][Java] Recibir telemetría y registrar congestión #74
**Etiquetas:** `caso-uso:CU-04` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como sistema, quiero recibir la posición de los conductores mientras tienen la app abierta, para calcular su velocidad y detectar congestión sin depender solo de los reportes manuales.

**Criterios de aceptación**
- [ ] Con la app en pantalla, con sesión y con permiso de ubicación, la app lee el GPS cada 3 segundos y envía las lecturas agrupadas cada 15 segundos a `POST /telemetria` (P-13).
- [ ] Envía telemetría tenga o no una ruta activa (RN-04.2).
- [ ] Al pasar a segundo plano o cerrarse, deja de leer y de enviar (RN-04.1, RNF-13).
- [ ] Sin permiso de ubicación no envía nada y el resto de la app sigue funcionando (RN-04.3).
- [ ] Cada lectura lleva posición, precisión y momento.
- [ ] En una prueba de 30 minutos con la app abierta en el mismo teléfono, la batería baja menos que con Google Maps navegando, y el mapa no se traba (RNF-18).

**Tareas**
- [ ] Implementar el controlador de telemetría atado al ciclo de vida de la app.
- [ ] Implementar el agrupado y el envío, descartando los grupos que no se pudieron enviar.
- [ ] Medir el consumo de batería en una prueba de 30 minutos, comparado con Google Maps.

**Casos de prueba**
- [ ] CP-122 Caso de prueba: con la app abierta 1 minuto llegan 4 envíos al backend; con la app minimizada, ninguno.
- [ ] CP-123 Caso de prueba: al revocar el permiso de ubicación, la telemetría se detiene y el mapa sigue funcionando.

**Relacionado:** RF-14 · RNF-13 · RNF-18 · CU-04 · RN-04.1 a RN-04.3 · P-13

Lo subes en una nueva rama llamada "feature/HU13-CU04-flutter-telemetria" (No olvides que el source de la rama es la de develop)

---

### [HU-07][CU-05][Flutter] Consultar surtidores cercanos

**Caso de uso:** CU-05 — Consultar Disponibilidad de Combustible
**Tecnología:** Flutter · **Sprint:** Sprint 5 (12/11–18/11) · **Responsable sugerido:** Alex, con Franz si hace falta
**Depende de:** [T-093][Flutter] Mapa base con Mapbox y ubicación #45 · [HU-07][CU-05][Java] Consultar surtidores cercanos #69
**Etiquetas:** `caso-uso:CU-05` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero consultar el estado de disponibilidad de combustible en surtidores cercanos, para evitar dirigirme a uno que esté agotado.

**Criterios de aceptación**
- [ ] La pestaña Surtidores pide `GET /surtidores` con la ubicación actual y muestra la lista del más cercano al más lejano (RN-05.2).
- [ ] Mientras la pestaña sigue abierta, la lista se vuelve a pedir cada 60 segundos (RN-05.17, P-27).
- [ ] Los surtidores también aparecen como marcadores en el Mapa principal, y al tocar uno se ve su estado. Con el mapa abierto, los marcadores se actualizan cada 60 segundos, igual que la lista (RN-05.17, D-25).
- [ ] Cada surtidor muestra su estado por tipo de combustible, la fila si se conoce, el origen del dato y su fecha (RN-05.4).
- [ ] El origen WhatsApp y el origen Conductores se distinguen a simple vista (RNF-07).
- [ ] Un surtidor sin datos vigentes muestra «sin información reciente» y un dato sin tipo muestra «Desconocido» (RN-05.5, RN-05.6).
- [ ] Sin permiso de ubicación, la app explica que lo necesita para ordenar por cercanía y deja concederlo.
- [ ] La lista aparece en menos de 3 segundos (RNF-03).

**Tareas**
- [ ] Implementar la vista Surtidores y su controlador de combustible.
- [ ] Diseñar la presentación del origen del dato.
- [ ] Escribir pruebas del controlador con respuestas falsas.

**Casos de prueba**
- [ ] CP-108 Caso de prueba: un surtidor con un aviso de WhatsApp de hace 10 minutos muestra el estado, el origen WhatsApp y la hora.
- [ ] CP-109 Caso de prueba: un surtidor sin datos muestra «sin información reciente».
- [ ] CP-110 Caso de prueba: con el mapa abierto, un dato nuevo de un surtidor cambia su marcador en menos de 2 minutos, sin tocar la pantalla.

**Relacionado:** RF-07 · RNF-03 · RNF-04 · RNF-07 · CU-05 · RN-05.2 a RN-05.6, RN-05.17 · D-23, D-25 · P-27

Lo subes en una nueva rama llamada "feature/HU07-CU05-flutter-surtidores" (No olvides que el source de la rama es la de develop)

---

## Sprint 6

### [HU-12][CU-05][Flutter] Preguntar por la recarga

**Caso de uso:** CU-05 — Consultar Disponibilidad de Combustible
**Tecnología:** Flutter · **Sprint:** Sprint 6 (19/11–25/11) · **Responsable sugerido:** Alex
**Depende de:** [HU-07][CU-05][Flutter] Consultar surtidores cercanos #71 · [HU-12][CU-05][Java] Registrar confirmaciones de recarga #72
**Etiquetas:** `caso-uso:CU-05` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor de la app, quiero indicar que acabo de recargar combustible y de qué tipo, para que los demás conductores sepan que ese surtidor tiene ese combustible.

**Criterios de aceptación**
- [ ] Con la app abierta, si el conductor está detenido 3 minutos a menos de 50 m de un surtidor del catálogo, aparece «¿Acabas de recargar combustible?» (RN-05.8, D-11, P-09).
- [ ] Si responde «Sí», elige entre Gasolina, Diesel, Gasolina Premium y Diesel ULS; Desconocido no aparece (RN-05.9).
- [ ] Si responde «No» o cierra cualquiera de las dos preguntas, no se envía nada (RN-05.10), y la pregunta no vuelve a aparecer mientras siga detenido junto a ese surtidor.
- [ ] La respuesta toma dos toques como máximo y la pregunta no tapa la navegación ni la lista de surtidores (RN-05.16).
- [ ] La app envía el surtidor, el tipo y la ubicación donde detectó la parada a `POST /surtidores/{id}/confirmaciones`, aunque el conductor ya haya arrancado, y muestra la confirmación o el rechazo.
- [ ] Después de confirmar, o si el backend responde que es repetida, la app no vuelve a preguntar por ese surtidor durante 60 minutos (RN-05.14, P-14).

**Tareas**
- [ ] Implementar la detección de «detenido junto a un surtidor» con la ubicación que ya obtiene la app.
- [ ] Implementar la vista Pregunta de recarga, no intrusiva.
- [ ] Implementar el envío y el silencio de 60 minutos por surtidor.
- [ ] Escribir pruebas del controlador con ubicaciones simuladas.

**Casos de prueba**
- [ ] CP-130 Caso de prueba: con una ubicación simulada detenida 3 minutos junto a un surtidor, aparece la pregunta; al elegir Diesel, el surtidor muestra Diesel disponible con origen Conductores.
- [ ] CP-131 Caso de prueba: al responder «No», no se envía nada al backend y la pregunta no reaparece mientras el conductor siga detenido allí.
- [ ] CP-132 Caso de prueba: 10 minutos después, en el mismo surtidor, la pregunta no vuelve a aparecer.

**Relacionado:** RF-13 · RNF-09 · CU-05 · RN-05.8 a RN-05.10, RN-05.14, RN-05.16 · D-11 · P-09, P-14

Lo subes en una nueva rama llamada "feature/HU12-CU05-flutter-pregunta-recarga" (No olvides que el source de la rama es la de develop)

---

### [HU-06][CU-04][Flutter] Aviso de ruta alternativa

**Caso de uso:** CU-04 — Recalcular Ruta
**Tecnología:** Flutter · **Sprint:** Sprint 6 (19/11–25/11) · **Responsable sugerido:** Alex
**Depende de:** [HU-05][CU-03][Flutter] Cómo llegar y navegación activa #70 · [HU-06][CU-04][Java] Revisar la ruta activa #82
**Etiquetas:** `caso-uso:CU-04` · `tipo:historia-usuario` · `fase:construccion` · `tec:flutter`

**Historia de usuario:** Como conductor navegando una ruta activa, quiero que el sistema me proponga una ruta alternativa cuando un evento vial afecte mi camino, para evitar demoras sin tener que buscarla yo.

**Criterios de aceptación**
- [ ] Mientras la ruta está Activa, junto con su consulta de cada 5 segundos, la app envía a `POST /rutas/revision` su posición, los tramos que faltan, el destino, las paradas que faltan y los eventos ya avisados (RN-04.13, D-12).
- [ ] Si llega una alternativa, se muestra sin interrumpir la navegación, con «Aceptar» y «Rechazar» (RN-04.17).
- [ ] Si el conductor no responde en 15 segundos, el aviso se cierra solo, sigue la ruta original y el evento queda como ya avisado (RN-04.21, P-26).
- [ ] Al aceptar, la alternativa pasa a ser la ruta Activa; al rechazar, sigue la original con el evento a la vista (RN-04.18).
- [ ] Si la respuesta es «sin alternativa», la app avisa del evento que afecta la ruta, la mantiene y agrega el evento a los ya avisados (RN-04.16, RN-04.19).
- [ ] Si la respuesta es «ruta desactualizada», la app pide la ruta de nuevo desde la posición actual, con el mismo destino y las paradas que faltan.
- [ ] Si la revisión falla (sin red o con 503), la app sigue con la ruta actual sin mostrar un error y vuelve a intentar en el siguiente ciclo.
- [ ] Un mismo evento produce un solo aviso en la ruta, se acepte o se rechace (RN-04.19).
- [ ] La revisión corre fuera del hilo de la interfaz: con el perfilador de Flutter, el mapa no pierde cuadros mientras espera la respuesta (HU-06, rendimiento).

**Tareas**
- [ ] Agregar la revisión al ciclo del controlador de navegación.
- [ ] Implementar el aviso no intrusivo con sus dos botones.
- [ ] Guardar los eventos ya avisados de la ruta activa.
- [ ] Escribir pruebas del controlador con respuestas falsas.

**Casos de prueba**
- [ ] CP-138 Caso de prueba: durante una navegación, se reporta un accidente desde otro teléfono sobre la ruta y aparece el aviso de alternativa.
- [ ] CP-139 Caso de prueba: al rechazar la alternativa, la ruta sigue igual, con el evento a la vista, y el aviso no vuelve.
- [ ] CP-140 Caso de prueba: si el conductor no toca el aviso, a los 15 segundos se cierra, la ruta sigue igual y el aviso no vuelve.

**Relacionado:** RF-06 · CU-04 · RN-04.13, RN-04.16 a RN-04.19, RN-04.21 · D-12, D-19 · P-26

Lo subes en una nueva rama llamada "feature/HU06-CU04-flutter-ruta-alternativa" (No olvides que el source de la rama es la de develop)

---

### [T-104][Flutter] Política de privacidad

**Tipo:** Tarea de documentación (cubre CU-04, CU-06 y CU-08)
**Tecnología:** Flutter · **Sprint:** Sprint 6 (19/11–25/11) · **Responsable sugerido:** Franz
**Depende de:** [T-097][Servidor] Dominio, Nginx y HTTPS #52 · [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado #59 · [HU-13][CU-04][Flutter] Enviar telemetría con la app abierta #75
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:flutter`

**Descripción:** Redactar la política de privacidad que pide [07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md) y enlazarla desde la app. Las tiendas de aplicaciones también la piden por el permiso de ubicación.

**Criterios de aceptación**
- [ ] La política dice qué datos se recogen y para qué: la cuenta de Google, la ubicación solo con la app abierta, la telemetría sin datos del conductor, y los reportes, votos y confirmaciones, que nunca se muestran con su autor (RNF-10, RNF-13).
- [ ] Dice cuánto se guarda cada dato, según [06_base_de_datos.md](../../03_des/02_Arquitectura/06_base_de_datos.md).
- [ ] Dice que el texto y la fecha de los mensajes de los grupos se envían al proveedor del LLM, y que en la capa gratuita de Gemini ese proveedor puede usarlos para mejorar sus productos y que pueden leerlos revisores humanos (Visión 6.3, D-17).
- [ ] Está publicada en una página del dominio del proyecto y se puede abrir desde la vista Inicio de sesión, antes de entrar.
- [ ] Está escrita en español claro, sin lenguaje legal innecesario.

**Tareas**
- [ ] Redactar la política con lo que dicen `06_base_de_datos.md` y `07_seguridad_privacidad.md`.
- [ ] Revisarla con todo el equipo.
- [ ] Publicarla en el dominio (Nginx) y agregar el enlace en la app.

**Casos de prueba**
- [ ] CP-133 Caso de prueba: desde la vista Inicio de sesión, sin cuenta, se abre la política y menciona al proveedor del LLM.

**Relacionado:** RNF-10 · RNF-11 · RNF-13 · RNF-16 · RNF-17 · Visión 6.3 · D-17 · [07_seguridad_privacidad.md](../../03_des/02_Arquitectura/07_seguridad_privacidad.md)

Lo subes en una nueva rama llamada "feature/T104-flutter-politica-privacidad" (No olvides que el source de la rama es la de develop)
