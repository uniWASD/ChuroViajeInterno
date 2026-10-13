# Backlog de Construcción — Núcleo C++ del motor core

[Índice del backlog](README.md)

Responsable: Marcelo. Código en `04_imp/motor_cpp`.

El núcleo guarda la red vial de Tarija en memoria, calcula rutas con A*, aplica el tráfico de los eventos y analiza la telemetría. No abre puertos ni llama a nadie: lo arranca el backend como proceso hijo y solo responde los mensajes que el wrapper `MotorDeRutas` le escribe por su entrada estándar, una línea de JSON por mensaje ([D-15](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). El prototipo de `05_pro/PruebaCalculoRuta` ya validó el algoritmo; aquí se lleva a C++ con datos reales. Antes de tomar un issue, lee la sección «Backend con el motor core» de [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md).

El servidor es ARM (Oracle Cloud, [D-16](../../03_des/02_Arquitectura/08_decisiones_parametros.md)): el núcleo se compila en el propio servidor y no debe depender de nada propio de x86.

| Sprint | Issues |
| --- | --- |
| S1 (15/10–21/10) | Estructura del núcleo y protocolo por pipe |
| S2 (22/10–28/10) | Red vial desde OpenStreetMap |
| S3 (29/10–04/11) | HU-05 Calcular la ruta óptima con A* |
| S4 (05/11–11/11) | HU-05 Tráfico de los eventos en los tramos |
| S5 (12/11–18/11) | HU-13 Detectar congestión con la telemetría · HU-06 Revisar la ruta activa |
| S7 (26/11–30/11) | Rendimiento con la red real de Tarija |

---

## Sprint 1

### [T-XXX][C++] Estructura del núcleo y protocolo por pipe

**Tipo:** Tarea de infraestructura (base del motor core; cubre CU-03 y CU-04)
**Tecnología:** C++ · **Sprint:** Sprint 1 (15/10–21/10) · **Responsable sugerido:** Marcelo
**Depende de:** nada
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:cpp`

**Descripción:** Crear el programa del núcleo con su bucle de mensajes, para que el wrapper de Java pueda hablar con él desde el sprint 2 aunque todavía no calcule rutas.

**Criterios de aceptación**
- [ ] El núcleo compila con CMake en Linux (x86 y ARM) y en Windows, en `04_imp/motor_cpp`.
- [ ] Lee una línea de JSON por petición en su entrada estándar y escribe una línea de JSON por respuesta en su salida estándar, con el mismo `id` de la petición.
- [ ] Reconoce las cinco operaciones del contrato. `consultar_estado` ya responde de verdad; las otras responden «no implementada» hasta que lleguen sus issues.
- [ ] Un mensaje mal formado o con una operación desconocida responde `"ok":false` con un `error` de código y descripción, y el núcleo sigue funcionando.
- [ ] Los registros van a la salida de errores; la salida estándar solo lleva respuestas.
- [ ] Al cerrarse la entrada estándar, el núcleo termina limpio.
- [ ] Las pruebas (GoogleTest o Catch2) corren con un solo comando.

**Tareas**
- [ ] Crear el proyecto CMake y agregar nlohmann/json y la librería de pruebas.
- [ ] Implementar el bucle de lectura, el despacho por `operacion` y las respuestas de error.
- [ ] Implementar `consultar_estado` (red cargada o no, fecha del extracto).
- [ ] Escribir un script corto que mande mensajes de prueba al núcleo por la terminal.
- [ ] Escribir el README de `04_imp/motor_cpp`: cómo compilar, probar y mandar un mensaje a mano.

**Casos de prueba**
- [ ] CP-XX Caso de prueba: `{"id":1,"operacion":"consultar_estado"}` responde `{"id":1,"ok":true,...}` en una sola línea.
- [ ] CP-XX Caso de prueba: una línea que no es JSON responde `"ok":false` y la siguiente petición válida se atiende normal.

**Relacionado:** RNF-02 · CU-03 · CU-04 · D-15 · [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md)

Lo subes en una nueva rama llamada "feature/TXX-cpp-estructura-nucleo" (No olvides que el source de la rama es la de develop)

---

## Sprint 2

### [T-XXX][C++] Red vial desde OpenStreetMap

**Tipo:** Tarea de desarrollo (cubre CU-03 y CU-04)
**Tecnología:** C++ · **Sprint:** Sprint 2 (22/10–28/10) · **Responsable sugerido:** Marcelo
**Depende de:** [T-XXX][C++] Estructura del núcleo y protocolo por pipe · [T-XXX][Java] Base de datos, catálogos y zona de cobertura (por el polígono de la mancha urbana)
**Etiquetas:** `tipo:tarea` · `fase:construccion` · `tec:cpp`

**Descripción:** Preparar la red de calles de Tarija a partir de un extracto de OpenStreetMap y cargarla en memoria al arrancar ([D-08](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). Es la base de `RedVial` y `Tramo`.

**Criterios de aceptación**
- [ ] Un script de preparación recorta el extracto de OpenStreetMap con el polígono de `04_imp/datos/mancha_urbana_tarija.geojson` y deja solo las vías vehiculares (RN-03.5).
- [ ] Cada calle de doble sentido queda como dos tramos y cada calle de un sentido como uno (RN-03.6).
- [ ] La longitud de cada tramo es la de su recorrido real, sumando sus segmentos, nunca menor que la línea recta entre sus extremos (RN-03.7).
- [ ] Cada tramo guarda el nombre de su calle y su tipo de vía: vía rápida, avenida principal o zona residencial, según una tabla de correspondencia con las etiquetas `highway` de OpenStreetMap que queda escrita en el README.
- [ ] El núcleo carga el archivo preparado al arrancar y `consultar_estado` informa cuántas intersecciones y tramos tiene y la fecha del extracto.
- [ ] Un punto se ajusta a la intersección vehicular más cercana; si no hay ninguna a menos de 100 m, el punto es inválido (RN-03.4, P-23).
- [ ] La carga tarda menos de 10 segundos en el servidor.

**Tareas**
- [ ] Descargar el extracto de Bolivia y recortar Tarija (osmium o un script en Python con pyosmium).
- [ ] Definir la tabla de etiquetas `highway` que cuentan como vehiculares y su tipo de vía, partiendo de las velocidades de `05_pro/CalculoVelocidad`.
- [ ] Definir el formato del archivo preparado (nodos y tramos) y documentarlo.
- [ ] Implementar `RedVial` y `Tramo` en C++ y la carga del archivo.
- [ ] Implementar la búsqueda de la intersección más cercana con un índice espacial (por ejemplo, una grilla).
- [ ] Guardar el script de preparación en `04_imp/motor_cpp/preparacion` y el archivo resultante fuera del repositorio, si pesa mucho.

**Casos de prueba**
- [ ] CP-XX Caso de prueba: en la red cargada, una calle peatonal conocida del centro no tiene ningún tramo.
- [ ] CP-XX Caso de prueba: una calle de un solo sentido conocida tiene un tramo y no el contrario.
- [ ] CP-XX Caso de prueba: un punto en medio de la Plaza Luis de Fuentes se ajusta a una esquina vecina; un punto fuera del polígono es inválido.

**Relacionado:** RF-05 · CU-03 · RN-03.4 a RN-03.7 · D-08 · P-23 · [03_modelo_dominio.md](../../03_des/02_Arquitectura/03_modelo_dominio.md)

Lo subes en una nueva rama llamada "feature/TXX-cpp-red-vial-osm" (No olvides que el source de la rama es la de develop)

---

## Sprint 3

### [HU-05][CU-03][C++] Calcular la ruta óptima con A*

**Caso de uso:** CU-03 — Calcular Ruta Óptima
**Tecnología:** C++ · **Sprint:** Sprint 3 (29/10–04/11) · **Responsable sugerido:** Marcelo
**Depende de:** [T-XXX][C++] Red vial desde OpenStreetMap
**Issue anterior:** #29 (prototipo en Python con manejo de errores)
**Etiquetas:** `caso-uso:CU-03` · `tipo:historia-usuario` · `fase:construccion` · `tec:cpp`

**Historia de usuario:** Como conductor de la app, quiero calcular la ruta vehicular óptima hacia mi destino, para llegar por el camino más eficiente considerando datos viales vigentes.

**Criterios de aceptación**
- [ ] `calcular_ruta` recibe origen, destino y paradas, y devuelve la ruta de menor costo que pasa por las paradas en el orden recibido (RN-03.10).
- [ ] El cálculo usa A* con la distancia en línea recta como estimación; Dijkstra queda disponible por configuración (RN-03.11).
- [ ] El costo de cada tramo es su longitud por el factor de su nivel de tráfico (RN-03.8); mientras no haya eventos, todos los tramos están fluidos (RN-03.9).
- [ ] La ruta nunca usa un tramo a contramano (RN-03.6).
- [ ] La respuesta trae `estado` `ENCONTRADA` con el trazado, la distancia, el tiempo estimado, y por cada tramo su identificador y el nombre de su calle (RN-03.13).
- [ ] El tiempo estimado suma, tramo por tramo, la longitud entre la velocidad esperada de su tipo de vía, corregida por su nivel de tráfico (RN-03.14).
- [ ] Sin camino posible responde `SIN_RUTA`, y con un punto que no se ajusta a la red, `PUNTO_INVALIDO`; ninguno de los dos es un error (RN-03.4, RN-03.12).
- [ ] Ninguna consulta tarda más de 1 segundo dentro del núcleo, para dejar margen a los 3 segundos de RNF-02.

**Tareas**
- [ ] Implementar A* sobre `RedVial`, con los niveles de tráfico y sus factores (1,0; 1,5; 2,5; 4,0) ya preparados para el issue de tráfico.
- [ ] Implementar el encadenado de tramos para las paradas.
- [ ] Implementar el cálculo del tiempo estimado.
- [ ] Llevar a C++ los casos CP-09 a CP-17 del prototipo, con la red irregular de prueba, para comprobar que el resultado es el mismo. CP-18 y CP-19 necesitan el tráfico y van en el sprint 4.
- [ ] Medir el tiempo con consultas al azar sobre la red real.

**Casos de prueba**
- [ ] CP-XX Caso de prueba: la red irregular del prototipo da en C++ las mismas rutas que daba en Python en CP-14 a CP-17.
- [ ] CP-XX Caso de prueba: con una parada intermedia, la ruta pasa por ella y la distancia es la suma de los dos tramos.
- [ ] CP-XX Caso de prueba: 100 consultas al azar en la red real tardan menos de 1 segundo cada una.

**Relacionado:** RF-05 · RNF-02 · CU-03 · RN-03.4, RN-03.6, RN-03.8, RN-03.10 a RN-03.14 · [05_pro/PruebaCalculoRuta](../../05_pro/PruebaCalculoRuta/README.md)

Lo subes en una nueva rama llamada "feature/HU05-CU03-cpp-calcular-ruta" (No olvides que el source de la rama es la de develop)

---

## Sprint 4

### [HU-05][CU-03][C++] Tráfico de los eventos en los tramos

**Caso de uso:** CU-03 — Calcular Ruta Óptima
**Tecnología:** C++ · **Sprint:** Sprint 4 (05/11–11/11) · **Responsable sugerido:** Marcelo
**Depende de:** [HU-05][CU-03][C++] Calcular la ruta óptima con A*
**Etiquetas:** `caso-uso:CU-03` · `tipo:historia-usuario` · `fase:construccion` · `tec:cpp`

**Historia de usuario:** Como conductor de la app, quiero calcular la ruta vehicular óptima hacia mi destino, para llegar por el camino más eficiente considerando datos viales vigentes.

Este issue cubre la parte de «datos viales vigentes»: cómo los eventos cambian el costo de cada tramo.

**Criterios de aceptación**
- [ ] `actualizar_trafico` recibe un evento que nace, cambia su vencimiento, se retira o vence (identificador, tipo, nivel si es congestión, ubicación y vencimiento) y responde los tramos afectados.
- [ ] Un evento reportado por un conductor se asigna a la calle vehicular más cercana y afecta a sus dos sentidos; una congestión, solo al tramo y sentido donde se midió (D-18).
- [ ] El nivel del tramo sale de los eventos vigentes sobre él y, si hay varios, vale el más grave (RN-03.9).
- [ ] Los factores son los de P-12: accidente ×4,0; control policial y otro ×1,5; congestión ×2,5 o ×4,0 según el nivel que trae el mensaje.
- [ ] Un bloqueo vigente cierra el tramo: ninguna ruta pasa por él (RN-03.9).
- [ ] Al retirarse o vencer el último evento de un tramo, el tramo vuelve a fluido (RN-02.15).
- [ ] Un evento cuyo vencimiento ya pasó deja de contar aunque el aviso de vencimiento no haya llegado; si llega un vencimiento nuevo para el mismo evento, vale el nuevo.

**Tareas**
- [ ] Implementar la tabla de eventos vigentes por tramo.
- [ ] Implementar el nivel del tramo según P-12 y el cierre por bloqueo.
- [ ] Llevar a C++ los casos CP-18 y CP-19 del prototipo.
- [ ] Escribir pruebas de nacer, retirar y vencer.

**Casos de prueba**
- [ ] CP-XX Caso de prueba: un accidente sobre la calle directa entre dos puntos hace que la ruta tome el rodeo cuando el rodeo cuesta menos que la calle por 4,0.
- [ ] CP-XX Caso de prueba: un bloqueo sobre la única calle de acceso a un punto hace que la ruta a ese punto responda `SIN_RUTA`.
- [ ] CP-XX Caso de prueba: al retirarse el bloqueo, la misma ruta vuelve a encontrarse.
- [ ] CP-XX Caso de prueba: un bloqueo en una calle de doble sentido impide pasar por ella en los dos sentidos; una congestión en esa calle solo encarece el sentido donde se midió.

**Relacionado:** RF-05 · RF-06 · CU-03 · CU-04 · RN-02.15, RN-03.9 · P-12 · D-14, D-18

Lo subes en una nueva rama llamada "feature/HU05-CU03-cpp-trafico-tramos" (No olvides que el source de la rama es la de develop)

---

## Sprint 5

### [HU-13][CU-04][C++] Detectar congestión con la telemetría

**Caso de uso:** CU-04 — Recalcular Ruta
**Tecnología:** C++ · **Sprint:** Sprint 5 (12/11–18/11) · **Responsable sugerido:** Marcelo
**Depende de:** [HU-05][CU-03][C++] Tráfico de los eventos en los tramos
**Issue anterior:** #3 (prototipo de cálculo de velocidad)
**Etiquetas:** `caso-uso:CU-04` · `tipo:historia-usuario` · `fase:construccion` · `tec:cpp`

**Historia de usuario:** Como sistema, quiero recibir la posición de los conductores mientras tienen la app abierta, para calcular su velocidad y detectar congestión sin depender solo de los reportes manuales.

Este issue cubre la parte del núcleo: analizar las lecturas y decidir si hay congestión.

**Criterios de aceptación**
- [ ] `analizar_telemetria` recibe lecturas agrupadas por identificador temporal de recorrido, con la marca de simulado, sin datos del conductor.
- [ ] La velocidad sale de la distancia y el tiempo entre dos lecturas seguidas del mismo recorrido (RN-04.5).
- [ ] Se descarta la lectura con precisión peor que 50 m o que implica más de 150 km/h (RN-04.6).
- [ ] Una lectura de 25 km/h o menos con un giro mayor a 40 grados se toma como giro y no como congestión (RN-04.7).
- [ ] Cada lectura válida se asigna al tramo vehicular donde está; si el tramo más cercano está a más de 30 m, la lectura se ignora (RN-04.8, P-24).
- [ ] Un tramo está congestionado por debajo de la mitad de la velocidad esperada de su tipo de vía y muy congestionado por debajo de la cuarta parte (RN-04.9, P-02).
- [ ] Hay congestión cuando un recorrido sigue lento en un tramo durante 2 minutos seguidos; entonces la respuesta informa el tramo, un punto y el nivel (RN-04.10).
- [ ] Los recorridos simulados y los reales se analizan por separado: una congestión que sale de recorridos simulados se informa como simulada (RN-04.20, RN-07.6).
- [ ] El núcleo solo guarda la última lectura de cada recorrido y la olvida a los 5 minutos sin lecturas nuevas (RN-04.12, P-25).

**Tareas**
- [ ] Llevar a C++ las fórmulas de `05_pro/CalculoVelocidad` (Haversine y rumbo), con los umbrales por tipo de vía de P-02 en lugar de los fijos del prototipo.
- [ ] Implementar el estado en memoria por recorrido y por tramo.
- [ ] Implementar el ajuste de una lectura al tramo más cercano.
- [ ] Escribir pruebas con recorridos fabricados: fluido, lento, detenido en un giro.

**Casos de prueba**
- [ ] CP-XX Caso de prueba: un recorrido a 8 km/h durante 2 minutos en una avenida principal produce congestión; durante 1 minuto, no.
- [ ] CP-XX Caso de prueba: un vehículo que frena para doblar una esquina no produce congestión.
- [ ] CP-XX Caso de prueba: una lectura con precisión de 80 m se descarta.
- [ ] CP-XX Caso de prueba: un recorrido simulado lento produce una congestión informada como simulada.

**Relacionado:** RF-14 · RNF-13 · CU-04 · RN-04.5 a RN-04.10, RN-04.12, RN-04.20 · P-02, P-24, P-25 · D-07 · [05_pro/CalculoVelocidad](../../05_pro/CalculoVelocidad/CU-04_CalculoVelocidad.md)

Lo subes en una nueva rama llamada "feature/HU13-CU04-cpp-detectar-congestion" (No olvides que el source de la rama es la de develop)

---

### [HU-06][CU-04][C++] Revisar la ruta activa

**Caso de uso:** CU-04 — Recalcular Ruta
**Tecnología:** C++ · **Sprint:** Sprint 5 (12/11–18/11) · **Responsable sugerido:** Marcelo
**Depende de:** [HU-05][CU-03][C++] Tráfico de los eventos en los tramos
**Etiquetas:** `caso-uso:CU-04` · `tipo:historia-usuario` · `fase:construccion` · `tec:cpp`

**Historia de usuario:** Como conductor navegando una ruta activa, quiero que el sistema me proponga una ruta alternativa cuando un evento vial afecte mi camino, para evitar demoras sin tener que buscarla yo.

**Criterios de aceptación**
- [ ] `revisar_ruta` recibe la posición actual, los identificadores de los tramos que faltan, el destino, las paradas que faltan y los eventos ya avisados.
- [ ] Busca algún evento vigente, de cualquier tipo y origen, sobre los tramos que faltan, sin contar los ya avisados (RN-04.14).
- [ ] Si lo hay, calcula la alternativa desde la posición actual, con el destino y las paradas que faltan (RN-04.15).
- [ ] Propone la alternativa solo si cuesta menos que seguir por la ruta actual con el tráfico al día; si no, responde «congestión sin alternativa» (RN-04.16).
- [ ] Cuando la respuesta es una alternativa o «congestión sin alternativa», dice qué evento la provocó, para que la app no lo vuelva a avisar (RN-04.19).
- [ ] Si los identificadores de tramo ya no existen porque se recargó la red, responde «ruta desactualizada» y la app pide la ruta de nuevo.

**Tareas**
- [ ] Implementar la búsqueda de eventos vigentes sobre los tramos que faltan.
- [ ] Reutilizar `calcular_ruta` para la alternativa y comparar costos con el tráfico actual.
- [ ] Escribir pruebas de los tres resultados.

**Casos de prueba**
- [ ] CP-XX Caso de prueba: un accidente sobre un tramo que falta produce una alternativa más barata.
- [ ] CP-XX Caso de prueba: un control policial (×1,5) sobre un tramo cuya alternativa cuesta más produce «congestión sin alternativa».
- [ ] CP-XX Caso de prueba: el mismo evento, enviado como ya avisado, produce «sin cambios».

**Relacionado:** RF-06 · CU-04 · RN-04.13 a RN-04.16, RN-04.19 · D-12

Lo subes en una nueva rama llamada "feature/HU06-CU04-cpp-revisar-ruta" (No olvides que el source de la rama es la de develop)

---

## Sprint 7

### [T-XXX][C++] Rendimiento con la red real de Tarija

**Tipo:** Tarea de pruebas (cubre CU-03 y CU-04)
**Tecnología:** C++ · **Sprint:** Sprint 7 (26/11–30/11) · **Responsable sugerido:** Marcelo
**Depende de:** [HU-06][CU-04][C++] Revisar la ruta activa · [T-XXX][Simulador] Pruebas de carga y reporte
**Etiquetas:** `tipo:prueba` · `fase:construccion` · `tec:cpp`

**Descripción:** Medir el núcleo en el servidor real, con la red de Tarija y la carga del simulador, y ajustar los parámetros que los documentos dejan «a ajustar con pruebas» (niveles de tráfico, P-02, P-23, P-24 y P-25).

**Criterios de aceptación**
- [ ] La ruta más lenta de 1000 consultas al azar tarda menos de 1 segundo dentro del núcleo en el servidor.
- [ ] Durante las pruebas de carga del simulador, el núcleo no se reinicia por demoras y su memoria queda estable durante una hora. El tiempo que ve la app (RNF-02) lo mide el issue del simulador.
- [ ] Los factores de los niveles de tráfico y los umbrales de congestión quedan revisados con los resultados, y cualquier cambio se anota en `08_decisiones_parametros.md`.

**Tareas**
- [ ] Escribir el banco de medición de rutas.
- [ ] Revisar el consumo de memoria con la carga del simulador.
- [ ] Anotar los resultados en `05_pro`.

**Casos de prueba**
- [ ] CP-XX Caso de prueba: 1000 rutas al azar en la red real, en el servidor, cada una en menos de 1 segundo.

**Relacionado:** RNF-02 · CU-03 · CU-04 · P-02, P-12, P-23, P-24, P-25

Lo subes en una nueva rama llamada "feature/TXX-cpp-rendimiento" (No olvides que el source de la rama es la de develop)
