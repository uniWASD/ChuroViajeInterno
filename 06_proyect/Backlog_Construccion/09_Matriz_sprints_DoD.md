# Matriz de desarrollo y pruebas por sprint

Qué se entrega en cada sprint de la Construcción (4 a 7), quién lo hace, qué criterios debe cumplir y qué evidencia se espera de cada caso de prueba. Sale de `MatrizSprint_DoD.xlsx`. La columna «Validación» se llena a mano en la copia impresa, cuando el caso de prueba pasa con su evidencia.

La evidencia esperada de cada caso de prueba también está anotada en su issue, junto al caso.

## Definition of Done

Un issue está terminado cuando cumple las tres condiciones:

1. Criterios de aceptación: todos están marcados en GitHub. Si uno no se cumple, se aclara en el issue. Nunca se marca a medias.
2. Casos de prueba: cada CP pasa y tiene su evidencia esperada adjunta en el PR o en un comentario, según el tipo:
   - salida de la CI para C++ y Java;
   - video o captura para Flutter;
   - salida del comando para el servidor.
3. Código integrado:
   - el trabajo está en su rama feature/…, con un PR hacia develop;
   - la CI está en verde;
   - Marcelo revisó el PR.

## Sprint 4 (05/11 – 11/11)

10 issues · 35 puntos.

