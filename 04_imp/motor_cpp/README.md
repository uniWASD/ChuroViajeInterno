# Núcleo C++ del motor core

Programa que mantiene la red vial y calcula rutas ([D-15](../../03_des/02_Arquitectura/08_decisiones_parametros.md)). No tiene servidor web ni abre puertos: el backend lo arranca como proceso hijo a través del wrapper Java `MotorDeRutas` y le habla por pipe. El contrato completo está en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#backend-con-el-motor-core).

**Estado (T-090, T-096, #58, #66, #76 y #73):** estructura, protocolo, carga de la red vial, cálculo de rutas con A*, tráfico de los eventos, revisión de la ruta activa y detección de congestión con la telemetría. Las cinco operaciones del contrato responden de verdad.

## Arranque y red vial

```
motor_nucleo --red <archivo> [--algoritmo a_estrella|dijkstra]
```

`--red` es la ruta del archivo JSON que arma el script de `preparacion/` (formato en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#archivo-de-la-red-vial)). El núcleo lo carga completo antes de atender la primera petición, así que la primera respuesta puede demorar unos segundos. Si el archivo falta, no es JSON, tiene otra versión o rompe una regla del formato, el núcleo lo anota en la salida de errores y arranca igual **sin red**: `consultar_estado` responde `"red_cargada":false` y el wrapper no lo da por listo. Sin `--red` pasa lo mismo, sin mensaje. Un argumento desconocido termina el programa con código 2.

`RedVial::ajustar(lat, lon)` devuelve la intersección más cercana a un punto, o nada si no hay ninguna a menos de 100 m (RN-03.4, P-23); usa una grilla con celdas de 100 m.

`--algoritmo` elige cómo se calculan las rutas (RN-03.11): `a_estrella` por omisión, o `dijkstra`. Los dos dan la misma ruta; Dijkstra solo sirve para comparar.

## Calcular una ruta

```json
{"id":17,"operacion":"calcular_ruta","origen":[-21.5355,-64.7296],"destino":[-21.5300,-64.7260],"paradas":[]}
{"id":17,"ok":true,"estado":"ENCONTRADA","distancia_m":755,"tiempo_s":136,"trazado":[[-21.5355,-64.7296],[-21.5348,-64.728],[-21.53,-64.726]],"tramos":[{"id":"Plaza-Mercado","calle":"Calle Comercio"},{"id":"Mercado-Universidad","calle":"Av. Las Américas"}]}
```

(Respuesta con `tests/datos/red_irregular.json`, la red del prototipo de `05_pro/PruebaCalculoRuta`.)

- Cada punto se ajusta a la intersección más cercana. Si alguno queda a más de 100 m de la red, la respuesta es `PUNTO_INVALIDO`; si no hay camino entre dos puntos seguidos, `SIN_RUTA`. Las dos son `"ok":true` (RN-03.4, RN-03.12).
- La ruta pasa por las paradas en el orden recibido (RN-03.10) y nunca recorre un tramo a contramano (RN-03.6).
- El costo de un tramo es su longitud por el factor de su nivel de tráfico: 1,0, 1,5, 2,5 o 4,0, y un tramo cerrado no se usa (RN-03.8). A* estima con la distancia en línea recta, que nunca supera el costo real.
- El tiempo estimado suma la longitud de cada tramo entre la velocidad de su tipo de vía (55, 40 y 20 km/h), por su factor de tráfico (RN-03.14).
- La distancia y el tiempo van redondeados a metros y segundos; las coordenadas del trazado, a seis decimales.

## Herramienta de consola: ruta entre dos puntos

Para probar el cálculo sin el backend, `ruta_nodos` recibe un origen y un destino y devuelve la lista de nodos de la ruta, con sus coordenadas, su distancia, su tiempo y las calles por las que pasa. Cada extremo puede ser un id de nodo o unas coordenadas `lat,lon` (sin espacios). No la usa el backend: el contrato sigue siendo `calcular_ruta`.

```
./build/ruta_nodos --red tests/datos/red_irregular.json Plaza Mirador
{"origen":"Plaza","destino":"Mirador","nodo_origen":"Plaza","nodo_destino":"Mirador","estado":"ENCONTRADA","nodos":["Plaza","Mercado","Universidad","Mirador"],"coordenadas":[[-21.5355,-64.7296],[-21.5348,-64.728],[-21.53,-64.726],[-21.529,-64.722]],"distancia_m":1526,"tiempo_s":275,"calles":["Calle Comercio","Av. Las Américas","Camino al Mirador"]}

./build/ruta_nodos --red tests/datos/red_irregular.json -21.5356,-64.7297 -21.5291,-64.7221
```

Con coordenadas, cada punto se ajusta a la intersección más cercana si hay una a menos de 100 m, igual que en `calcular_ruta` (RN-03.4). La respuesta dice a qué nodo se ajustó (`nodo_origen`, `nodo_destino`) y a cuántos metros (`ajuste_origen_m`, `ajuste_destino_m`). Si no hay ninguna intersección cerca, el estado es `PUNTO_INVALIDO`. Los otros estados son `ENCONTRADA`, `SIN_RUTA` y `NODO_DESCONOCIDO`. En PowerShell, las coordenadas van entre comillas (`"-21.5356,-64.7297"`), porque una coma sin comillas separa elementos.

Sin los dos extremos, lee una consulta por línea (`origen destino`) hasta que se cierra la entrada. Con la red real, los ids son los del archivo (`osm:<número>` o `corte:...`), así que lo cómodo es escribir coordenadas. Esta herramienta calcula sin tráfico.

## Tráfico de los eventos

El backend avisa con `actualizar_trafico` cada vez que un evento nace, cambia su vencimiento, se retira o vence, y al arrancar el núcleo le reenvía todos los vigentes (D-14). El formato está en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#cómo-viajan-los-mensajes-por-el-pipe).

```json
{"id":18,"operacion":"actualizar_trafico","evento":"153","accion":"nace","tipo":"ACCIDENTE","ubicacion":[-21.5352,-64.7288],"vence":"2026-10-06T16:02:00-04:00"}
{"id":18,"ok":true,"tramos":["osm:5001:1:ida","osm:5001:1:vuelta"]}
```

- Un evento de un conductor se asigna a la calle cuyo trazado pasa más cerca, si está a menos de 100 m, y afecta a sus dos sentidos; más lejos, a ninguno. Una congestión afecta solo al tramo que trae en `tramo` (RN-03.9, D-18).
- Factores (P-12): accidente ×4,0; control policial y otro ×1,5; congestión ×2,5 o ×4,0 según su `nivel`. Un bloqueo cierra el tramo. Si hay varios eventos sobre un tramo, vale el más grave.
- Al retirarse o vencer el último evento de un tramo, el tramo vuelve a fluido (RN-02.15). Un evento cuyo vencimiento pasó deja de contar aunque no llegue su aviso, y un aviso nuevo del mismo evento reemplaza al anterior.
- Los eventos viven solo en la memoria del núcleo: si se reinicia, el wrapper se los vuelve a enviar.

## Revisar la ruta activa

Mientras el conductor navega, la app manda lo que le falta de la ruta y el núcleo revisa si un evento la afecta (CU-04, #76). El formato está en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#revisar-la-ruta-activa-revisar_ruta).

```json
{"id":21,"operacion":"revisar_ruta","posicion":[-21.5355,-64.7296],"tramos":["Plaza-Mercado","Mercado-Universidad","Universidad-Mirador"],"destino":[-21.529,-64.722],"paradas":[],"eventos_avisados":[]}
{"id":21,"ok":true,"estado":"ALTERNATIVA","evento":"153","costo":1700,"costo_actual":3244,"distancia_m":1700,"tiempo_s":306,"trazado":[[-21.5355,-64.7296],[-21.533,-64.731],[-21.53,-64.726],[-21.529,-64.722]],"tramos":[{"id":"Plaza-Hospital","calle":"Av. Circunvalación"},{"id":"Hospital-Universidad","calle":"Calle La Cuesta (bajada)"},{"id":"Universidad-Mirador","calle":"Camino al Mirador"}]}
```

(Respuesta con `tests/datos/red_irregular.json` y un accidente en la Av. Las Américas.)

- Busca, en el orden de la ruta, el primer evento vigente sobre un tramo que falta que no esté en `eventos_avisados`. Cuenta cualquier tipo y origen, también la congestión por telemetría (RN-04.14, RN-04.19).
- Si no hay ninguno, responde `SIN_CAMBIOS`. Si lo hay, calcula la alternativa desde la posición actual, con el destino y las paradas que faltan (RN-04.15).
- Propone la alternativa (`ALTERNATIVA`) solo si cuesta menos que seguir por la ruta actual con el tráfico al día; si no, responde `SIN_ALTERNATIVA` (RN-04.16). Las dos traen el `evento` que las provocó, para que la app lo agregue a los ya avisados.
- Los dos costos se miden desde la misma intersección: la más cercana a la posición. Un bloqueo en lo que falta hace imposible seguir, y entonces la respuesta no trae `costo_actual`.
- Si algún id de `tramos` no existe (se recargó la red vial), responde `RUTA_DESACTUALIZADA` y la app pide la ruta de nuevo.

## Detectar congestión con la telemetría

El backend le pasa las lecturas del GPS agrupadas por un identificador temporal de recorrido, sin datos del conductor (CU-04, #73). El formato está en [05_contratos.md](../../03_des/02_Arquitectura/05_contratos.md#analizar-la-telemetría-analizar_telemetria).

```json
{"id":32,"operacion":"analizar_telemetria","recorridos":[{"recorrido":"3f9a1c","simulado":false,"lecturas":[{"posicion":[-21.54,-64.735357],"precision_m":8,"momento":"2026-11-14T10:02:03-04:00"}]}]}
{"id":32,"ok":true,"congestiones":[{"tramo":"avenida:ida","punto":[-21.54,-64.735357],"nivel":"MUY_CONGESTIONADO","simulado":false}],"lecturas_validas":1,"lecturas_descartadas":0}
```

(Respuesta con `tests/datos/red_telemetria.json`, después de 2 minutos de un recorrido a 8 km/h por la avenida.)

Las fórmulas son las de `05_pro/CalculoVelocidad` (haversine y azimut); los umbrales de lentitud dependen del tipo de vía (P-02):

- La velocidad sale de la distancia y el tiempo entre dos lecturas seguidas del mismo recorrido (RN-04.5).
- Se descarta la lectura con precisión peor que 50 m o que implica más de 150 km/h (RN-04.6). Una lectura repetida o fuera de orden tampoco da velocidad.
- Una lectura de 25 km/h o menos con un cambio de rumbo de más de 40 grados es un giro y corta la racha lenta (RN-04.7). Si el vehículo se movió menos de 3 m, no se calcula el rumbo: un auto detenido no gira.
- Cada lectura válida va al tramo que pasa a no más de 30 m (RN-04.8, P-24); más lejos, se ignora. En una calle de doble sentido va al tramo cuyo sentido se parece más al rumbo del vehículo (D-18).
- Un tramo está lento por debajo de la mitad de la velocidad esperada de su vía (55, 40 o 20 km/h). Cuando un recorrido lleva 2 minutos seguidos lento en el mismo tramo, el núcleo informa la congestión con el tramo, el punto de la última lectura y el nivel: muy congestionado si la velocidad media de esos 2 minutos queda por debajo de la cuarta parte de la esperada (RN-04.9, RN-04.10). Mientras siga lento, la vuelve a informar en cada mensaje, y el backend extiende el mismo evento (RN-04.11).
- Los recorridos simulados y los reales no se mezclan, aunque tengan el mismo identificador, y una congestión que sale de uno simulado va con `"simulado":true` (RN-04.20). Si varios recorridos de un mensaje congestionan el mismo tramo, va una sola, la más grave.
- De cada recorrido el núcleo solo guarda la última lectura y la racha en curso, y lo olvida a los 5 minutos sin lecturas nuevas (RN-04.12, P-25).

## Protocolo

- **Entrada estándar:** una petición por línea, en JSON. Lleva `id` (entero o texto) y `operacion`.
- **Salida estándar:** una respuesta por línea, en JSON, con el mismo `id`. No sale nada más por aquí.
- **Salida de errores:** registros para el log del backend. Nunca repiten el contenido de una petición.
- **Cierre:** al cerrarse la entrada estándar, el núcleo termina solo con código 0.
- Las líneas en blanco se ignoran (no generan respuesta) y se acepta `\r\n`.

Operaciones del contrato: `consultar_estado`, `calcular_ruta`, `revisar_ruta`, `actualizar_trafico`, `analizar_telemetria`.

```json
{"id":1,"operacion":"consultar_estado"}
{"id":1,"ok":true,"red_cargada":false,"fecha_extracto":null,"nodos":0,"tramos":0}
```

Con la red cargada: `{"id":1,"ok":true,"red_cargada":true,"fecha_extracto":"2026-10-28","nodos":6,"tramos":10}`.

### Respuestas con `"ok":false`

Traen `error` con un `codigo` y una `descripcion` en español. El núcleo sigue funcionando después de cualquiera de ellas.

| Código | Cuándo | `id` de la respuesta |
| --- | --- | --- |
| `JSON_INVALIDO` | La línea no es JSON | `null` |
| `PETICION_INVALIDA` | Es JSON, pero no es un objeto, o falta `id` u `operacion`, o tienen un tipo incorrecto | el de la petición si se pudo leer; si no, `null` |
| `OPERACION_DESCONOCIDA` | La `operacion` no existe en el contrato | el de la petición |
| `NO_IMPLEMENTADA` | Ya no se usa: todas las operaciones del contrato están implementadas | el de la petición |
| `RED_NO_CARGADA` | `calcular_ruta`, `revisar_ruta`, `actualizar_trafico` o `analizar_telemetria` llegó y el núcleo no tiene la red vial | el de la petición |
| `ERROR_INTERNO` | Fallo inesperado del núcleo | el de la petición si se conocía |

Que no haya ruta (`SIN_RUTA`, `PUNTO_INVALIDO`) **no** es un error: va como `"ok":true`. Una petición de ruta sin `origen` o `destino`, o con un punto que no es `[latitud, longitud]`, responde `PETICION_INVALIDA`.

## Requisitos

- CMake 3.16 o superior y un compilador de C++17 (g++, clang o Visual Studio / MSVC).
- Python 3 (opcional): solo para el script de mensajes y la prueba de extremo a extremo.
- Conexión a internet la primera vez que se configura: CMake descarga nlohmann/json 3.11.3 y GoogleTest 1.14.0 (con su huella SHA-256). Si ya están instaladas en el sistema (`find_package`), usa esas.

## Compilar

Desde esta carpeta (`04_imp/motor_cpp`):

```
cmake -S . -B build
cmake --build build
```

- **Linux x86 y ARM** (por ejemplo la instancia `churoviaje-server`, Ampere A1): el mismo comando. No hay opciones específicas de procesador.
- **Windows:** el mismo comando con Visual Studio (el ejecutable queda en `build\Release\motor_nucleo.exe` si se agrega `--config Release` al segundo comando) o con MinGW/Ninja (`build\motor_nucleo.exe`).
- Para compilar sin pruebas (por ejemplo en el servidor): `cmake -S . -B build -DBUILD_TESTING=OFF`.

## Probar

Un solo comando compila lo que falte y corre todas las pruebas (GoogleTest y la prueba por pipe):

```
cmake --build build --target pruebas
```

También sirve `ctest --test-dir build --output-on-failure` (en Visual Studio agrega `-C Release`).

Las pruebas cubren, entre otras, **CP-24** (`consultar_estado` responde en una sola línea con el mismo `id`), **CP-25** (una línea que no es JSON responde `"ok":false` y la siguiente petición válida se atiende normal), **CP-71** (la red irregular del prototipo da las mismas rutas que en Python), **CP-72** (una parada intermedia), **CP-95 a CP-98** (accidente, bloqueo, retiro del bloqueo y sentidos afectados), **CP-124 a CP-126** (revisión de la ruta activa: alternativa más barata, sin alternativa y evento ya avisado), **CP-114 a CP-118** (congestión con la telemetría: 2 minutos lentos en una avenida, giro en la esquina, lectura imprecisa, un solo sentido y recorrido simulado) y **CP-73** (100 consultas en una red de 10 000 esquinas, cada una en menos de 1 segundo). Con `RED_VIAL_REAL=<ruta de red_vial_tarija.json>` también se mide CP-73 con la red real y se calculan los viajes de referencia de Tarija (**CP-167** y **CP-168**, T-120; ver [04_imp/datos](../datos/README.md#viajes-de-referencia)); sin esa variable, esas pruebas se saltan.

## Mandar un mensaje a mano

Linux, macOS o Git Bash:

```
echo '{"id":1,"operacion":"consultar_estado"}' | ./build/motor_nucleo
```

PowerShell:

```
'{"id":1,"operacion":"consultar_estado"}' | .\build\Release\motor_nucleo.exe
```

Para una conversación completa, el script arranca el núcleo por pipe como lo hará el wrapper, manda mensajes de ejemplo (uno malformado, uno desconocido, una ruta) e imprime cada respuesta:

```
python scripts/enviar_mensajes.py --nucleo build/motor_nucleo
python scripts/enviar_mensajes.py --nucleo build/motor_nucleo -m '{"id":1,"operacion":"consultar_estado"}'
python scripts/enviar_mensajes.py --nucleo build/motor_nucleo --red tests/datos/red_prueba_plaza.json --verificar --esperar-nodos 6
```

Para escribir mensajes a mano uno por uno, ejecuta `./build/motor_nucleo` y escribe en la terminal; `Ctrl+D` (Linux) o `Ctrl+Z` y Enter (Windows) cierra la entrada y el núcleo termina.

## Estructura

| Archivo | Contenido |
| --- | --- |
| `src/nucleo.hpp/.cpp` | `Nucleo::atender`: una línea de JSON entra, una respuesta sale; validación y despacho por `operacion` |
| `src/bucle.hpp/.cpp` | Bucle de lectura y escritura; separa respuestas (salida estándar) de registros (salida de errores) |
| `src/red_vial.hpp/.cpp` | `RedVial`: carga y validación del archivo, tramos, índice espacial y `ajustar` |
| `src/ruta.hpp/.cpp` | A* (o Dijkstra) entre dos nodos y `calcular_ruta` con coordenadas, paradas, distancia, tiempo y trazado |
| `src/trafico.hpp` | Nivel de tráfico y cierre de cada tramo, con sus factores |
| `src/eventos.hpp/.cpp` | Eventos vigentes: a qué tramos afectan, nivel del tramo según el más grave y vencimiento |
| `src/tiempo.hpp/.cpp` | Lectura de fechas ISO 8601 con zona horaria |
| `src/ruta_nodos.cpp` | Herramienta de consola: ruta entre dos puntos (ids de nodo o coordenadas) |
| `src/main.cpp` | Punto de entrada que arranca el wrapper (`--red <archivo>`) |
| `tests/` | Pruebas de GoogleTest (`test_red_vial.cpp`: CP-47, rechazos del formato y tiempo de carga; `test_ruta.cpp`: CP-71 a CP-73 y `calcular_ruta`; `test_trafico.cpp`: CP-95 a CP-98, CP-18, CP-19 y `actualizar_trafico`) |
| `tests/datos/red_irregular.json` | La red irregular del prototipo de `05_pro/PruebaCalculoRuta` en el formato de la red vial (8 nodos, 14 tramos) |
| `tests/datos/red_prueba_plaza.json` | Red pequeña hecha a mano alrededor de la plaza Luis de Fuentes (6 nodos, 10 tramos); pasa `preparacion/validar_red.py` |
| `scripts/enviar_mensajes.py` | Mensajes de prueba por pipe |

Las operaciones nuevas se agregan en `Nucleo::atender` (`src/nucleo.cpp`): quitar el nombre de `kOperacionesPendientes` y añadir su rama junto a `consultar_estado`, `calcular_ruta` y `actualizar_trafico`.