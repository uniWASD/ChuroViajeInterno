// Cálculo de la ruta óptima sobre la red vial (CU-03, RN-03.4 y RN-03.6 a RN-03.14).
#pragma once

#include <cstddef>
#include <optional>
#include <string_view>
#include <vector>

#include "red_vial.hpp"
#include "trafico.hpp"

namespace churoviaje {

// RN-03.11: A* con la distancia en línea recta como estimación; Dijkstra queda por configuración.
enum class Algoritmo { a_estrella, dijkstra };
std::optional<Algoritmo> algoritmo_desde_texto(std::string_view texto);

// Velocidad esperada de cada tipo de vía, en metros por segundo (RN-03.14). Es el valor central de los rangos
// de 05_pro/CalculoVelocidad: 55 km/h en vías rápidas, 40 en avenidas principales y 20 en zonas residenciales.
double velocidad_esperada_mps(TipoVia tipo);

// Camino de menor costo entre dos nodos. `tramos` va en orden, del primero al último.
struct Camino {
  bool encontrado = false;
  std::vector<std::size_t> tramos;
  double costo = 0;
};

// Busca el camino de menor costo de `desde` a `hasta` (posiciones en RedVial::nodos()). El costo de un tramo es
// su longitud por el factor de su nivel de tráfico (RN-03.8); los tramos cerrados no se usan. Sin `trafico`,
// todos los tramos están fluidos. Si `desde == hasta`, el camino existe y no tiene tramos.
Camino buscar_camino(const RedVial& red, std::size_t desde, std::size_t hasta, const Trafico* trafico,
                     Algoritmo algoritmo = Algoritmo::a_estrella);

// Los nodos por los que pasa un camino que empieza en `inicio`, en orden e incluidos los dos extremos.
std::vector<std::size_t> nodos_del_camino(const RedVial& red, std::size_t inicio, const std::vector<std::size_t>& tramos);

enum class EstadoRuta { encontrada, sin_ruta, punto_invalido };

struct Ruta {
  EstadoRuta estado = EstadoRuta::sin_ruta;
  std::vector<std::size_t> tramos;  // en orden; solo si estado == encontrada
  std::vector<std::size_t> nodos;   // nodos por los que pasa, incluidos origen y destino ajustados
  std::vector<Punto> trazado;       // la geometría de los tramos, sin repetir los puntos de unión
  double distancia_m = 0;
  double tiempo_s = 0;
  double costo = 0;
};

// Ruta de `origen` a `destino` pasando por `paradas` en ese orden (RN-03.10). Cada punto se ajusta a la
// intersección más cercana (RN-03.4): si alguno no se puede ajustar, el estado es punto_invalido; si algún
// trecho no tiene camino, sin_ruta (RN-03.12). Ninguno de los dos es un error.
Ruta calcular_ruta(const RedVial& red, const Punto& origen, const Punto& destino, const std::vector<Punto>& paradas,
                   const Trafico* trafico, Algoritmo algoritmo = Algoritmo::a_estrella);

}  // namespace churoviaje
