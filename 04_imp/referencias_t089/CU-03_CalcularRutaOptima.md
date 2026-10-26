# CU-03 — Calcular ruta óptima

[Índice de reglas](README.md) · [Índice de la arquitectura](../README.md)

Cubre HU-05 y los requisitos RF-05, RNF-02 y RNF-10.

## Reglas

| Código | Regla | Objeto responsable | Fuente |
| --- | --- | --- | --- |
| RN-03.1 | Solo un conductor con sesión vigente puede pedir una ruta. | Sesión | CU-03 precondición, RNF-08 |
| RN-03.2 | Una solicitud lleva origen y destino obligatorios, y paradas intermedias opcionales en el orden que eligió el conductor. | Ruta, Parada | CU-03 paso 1, HU-05 c.1 |
| RN-03.3 | Origen, destino y paradas deben caer dentro de la mancha urbana de Tarija. Si alguno queda fuera, se responde «Origen o destino inválido» y se pide otro punto. | Ubicación | CU-03 ext. 1a, HU-05 c.2 |
| RN-03.4 | Cada punto se ajusta a la intersección vehicular más cercana de la red vial. Si no hay ninguna cerca, el punto es inválido. | RedVial | Visión 1.2.2, propuesta |
| RN-03.5 | La red vial contiene solo vías vehiculares. Una ruta nunca pasa por aceras ni vías peatonales. | RedVial | Visión 1.2.2, HU-05 c.1 |
| RN-03.6 | Un tramo se recorre únicamente en su sentido. Una calle de doble sentido son dos tramos. | Tramo | Issue #15 |
| RN-03.7 | La longitud de un tramo es su recorrido real, nunca menor que la línea recta entre sus extremos. | Tramo | Issues #15 y #29 |
| RN-03.8 | El costo de un tramo es su longitud multiplicada por el factor de su nivel de tráfico. | Tramo | Prototipo de ruteo, issue #2 |
| RN-03.9 | El nivel de tráfico de un tramo lo fijan los eventos vigentes ubicados sobre él. Sin eventos, el tramo está fluido. Un bloqueo vigente cierra el tramo y ninguna ruta pasa por él. Si hay varios, vale el más grave. | Tramo, EventoVial | CU-03 descripción, CU-04, [P-12](../08_decisiones_parametros.md) |
| RN-03.10 | La ruta óptima es la de menor costo total entre origen y destino, pasando por las paradas en su orden. | Ruta | CU-03 paso 3 |
| RN-03.11 | El cálculo usa A\* con la distancia en línea recta como estimación. Dijkstra queda como alternativa configurable. | RedVial | CU-03 sección 8, issue #15 |
| RN-03.12 | Si no existe camino vehicular entre los puntos, la respuesta es «No se encontró una ruta válida». No es un error del sistema. | Ruta | CU-03 ext. 3b, HU-05 c.4, issue #29 |
| RN-03.13 | Toda ruta se entrega con su trazado, su distancia y su tiempo estimado, y la app los muestra antes de que el conductor confirme. | Ruta | CU-03 paso 4, HU-05 |
| RN-03.14 | El tiempo estimado suma, tramo por tramo, la longitud dividida entre la velocidad esperada de ese tipo de vía, corregida por el nivel de tráfico. | Ruta, Tramo | Propuesta, con las velocidades de `05_pro/CalculoVelocidad` |
| RN-03.15 | La respuesta debe llegar en menos de 3 segundos. Si el motor core no responde a tiempo, se informa el fallo y se permite reintentar, sin dejar la pantalla a medias. | MotorDeRutas | CU-03 garantía mínima, RNF-02 |
| RN-03.16 | El cálculo no usa ni devuelve rutas o posiciones de otros conductores. | Ruta | CU-03 sección 7, RNF-10 |
| RN-03.17 | Al confirmar, la ruta pasa a Activa y la app empieza a seguirla. El servidor no la guarda. | Ruta | CU-03 paso 5, [D-05](../08_decisiones_parametros.md) |
| RN-03.18 | Si el mapa base de Mapbox no carga, la app informa un error de servicio externo y sugiere reintentar. El cálculo de la ruta no depende de Mapbox. | Controlador de ruta de la app | CU-03 ext. 3a, [D-08](../08_decisiones_parametros.md) |

El factor de cada tipo de evento sobre un tramo (RN-03.9) está en [P-12](../08_decisiones_parametros.md).

## Recorrido por Vista, Controlador y Modelo

1. **Vista Cómo llegar.** El conductor indica origen, destino y, si quiere, paradas. Toca «Calcular ruta».
2. **Controlador de ruta (app).** Comprueba que haya origen y destino, y envía la solicitud al backend con el JWT.
3. **Controlador REST de rutas (backend).** Comprueba el JWT (RN-03.1) y el formato. Llama al servicio del modelo que calcula rutas.
4. **Modelo (backend).** Valida los puntos (RN-03.2, RN-03.3) y pasa la solicitud a `MotorDeRutas`.
5. **Modelo (motor core).** La `RedVial` ajusta cada punto a una intersección (RN-03.4). Cada `Tramo` da su costo según su tráfico (RN-03.6 a RN-03.9). El motor arma la `Ruta` de menor costo, con distancia y tiempo (RN-03.10 a RN-03.14).
6. **Modelo (backend).** Recibe la ruta o el motivo por el que no hay ruta (RN-03.12, RN-03.15).
7. **Controlador REST de rutas (backend).** Devuelve la ruta con su tiempo y su distancia, o el mensaje correspondiente.
8. **Controlador de ruta (app) y vista.** Dibujan la ruta y muestran tiempo y distancia. Si hubo fallo, muestran el mensaje y dejan reintentar.
9. **Vista Cómo llegar.** El conductor toca «Iniciar»; la ruta pasa a Activa y se abre la vista Navegación activa (RN-03.17).

El motor core conoce el tráfico porque el backend lo mantiene al día: le avisa cada vez que un evento nace, se retira o vence.
