// Eventos vigentes y su efecto sobre los tramos de la red vial (#66, RN-03.9, P-12, D-14, D-18).
#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

#include "red_vial.hpp"
#include "trafico.hpp"

namespace churoviaje {

// Los códigos de tipo_evento de la base de datos.
enum class TipoEvento { accidente, control_policial, bloqueo, otro, congestion };
std::optional<TipoEvento> tipo_evento_desde_texto(std::string_view codigo);

// Lo que el backend avisa de un evento con actualizar_trafico.
struct AvisoEvento {
  std::string id;
  TipoEvento tipo = TipoEvento::otro;
  // Solo para la congestión: congestionado (×2,5) o muy_congestionado (×4,0).
  NivelTrafico nivel_congestion = NivelTrafico::congestionado;
  Punto ubicacion;
  // Solo para la congestión: el tramo donde se midió (lo informa analizar_telemetria). Si falta, se toma el
  // tramo más cercano a la ubicación, en un solo sentido.
  std::optional<std::size_t> tramo_medido;
  std::int64_t vence = 0;  // segundos desde 1970
};

class TablaEventos {
 public:
  explicit TablaEventos(const RedVial* red = nullptr);

  // Un evento nace o cambia su vencimiento: se guarda o se reemplaza, y devuelve los tramos que afecta. Un
  // evento de un conductor afecta a los dos sentidos de la calle más cercana, si está a menos de 100 m; más
  // lejos, a ninguno. Una congestión afecta solo a su tramo. Si ya venció a `ahora`, se quita y no afecta nada.
  std::vector<std::size_t> registrar(const AvisoEvento& aviso, std::int64_t ahora);

  // Un evento se retira o vence: se quita y devuelve los tramos que afectaba. Si no estaba, no pasa nada.
  std::vector<std::size_t> quitar(const std::string& id);

  // Quita los eventos que vencieron a `ahora`, aunque su aviso no haya llegado, y devuelve el tráfico vigente.
  const Trafico& trafico(std::int64_t ahora);

  std::size_t cantidad() const { return eventos_.size(); }

  // Los ids de los eventos que afectan al tramo, en el orden en que llegaron. Para que no incluya los vencidos,
  // hay que llamar antes a trafico(ahora).
  const std::vector<std::string>& eventos_en(std::size_t tramo) const;

 private:
  struct Guardado {
    TipoEvento tipo;
    NivelTrafico nivel;  // efecto sobre el costo (si no cierra)
    bool cierra;         // un bloqueo cierra el tramo
    std::vector<std::size_t> tramos;
    std::int64_t vence;
  };

  void recalcular(const std::vector<std::size_t>& tramos);

  const RedVial* red_;
  std::unordered_map<std::string, Guardado> eventos_;
  std::vector<std::vector<std::string>> por_tramo_;  // ids de los eventos sobre cada tramo
  Trafico trafico_;
};

}  // namespace churoviaje
