# CU-04 — Recalcular ruta

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-06 y HU-13, y los requisitos RF-06, RF-14 y RNF-13. Las reglas RN-04.1 a RN-04.12 y RN-04.22 a RN-04.25 son de la telemetría que detecta la congestión; de RN-04.13 a RN-04.19 y RN-04.21, del recálculo. RN-04.20 cubre los datos del simulador.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-04.1 | La app obtiene la posición del GPS solo mientras está abierta en pantalla y tiene permiso de ubicación, y envía posición, precisión y momento; la velocidad la calcula el motor core (RN-04.5). Al pasar a segundo plano o cerrarse, deja de obtenerla y de enviarla. | Controlador de telemetría de la app | HU-13 c.1 y c.2, RNF-13 |
| RN-04.2 | La telemetría se envía con o sin ruta activa. | Controlador de telemetría de la app | HU-13 c.1, Visión 1.2.2 |
| RN-04.3 | Sin permiso de ubicación no hay telemetría, y todo lo que no depende de la ubicación sigue funcionando. | Controlador de telemetría de la app | HU-13 c.4 |
| RN-04.4 | Solo se acepta telemetría de conductores con sesión vigente. | Sesión | HU-13 seguridad, RNF-08 |
| RN-04.5 | La velocidad se calcula con la distancia entre dos lecturas seguidas y el tiempo que pasó entre ellas. | LecturaTelemetría | `05_pro/CalculoVelocidad`, issue #3 |
| RN-04.6 | Una lectura se descarta si su precisión es peor que 50 metros o si implica una velocidad mayor a 150 km/h. | LecturaTelemetría | `05_pro/CalculoVelocidad` |
| RN-04.7 | Una lectura lenta, de 25 km/h o menos, con un cambio de dirección mayor a 40 grados se toma como un giro y no cuenta como congestión. | LecturaTelemetría | `05_pro/CalculoVelocidad`, issue #3 |
| RN-04.8 | Cada lectura válida se asigna al tramo vehicular donde está el vehículo; en una calle de doble sentido, al del sentido en que avanza. Fuera de una vía vehicular, la lectura se ignora. | RedVial, Tramo | Visión 1.2.2, [D-18](../08_decisiones_parametros.md) |
| RN-04.9 | Un tramo se clasifica como congestionado cuando se circula por debajo de la mitad de la velocidad esperada de su tipo de vía, y como muy congestionado por debajo de la cuarta parte. | Tramo | Prototipo `Analisis_Trafico.java`, [P-02](../08_decisiones_parametros.md) |
| RN-04.10 | Hay congestión cuando un vehículo sigue lento durante 2 minutos seguidos en un tramo, o en las cuadras seguidas de la misma calle (RN-04.24), y además cumple RN-04.22 y RN-04.23. Entonces el backend registra un `EventoVial` de tipo Congestión, con origen Telemetría, ubicado en ese tramo. | EventoVial, Tramo | HU-13 c.3, Visión 1.2.2, [D-04](../08_decisiones_parametros.md), [P-02](../08_decisiones_parametros.md) |
| RN-04.11 | Si ese tramo ya tiene una congestión vigente, no se crea otra: se actualiza el momento de la existente, que así dura mientras siga la lentitud. | EventoVial | Propuesta |
| RN-04.12 | Las lecturas no se conservan después de analizarse y nunca se muestran a otro usuario. El evento de congestión no indica qué conductor lo originó. | LecturaTelemetría | Visión 6.3, HU-13 privacidad, RNF-10 |
| RN-04.13 | El recálculo solo aplica a una ruta Activa. | Ruta | CU-04 precondición |
| RN-04.14 | Se evalúa cuando un evento vigente, de cualquier tipo y origen, cae sobre un tramo que al conductor todavía le falta recorrer. | Ruta, EventoVial | CU-04 disparador y paso 1, HU-06 c.1 |
| RN-04.15 | La alternativa parte de la posición actual del conductor y conserva el destino y las paradas que faltan. | Ruta | CU-04 paso 2 |
| RN-04.16 | Solo se propone la alternativa si cuesta menos que seguir por la ruta actual con el tráfico al día. Si no hay una mejor, se mantiene la ruta original y se avisa del evento. | Ruta | CU-04 ext. 2a, HU-06 c.2 |
| RN-04.17 | La alternativa se muestra sin interrumpir la navegación, y el conductor puede aceptarla o rechazarla. | Ruta | CU-04 pasos 3 y 4 y ext. 3a, HU-06 c.1 |
| RN-04.18 | Si el conductor la rechaza, sigue con la ruta original, con el evento a la vista. | Ruta | CU-04 ext. 3a, HU-06 c.3 |
| RN-04.19 | Un mismo evento produce una sola propuesta por ruta. No se repite, la acepte o la rechace el conductor. | Ruta | CU-04 sección 7, HU-06 seguridad |
| RN-04.20 | La telemetría de un usuario fantasma produce congestión marcada como simulada. | EventoVial | CU-07 sección 8, RNF-12 |
| RN-04.21 | Si el conductor no responde al aviso de ruta alternativa en 15 segundos, el aviso se cierra solo, sigue la ruta original y el evento cuenta como ya avisado (RN-04.19). | Ruta | CU-04 ext. 4a, HU-06 c.4, [D-19](../08_decisiones_parametros.md), [P-26](../08_decisiones_parametros.md) |
| RN-04.22 | **Venía manejando.** Un recorrido solo produce congestión si, en los últimos 10 minutos, estuvo al menos 15 segundos seguidos por encima de 15 km/h. Así no cuenta quien camina o abrió la app sin moverse. | LecturaTelemetría | Issue #172, [P-36](../08_decisiones_parametros.md) |
| RN-04.23 | **Tiene que avanzar.** Durante la racha lenta, el recorrido tiene que alejarse al menos 25 m en línea recta del punto donde empezó la racha. Una fila que avanza a tirones cuenta; un teléfono quieto, aunque el GPS tiemble, no. Se mide de punta a punta y no sumando pasos, para que el temblor no se acumule. | LecturaTelemetría | Issue #172, [P-37](../08_decisiones_parametros.md) |
| RN-04.24 | La racha lenta sigue cuando el recorrido pasa a la cuadra siguiente de la misma calle: el tramo que empieza donde terminó el anterior, con el mismo nombre y con menos de 40 grados de cambio de dirección. Doblar a otra calle corta la racha. Al cumplir los 2 minutos se informan como congestionadas todas las cuadras de la racha. | Tramo, RedVial | Issue #172, [D-44](../08_decisiones_parametros.md), [P-38](../08_decisiones_parametros.md) |
| RN-04.25 | Cada congestión que informa el motor trae el trazado de su tramo. El backend lo guarda al crear la congestión y la app la dibuja como una línea sobre la calle, con el color de su nivel. Una congestión sin trazado se sigue mostrando como un punto. Desde #191, las cuadras congestionadas seguidas, del mismo sentido y del mismo nivel, se dibujan como una sola línea de donde empieza a donde acaba, y la ruta se pinta de amarillo o de rojo donde pasa por una congestión de su mismo sentido. | EventoVial, Tramo | Issue #172, [D-45](../08_decisiones_parametros.md) |

