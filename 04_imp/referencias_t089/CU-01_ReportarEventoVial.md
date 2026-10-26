# CU-01 — Reportar evento vial

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-01 y HU-02, y los requisitos RF-01, RF-02, RNF-01, RNF-06, RNF-08 y RNF-09.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-01.1 | Solo un conductor con sesión vigente puede reportar. | Sesión | CU-01 precondición, RNF-08 |
| RN-01.2 | Un reporte lleva tres datos obligatorios: tipo de evento, ubicación y momento en que ocurrió. Si falta uno, se rechaza entero. | EventoVial | CU-01 paso 3, HU-01 c.3 |
| RN-01.3 | El tipo debe ser uno de los reportables: accidente, control policial, bloqueo u otro. La congestión no se puede reportar. | TipoEvento | CU-01 paso 2 |
| RN-01.4 | El momento en que ocurrió es la hora en que el conductor confirmó el reporte menos el tiempo elegido: ahora mismo, hace 5 minutos, hace 10 minutos u hace 30 minutos. Si el conductor no elige, vale hace 5 minutos. Nunca puede quedar en el futuro. | EventoVial | CU-01 paso 1, HU-01 c.2 |
| RN-01.5 | La ubicación es la del GPS al confirmar. Si el GPS no responde, vale el punto que el conductor ajusta en el mapa. Sin ubicación no hay reporte. | Ubicación | CU-01 sección 8, HU-01 c.5 |
| RN-01.6 | La ubicación debe caer dentro de la mancha urbana de Tarija. | Ubicación | Visión 1.2.2 |
| RN-01.7 | Un evento que al llegar al backend ya superó la vigencia de su tipo no se registra, y el conductor recibe el aviso. | EventoVial, TipoEvento | CU-01 ext. 3a, HU-02 c.5 |
| RN-01.8 | Un conductor no puede registrar dos eventos del mismo tipo, a menos de 100 metros uno del otro y dentro de 10 minutos: el segundo se rechaza como duplicado. | EventoVial | CU-01 sección 7, RNF-09 |
| RN-01.9 | Un conductor no puede enviar más de 5 reportes en 10 minutos, sean del tipo que sean. | Conductor | Visión 6.3, RNF-09 |
| RN-01.10 | Un evento aceptado nace en estado Activo, con origen Conductor y sin votos. | EventoVial | CU-01 postcondición |
| RN-01.11 | El registro es todo o nada: si el evento no puede guardarse, no queda ningún dato parcial y el conductor recibe el aviso de fallo sin que la app se bloquee. | EventoVial | CU-01 garantía mínima |
| RN-01.12 | El evento aceptado sale en la siguiente consulta de los demás conductores y nunca incluye quién lo reportó. | EventoVial | CU-01 paso 5, RNF-01, RNF-10 |
| RN-01.13 | Sin conexión, el reporte se guarda en el teléfono y se envía solo cuando vuelve la red, sin que el conductor intervenga. Al enviarse, la app lo notifica. | Cola local de la app | HU-02 c.1 a c.3 |
| RN-01.14 | Antes de reenviar un reporte guardado, la app comprueba su vigencia con los tiempos del catálogo. Si ya venció, lo saca de la cola y avisa al conductor. | Cola local de la app | HU-02 c.5 |
| RN-01.15 | Si el reenvío falla varias veces seguidas, el reporte sigue en la cola y se muestra como pendiente. Nunca se descarta en silencio. | Cola local de la app | HU-02 c.4 |
| RN-01.16 | Si la sesión expiró al reportar, el reporte se conserva, se pide iniciar sesión de nuevo y después se reintenta el envío. | Sesión | CU-01 ext. 4a, HU-01 c.6 |
| RN-01.17 | Un evento enviado por un usuario fantasma queda marcado como simulado. | EventoVial | CU-07 sección 8, RNF-12 |

Los valores de RN-01.8 y RN-01.9 son iniciales, se confirmaron el 06/10/2026 y se ajustarán con las pruebas. La opción «hace 30 minutos» de RN-01.4 reemplaza a «hace 1 hora» por la decisión [D-09](../08_decisiones_parametros.md).

## Recorrido por Vista, Controlador y Modelo

1. **Vista Mapa principal.** El conductor toca «Reportar evento».
2. **Controlador de reporte (app).** Abre la vista Reportar evento con «Hace 5 minutos» marcado.
3. **Vista Reportar evento.** El conductor elige el tipo, cambia el tiempo si hace falta y confirma. Son tres toques.
4. **Controlador de reporte (app).** Toma la ubicación del GPS o el punto ajustado a mano. Si no hay red, guarda el reporte en la cola local (RN-01.13) y termina aquí. Si hay red, envía el reporte al backend con el JWT.
5. **Controlador REST de eventos (backend).** Comprueba el JWT (RN-01.1) y que la petición tenga el formato esperado. Llama al servicio del modelo que registra eventos.
6. **Modelo (backend).** Construye la `Ubicación` (RN-01.5, RN-01.6), busca el `TipoEvento` (RN-01.3) y crea el `EventoVial` (RN-01.2, RN-01.4, RN-01.7). Comprueba duplicado y límite (RN-01.8, RN-01.9) y lo guarda como Activo (RN-01.10, RN-01.11).
7. **Controlador REST de eventos (backend).** Devuelve que el evento fue aceptado, o el motivo del rechazo.
8. **Controlador de reporte (app) y vista.** Muestran la confirmación o el mensaje de error. Si la sesión expiró, se pasa a CU-08 y después se reintenta (RN-01.16).
9. **Los demás conductores.** Reciben el evento en su siguiente consulta periódica, que es parte de CU-02 (RN-01.12).

Cuando vuelve la conexión, el controlador de reporte recorre la cola local: descarta lo vencido (RN-01.14) y reenvía el resto desde el paso 4 (RN-01.15).
