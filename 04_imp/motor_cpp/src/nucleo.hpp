// Núcleo del motor core: traduce una línea de JSON (petición) en una línea de JSON (respuesta).
// Contrato: 03_des/02_Arquitectura/05_contratos.md, sección «Backend con el motor core».
#pragma once

#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <string_view>

#include "red_vial.hpp"
#include "eventos.hpp"
#include "ruta.hpp"
#include "telemetria.hpp"
#include "trafico.hpp"

namespace churoviaje {

// Códigos del campo error.codigo cuando una respuesta trae "ok":false.
namespace codigo_error {
inline constexpr const char* json_invalido = "JSON_INVALIDO";            // la línea no es JSON
inline constexpr const char* peticion_invalida = "PETICION_INVALIDA";    // JSON válido, pero sin id u operacion correctos
inline constexpr const char* operacion_desconocida = "OPERACION_DESCONOCIDA";
inline constexpr const char* no_implementada = "NO_IMPLEMENTADA";        // ya no se usa: todo el contrato está implementado
inline constexpr const char* red_no_cargada = "RED_NO_CARGADA";          // la operación necesita la red vial y no hay
inline constexpr const char* error_interno = "ERROR_INTERNO";            // fallo inesperado; el núcleo sigue vivo
}  // namespace codigo_error

// Lo que consultar_estado informa (D-08). Sin red cargada: false, null, 0 y 0.
struct EstadoRed {
  bool cargada = false;
  std::optional<std::string> fecha_extracto;  // fecha ISO 8601 del extracto de OpenStreetMap
  std::size_t nodos = 0;
  std::size_t tramos = 0;
};

struct Respuesta {
  std::string linea;    // JSON en una sola línea, sin el salto final
  bool ok = true;
  std::string codigo;   // código del error si ok == false; vacío si no
};

class Nucleo {
 public:
  // Sin red vial (o solo con el estado declarado, para pruebas).
  explicit Nucleo(EstadoRed estado = {});
  // Con la red vial cargada (T-096); nullptr equivale a arrancar sin red. `algoritmo` elige A* o Dijkstra
  // para calcular rutas (RN-03.11).
  explicit Nucleo(std::shared_ptr<const RedVial> red, Algoritmo algoritmo = Algoritmo::a_estrella);

  const RedVial* red() const { return red_.get(); }

  // Atiende una petición. Devuelve std::nullopt si la línea está en blanco (no se responde nada).
  // Nunca lanza: cualquier fallo se convierte en una respuesta "ok":false. No es const porque actualizar_trafico
  // cambia los eventos vigentes y analizar_telemetria recuerda la última lectura de cada recorrido.
  std::optional<Respuesta> atender(std::string_view linea) noexcept;

  // Reloj con el que se decide si un evento ya venció (segundos desde 1970). Por omisión, el del sistema;
  // las pruebas lo reemplazan.
  void usar_reloj(std::function<std::int64_t()> reloj) { reloj_ = std::move(reloj); }
  std::size_t eventos_vigentes() {
    eventos_.trafico(reloj_());
    return eventos_.cantidad();
  }
  // Cuántos recorridos de telemetría recuerda el núcleo, después de olvidar los de más de 5 minutos (P-25).
  std::size_t recorridos_en_memoria() {
    telemetria_.olvidar_viejos(reloj_());
    return telemetria_.recorridos();
  }

 private:
  EstadoRed estado_;
  std::shared_ptr<const RedVial> red_;
  Algoritmo algoritmo_ = Algoritmo::a_estrella;
  TablaEventos eventos_;  // eventos vigentes y el tráfico que producen en cada tramo
  AnalizadorTelemetria telemetria_;  // última lectura de cada recorrido (RN-04.12)
  std::function<std::int64_t()> reloj_;
};

}  // namespace churoviaje