El prototipo usa umbrales fijos de 25 y 10 km/h. Aquí dependen del tipo de vía, porque en una zona residencial lo normal ya es ir a 15 o 25 km/h. El cambio se confirmó el 06/10/2026 y está registrado como [P-02](../08_decisiones_parametros.md).

**Gente quieta que marcaba tráfico (Sprint 5.5, #172).** Al probar la app, un teléfono quieto 2 minutos junto a una avenida producía congestión. RN-04.22 y RN-04.23 lo corrigen. La idea sale de cómo trabaja Waze: detecta un atasco cuando la velocidad de los usuarios que van por un segmento queda por debajo de lo normal, y mide esa velocidad con el tiempo que tardan en recorrerlo; en su comunidad se reconoce que un usuario detenido (cargando combustible o tomando un café) puede crear un atasco que no existe. Fuentes: [Waze Data Feed](https://support.google.com/waze/partners/answer/10618035), [Waze · Traffic data](https://wazeopedia.waze.com/wiki/USA/Traffic_data), [Waze · Detect parking vs slow traffic](https://www.waze.com/discuss/t/suggestions-detect-parking-vs-slow-traffic/101531), [Waze · Does Waze use regular traffic info](https://www.waze.com/discuss/t/does-waze-use-regular-traffic-info/85018). Waze no publica sus umbrales; los de P-36 y P-37 son nuestros.

**Cuadras cortas.** En la red de Tarija un tramo mide en promedio 82 m en avenidas y 91 m en zonas residenciales (`04_imp/motor_cpp/preparacion/VERIFICACION.md`). Para quedarse 2 minutos en una sola cuadra había que promediar menos de 2,5 km/h, y con RN-04.23 solo habría contado quien fuera entre 0,75 y 2,5 km/h. Por eso se adelantó RN-04.24 ([D-44](../08_decisiones_parametros.md)). Sigue pendiente exigir dos recorridos distintos en el mismo tramo.

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
4. **Controlador REST de rutas (backend).** Devuelve «sin cambios», «sin alternativa» o la ruta alternativa, con el evento que la provocó.
5. **Controlador de navegación (app) y vista Navegación activa.** Muestran el aviso sin tapar la navegación (RN-04.17).
6. **Vista Navegación activa.** Si el conductor acepta, la alternativa pasa a ser la ruta Activa. Si la rechaza, sigue la original con el evento a la vista (RN-04.18). Si no responde en 15 segundos, el aviso se cierra y sigue la original (RN-04.21).
