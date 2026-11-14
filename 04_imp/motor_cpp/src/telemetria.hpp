// Análisis de la telemetría (#73, CU-04): velocidad, filtros, tramo de cada lectura y congestión (RN-04.5 a
// RN-04.10, RN-04.12, RN-04.20, P-02, P-24, P-25, D-18). Lleva a C++ las fórmulas de 05_pro/CalculoVelocidad.
#pragma once

#include <cstddef>
#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include "red_vial.hpp"
#include "trafico.hpp"

namespace churoviaje {

// Una lectura del GPS tal como la manda la app: posición, precisión y momento (RN-04.1). No trae nada del conductor.
struct LecturaTelemetria {
  Punto posicion;
  double precision_m = 0;
  std::int64_t momento = 0;  // segundos desde 1970
};

// Una congestión que el núcleo informa al backend (RN-04.10): el tramo, un punto y el nivel.
struct CongestionDetectada {
  std::size_t tramo = 0;  // posición en RedVial::tramos()
  Punto punto;
  NivelTrafico nivel = NivelTrafico::congestionado;  // congestionado o muy_congestionado
  bool simulado = false;                             // RN-04.20: sale de recorridos simulados
};

struct ResultadoTelemetria {
  std::vector<CongestionDetectada> congestiones;
  std::size_t validas = 0;
  std::size_t descartadas = 0;
};

// Rumbo de `a` a `b`, en grados desde el norte en sentido horario (0 a 360): la fórmula de azimut del prototipo.
double rumbo_grados(const Punto& a, const Punto& b);
// Diferencia entre dos rumbos, de 0 a 180 grados.
double diferencia_rumbo(double a, double b);

class AnalizadorTelemetria {
 public:
  static constexpr double kPrecisionMaximaM = 50;          // RN-04.6
  static constexpr double kVelocidadMaximaKmh = 150;       // RN-04.6
  static constexpr double kVelocidadGiroKmh = 25;          // RN-04.7
  static constexpr double kGiroMinimoGrados = 40;          // RN-04.7
  static constexpr double kDistanciaTramoM = 30;           // RN-04.8, P-24
  static constexpr std::int64_t kTiempoCongestionS = 120;  // RN-04.10, P-02: 2 minutos seguidos
  static constexpr std::int64_t kOlvidoS = 5 * 60;         // RN-04.12, P-25
  // Por debajo de este desplazamiento el GPS solo tiembla: no se calcula el rumbo (un auto detenido no gira).
  static constexpr double kDesplazamientoParaRumboM = 3;

  explicit AnalizadorTelemetria(const RedVial* red = nullptr) : red_(red) {}

  // Analiza las lecturas de un recorrido (identificador temporal, sin datos del conductor) y devuelve las
  // congestiones que se cumplen con ellas. `ahora` es el reloj del núcleo: un recorrido sin lecturas nuevas
  // durante kOlvidoS se olvida (RN-04.12). Los recorridos simulados y los reales no se mezclan (RN-04.20).
  ResultadoTelemetria analizar(const std::string& recorrido, bool simulado, std::vector<LecturaTelemetria> lecturas,
                               std::int64_t ahora);

  // Cuántos recorridos recuerda el núcleo (para pruebas y diagnóstico).
  std::size_t recorridos() const { return estados_.size(); }
  // Olvida los recorridos sin lecturas nuevas desde hace más de kOlvidoS.
  void olvidar_viejos(std::int64_t ahora);

 private:
  // Lo único que se guarda de un recorrido: su última lectura y la racha lenta en curso (RN-04.12).
  struct Estado {
    bool con_lectura = false;  // si `ultima` es una lectura de verdad
    LecturaTelemetria ultima;
    std::optional<double> rumbo;             // el de las dos últimas lecturas que se movieron
    std::optional<std::size_t> tramo_lento;  // tramo donde va lento, si va lento
    std::int64_t lento_desde = 0;
    double metros_lentos = 0;
    double segundos_lentos = 0;
    std::int64_t visto = 0;  // reloj del núcleo en la última lectura recibida
  };

  const RedVial* red_;
  std::map<std::pair<bool, std::string>, Estado> estados_;
};

}  // namespace churoviaje