| Issue | Responsable | Casos de prueba |
| --- | --- | --- |
| [HU-01][CU-01][Java] Registrar reportes de eventos viales ([#53](https://github.com/uniWASD/ChuroViajeInterno/issues/53)) | Jorge | CP-52, CP-53, CP-54, CP-55, CP-56 |
| [HU-01][CU-01][Flutter] Reportar un evento vial ([#54](https://github.com/uniWASD/ChuroViajeInterno/issues/54)) | Franz | CP-57, CP-58, CP-59, CP-60 |
| [HU-02][CU-01][Flutter] Guardar y reintentar reportes sin conexión ([#61](https://github.com/uniWASD/ChuroViajeInterno/issues/61)) | Franz | CP-81, CP-82, CP-83 |
| [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos ([#55](https://github.com/uniWASD/ChuroViajeInterno/issues/55)) | Antonio | CP-61, CP-62, CP-63, CP-64 |
| [HU-03][CU-02][Flutter] Ver los eventos en el mapa ([#56](https://github.com/uniWASD/ChuroViajeInterno/issues/56)) | Alex | CP-65, CP-66, CP-67 |
| [T-099][Java] Mantener al día el tráfico del motor ([#67](https://github.com/uniWASD/ChuroViajeInterno/issues/67)) | Jorge | CP-99, CP-100 |
| [HU-10][CU-08][Flutter] Iniciar sesión con Google ([#48](https://github.com/uniWASD/ChuroViajeInterno/issues/48)) | Alex | CP-37, CP-38, CP-39, CP-40 |
| [HU-11][CU-02][Java] Votar eventos ([#63](https://github.com/uniWASD/ChuroViajeInterno/issues/63)) | Antonio | CP-86, CP-87, CP-88, CP-89 |
| [T-098][Servidor] Desplegar el backend con el núcleo C++ ([#60](https://github.com/uniWASD/ChuroViajeInterno/issues/60)) | Marcelo | CP-79, CP-80 |
| [T-097][Servidor] Dominio, Nginx y HTTPS ([#52](https://github.com/uniWASD/ChuroViajeInterno/issues/52)) | Marcelo | CP-51 |

### [HU-01][CU-01][Java] Registrar reportes de eventos viales · [#53](https://github.com/uniWASD/ChuroViajeInterno/issues/53)

**Responsable:** Jorge · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-01: Como conductor de la app, quiero reportar un evento vial desde el mapa, para que otros conductores lo vean y puedan evitarlo.
- RNF-01: Los eventos reportados deberán aparecer en el mapa de los demás conductores en 5 a 10 segundos como máximo.
- RNF-06: Reportar un evento deberá tomar como máximo 3 toques desde el mapa.
- RNF-08: Solo los conductores que iniciaron sesión con su cuenta de Google podrán usar la app. El simulador y el servicio de WhatsApp entran con credenciales propias.
- RNF-09: El sistema deberá evitar que un mismo usuario envíe reportes, votos o confirmaciones repetidos o abusivos.
- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.

**Criterios de aceptación**

- Registra el evento con tipo, ubicación y hace cuánto ocurrió (5 minutos si no se indica)
- Rechaza datos incompletos, congestión, puntos fuera de Tarija y eventos ya vencidos
- Rechaza duplicados (mismo tipo, a menos de 100 m, en 10 min) y más de 5 reportes en 10 min
- El evento nace Activo y nunca muestra quién lo reportó

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-52** un accidente «hace 5 minutos» dentro de Tarija se registra Activo y el momento en que ocurrió es 5 minutos antes de la confirmación | Prueba automatizada y su salida en la CI |  |
| **CP-53** un reporte sin ubicación responde 400 y no se guarda nada | Prueba automatizada y su salida en la CI |  |
| **CP-54** el mismo conductor reporta dos controles policiales a 50 m en 3 minutos; el segundo responde 409 | Prueba automatizada y su salida en la CI |  |
| **CP-55** el sexto reporte en 10 minutos responde 429 | Prueba automatizada y su salida en la CI |  |
| **CP-56** un reporte de «congestión» responde 422 | Prueba automatizada y su salida en la CI |  |

### [HU-01][CU-01][Flutter] Reportar un evento vial · [#54](https://github.com/uniWASD/ChuroViajeInterno/issues/54)

**Responsable:** Franz · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-01: Como conductor de la app, quiero reportar un evento vial desde el mapa, para que otros conductores lo vean y puedan evitarlo.
- RNF-01: Los eventos reportados deberán aparecer en el mapa de los demás conductores en 5 a 10 segundos como máximo.
- RNF-06: Reportar un evento deberá tomar como máximo 3 toques desde el mapa.
- RNF-08: Solo los conductores que iniciaron sesión con su cuenta de Google podrán usar la app. El simulador y el servicio de WhatsApp entran con credenciales propias.
- RNF-09: El sistema deberá evitar que un mismo usuario envíe reportes, votos o confirmaciones repetidos o abusivos.

**Criterios de aceptación**

- Se reporta en 3 toques desde el mapa: accidente, control, bloqueo u otro
- Toma la ubicación del GPS o deja ajustarla en el mapa
- Muestra la confirmación o el motivo del rechazo
- Si la sesión venció, la renueva sola y reintenta sin perder el reporte

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-57** reportar un control policial con «Hace 5 minutos» toma 3 toques y aparece en el mapa de otro teléfono | Video corto de los teléfonos que participan |  |
| **CP-58** con el GPS apagado, el conductor ajusta el punto en el mapa y el reporte se envía con ese punto | Video corto de la pantalla |  |
| **CP-59** con el JWT vencido y el token de renovación vigente, el reporte se envía sin pedir iniciar sesión | Video corto de la pantalla |  |
| **CP-60** con el token de renovación también vencido, la app pide iniciar sesión y el reporte se envía después sin volver a llenarlo | Video corto de la pantalla |  |

### [HU-02][CU-01][Flutter] Guardar y reintentar reportes sin conexión · [#61](https://github.com/uniWASD/ChuroViajeInterno/issues/61)

**Responsable:** Franz · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-02: Como conductor de la app, quiero que mi reporte se guarde localmente cuando no tengo conexión a internet, para no perder la información y que se envíe automáticamente al recuperar la conexión.

**Criterios de aceptación**

- Sin conexión, el reporte se guarda en el teléfono y sobrevive a cerrar la app
- Al volver la red se envía solo y avisa al conductor
- Si perdió vigencia no se envía y se avisa; si falla varias veces queda pendiente

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-81** en modo avión se reporta un accidente; al quitar el modo avión se envía solo y aparece el aviso | Video corto de la pantalla |  |
| **CP-82** un control policial guardado hace 70 minutos no se envía y se avisa que perdió vigencia | Video corto de la pantalla |  |
| **CP-83** con el backend caído, el reporte queda como pendiente después de varios intentos | Video corto de la pantalla |  |

### [HU-03][CU-02][Java] Consultar eventos vigentes y vencerlos · [#55](https://github.com/uniWASD/ChuroViajeInterno/issues/55)

**Responsable:** Antonio · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-03: Como conductor de la app, quiero ver en el mapa los eventos viales activos reportados por otros conductores, para anticiparme a choques, controles o congestiones en mi ruta.
- RNF-01: Los eventos reportados deberán aparecer en el mapa de los demás conductores en 5 a 10 segundos como máximo.
- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-19: El mapa deberá mostrar la atribución de Mapbox y OpenStreetMap que piden sus licencias.

**Criterios de aceptación**

- GET /eventos devuelve solo eventos activos y confirmados, sin datos de quién reportó
- Los eventos vencen solos (30 min congestión, 60 min los demás)
- Devuelve solo los cambios desde la última consulta
- Responde en menos de 1 segundo con 500 eventos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-61** un control policial de hace 61 minutos ya no sale en GET /eventos y queda Vencido | Prueba automatizada y su salida en la CI |  |
| **CP-62** con la marca de una consulta anterior solo llegan los eventos nuevos, los que se confirmaron y los que dejaron de estar vigentes | Prueba automatizada y su salida en la CI |  |
| **CP-63** un evento que vence entre dos consultas llega como vencido en la segunda, aunque la tarea periódica no haya corrido todavía | Prueba automatizada y su salida en la CI |  |
| **CP-64** la respuesta no contiene ningún identificador de conductor | Prueba automatizada y su salida en la CI |  |

### [HU-03][CU-02][Flutter] Ver los eventos en el mapa · [#56](https://github.com/uniWASD/ChuroViajeInterno/issues/56)

**Responsable:** Alex · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-03: Como conductor de la app, quiero ver en el mapa los eventos viales activos reportados por otros conductores, para anticiparme a choques, controles o congestiones en mi ruta.
- RNF-01: Los eventos reportados deberán aparecer en el mapa de los demás conductores en 5 a 10 segundos como máximo.
- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-19: El mapa deberá mostrar la atribución de Mapbox y OpenStreetMap que piden sus licencias.

**Criterios de aceptación**

- El mapa se actualiza cada 5 segundos pidiendo solo los cambios
- Cada evento tiene el color de su tipo y, al tocarlo, dice hace cuánto ocurrió
- Los vencidos o retirados desaparecen; si el backend falla, conserva lo último y avisa
- No consulta con la app en segundo plano

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-65** un evento reportado desde otro teléfono aparece en el mapa en menos de 10 segundos (RNF-01) | Video corto de los teléfonos que participan |  |
| **CP-66** con el backend apagado, el mapa conserva los eventos y muestra «No se pudo actualizar eventos» | Captura de pantalla |  |
| **CP-67** un evento que vence mientras se mira desaparece en la siguiente actualización | Video corto de la pantalla |  |

### [T-099][Java] Mantener al día el tráfico del motor · [#67](https://github.com/uniWASD/ChuroViajeInterno/issues/67)

**Responsable:** Jorge · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-05: Como conductor de la app, quiero calcular la ruta vehicular óptima hacia mi destino, para llegar por el camino más eficiente considerando datos viales vigentes.
- HU-06: Como conductor navegando una ruta activa, quiero que el sistema me proponga una ruta alternativa cuando un evento vial afecte mi camino, para evitar demoras sin tener que buscarla yo.

**Criterios de aceptación**

- El motor recibe cada evento nuevo, cambio de vencimiento, retiro o vencimiento
- Al arrancar, el núcleo recibe todos los eventos vigentes
- Si el aviso al motor falla, se reintenta y nunca se pierde

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-99** un bloqueo reportado sobre la única calle entre dos puntos hace que la ruta entre ellos responda «sin ruta»; al retirarlo por votos, la ruta vuelve a existir | Prueba automatizada y su salida en la CI |  |
| **CP-100** se reinicia el núcleo con 3 eventos vigentes y, antes de la primera ruta, el núcleo ya recibió los 3 | Prueba automatizada y su salida en la CI |  |

### [HU-10][CU-08][Flutter] Iniciar sesión con Google · [#48](https://github.com/uniWASD/ChuroViajeInterno/issues/48)

**Responsable:** Alex · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-10: Como conductor de la app, quiero iniciar sesión con mi cuenta de Google, para acceder de forma rápida y segura sin gestionar una contraseña adicional.
- RNF-05: El inicio de sesión con Google deberá tardar menos de 3 segundos.
- RNF-08: Solo los conductores que iniciaron sesión con su cuenta de Google podrán usar la app. El simulador y el servicio de WhatsApp entran con credenciales propias.
- RNF-14: Todo lo que viaja entre la app y el servidor deberá ir cifrado.
- RNF-16: La app deberá tener una política de privacidad que explique qué datos se recogen, cuánto tiempo se guardan y que los mensajes de los grupos de WhatsApp se envían a un servicio de análisis externo.

**Criterios de aceptación**

- Sin sesión solo se ve «Iniciar sesión con Google»; entra al mapa en menos de 3 segundos
- Si cancela o falla, vuelve al inicio y deja reintentar
- Guarda la sesión de forma segura y la renueva sola; puede entrar sin conexión
- «Cerrar sesión» borra del teléfono la sesión y los datos guardados

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-37** inicio de sesión con una cuenta de Google real abre el mapa en menos de 3 segundos | Video corto de la pantalla donde se vea el tiempo de respuesta |  |
| **CP-38** cancelar el selector de cuentas deja al conductor en la vista de inicio | Captura de pantalla |  |
| **CP-39** con sesión vigente y el teléfono en modo avión, la app abre directo en el mapa | Captura de pantalla |  |
| **CP-40** después de cerrar sesión, la app vuelve a Inicio de sesión y no queda ningún token guardado | Video corto de la pantalla |  |

### [HU-11][CU-02][Java] Votar eventos · [#63](https://github.com/uniWASD/ChuroViajeInterno/issues/63)

**Responsable:** Antonio · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- HU-11: Como conductor de la app, quiero ver la lista de eventos activos y votar si cada uno es real o falso, para que el mapa muestre solo eventos confiables.
- RNF-09: El sistema deberá evitar que un mismo usuario envíe reportes, votos o confirmaciones repetidos o abusivos.

**Criterios de aceptación**

- Se vota «real» o «falso» un evento activo reportado por otro conductor
- Un voto por conductor y evento; no se vota el propio ni la congestión
- 3 votos «falso» lo retiran; 3 «real» lo confirman
- No muestra quién votó

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-86** tres conductores votan «falso» un accidente; queda Retirado y no sale en la siguiente GET /eventos | Prueba automatizada y su salida en la CI |  |
| **CP-87** un evento con 3 votos «real» recibe después 3 «falso» y sigue Confirmado | Prueba automatizada y su salida en la CI |  |
| **CP-88** el mismo conductor vota dos veces; el segundo voto responde 409 | Prueba automatizada y su salida en la CI |  |
| **CP-89** el reportante intenta votar su evento y recibe 422 | Prueba automatizada y su salida en la CI |  |

### [T-098][Servidor] Desplegar el backend con el núcleo C++ · [#60](https://github.com/uniWASD/ChuroViajeInterno/issues/60)

**Responsable:** Marcelo · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- RNF-02: El cálculo de una ruta deberá tardar menos de 3 segundos.
- RNF-08: Solo los conductores que iniciaron sesión con su cuenta de Google podrán usar la app. El simulador y el servicio de WhatsApp entran con credenciales propias.

**Criterios de aceptación**

- El backend corre como servicio, arranca solo y se reinicia si se cae
- El núcleo se compila en el servidor y no abre puertos
- Los secretos están fuera del repositorio y las credenciales de prueba están desactivadas
- Hay una guía para desplegar una versión nueva y volver a la anterior

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-79** tras reiniciar la instancia, el backend y el núcleo vuelven solos y el inicio de sesión funciona desde la app | Salida del comando de comprobación, sin IPs internas, llaves ni contraseñas |  |
| **CP-80** al matar el proceso del núcleo, el backend lo vuelve a arrancar | Salida del comando de comprobación, sin IPs internas, llaves ni contraseñas |  |

### [T-097][Servidor] Dominio, Nginx y HTTPS · [#52](https://github.com/uniWASD/ChuroViajeInterno/issues/52)

**Responsable:** Marcelo · **Fechas:** 05/11 – 11/11

**Historia de usuario y requisitos**

- RNF-08: Solo los conductores que iniciaron sesión con su cuenta de Google podrán usar la app. El simulador y el servicio de WhatsApp entran con credenciales propias.
- RNF-14: Todo lo que viaja entre la app y el servidor deberá ir cifrado.

**Criterios de aceptación**

- Dominio propio con HTTPS de Let's Encrypt que se renueva solo
- HTTP redirige a HTTPS y no se abren otros puertos
- Nginx pasa las peticiones al backend, que no es accesible desde fuera
- Nota A o mejor en SSL Labs

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-51** http://dominio redirige a https://dominio, y el certificado es válido en un teléfono | Salida del comando de comprobación, sin IPs internas, llaves ni contraseñas |  |

## Sprint 5 (12/11 – 18/11)

12 issues · 37 puntos.

| Issue | Responsable | Casos de prueba |
| --- | --- | --- |
| [HU-05][CU-03][Flutter] Navegación activa ([#94](https://github.com/uniWASD/ChuroViajeInterno/issues/94)) | Alex | CP-107, CP-155 |
| [HU-06][CU-04][C++] Revisar la ruta activa ([#76](https://github.com/uniWASD/ChuroViajeInterno/issues/76)) | Marcelo | CP-124, CP-125, CP-126 |
| [HU-06][CU-04][Java] Revisar la ruta activa ([#82](https://github.com/uniWASD/ChuroViajeInterno/issues/82)) | Jorge | CP-134, CP-135, CP-136, CP-137 |
| [HU-06][CU-04][Flutter] Aviso de ruta alternativa ([#83](https://github.com/uniWASD/ChuroViajeInterno/issues/83)) | Alex | CP-138, CP-139, CP-140 |
| [HU-07][CU-05][Java] Consultar surtidores cercanos ([#69](https://github.com/uniWASD/ChuroViajeInterno/issues/69)) | Antonio | CP-102, CP-103, CP-104 |
| [HU-08][CU-06][Java] Recibir el análisis de WhatsApp ([#57](https://github.com/uniWASD/ChuroViajeInterno/issues/57)) | Antonio | CP-68, CP-69, CP-70 |
| [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado ([#59](https://github.com/uniWASD/ChuroViajeInterno/issues/59)) | Franz | CP-74, CP-75, CP-76, CP-77, CP-78 |
| [T-102][Simulador] Estructura y parámetros del simulador ([#78](https://github.com/uniWASD/ChuroViajeInterno/issues/78)) | Antonio | CP-128 |
| [HU-11][CU-02][Flutter] Lista de eventos y votos ([#64](https://github.com/uniWASD/ChuroViajeInterno/issues/64)) | Alex | CP-90, CP-91 |
| [HU-13][CU-04][C++] Detectar congestión con la telemetría ([#73](https://github.com/uniWASD/ChuroViajeInterno/issues/73)) | Marcelo | CP-114, CP-115, CP-116, CP-117, CP-118 |
| [HU-13][CU-04][Java] Recibir telemetría y registrar congestión ([#74](https://github.com/uniWASD/ChuroViajeInterno/issues/74)) | Jorge | CP-119, CP-120, CP-121 |
| [HU-13][CU-04][Flutter] Enviar telemetría con la app abierta ([#75](https://github.com/uniWASD/ChuroViajeInterno/issues/75)) | Franz | CP-122, CP-123 |

### [HU-05][CU-03][Flutter] Navegación activa · [#94](https://github.com/uniWASD/ChuroViajeInterno/issues/94)

**Responsable:** Alex · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-05: Como conductor de la app, quiero calcular la ruta vehicular óptima hacia mi destino, para llegar por el camino más eficiente considerando datos viales vigentes.
- RNF-02: El cálculo de una ruta deberá tardar menos de 3 segundos.
- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-17: El sistema deberá guardar los datos solo el tiempo necesario y no conservar el historial de rutas de los conductores.

**Criterios de aceptación**

- Muestra la posición, la calle que sigue con el sentido del giro y lo que falta
- Al llegar o cancelar, la ruta termina y se borra del teléfono

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-107** al tocar «Iniciar», la vista Navegación activa sigue la posición del conductor | Video corto de la pantalla |  |
| **CP-155** al acercarse a una esquina de la ruta, la vista muestra la calle que sigue y el sentido del giro; a menos de 30 m del destino, la navegación termina | Video corto de la pantalla |  |

### [HU-06][CU-04][C++] Revisar la ruta activa · [#76](https://github.com/uniWASD/ChuroViajeInterno/issues/76)

**Responsable:** Marcelo · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-06: Como conductor navegando una ruta activa, quiero que el sistema me proponga una ruta alternativa cuando un evento vial afecte mi camino, para evitar demoras sin tener que buscarla yo.

**Criterios de aceptación**

- Revisa si un evento nuevo afecta los tramos que faltan
- Propone una alternativa solo si cuesta menos que seguir por la ruta actual
- Indica qué evento la provocó, para no avisarlo de nuevo
- Si la red cambió, responde «ruta desactualizada»

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-124** un accidente sobre un tramo que falta produce una alternativa más barata | Prueba automatizada y su salida en la CI |  |
| **CP-125** un control policial (×1,5) sobre un tramo cuya alternativa cuesta más produce «sin alternativa» | Prueba automatizada y su salida en la CI |  |
| **CP-126** el mismo evento, enviado como ya avisado, produce «sin cambios» | Prueba automatizada y su salida en la CI |  |

### [HU-06][CU-04][Java] Revisar la ruta activa · [#82](https://github.com/uniWASD/ChuroViajeInterno/issues/82)

**Responsable:** Jorge · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-06: Como conductor navegando una ruta activa, quiero que el sistema me proponga una ruta alternativa cuando un evento vial afecte mi camino, para evitar demoras sin tener que buscarla yo.

**Criterios de aceptación**

- POST /rutas/revision responde «sin cambios», «sin alternativa» o una alternativa
- La alternativa parte de la posición actual y solo se propone si es mejor
- Un evento ya avisado no vuelve a provocar una propuesta
- No guarda la ruta y responde en menos de 3 segundos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-134** con un accidente nuevo sobre un tramo que falta, la respuesta trae una alternativa más barata | Prueba automatizada y su salida en la CI |  |
| **CP-135** si no hay camino mejor, la respuesta es «sin alternativa», con el evento que la provocó | Prueba automatizada y su salida en la CI |  |
| **CP-136** el mismo evento, enviado como ya avisado, devuelve «sin cambios» | Prueba automatizada y su salida en la CI |  |
| **CP-137** con tramos que no existen, la respuesta es «ruta desactualizada» | Prueba automatizada y su salida en la CI |  |

### [HU-06][CU-04][Flutter] Aviso de ruta alternativa · [#83](https://github.com/uniWASD/ChuroViajeInterno/issues/83)

**Responsable:** Alex · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-06: Como conductor navegando una ruta activa, quiero que el sistema me proponga una ruta alternativa cuando un evento vial afecte mi camino, para evitar demoras sin tener que buscarla yo.

**Criterios de aceptación**

- Durante la navegación, revisa la ruta cada 5 segundos
- La alternativa se muestra sin interrumpir, con «Aceptar» y «Rechazar»
- Sin respuesta en 15 segundos se cierra y sigue la ruta original
- Cada evento se avisa una sola vez; si la revisión falla, sigue sin error

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-138** durante una navegación, se reporta un accidente desde otro teléfono sobre la ruta y aparece el aviso de alternativa | Video corto de los teléfonos que participan |  |
| **CP-139** al rechazar la alternativa, la ruta sigue igual, con el evento a la vista, y el aviso no vuelve | Video corto de la pantalla |  |
| **CP-140** si el conductor no toca el aviso, a los 15 segundos se cierra, la ruta sigue igual y el aviso no vuelve | Video corto de la pantalla donde se vea el tiempo de respuesta |  |

### [HU-07][CU-05][Java] Consultar surtidores cercanos · [#69](https://github.com/uniWASD/ChuroViajeInterno/issues/69)

**Responsable:** Antonio · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-07: Como conductor de la app, quiero consultar el estado de disponibilidad de combustible en surtidores cercanos, para evitar dirigirme a uno que esté agotado.
- RNF-03: La lista de surtidores cercanos deberá mostrarse en menos de 3 segundos.
- RNF-04: El estado de un surtidor deberá actualizarse en la app en menos de 6 minutos desde que llega el mensaje de WhatsApp.
- RNF-07: La app deberá mostrar claramente de dónde viene el dato de cada surtidor (grupos de WhatsApp o conductores).

**Criterios de aceptación**

- GET /surtidores devuelve los surtidores del más cercano al más lejano
- Por cada combustible sale el dato más reciente, con su origen (WhatsApp, Conductores o ANH)
- Un dato de más de 6 horas no cuenta: sale «sin información reciente»
- No muestra quién aportó el dato y responde en menos de 3 segundos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-102** un surtidor con Gasolina «agotado» por WhatsApp a las 10:00 y «disponible» por Conductores a las 10:30 muestra disponible, origen Conductores, 10:30 | Prueba automatizada y su salida en la CI |  |
| **CP-103** un surtidor cuyo último dato es de hace 7 horas sale como «sin información reciente» | Prueba automatizada y su salida en la CI |  |
| **CP-104** desde la Plaza, el primer surtidor de la lista es el más cercano | Prueba automatizada y su salida en la CI |  |

### [HU-08][CU-06][Java] Recibir el análisis de WhatsApp · [#57](https://github.com/uniWASD/ChuroViajeInterno/issues/57)

**Responsable:** Antonio · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-08: Como sistema, quiero extraer los mensajes de los grupos de WhatsApp monitoreados e interpretarlos por lotes, para mantener actualizado el estado de los surtidores sin depender de reportes manuales.
- RNF-04: El estado de un surtidor deberá actualizarse en la app en menos de 6 minutos desde que llega el mensaje de WhatsApp.
- RNF-11: Los mensajes de WhatsApp deberán guardarse sin datos de quien los envió y borrarse después de analizarlos. Al servicio de análisis solo se enviarán el texto y la fecha, con los números de teléfono ocultos.
- RNF-16: La app deberá tener una política de privacidad que explique qué datos se recogen, cuánto tiempo se guardan y que los mensajes de los grupos de WhatsApp se envían a un servicio de análisis externo.

**Criterios de aceptación**

- Los endpoints de monitoreo solo aceptan la credencial del servicio
- Cada aviso se valida por separado; los inválidos se descartan con su motivo
- Un aviso válido se guarda con origen WhatsApp y la fecha del mensaje
- Un dato más viejo no reemplaza a uno más reciente

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-68** un lote con un aviso de «Las Vegas» válido y otro de un surtidor que no existe actualiza solo el primero y reporta el segundo como descartado | Prueba automatizada y su salida en la CI |  |
| **CP-69** un aviso de las 15:00 no reemplaza un dato del mismo surtidor y tipo de las 15:10 | Prueba automatizada y su salida en la CI |  |
| **CP-70** una petición con el JWT de un conductor responde 403 | Prueba automatizada y su salida en la CI |  |

### [HU-08][CU-06][WhatsApp/LLM] Analizar los mensajes por lotes y enviar el resultado · [#59](https://github.com/uniWASD/ChuroViajeInterno/issues/59)

**Responsable:** Franz · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-08: Como sistema, quiero extraer los mensajes de los grupos de WhatsApp monitoreados e interpretarlos por lotes, para mantener actualizado el estado de los surtidores sin depender de reportes manuales.
- RNF-04: El estado de un surtidor deberá actualizarse en la app en menos de 6 minutos desde que llega el mensaje de WhatsApp.
- RNF-11: Los mensajes de WhatsApp deberán guardarse sin datos de quien los envió y borrarse después de analizarlos. Al servicio de análisis solo se enviarán el texto y la fecha, con los números de teléfono ocultos.
- RNF-16: La app deberá tener una política de privacidad que explique qué datos se recogen, cuánto tiempo se guardan y que los mensajes de los grupos de WhatsApp se envían a un servicio de análisis externo.

**Criterios de aceptación**

- Cada 5 minutos envía los mensajes pendientes al LLM en un solo lote
- Al LLM solo van texto y fecha, con los teléfonos ocultos
- Descarta preguntas y mensajes ambiguos; si el LLM falla, reintenta en el siguiente ciclo
- Envía el resultado al backend y borra el texto de los mensajes

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-74** «ya hay gasolina en Las Vegas, fila corta» produce un aviso de Las Vegas, Gasolina, disponible, fila corta | Registro de la ejecución del servicio, sin números de teléfono ni nombres |  |
| **CP-75** «¿alguien sabe si hay diésel en El Portillo?» se descarta | Registro de la ejecución del servicio, sin números de teléfono ni nombres |  |
| **CP-76** con el LLM caído, los mensajes siguen pendientes y se analizan en el ciclo siguiente | Registro de la ejecución del servicio, sin números de teléfono ni nombres |  |
| **CP-77** un mensaje válido en el grupo de prueba queda en la tabla estado_combustible del backend en menos de 6 minutos (RNF-04) | Registro de la ejecución del servicio, sin números de teléfono ni nombres |  |
| **CP-78** un mensaje con «llamen al 71234567» llega al LLM con el número enmascarado | Registro de la ejecución del servicio, sin números de teléfono ni nombres |  |

### [T-102][Simulador] Estructura y parámetros del simulador · [#78](https://github.com/uniWASD/ChuroViajeInterno/issues/78)

**Responsable:** Antonio · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-09: Como desarrollador del equipo, quiero ejecutar un simulador que genere "usuarios fantasma" con eventos y telemetría, para validar el rendimiento y la estabilidad del sistema bajo carga antes de un despliegue.
- RNF-12: El simulador no deberá dañar los datos reales del sistema.

**Criterios de aceptación**

- Los parámetros se configuran por comando o archivo y se validan antes de correr
- Queda definida la carga normal y la decisión entre Locust o simulador propio
- Por defecto apunta al ambiente de pruebas, nunca a producción

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-128** con −5 usuarios fantasma, el simulador no arranca y muestra los límites permitidos | Reporte de la ejecución del simulador |  |

### [HU-11][CU-02][Flutter] Lista de eventos y votos · [#64](https://github.com/uniWASD/ChuroViajeInterno/issues/64)

**Responsable:** Alex · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-11: Como conductor de la app, quiero ver la lista de eventos activos y votar si cada uno es real o falso, para que el mapa muestre solo eventos confiables.
- RNF-09: El sistema deberá evitar que un mismo usuario envíe reportes, votos o confirmaciones repetidos o abusivos.

**Criterios de aceptación**

- La pestaña Eventos muestra los vigentes con su tipo y hace cuánto ocurrieron
- Votar toma un toque; después los botones se desactivan
- La congestión aparece sin botones de voto
- Los retirados desaparecen y los confirmados llevan su marca

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-90** tres teléfonos votan «falso» el mismo evento y desaparece de la lista y del mapa de un cuarto | Video corto de los teléfonos que participan |  |
| **CP-91** un segundo voto sobre el mismo evento muestra el mensaje del backend y no cambia nada | Captura de pantalla |  |

### [HU-13][CU-04][C++] Detectar congestión con la telemetría · [#73](https://github.com/uniWASD/ChuroViajeInterno/issues/73)

**Responsable:** Marcelo · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-13: Como sistema, quiero recibir la posición de los conductores mientras tienen la app abierta, para calcular su velocidad y detectar congestión sin depender solo de los reportes manuales.
- RNF-13: La app deberá obtener la ubicación del conductor solo mientras está abierta, nunca en segundo plano, y el conductor podrá quitar el permiso cuando quiera.
- RNF-17: El sistema deberá guardar los datos solo el tiempo necesario y no conservar el historial de rutas de los conductores.
- RNF-18: Usar la app no deberá gastar más batería que una app de navegación común.

**Criterios de aceptación**

- Calcula la velocidad entre lecturas y descarta las imprecisas o imposibles
- Asigna cada lectura a su tramo y sentido
- Hay congestión si un recorrido sigue lento 2 minutos en un tramo
- Separa lo simulado de lo real y olvida los recorridos a los 5 minutos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-114** un recorrido a 8 km/h durante 2 minutos en una avenida principal produce congestión; durante 1 minuto, no | Prueba automatizada y su salida en la CI |  |
| **CP-115** un vehículo que frena para doblar una esquina no produce congestión | Prueba automatizada y su salida en la CI |  |
| **CP-116** una lectura con precisión de 80 m se descarta | Prueba automatizada y su salida en la CI |  |
| **CP-117** un recorrido lento en un sentido de una calle de doble sentido congestiona ese tramo y no el del sentido contrario | Prueba automatizada y su salida en la CI |  |
| **CP-118** un recorrido simulado lento produce una congestión informada como simulada | Prueba automatizada y su salida en la CI |  |

### [HU-13][CU-04][Java] Recibir telemetría y registrar congestión · [#74](https://github.com/uniWASD/ChuroViajeInterno/issues/74)

**Responsable:** Jorge · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-13: Como sistema, quiero recibir la posición de los conductores mientras tienen la app abierta, para calcular su velocidad y detectar congestión sin depender solo de los reportes manuales.
- RNF-08: Solo los conductores que iniciaron sesión con su cuenta de Google podrán usar la app. El simulador y el servicio de WhatsApp entran con credenciales propias.
- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-13: La app deberá obtener la ubicación del conductor solo mientras está abierta, nunca en segundo plano, y el conductor podrá quitar el permiso cuando quiera.
- RNF-17: El sistema deberá guardar los datos solo el tiempo necesario y no conservar el historial de rutas de los conductores.
- RNF-18: Usar la app no deberá gastar más batería que una app de navegación común.

**Criterios de aceptación**

- Recibe la telemetría de conductores con sesión y no la guarda
- El recorrido no se puede relacionar con el conductor (identificador que cambia cada día)
- Registra la congestión que detecta el motor, o la extiende si ya existe
- Responde «recibido» en menos de 300 ms

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-119** cuando el motor informa congestión en un tramo, aparece un evento Congestión con origen Telemetría en GET /eventos | Prueba automatizada y su salida en la CI |  |
| **CP-120** una segunda congestión en el mismo tramo no crea otro evento; mueve el momento del existente | Prueba automatizada y su salida en la CI |  |
| **CP-121** después de procesar las lecturas, la base no tiene ninguna fila de telemetría | Prueba automatizada y su salida en la CI |  |

### [HU-13][CU-04][Flutter] Enviar telemetría con la app abierta · [#75](https://github.com/uniWASD/ChuroViajeInterno/issues/75)

**Responsable:** Franz · **Fechas:** 12/11 – 18/11

**Historia de usuario y requisitos**

- HU-13: Como sistema, quiero recibir la posición de los conductores mientras tienen la app abierta, para calcular su velocidad y detectar congestión sin depender solo de los reportes manuales.
- RNF-13: La app deberá obtener la ubicación del conductor solo mientras está abierta, nunca en segundo plano, y el conductor podrá quitar el permiso cuando quiera.
- RNF-17: El sistema deberá guardar los datos solo el tiempo necesario y no conservar el historial de rutas de los conductores.
- RNF-18: Usar la app no deberá gastar más batería que una app de navegación común.

**Criterios de aceptación**

- Con la app abierta, lee el GPS cada 3 s y envía cada 15 s
- En segundo plano o sin permiso no envía nada
- En 30 minutos gasta menos batería que Google Maps navegando

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-122** con la app abierta 1 minuto llegan 4 envíos al backend; con la app minimizada, ninguno | Video corto de la pantalla y registro del backend con los envíos recibidos |  |
| **CP-123** al revocar el permiso de ubicación, la telemetría se detiene y el mapa sigue funcionando | Video corto de la pantalla |  |

## Sprint 6 (19/11 – 25/11)

10 issues · 29 puntos.

| Issue | Responsable | Casos de prueba |
| --- | --- | --- |
| [HU-04][CU-02][Flutter] Ver eventos guardados sin conexión ([#62](https://github.com/uniWASD/ChuroViajeInterno/issues/62)) | Alex | CP-84, CP-85 |
| [HU-07][CU-05][Flutter] Consultar surtidores cercanos ([#71](https://github.com/uniWASD/ChuroViajeInterno/issues/71)) | Franz | CP-108, CP-109, CP-110 |
| [HU-09][CU-07][Java] Soporte para el simulador ([#84](https://github.com/uniWASD/ChuroViajeInterno/issues/84)) | Jorge | CP-141, CP-142, CP-143 |
| [HU-09][CU-07][Simulador] Usuarios fantasma que envían eventos y telemetría ([#87](https://github.com/uniWASD/ChuroViajeInterno/issues/87)) | Antonio | CP-146, CP-147, CP-148 |
| [HU-12][CU-05][Java] Registrar confirmaciones de recarga ([#72](https://github.com/uniWASD/ChuroViajeInterno/issues/72)) | Jorge | CP-111, CP-112, CP-113 |
| [HU-12][CU-05][Flutter] Preguntar por la recarga ([#80](https://github.com/uniWASD/ChuroViajeInterno/issues/80)) | Alex | CP-130, CP-131, CP-132 |
| [T-100][Servidor] Desplegar el servicio de WhatsApp/LLM ([#68](https://github.com/uniWASD/ChuroViajeInterno/issues/68)) | Marcelo | CP-101 |
| [T-103][Servidor] Respaldos de la base de datos y registros ([#79](https://github.com/uniWASD/ChuroViajeInterno/issues/79)) | Marcelo | CP-129 |
| [T-105][Java] Limpieza de datos vencidos ([#85](https://github.com/uniWASD/ChuroViajeInterno/issues/85)) | Antonio | CP-144 |
| [T-106][Servidor] Ambiente de pruebas para el simulador ([#86](https://github.com/uniWASD/ChuroViajeInterno/issues/86)) | Marcelo | CP-145 |

### [HU-04][CU-02][Flutter] Ver eventos guardados sin conexión · [#62](https://github.com/uniWASD/ChuroViajeInterno/issues/62)

**Responsable:** Alex · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- HU-04: Como conductor de la app, quiero seguir viendo los últimos eventos conocidos cuando no tengo conexión a internet, para no quedarme sin información aunque esté desactualizada.

**Criterios de aceptación**

- Sin conexión, el mapa y la lista muestran los últimos eventos guardados
- Una marca visible avisa que pueden estar desactualizados
- Al volver la red, se reemplazan por los eventos vigentes

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-84** se cierra la app con 3 eventos en el mapa, se abre en modo avión y se ven los 3 con la marca de desactualizados | Video corto de la pantalla |  |
| **CP-85** al volver la red, la marca desaparece y el mapa muestra los vigentes | Video corto de la pantalla |  |

### [HU-07][CU-05][Flutter] Consultar surtidores cercanos · [#71](https://github.com/uniWASD/ChuroViajeInterno/issues/71)

**Responsable:** Franz · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- HU-07: Como conductor de la app, quiero consultar el estado de disponibilidad de combustible en surtidores cercanos, para evitar dirigirme a uno que esté agotado.
- RNF-03: La lista de surtidores cercanos deberá mostrarse en menos de 3 segundos.
- RNF-04: El estado de un surtidor deberá actualizarse en la app en menos de 6 minutos desde que llega el mensaje de WhatsApp.
- RNF-07: La app deberá mostrar claramente de dónde viene el dato de cada surtidor (grupos de WhatsApp o conductores).

**Criterios de aceptación**

- La lista y los marcadores del mapa se actualizan cada 60 segundos
- Cada surtidor muestra estado por combustible, fila, origen y fecha
- Desde un surtidor se puede abrir «Cómo llegar»
- La lista aparece en menos de 3 segundos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-108** un surtidor con un aviso de WhatsApp de hace 10 minutos muestra el estado, el origen WhatsApp y la hora | Captura de pantalla |  |
| **CP-109** un surtidor sin datos muestra «sin información reciente» | Captura de pantalla |  |
| **CP-110** con el mapa abierto, un dato nuevo de un surtidor cambia su marcador en menos de 2 minutos, sin tocar la pantalla | Video corto de la pantalla donde se vea el tiempo de respuesta |  |

### [HU-09][CU-07][Java] Soporte para el simulador · [#84](https://github.com/uniWASD/ChuroViajeInterno/issues/84)

**Responsable:** Jorge · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- HU-09: Como desarrollador del equipo, quiero ejecutar un simulador que genere "usuarios fantasma" con eventos y telemetría, para validar el rendimiento y la estabilidad del sistema bajo carga antes de un despliegue.
- RNF-12: El simulador no deberá dañar los datos reales del sistema.

**Criterios de aceptación**

- Da sesiones de conductor simulado solo en el ambiente de pruebas
- Todo lo simulado queda marcado y pasa por las mismas reglas que lo real
- Un comando borra los datos simulados sin tocar los reales
- Cada prueba de carga queda en los registros

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-141** en el perfil prod, POST /sesion/prueba responde 403 | Prueba automatizada y su salida en la CI |  |
| **CP-142** un evento simulado no aparece a un conductor real fuera del modo demostración | Prueba automatizada y su salida en la CI |  |
| **CP-143** después del borrado no queda ninguna fila simulada y las reales siguen intactas | Prueba automatizada y su salida en la CI |  |

### [HU-09][CU-07][Simulador] Usuarios fantasma que envían eventos y telemetría · [#87](https://github.com/uniWASD/ChuroViajeInterno/issues/87)

**Responsable:** Antonio · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- HU-09: Como desarrollador del equipo, quiero ejecutar un simulador que genere "usuarios fantasma" con eventos y telemetría, para validar el rendimiento y la estabilidad del sistema bajo carga antes de un despliegue.
- RNF-12: El simulador no deberá dañar los datos reales del sistema.

**Criterios de aceptación**

- Cada usuario fantasma recorre Tarija y envía eventos y telemetría por la API
- Nunca toca la base de datos ni altera datos reales, aunque se interrumpa
- Al terminar muestra un resumen de peticiones, errores y tiempos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-146** 10 usuarios fantasma durante 5 minutos generan eventos y congestiones simuladas visibles en modo demostración | Reporte de la ejecución del simulador |  |
| **CP-147** se interrumpe la simulación a la mitad y los datos reales siguen iguales | Reporte de la ejecución del simulador |  |
| **CP-148** después del borrado no queda ningún dato simulado | Reporte de la ejecución del simulador |  |

### [HU-12][CU-05][Java] Registrar confirmaciones de recarga · [#72](https://github.com/uniWASD/ChuroViajeInterno/issues/72)

**Responsable:** Jorge · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- HU-12: Como conductor de la app, quiero indicar que acabo de recargar combustible y de qué tipo, para que los demás conductores sepan que ese surtidor tiene ese combustible.
- RNF-07: La app deberá mostrar claramente de dónde viene el dato de cada surtidor (grupos de WhatsApp o conductores).
- RNF-09: El sistema deberá evitar que un mismo usuario envíe reportes, votos o confirmaciones repetidos o abusivos.

**Criterios de aceptación**

- Acepta la confirmación solo a menos de 50 m del surtidor y con un tipo conocido
- No repite la misma confirmación del conductor en 60 minutos
- Marca el combustible como disponible (origen Conductores), nunca como agotado

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-111** un conductor a 20 m del surtidor confirma Diesel y el surtidor queda con Diesel disponible, origen Conductores | Prueba automatizada y su salida en la CI |  |
| **CP-112** la misma confirmación 10 minutos después responde 409 | Prueba automatizada y su salida en la CI |  |
| **CP-113** una confirmación desde 500 m responde 422 | Prueba automatizada y su salida en la CI |  |

### [HU-12][CU-05][Flutter] Preguntar por la recarga · [#80](https://github.com/uniWASD/ChuroViajeInterno/issues/80)

**Responsable:** Alex · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- HU-12: Como conductor de la app, quiero indicar que acabo de recargar combustible y de qué tipo, para que los demás conductores sepan que ese surtidor tiene ese combustible.
- RNF-07: La app deberá mostrar claramente de dónde viene el dato de cada surtidor (grupos de WhatsApp o conductores).
- RNF-09: El sistema deberá evitar que un mismo usuario envíe reportes, votos o confirmaciones repetidos o abusivos.

**Criterios de aceptación**

- Tras 3 minutos detenido junto a un surtidor, pregunta «¿Acabas de recargar combustible?»
- Con «Sí» se elige el tipo; con «No» no se envía nada
- Responder toma 2 toques y no tapa la navegación
- No vuelve a preguntar por ese surtidor en 60 minutos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-130** con una ubicación simulada detenida 3 minutos junto a un surtidor, aparece la pregunta; al elegir Diesel, el surtidor muestra Diesel disponible con origen Conductores | Video corto de la pantalla |  |
| **CP-131** al responder «No», no se envía nada al backend y la pregunta no reaparece mientras el conductor siga detenido allí | Video corto de la pantalla |  |
| **CP-132** 10 minutos después, en el mismo surtidor, la pregunta no vuelve a aparecer | Video corto de la pantalla |  |

### [T-100][Servidor] Desplegar el servicio de WhatsApp/LLM · [#68](https://github.com/uniWASD/ChuroViajeInterno/issues/68)

**Responsable:** Marcelo · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- RNF-04: El estado de un surtidor deberá actualizarse en la app en menos de 6 minutos desde que llega el mensaje de WhatsApp.
- RNF-11: Los mensajes de WhatsApp deberán guardarse sin datos de quien los envió y borrarse después de analizarlos. Al servicio de análisis solo se enviarán el texto y la fecha, con los números de teléfono ocultos.

**Criterios de aceptación**

- El servicio corre solo, se reinicia si se cae y no abre puertos hacia fuera
- Sus credenciales solo las lee el usuario del servicio
- La guía explica cómo volver a vincular el número

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-101** un mensaje en el grupo de prueba queda en la tabla estado_combustible en menos de 6 minutos (RNF-04). La comprobación en la app se hace con «[HU-07][CU-05][Flutter] Consultar surtidores cercanos», del mismo sprint | Salida del comando de comprobación, sin IPs internas, llaves ni contraseñas |  |

### [T-103][Servidor] Respaldos de la base de datos y registros · [#79](https://github.com/uniWASD/ChuroViajeInterno/issues/79)

**Responsable:** Marcelo · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-11: Los mensajes de WhatsApp deberán guardarse sin datos de quien los envió y borrarse después de analizarlos. Al servicio de análisis solo se enviarán el texto y la fecha, con los números de teléfono ocultos.

**Criterios de aceptación**

- Respaldo diario fuera de la instancia, guardando los últimos 7
- Al menos una restauración probada, con su guía
- Los registros se borran a los 14 días y no guardan ubicaciones ni mensajes

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-129** el respaldo de ayer se restaura en una base aparte y tiene los mismos eventos y surtidores | Salida del comando de comprobación, sin IPs internas, llaves ni contraseñas |  |

### [T-105][Java] Limpieza de datos vencidos · [#85](https://github.com/uniWASD/ChuroViajeInterno/issues/85)

**Responsable:** Antonio · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-17: El sistema deberá guardar los datos solo el tiempo necesario y no conservar el historial de rutas de los conductores.

**Criterios de aceptación**

- Una tarea diaria borra eventos, votos y confirmaciones de más de 30 días
- También borra los tokens de renovación vencidos o cerrados
- Nada vigente se borra

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-144** un evento vencido hace 31 días y sus votos se borran; uno vencido hace 29 días se conserva | Prueba automatizada y su salida en la CI |  |

### [T-106][Servidor] Ambiente de pruebas para el simulador · [#86](https://github.com/uniWASD/ChuroViajeInterno/issues/86)

**Responsable:** Marcelo · **Fechas:** 19/11 – 25/11

**Historia de usuario y requisitos**

- RNF-12: El simulador no deberá dañar los datos reales del sistema.

**Criterios de aceptación**

- Base y backend de pruebas separados, con su propio núcleo y subdominio
- Solo ahí están habilitadas las credenciales de prueba
- Se enciende y apaga sin afectar a producción y tiene la CPU limitada

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-145** POST /sesion/prueba funciona en pruebas.dominio y responde 403 en el dominio de producción | Salida del comando de comprobación, sin IPs internas, llaves ni contraseñas |  |

## Sprint 7 (26/11 – 30/11)

6 issues · 18 puntos.

| Issue | Responsable | Casos de prueba |
| --- | --- | --- |
| [T-101][WhatsApp/LLM] Prueba con mensajes reales ([#77](https://github.com/uniWASD/ChuroViajeInterno/issues/77)) | Franz | CP-127 |
| [T-107][Java] Pruebas de punta a punta ([#88](https://github.com/uniWASD/ChuroViajeInterno/issues/88)) | Jorge y Antonio | CP-149, CP-150, CP-166 |
| [T-108][Simulador] Pruebas de carga y reporte ([#89](https://github.com/uniWASD/ChuroViajeInterno/issues/89)) | Antonio | CP-151, CP-152 |
| [T-109][C++] Rendimiento con la red real de Tarija ([#90](https://github.com/uniWASD/ChuroViajeInterno/issues/90)) | Marcelo | CP-153 |
| [T-110][Servidor] Despliegue final y revisión de seguridad ([#91](https://github.com/uniWASD/ChuroViajeInterno/issues/91)) | Marcelo | CP-154 |
| [T-104][Flutter] Política de privacidad ([#81](https://github.com/uniWASD/ChuroViajeInterno/issues/81)) | Franz | CP-133 |

### [T-101][WhatsApp/LLM] Prueba con mensajes reales · [#77](https://github.com/uniWASD/ChuroViajeInterno/issues/77)

**Responsable:** Franz · **Fechas:** 26/11 – 30/11

**Historia de usuario y requisitos**

- HU-08: Como sistema, quiero extraer los mensajes de los grupos de WhatsApp monitoreados e interpretarlos por lotes, para mantener actualizado el estado de los surtidores sin depender de reportes manuales.
- RNF-04: El estado de un surtidor deberá actualizarse en la app en menos de 6 minutos desde que llega el mensaje de WhatsApp.
- RNF-11: Los mensajes de WhatsApp deberán guardarse sin datos de quien los envió y borrarse después de analizarlos. Al servicio de análisis solo se enviarán el texto y la fecha, con los números de teléfono ocultos.

**Criterios de aceptación**

- Muestra de al menos 100 mensajes con la respuesta correcta anotada a mano
- Se mide la precisión del LLM contra un mínimo fijado antes
- Un mensaje llega a la app en menos de 6 minutos

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-127** la precisión sobre la muestra anotada alcanza el mínimo que fijó el equipo | Registro de la ejecución del servicio, sin números de teléfono ni nombres |  |

### [T-107][Java] Pruebas de punta a punta · [#88](https://github.com/uniWASD/ChuroViajeInterno/issues/88)

**Responsable:** Jorge y Antonio · **Fechas:** 26/11 – 30/11

**Historia de usuario y requisitos**

- RNF-01: Los eventos reportados deberán aparecer en el mapa de los demás conductores en 5 a 10 segundos como máximo.
- RNF-02: El cálculo de una ruta deberá tardar menos de 3 segundos.
- RNF-03: La lista de surtidores cercanos deberá mostrarse en menos de 3 segundos.

**Criterios de aceptación**

- Una prueba de integración por caso de uso, con la base y el núcleo reales
- Todas las pruebas pasan en GitHub
- Cada fallo encontrado tiene su issue de bug

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-149** un evento reportado por un conductor llega a otro en su siguiente consulta, en menos de 10 segundos (RNF-01) | Prueba automatizada con la medición de tiempos y su salida |  |
| **CP-150** un bloqueo reportado cambia la ruta calculada después entre los mismos puntos | Prueba automatizada y su salida en la CI |  |
| **CP-166** con un ANH falso que informa 0 litros de Gasolina en un surtidor vinculado, GET /surtidores lo muestra agotado con origen ANH; si después ANH responde 503, el dato anterior sigue ahí | Prueba automatizada y su salida en la CI |  |

### [T-108][Simulador] Pruebas de carga y reporte · [#89](https://github.com/uniWASD/ChuroViajeInterno/issues/89)

**Responsable:** Antonio · **Fechas:** 26/11 – 30/11

**Historia de usuario y requisitos**

- RNF-01: Los eventos reportados deberán aparecer en el mapa de los demás conductores en 5 a 10 segundos como máximo.
- RNF-02: El cálculo de una ruta deberá tardar menos de 3 segundos.
- RNF-03: La lista de surtidores cercanos deberá mostrarse en menos de 3 segundos.
- RNF-12: El simulador no deberá dañar los datos reales del sistema.

**Criterios de aceptación**

- Tres escenarios: carga normal, el doble y creciente hasta saturar
- Se miden los tiempos de eventos (5–10 s), rutas y surtidores (menos de 3 s)
- Si se satura, el backend no se cae; el reporte queda en el repositorio
- Al terminar se borran los datos simulados

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-151** con la carga normal, el 95 % de las rutas responde en menos de 3 segundos | Reporte de la ejecución del simulador |  |
| **CP-152** con la carga creciente, el backend no se cae y la saturación queda registrada | Reporte de la ejecución del simulador |  |

### [T-109][C++] Rendimiento con la red real de Tarija · [#90](https://github.com/uniWASD/ChuroViajeInterno/issues/90)

**Responsable:** Marcelo · **Fechas:** 26/11 – 30/11

**Historia de usuario y requisitos**

- RNF-02: El cálculo de una ruta deberá tardar menos de 3 segundos.

**Criterios de aceptación**

- La ruta más lenta de 1000 consultas tarda menos de 1 segundo
- En las pruebas de carga el núcleo no se reinicia y su memoria se mantiene estable
- Los parámetros de tráfico quedan revisados con los resultados

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-153** 1000 rutas al azar en la red real, en el servidor, cada una en menos de 1 segundo | Salida de la prueba ejecutada en local con la red real (la red no está en el repositorio) |  |

### [T-110][Servidor] Despliegue final y revisión de seguridad · [#91](https://github.com/uniWASD/ChuroViajeInterno/issues/91)

**Responsable:** Marcelo · **Fechas:** 26/11 – 30/11

**Historia de usuario y requisitos**

- RNF-08: Solo los conductores que iniciaron sesión con su cuenta de Google podrán usar la app. El simulador y el servicio de WhatsApp entran con credenciales propias.
- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-11: Los mensajes de WhatsApp deberán guardarse sin datos de quien los envió y borrarse después de analizarlos. Al servicio de análisis solo se enviarán el texto y la fecha, con los números de teléfono ocultos.
- RNF-12: El simulador no deberá dañar los datos reales del sistema.

**Criterios de aceptación**

- La versión de main está desplegada y la app de demostración apunta a ella
- Desde fuera solo responden los puertos 443, 22 y 80
- Sin credenciales de prueba, datos simulados ni secretos en el repositorio
- Política de privacidad publicada y lista de seguridad firmada

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-154** un escaneo de puertos desde fuera solo encuentra el 22 y el 443 (y el 80, que redirige) | Salida del comando de comprobación, sin IPs internas, llaves ni contraseñas |  |

### [T-104][Flutter] Política de privacidad · [#81](https://github.com/uniWASD/ChuroViajeInterno/issues/81)

**Responsable:** Franz · **Fechas:** 26/11 – 30/11

**Historia de usuario y requisitos**

- RNF-10: El sistema no deberá mostrar datos personales, rutas ni ubicaciones de otros usuarios.
- RNF-11: Los mensajes de WhatsApp deberán guardarse sin datos de quien los envió y borrarse después de analizarlos. Al servicio de análisis solo se enviarán el texto y la fecha, con los números de teléfono ocultos.
- RNF-13: La app deberá obtener la ubicación del conductor solo mientras está abierta, nunca en segundo plano, y el conductor podrá quitar el permiso cuando quiera.
- RNF-16: La app deberá tener una política de privacidad que explique qué datos se recogen, cuánto tiempo se guardan y que los mensajes de los grupos de WhatsApp se envían a un servicio de análisis externo.
- RNF-17: El sistema deberá guardar los datos solo el tiempo necesario y no conservar el historial de rutas de los conductores.

**Criterios de aceptación**

- Explica qué datos se recogen, para qué y cuánto tiempo se guardan
- Aclara que los mensajes de los grupos se envían a Gemini y que parte de los datos viene de ANH
- Está publicada en el dominio y se abre desde el inicio de sesión
- Escrita en español claro

| Caso de prueba | Evidencia esperada | Validación |
| --- | --- | --- |
| **CP-133** desde la vista Inicio de sesión, sin cuenta, se abre la política y menciona al proveedor del LLM y a ANH | Captura de pantalla |  |
