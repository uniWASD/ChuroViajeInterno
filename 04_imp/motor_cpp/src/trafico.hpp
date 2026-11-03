// Nivel de tráfico de cada tramo de la red vial (RN-03.8, RN-03.9).
// En este issue todos los tramos están fluidos; el issue de tráfico los cambia según los eventos vigentes.
#pragma once

#include <cstddef>
#include <vector>

namespace churoviaje {

enum class NivelTrafico { fluido, moderado, congestionado, muy_congestionado };

// Por cuánto se multiplica el costo de un tramo según su nivel (prototipo de 05_pro/PruebaCalculoRuta, P-12).
constexpr double factor(NivelTrafico nivel) {
  switch (nivel) {
    case NivelTrafico::moderado: return 1.5;
    case NivelTrafico::congestionado: return 2.5;
    case NivelTrafico::muy_congestionado: return 4.0;
    case NivelTrafico::fluido: break;
  }
  return 1.0;
}

// Nivel y cierre de cada tramo, por su posición en RedVial::tramos(). Un tramo cerrado (un bloqueo vigente)
// no lo usa ninguna ruta.
class Trafico {
 public:
  explicit Trafico(std::size_t tramos = 0) : niveles_(tramos, NivelTrafico::fluido), cerrados_(tramos, false) {}

  std::size_t tramos() const { return niveles_.size(); }
  NivelTrafico nivel(std::size_t tramo) const { return tramo < niveles_.size() ? niveles_[tramo] : NivelTrafico::fluido; }
  bool cerrado(std::size_t tramo) const { return tramo < cerrados_.size() && cerrados_[tramo]; }
  double factor_tramo(std::size_t tramo) const { return factor(nivel(tramo)); }

  void fijar(std::size_t tramo, NivelTrafico nivel) {
    if (tramo < niveles_.size()) niveles_[tramo] = nivel;
  }
  void cerrar(std::size_t tramo, bool cerrado = true) {
    if (tramo < cerrados_.size()) cerrados_[tramo] = cerrado;
  }

 private:
  std::vector<NivelTrafico> niveles_;
  std::vector<bool> cerrados_;
};

}  // namespace churoviaje
