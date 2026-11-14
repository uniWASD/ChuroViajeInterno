#include "telemetria.hpp"

#include <algorithm>
#include <cmath>

#include "ruta.hpp"

namespace churoviaje {
namespace {

constexpr double kPi = 3.14159265358979323846;

void cortar_racha(double& metros, double& segundos, std::optional<std::size_t>& tramo) {
  tramo.reset();
  metros = 0;
  segundos = 0;
}

}  // namespace

double rumbo_grados(const Punto& a, const Punto& b) {
  const double rad = kPi / 180.0;
  const double lat1 = a.lat * rad, lat2 = b.lat * rad;
  const double dlon = (b.lon - a.lon) * rad;
  const double y = std::sin(dlon) * std::cos(lat2);
  const double x = std::cos(lat1) * std::sin(lat2) - std::sin(lat1) * std::cos(lat2) * std::cos(dlon);
  return std::fmod(std::atan2(y, x) / rad + 360.0, 360.0);
}

double diferencia_rumbo(double a, double b) {
  const double d = std::fmod(std::fabs(a - b), 360.0);
  return d > 180.0 ? 360.0 - d : d;
}

void AnalizadorTelemetria::olvidar_viejos(std::int64_t ahora) {
  for (auto it = estados_.begin(); it != estados_.end();) {
    if (ahora - it->second.visto > kOlvidoS) {
      it = estados_.erase(it);
    } else {
      ++it;
    }
  }
}

ResultadoTelemetria AnalizadorTelemetria::analizar(const std::string& recorrido, bool simulado,
                                                   std::vector<LecturaTelemetria> lecturas, std::int64_t ahora) {
  olvidar_viejos(ahora);
  ResultadoTelemetria resultado;
  std::stable_sort(lecturas.begin(), lecturas.end(),
                   [](const LecturaTelemetria& a, const LecturaTelemetria& b) { return a.momento < b.momento; });

  // La clave lleva la marca de simulado: un recorrido simulado y uno real nunca comparten estado (RN-04.20).
  Estado& e = estados_[{simulado, recorrido}];
  for (const LecturaTelemetria& l : lecturas) {
    // RN-04.6: precisión peor que 50 m.
    if (!std::isfinite(l.precision_m) || l.precision_m < 0 || l.precision_m > kPrecisionMaximaM ||
        !std::isfinite(l.posicion.lat) || !std::isfinite(l.posicion.lon)) {
      ++resultado.descartadas;
      continue;
    }
    if (!e.con_lectura) {
      e.con_lectura = true;
      e.ultima = l;
      ++resultado.validas;
      continue;
    }
    const std::int64_t dt = l.momento - e.ultima.momento;
    if (dt <= 0) {  // repetida o fuera de orden: no da velocidad
      ++resultado.descartadas;
      continue;
    }
    if (dt > kOlvidoS) {  // pasó demasiado tiempo: empieza de nuevo
      e.ultima = l;
      e.rumbo.reset();
      cortar_racha(e.metros_lentos, e.segundos_lentos, e.tramo_lento);
      ++resultado.validas;
      continue;
    }
    // RN-04.5: velocidad con la distancia y el tiempo entre dos lecturas seguidas.
    const double metros = distancia_m(e.ultima.posicion, l.posicion);
    const double kmh = metros / static_cast<double>(dt) * 3.6;
    if (kmh > kVelocidadMaximaKmh) {  // RN-04.6: un salto del GPS; la lectura anterior sigue siendo la buena
      ++resultado.descartadas;
      continue;
    }
    ++resultado.validas;

    std::optional<double> rumbo_ahora;
    if (metros >= kDesplazamientoParaRumboM) rumbo_ahora = rumbo_grados(e.ultima.posicion, l.posicion);
    // RN-04.7: lento y con un cambio de dirección de más de 40 grados es un giro, no congestión.
    const bool giro = kmh <= kVelocidadGiroKmh && rumbo_ahora && e.rumbo &&
                      diferencia_rumbo(*rumbo_ahora, *e.rumbo) > kGiroMinimoGrados;
    const std::optional<double> sentido = rumbo_ahora ? rumbo_ahora : e.rumbo;
    const std::int64_t comienzo = e.ultima.momento;
    if (rumbo_ahora) e.rumbo = rumbo_ahora;
    e.ultima = l;

    // RN-04.8, P-24, D-18: el tramo vehicular a no más de 30 m, en el sentido en que avanza.
    const auto tramo = red_ ? red_->tramo_en_sentido(l.posicion.lat, l.posicion.lon, sentido, kDistanciaTramoM)
                            : std::nullopt;
    if (giro || !tramo) {
      cortar_racha(e.metros_lentos, e.segundos_lentos, e.tramo_lento);
      continue;
    }
    // RN-04.9, P-02: lento es ir por debajo de la mitad de la velocidad esperada de la vía.
    const double esperada_kmh = velocidad_esperada_mps(red_->tramos()[tramo->tramo].tipo_via) * 3.6;
    if (kmh >= esperada_kmh / 2) {
      cortar_racha(e.metros_lentos, e.segundos_lentos, e.tramo_lento);
      continue;
    }
    if (e.tramo_lento != tramo->tramo) {
      e.tramo_lento = tramo->tramo;
      e.lento_desde = comienzo;
      e.metros_lentos = 0;
      e.segundos_lentos = 0;
    }
    e.metros_lentos += metros;
    e.segundos_lentos += static_cast<double>(dt);

    // RN-04.10: 2 minutos seguidos lento en el mismo tramo. El nivel sale de la velocidad media de la racha:
    // por debajo de la cuarta parte de la esperada, muy congestionado.
    if (l.momento - e.lento_desde >= kTiempoCongestionS) {
      const double media_kmh = e.metros_lentos / e.segundos_lentos * 3.6;
      CongestionDetectada c;
      c.tramo = tramo->tramo;
      c.punto = l.posicion;
      c.nivel = media_kmh < esperada_kmh / 4 ? NivelTrafico::muy_congestionado : NivelTrafico::congestionado;
      c.simulado = simulado;
      auto ya = std::find_if(resultado.congestiones.begin(), resultado.congestiones.end(),
                             [&](const CongestionDetectada& x) { return x.tramo == c.tramo; });
      if (ya != resultado.congestiones.end()) {
        *ya = c;  // vale la lectura más reciente del recorrido en ese tramo
      } else {
        resultado.congestiones.push_back(c);
      }
    }
  }
  if (!e.con_lectura) {
    estados_.erase({simulado, recorrido});  // no llegó ninguna lectura útil: no hay nada que recordar
  } else {
    e.visto = ahora;
  }
  return resultado;
}

}  // namespace churoviaje
