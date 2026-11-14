#include "revision.hpp"

#include <limits>
#include <optional>
#include <unordered_set>
#include <utility>

namespace churoviaje {
namespace {

constexpr double kInfinito = std::numeric_limits<double>::infinity();

double costo_tramo(const RedVial& red, const Trafico& trafico, std::size_t tramo) {
  if (trafico.cerrado(tramo)) return kInfinito;  // un bloqueo vigente corta la ruta (RN-03.9)
  return red.tramos()[tramo].longitud_m * trafico.factor_tramo(tramo);
}

// Costo de seguir por la ruta actual desde la posición, con el tráfico al día (RN-04.16). Se mide desde la misma
// intersección de donde sale la alternativa, para que los dos costos sean comparables: si esa intersección es el
// comienzo de algún tramo que falta, se suma desde ahí; si no, se agrega el camino hasta el primero.
double costo_de_seguir(const RedVial& red, const Trafico& trafico, const Punto& posicion,
                       const std::vector<std::size_t>& faltan, Algoritmo algoritmo) {
  std::size_t desde = 0;
  double previo = 0;
  if (const auto ajuste = red.ajustar(posicion.lat, posicion.lon)) {
    std::optional<std::size_t> inicio;
    for (std::size_t k = 0; k < faltan.size(); ++k) {
      if (red.tramos()[faltan[k]].desde == ajuste->nodo) {
        inicio = k;
        break;
      }
    }
    if (inicio) {
      desde = *inicio;
    } else {
      const Camino hasta_la_ruta = buscar_camino(red, ajuste->nodo, red.tramos()[faltan.front()].desde, &trafico,
                                                 algoritmo);
      if (!hasta_la_ruta.encontrado) return kInfinito;
      previo = hasta_la_ruta.costo;
    }
  }
  double costo = previo;
  for (std::size_t k = desde; k < faltan.size(); ++k) costo += costo_tramo(red, trafico, faltan[k]);
  return costo;
}

}  // namespace

Revision revisar_ruta(const RedVial& red, TablaEventos& eventos, std::int64_t ahora, const PeticionRevision& peticion,
                      Algoritmo algoritmo) {
  Revision revision;

  std::vector<std::size_t> faltan;
  faltan.reserve(peticion.tramos.size());
  for (const std::string& id : peticion.tramos) {
    const auto tramo = red.buscar_tramo(id);
    if (!tramo) {
      revision.estado = EstadoRevision::ruta_desactualizada;
      return revision;
    }
    faltan.push_back(*tramo);
  }
  if (faltan.empty()) return revision;  // ya no falta nada: sin cambios

  // trafico() quita antes los eventos vencidos, así eventos_en() solo devuelve los vigentes (RN-04.14).
  const Trafico& trafico = eventos.trafico(ahora);
  const std::unordered_set<std::string> avisados(peticion.avisados.begin(), peticion.avisados.end());
  for (const std::size_t t : faltan) {
    for (const std::string& evento : eventos.eventos_en(t)) {
      if (avisados.count(evento) == 0) {
        revision.evento = evento;
        break;
      }
    }
    if (!revision.evento.empty()) break;
  }
  if (revision.evento.empty()) return revision;  // RN-04.19: los ya avisados no vuelven a provocar nada

  revision.costo_actual = costo_de_seguir(red, trafico, peticion.posicion, faltan, algoritmo);
  Ruta alternativa = calcular_ruta(red, peticion.posicion, peticion.destino, peticion.paradas, &trafico, algoritmo);
  if (alternativa.estado == EstadoRuta::encontrada && alternativa.costo < revision.costo_actual - kMejoraMinimaCosto) {
    revision.estado = EstadoRevision::alternativa;
    revision.alternativa = std::move(alternativa);
  } else {
    revision.estado = EstadoRevision::sin_alternativa;  // RN-04.16: se mantiene la ruta y se avisa del evento
  }
  return revision;
}

}  // namespace churoviaje
