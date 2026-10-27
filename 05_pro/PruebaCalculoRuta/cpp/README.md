# Motor de ruteo en C++ (CU-03, HU-05)

Port a C++17 del prototipo en Python de la carpeta superior: red de calles dirigida +
A* con multiplicadores de tráfico. Sin dependencias externas. Esta es la base que
después pasará al motor core.

| Archivo | Contenido |
|---|---|
| `red_de_calles.hpp/.cpp` | Modelo: `RedDeCalles`, niveles de tráfico, eventos, A* y `ResultadoRuta` |
| `redes_de_prueba.hpp/.cpp` | `construir_red_irregular()` y `construir_red_grande()` (misma red que el prototipo Python) |
| `aleatorio_python.hpp` | Réplica del generador `random.Random` de Python, para que la red grande sea idéntica |
| `main.cpp` | Consola: coordenadas por pantalla → mejor ruta por pantalla |
| `test_ruteo.cpp` | Pruebas CP-09 a CP-19, tipo de calle y A* contra Dijkstra, sin librerías de test |

## Compilar

Con g++ (MinGW, WSL o Linux), desde esta carpeta (`cpp/`):

```
g++ -std=c++17 -O2 main.cpp red_de_calles.cpp redes_de_prueba.cpp -o ruteo
g++ -std=c++17 -O2 test_ruteo.cpp red_de_calles.cpp redes_de_prueba.cpp -o test_ruteo
```

Con Visual Studio (Developer Command Prompt): `/utf-8` es necesario por las tildes.

```
cl /std:c++17 /O2 /EHsc /utf-8 main.cpp red_de_calles.cpp redes_de_prueba.cpp /Fe:ruteo.exe
cl /std:c++17 /O2 /EHsc /utf-8 test_ruteo.cpp red_de_calles.cpp redes_de_prueba.cpp /Fe:test_ruteo.exe
```

Con CMake: `cmake -S . -B build && cmake --build build && ctest --test-dir build`

## Usar la consola

```
ruteo                                  modo interactivo: pide origen y destino
ruteo -21.5355 -64.7296 -21.5300 -64.7260
ruteo "-21.5355,-64.7296" "-21.5300,-64.7260"
ruteo --nodos                          lista los nodos de la red con sus coordenadas
```

Salida de ejemplo:

```
Origen:  (-21.535500, -64.729600) -> nodo Plaza (a 0 m)
Destino: (-21.530000, -64.726000) -> nodo Universidad (a 0 m)

Mejor ruta: Plaza -> Mercado -> Universidad

  1. Plaza  (-21.535500, -64.729600)
       | Calle Comercio, 183 m
  2. Mercado  (-21.534800, -64.728000)
       | Av. Las Américas, 572 m
  3. Universidad  (-21.530000, -64.726000)

Nodos recorridos : 3
Distancia real   : 755 m
Costo total      : 755 m-equivalentes
```

- Cada coordenada se asocia al **nodo más cercano** de la red. Si está a más de
  1000 m de cualquier nodo se informa «Origen o destino inválido» (CU-03, ext. 1a).
- Si no hay camino: «No se encontró una ruta válida» (CU-03, ext. 3b).
- Acepta punto o coma decimal. Códigos de salida: 0 ruta encontrada, 1 sin ruta, 2 entrada inválida.
- `--evento ORIGEN DESTINO TIPO` (repetible) reporta tráfico antes de calcular, por ejemplo
  `ruteo --evento Hospital Universidad choque -21.5330 -64.7310 -21.5300 -64.7260`.
- `--red grande` usa la red de 100×100 intersecciones (10 000 nodos) del CP-13.
- Tras la ruta se imprimen las matrices de **eventos** y de **costos** de todo el grafo (solo redes de
  30 nodos o menos); `--sin-matrices` las desactiva. En la de costos, `*` marca los tramos de la ruta.
- `--tipos` clasifica las calles de la red irregular y penaliza su costo, además de imprimir una
  matriz de tipos.

## Cómo se calcula el costo de una calle

```
costo = distancia × penalización por tipo de calle × multiplicador de tráfico
```

| Tipo de calle | Penalización |
|---|---|
| sin clasificar (por defecto) | x1.0 |
| circunvalación | x1.0 |
| avenida | x1.1 |
| residencial | x1.3 |

Valores iniciales, a ajustar con las pruebas (están en `penalizacion()` de `red_de_calles.cpp`). Todas
deben ser >= 1.0: así la heurística Haversine sigue siendo admisible y A* sigue dando la ruta óptima.
El multiplicador de tráfico va en `multiplicador()`: fluido x1.0, moderado x1.5, congestionado x2.5,
muy congestionado x4.0.
