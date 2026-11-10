#include "ruta.hpp"

#include <cmath>
#include <functional>
#include <limits>
#include <queue>
#include <utility>

namespace churoviaje {
namespace {

constexpr double kInfinito = std::numeric_limits<double>::infinity();
constexpr std::size_t kNinguno = std::numeric_limits<std::size_t>::max();

double kmh_a_mps(double kmh) { return kmh / 3.6; }

bool mismo_punto(const Punto& a, const Punto& b) { return a.lat == b.lat && a.lon == b.lon; }

}  // namespace

std::optional<Algoritmo> algoritmo_desde_texto(std::string_view texto) {
  if (texto == "a_estrella" || texto == "astar") return Algoritmo::a_estrella;
  if (texto == "dijkstra") return Algoritmo::dijkstra;
  return std::nullopt;
}

double velocidad_esperada_mps(TipoVia tipo) {
  switch (tipo) {
    case TipoVia::via_rapida: return kmh_a_mps(55);
    case TipoVia::avenida_principal: return kmh_a_mps(40);
    case TipoVia::zona_residencial: break;
  }
  return kmh_a_mps(20);
}

Camino buscar_camino(const RedVial& red, std::size_t desde, std::size_t hasta, const Trafico* trafico,
                     Algoritmo algoritmo) {
  Camino camino;
  const std::size_t n = red.nodos().size();
  if (desde >= n || hasta >= n) return camino;
  if (desde == hasta) {
    camino.encontrado = true;
    return camino;
  }

  const auto& nodos = red.nodos();
  const auto& tramos = red.tramos();
  const Punto& meta = nodos[hasta].posicion;
  const double factor_estimacion = red.factor_estimacion();
  auto estimacion = [&](std::size_t nodo) {
    return algoritmo == Algoritmo::dijkstra ? 0.0 : distancia_m(nodos[nodo].posicion, meta) * factor_estimacion;
  };

  std::vector<double> costo(n, kInfinito);
  std::vector<std::size_t> llegada(n, kNinguno);  // tramo por el que se llegó a cada nodo
  std::vector<bool> cerrado(n, false);
  // (costo + estimación, nodo): ante un empate sale el nodo de menor posición, así el resultado no cambia
  // de una ejecución a otra.
  using Entrada = std::pair<double, std::size_t>;
  std::priority_queue<Entrada, std::vector<Entrada>, std::greater<Entrada>> abiertos;

  costo[desde] = 0;
  abiertos.emplace(estimacion(desde), desde);
  while (!abiertos.empty()) {
    const std::size_t actual = abiertos.top().second;
    abiertos.pop();
    if (cerrado[actual]) continue;
    if (actual == hasta) break;
    cerrado[actual] = true;
    for (const std::size_t t : red.tramos_que_salen(actual)) {
      if (trafico && trafico->cerrado(t)) continue;
      const Tramo& tramo = tramos[t];
      if (cerrado[tramo.hasta]) continue;
      const double nuevo = costo[actual] + tramo.longitud_m * (trafico ? trafico->factor_tramo(t) : 1.0);
      if (nuevo < costo[tramo.hasta]) {
        costo[tramo.hasta] = nuevo;
        llegada[tramo.hasta] = t;
        abiertos.emplace(nuevo + estimacion(tramo.hasta), tramo.hasta);
      }
    }
  }

  if (costo[hasta] == kInfinito) return camino;
  for (std::size_t nodo = hasta; nodo != desde; nodo = tramos[llegada[nodo]].desde) {
    camino.tramos.push_back(llegada[nodo]);
  }
  camino.tramos = std::vector<std::size_t>(camino.tramos.rbegin(), camino.tramos.rend());
  camino.encontrado = true;
  camino.costo = costo[hasta];
  return camino;
}

std::vector<std::size_t> nodos_del_camino(const RedVial& red, std::size_t inicio,
                                          const std::vector<std::size_t>& tramos) {
  std::vector<std::size_t> nodos{inicio};
  for (const std::size_t t : tramos) nodos.push_back(red.tramos()[t].hasta);
  return nodos;
}

Ruta calcular_ruta(const RedVial& red, const Punto& origen, const Punto& destino, const std::vector<Punto>& paradas,
                   const Trafico* trafico, Algoritmo algoritmo) {
  Ruta ruta;

  std::vector<Punto> puntos;
  puntos.reserve(paradas.size() + 2);
  puntos.push_back(origen);
  puntos.insert(puntos.end(), paradas.begin(), paradas.end());
  puntos.push_back(destino);

  std::vector<std::size_t> ajustados;
  ajustados.reserve(puntos.size());
  for (const Punto& p : puntos) {
    const auto ajuste = red.ajustar(p.lat, p.lon);
    if (!ajuste) {
      ruta.estado = EstadoRuta::punto_invalido;
      return ruta;
    }
    ajustados.push_back(ajuste->nodo);
  }

  for (std::size_t i = 0; i + 1 < ajustados.size(); ++i) {
    const Camino trecho = buscar_camino(red, ajustados[i], ajustados[i + 1], trafico, algoritmo);
    if (!trecho.encontrado) {
      ruta.estado = EstadoRuta::sin_ruta;
      return ruta;
    }
    ruta.tramos.insert(ruta.tramos.end(), trecho.tramos.begin(), trecho.tramos.end());
    ruta.costo += trecho.costo;
  }

  ruta.estado = EstadoRuta::encontrada;
  ruta.nodos = nodos_del_camino(red, ajustados.front(), ruta.tramos);
  ruta.trazado.push_back(red.nodos()[ajustados.front()].posicion);
  for (const std::size_t t : ruta.tramos) {
    const Tramo& tramo = red.tramos()[t];
    ruta.distancia_m += tramo.longitud_m;
    ruta.tiempo_s += tramo.longitud_m / velocidad_esperada_mps(tramo.tipo_via) * (trafico ? trafico->factor_tramo(t) : 1.0);
    for (const Punto& p : tramo.geometria) {
      if (!mismo_punto(p, ruta.trazado.back())) ruta.trazado.push_back(p);
    }
  }
  return ruta;
}

}  // namespace churoviaje
