// Revisión de una ruta activa (#76, CU-04): si un evento vigente cae sobre lo que falta de la ruta, busca una
// alternativa y la propone solo si cuesta menos que seguir (RN-04.14 a RN-04.16, RN-04.19, D-12).
#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "eventos.hpp"
#include "red_vial.hpp"
#include "ruta.hpp"

namespace churoviaje {

// Lo que la app manda mientras navega (D-12): el servidor no guarda la ruta (D-05).
struct PeticionRevision {
  Punto posicion;                          // dónde está el conductor ahora
  std::vector<std::string> tramos;         // ids de los tramos que le faltan, en orden
  Punto destino;
  std::vector<Punto> paradas;              // las paradas que le faltan, en orden
  std::vector<std::string> avisados;       // eventos que ya provocaron un aviso en esta ruta (RN-04.19)
};

enum class EstadoRevision {
  sin_cambios,          // ningún evento nuevo toca lo que falta
  sin_alternativa,      // hay un evento, pero no hay un camino mejor: se sigue igual (RN-04.16)
  alternativa,          // hay un camino que cuesta menos (RN-04.16)
  ruta_desactualizada,  // algún tramo ya no existe: se recargó la red y la app pide la ruta de nuevo
};

struct Revision {
  EstadoRevision estado = EstadoRevision::sin_cambios;
  std::string evento;          // el que provocó la revisión; vacío en sin_cambios y ruta_desactualizada
  Ruta alternativa;            // solo en alternativa
  double costo_actual = 0;     // costo de seguir por la ruta actual; infinito si un bloqueo la corta
};

// Tolerancia para comparar costos: una alternativa que cuesta lo mismo que seguir no se propone.
inline constexpr double kMejoraMinimaCosto = 1e-6;

// Revisa la ruta con el tráfico vigente a `ahora`. Busca, en el orden de la ruta, el primer evento sobre un tramo
// que falta y que no esté en los ya avisados (RN-04.14, RN-04.19). Si lo hay, calcula la alternativa desde la
// posición actual con el destino y las paradas que faltan (RN-04.15) y la propone si cuesta menos que seguir.
Revision revisar_ruta(const RedVial& red, TablaEventos& eventos, std::int64_t ahora, const PeticionRevision& peticion,
                      Algoritmo algoritmo = Algoritmo::a_estrella);

}  // namespace churoviaje
