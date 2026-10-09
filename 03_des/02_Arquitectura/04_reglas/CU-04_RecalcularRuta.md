# CU-04 — Recalcular ruta

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-06 y HU-13, y los requisitos RF-06, RF-14 y RNF-13. Las reglas RN-04.1 a RN-04.12 son de la telemetría que detecta la congestión; de RN-04.13 a RN-04.19, del recálculo. RN-04.20 cubre los datos del simulador.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-04.1 | La app obtiene posición y velocidad solo mientras está abierta en pantalla y tiene permiso de ubicación. Al pasar a segundo plano o cerrarse, deja de obtenerlas y de enviarlas. | Controlador de telemetría de la app | HU-13 c.1 y c.2, RNF-13 |
| RN-04.2 | La telemetría se envía con o sin ruta activa. | Controlador de telemetría de la app | HU-13 c.1, Visión 1.2.2 |
| RN-04.3 | Sin permiso de ubicación no hay telemetría, y todo lo que no depende de la ubicación sigue funcionando. | Controlador de telemetría de la app | HU-13 c.4 |
| RN-04.4 | Solo se acepta telemetría de conductores con sesión vigente. | Sesión | HU-13 seguridad, RNF-08 |
| RN-04.5 | La velocidad se calcula con la distancia entre dos lecturas seguidas y el tiempo que pasó entre ellas. | LecturaTelemetría | `05_pro/CalculoVelocidad`, issue #3 |
| RN-04.6 | Una lectura se descarta si su precisión es peor que 50 metros o si implica una velocidad mayor a 150 km/h. | LecturaTelemetría | `05_pro/CalculoVelocidad` |
| RN-04.7 | Una lectura lenta, de 25 km/h o menos, con un cambio de dirección mayor a 40 grados se toma como un giro y no cuenta como congestión. | LecturaTelemetría | `05_pro/CalculoVelocidad`, issue #3 |
| RN-04.8 | Cada lectura válida se asigna al tramo vehicular donde está el vehículo. Fuera de una vía vehicular, la lectura se ignora. | RedVial, Tramo | Visión 1.2.2 |
| RN-04.9 | Un tramo se clasifica como congestionado cuando se circula por debajo de la mitad de la velocidad esperada de su tipo de vía, y como muy congestionado por debajo de la cuarta parte. | Tramo | Prototipo `Analisis_Trafico.java`, [P-02](../08_decisiones_parametros.md) |
| RN-04.10 | Hay congestión cuando un vehículo sigue detenido o lento en un tramo durante 2 minutos seguidos. Entonces el backend registra un `EventoVial` de tipo Congestión, con origen Telemetría, ubicado en ese tramo. | EventoVial, Tramo | HU-13 c.3, Visión 1.2.2, [D-04](../08_decisiones_parametros.md), [P-02](../08_decisiones_parametros.md) |
| RN-04.11 | Si ese tramo ya tiene una congestión vigente, no se crea otra: se actualiza el momento de la existente, que así dura mientras siga la lentitud. | EventoVial | Propuesta |
| RN-04.12 | Las lecturas no se conservan después de analizarse y nunca se muestran a otro usuario. El evento de congestión no indica qué conductor lo originó. | LecturaTelemetría | Visión 6.3, HU-13 privacidad, RNF-10 |
| RN-04.13 | El recálculo solo aplica a una ruta Activa. | Ruta | CU-04 precondición |
| RN-04.14 | Se evalúa cuando un evento vigente, de cualquier tipo y origen, cae sobre un tramo que al conductor todavía le falta recorrer. | Ruta, EventoVial | CU-04 paso 1, HU-06 c.1 |
| RN-04.15 | La alternativa parte de la posición actual del conductor y conserva el destino y las paradas que faltan. | Ruta | CU-04 paso 2 |
| RN-04.16 | Solo se propone la alternativa si cuesta menos que seguir por la ruta actual con el tráfico al día. Si no hay una mejor, se mantiene la ruta original y se avisa de la congestión. | Ruta | CU-04 ext. 2a, HU-06 c.2 |
| RN-04.17 | La alternativa se muestra sin interrumpir la navegación, y el conductor puede aceptarla o rechazarla. | Ruta | CU-04 paso 3, HU-06 c.1 |
| RN-04.18 | Si el conductor la rechaza, sigue con la ruta original marcada como congestionada. | Ruta | CU-04 ext. 3a, HU-06 c.3 |
| RN-04.19 | Un mismo evento produce una sola propuesta por ruta. No se repite, la acepte o la rechace el conductor. | Ruta | CU-04 sección 7, HU-06 seguridad |
| RN-04.20 | La telemetría de un usuario fantasma produce congestión marcada como simulada. | EventoVial | CU-07 sección 8, RNF-12 |

El prototipo usa umbrales fijos de 25 y 10 km/h. Aquí dependen del tipo de vía, porque en una zona residencial lo normal ya es ir a 15 o 25 km/h. El cambio se confirmó el 06/10/2026 y está registrado como [P-02](../08_decisiones_parametros.md).

## Recorrido por Vista, Controlador y Modelo

La telemetría, que no tiene vista porque el conductor no interviene:

1. **Controlador de telemetría (app).** Con la app en pantalla y con permiso, lee el GPS y envía las lecturas al backend (RN-04.1 a RN-04.3).
2. **Controlador REST de telemetría (backend).** Comprueba el JWT (RN-04.4) y el formato. Pasa las lecturas al servicio del modelo.
3. **Modelo (backend).** Entrega las lecturas a `MotorDeRutas` sin guardarlas (RN-04.12).
4. **Modelo (motor core).** Filtra cada `LecturaTelemetría` (RN-04.5 a RN-04.7), la asigna a su `Tramo` (RN-04.8) y decide si hay congestión (RN-04.9, RN-04.10). Si la hay, avisa al backend.
5. **Modelo (backend).** Crea o actualiza el `EventoVial` de congestión (RN-04.10, RN-04.11). Desde ahí se comporta como cualquier evento de CU-02.

El recálculo:

1. **Controlador de navegación (app).** Mientras la ruta está Activa, envía al backend su posición y lo que le falta recorrer, junto con su consulta periódica.
2. **Controlador REST de rutas (backend).** Comprueba el JWT y llama al servicio del modelo que revisa rutas.
3. **Modelo (backend y motor core).** Comprueba si algún evento vigente toca lo que falta de la ruta (RN-04.13, RN-04.14). Si lo hay, calcula la alternativa y la compara con la ruta actual (RN-04.15, RN-04.16, RN-04.19).
4. **Controlador REST de rutas (backend).** Devuelve «sin cambios», «hay congestión y no hay alternativa» o la ruta alternativa.
5. **Controlador de navegación (app) y vista Navegación activa.** Muestran el aviso sin tapar la navegación (RN-04.17).
6. **Vista Navegación activa.** Si el conductor acepta, la alternativa pasa a ser la ruta Activa. Si la rechaza, sigue la original marcada como congestionada (RN-04.18).
