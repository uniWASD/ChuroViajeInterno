# CU-02 — Visualizar eventos viales

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-03, HU-04 y HU-11, y los requisitos RF-03, RF-04, RF-12, RNF-01, RNF-09 y RNF-10.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-02.1 | Solo un conductor con sesión vigente puede consultar eventos y votar. | Sesión | CU-02 precondición, RNF-08 |
| RN-02.2 | Solo se entregan los eventos en estado Activo o Confirmado. Los retirados y los vencidos no salen nunca. | EventoVial | CU-02 paso 3 |
| RN-02.3 | Un evento está vigente mientras no pase el tiempo de su tipo, contado desde que ocurrió: 30 minutos la congestión y 60 minutos los demás tipos. | EventoVial, TipoEvento | CU-02 sección 8, HU-03 c.4 |
| RN-02.4 | Al superar su vigencia, el evento pasa a Vencido y desaparece del mapa y de la lista en la siguiente consulta, aunque el conductor lo esté mirando. | EventoVial | CU-02 ext. 3a, HU-03 c.4 |
| RN-02.5 | De cada evento se entrega su tipo, su ubicación, cuándo ocurrió, si está confirmado y su origen. Nunca quién lo reportó ni quién lo votó. | EventoVial | CU-02 sección 7, RNF-10 |
| RN-02.6 | Con el mapa abierto, la app consulta los eventos cada 5 segundos. Puede pedir solo lo que cambió desde su última consulta. | Controlador de mapa de la app | CU-02 paso 4 y sección 8, RNF-01 |
| RN-02.7 | Cada evento se dibuja con el color de su tipo. Al tocarlo se muestran su tipo y hace cuánto ocurrió. | TipoEvento | HU-03 c.1 y c.5 |
| RN-02.8 | Si el backend no responde o tarda demasiado, el mapa conserva lo último que mostró, avisa «No se pudo actualizar eventos» y vuelve a intentar en el siguiente ciclo. | Controlador de mapa de la app | CU-02 ext. 2a, HU-03 c.3 |
| RN-02.9 | Sin conexión, la app muestra los últimos eventos que guardó y avisa que pueden estar desactualizados. Al volver la red los reemplaza por los vigentes. | Caché local de la app | CU-02 garantía mínima, HU-04 |
| RN-02.10 | Solo se puede votar un evento Activo o Confirmado, sea cual sea su origen. | EventoVial | HU-11 c.1 |
| RN-02.11 | Cada conductor vota una sola vez por evento, «real» o «falso». Un segundo voto se rechaza. | Voto | CU-02 ext. 5a, HU-11 c.2, RNF-09 |
| RN-02.12 | El conductor que reportó un evento no puede votarlo. | Voto | Propuesta [P-17](../08_decisiones_parametros.md) |
| RN-02.13 | Al llegar a 3 votos «falso», el evento pasa a Retirado y desaparece del mapa y de la lista de todos en la siguiente consulta. | EventoVial | CU-02 ext. 5a, HU-11 c.3, [P-01](../08_decisiones_parametros.md) |
| RN-02.14 | Al llegar a 3 votos «real», el evento pasa a Confirmado y se muestra con su marca. Un evento confirmado ya no se retira por votos: solo vence. | EventoVial | HU-11 c.4, [P-11](../08_decisiones_parametros.md) |
| RN-02.15 | Un evento retirado o vencido deja de pesar en el cálculo de rutas desde ese momento. | EventoVial, Tramo | CU-04 |
| RN-02.16 | Un voto de un usuario fantasma queda marcado como simulado. | Voto | CU-07 sección 8, RNF-12 |

## Recorrido por Vista, Controlador y Modelo

La consulta periódica:

1. **Vista Mapa principal.** Se abre al entrar a la app.
2. **Controlador de mapa (app).** Pide los permisos, carga el mapa base de Mapbox, lanza la primera consulta y la repite cada 5 segundos (RN-02.6).
3. **Controlador REST de eventos (backend).** Comprueba el JWT (RN-02.1) y llama al servicio del modelo que consulta eventos.
4. **Modelo (backend).** El repositorio trae los eventos y cada `EventoVial` responde si sigue vigente (RN-02.2 a RN-02.4). El servicio arma la lista sin datos personales (RN-02.5).
5. **Controlador REST de eventos (backend).** Devuelve la lista, o solo los cambios desde la última consulta.
6. **Controlador de mapa (app).** Actualiza su estado y la caché local. Si no hubo respuesta, aplica RN-02.8; si no hay red, RN-02.9.
7. **Vista Mapa principal.** Dibuja cada evento con su color y, al tocarlo, muestra tipo y antigüedad (RN-02.7).

El voto:

1. **Vista Lista de eventos.** El conductor abre la lista, elige un evento y toca «real» o «falso».
2. **Controlador de eventos (app).** Envía el voto al backend con el JWT.
3. **Controlador REST de votos (backend).** Comprueba el JWT y el formato, y llama al servicio del modelo que registra votos.
4. **Modelo (backend).** El `EventoVial` comprueba que admite votos (RN-02.10) y que ese conductor puede votarlo (RN-02.11, RN-02.12). Registra el `Voto`, recuenta y cambia de estado si corresponde (RN-02.13, RN-02.14).
5. **Controlador REST de votos (backend).** Devuelve el voto aceptado o el motivo del rechazo.
6. **Los demás conductores.** Ven el retiro o la confirmación en su siguiente consulta.

Una tarea periódica del modelo marca como Vencido todo evento que superó su vigencia y avisa al motor core para que deje de contarlo (RN-02.15).
