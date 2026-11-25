#include "telemetria.hpp"

#include <algorithm>
#include <cmath>

#include "ruta.hpp"

namespace churoviaje {
namespace {

constexpr double kPi = 3.14159265358979323846;

void cortar_racha(std::vector<std::size_t>& tramos, std::vector<std::size_t>& informados, double& metros,
                  double& segundos) {
  tramos.clear();
  informados.clear();
  metros = 0;
  segundos = 0;
}

bool contiene(const std::vector<std::size_t>& lista, std::size_t valor) {
  return std::find(lista.begin(), lista.end(), valor) != lista.end();
}

// Rumbo del final de un tramo y del comienzo del siguiente, con los dos últimos y los dos primeros puntos.
double rumbo_final(const Tramo& t) { return rumbo_grados(t.geometria[t.geometria.size() - 2], t.geometria.back()); }
double rumbo_inicial(const Tramo& t) { return rumbo_grados(t.geometria[0], t.geometria[1]); }

// RN-04.24, P-38: `siguiente` es la cuadra que sigue a `actual` en la misma calle si empieza donde termina la otra,
// no es la misma calle de vuelta, tiene el mismo nombre y casi no cambia de dirección.
bool es_cuadra_siguiente(const RedVial& red, std::size_t actual, std::size_t siguiente, double cambio_maximo) {
  const Tramo& a = red.tramos()[actual];
  const Tramo& b = red.tramos()[siguiente];
  if (a.hasta != b.desde || a.opuesto == siguiente || a.nombre != b.nombre) return false;
  if (a.geometria.size() < 2 || b.geometria.size() < 2) return false;
  return diferencia_rumbo(rumbo_final(a), rumbo_inicial(b)) < cambio_maximo;
}

// El punto que está a la mitad del trazado de un tramo: lo que se informa de una cuadra de la racha que el
// recorrido ya dejó atrás.
Punto mitad_del_tramo(const Tramo& t) {
  if (t.geometria.empty()) return {};
  double total = 0;
  for (std::size_t i = 1; i < t.geometria.size(); ++i) total += distancia_m(t.geometria[i - 1], t.geometria[i]);
  double falta = total / 2;
  for (std::size_t i = 1; i < t.geometria.size(); ++i) {
    const double trozo = distancia_m(t.geometria[i - 1], t.geometria[i]);
    if (trozo >= falta && trozo > 0) {
      const double f = falta / trozo;
      return {t.geometria[i - 1].lat + (t.geometria[i].lat - t.geometria[i - 1].lat) * f,
              t.geometria[i - 1].lon + (t.geometria[i].lon - t.geometria[i - 1].lon) * f};
    }
    falta -= trozo;
  }
  return t.geometria.back();
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
      e.rapido_desde.reset();
      cortar_racha(e.tramos_lentos, e.informados, e.metros_lentos, e.segundos_lentos);
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
    const Punto desde_donde = e.ultima.posicion;
    if (rumbo_ahora) e.rumbo = rumbo_ahora;
    e.ultima = l;

    // RN-04.22, P-36: cuenta cuánto tiempo seguido va a velocidad de vehículo, esté o no sobre una calle de la red.
    if (kmh > kVelocidadManejoKmh) {
      if (!e.rapido_desde) e.rapido_desde = comienzo;
      if (l.momento - *e.rapido_desde >= kTiempoManejoS) e.manejando_hasta = l.momento;
    } else {
      e.rapido_desde.reset();
    }

    // RN-04.8, P-24, D-18: el tramo vehicular a no más de 30 m, en el sentido en que avanza.
    const auto tramo = red_ ? red_->tramo_en_sentido(l.posicion.lat, l.posicion.lon, sentido, kDistanciaTramoM)
                            : std::nullopt;
    if (giro || !tramo) {
      cortar_racha(e.tramos_lentos, e.informados, e.metros_lentos, e.segundos_lentos);
      continue;
    }
    // RN-04.9, P-02: lento es ir por debajo de la mitad de la velocidad esperada de la vía.
    const double esperada_kmh = velocidad_esperada_mps(red_->tramos()[tramo->tramo].tipo_via) * 3.6;
    if (kmh >= esperada_kmh / 2) {
      cortar_racha(e.tramos_lentos, e.informados, e.metros_lentos, e.segundos_lentos);
      continue;
    }
    // RN-04.24, D-44: la racha sigue en la misma cuadra, en una cuadra de la racha a la que el GPS volvió por un
    // temblor junto a la esquina, o en la cuadra siguiente de la misma calle. En cualquier otro tramo empieza de nuevo.
    const bool sigue = !e.tramos_lentos.empty() &&
                       (contiene(e.tramos_lentos, tramo->tramo) ||
                        es_cuadra_siguiente(*red_, e.tramos_lentos.back(), tramo->tramo, kCambioMaximoMismaCalleGrados));
    if (!sigue) {
      cortar_racha(e.tramos_lentos, e.informados, e.metros_lentos, e.segundos_lentos);
      e.lento_desde = comienzo;
      e.inicio_lento = desde_donde;
      e.ultimo_avance = desde_donde;
      e.avance_desde = comienzo;
    }
    // RN-04.26: cada vez que se aleja 10 m del último punto donde avanzó, vuelve a contar desde ahí.
    if (distancia_m(e.ultimo_avance, l.posicion) >= kAvanceRecienteM) {
      e.ultimo_avance = l.posicion;
      e.avance_desde = l.momento;
    }
    if (!contiene(e.tramos_lentos, tramo->tramo)) e.tramos_lentos.push_back(tramo->tramo);
    e.metros_lentos += metros;
    e.segundos_lentos += static_cast<double>(dt);

    // RN-04.10: 2 minutos seguidos lento, en la misma cuadra o en las siguientes de la misma calle. Además,
    // RN-04.22: venía manejando hace poco, RN-04.23: avanzó desde donde empezó la racha, y RN-04.26: sigue
    // avanzando. El nivel sale de la velocidad media de la racha: por debajo de la cuarta parte de la esperada, muy
    // congestionado.
    const bool dos_minutos = l.momento - e.lento_desde >= kTiempoCongestionS;
    const bool venia_manejando = e.manejando_hasta && l.momento - *e.manejando_hasta <= kVentanaManejoS;
    const bool avanzo = distancia_m(e.inicio_lento, l.posicion) >= kAvanceMinimoM;
    // RN-04.26: y sigue avanzando. Quien se acercó despacio y estacionó ya cumple RN-04.23, pero lleva más de
    // 90 segundos sin moverse 10 m.
    const bool sigue_avanzando = l.momento - e.avance_desde <= kSinAvanzarMaximoS;
    if (dos_minutos && venia_manejando && avanzo && sigue_avanzando) {
      const double media_kmh = e.metros_lentos / e.segundos_lentos * 3.6;
      const NivelTrafico nivel =
          media_kmh < esperada_kmh / 4 ? NivelTrafico::muy_congestionado : NivelTrafico::congestionado;
      // Se informa la cuadra donde está ahora y, una sola vez por racha, cada cuadra que ya dejó atrás.
      for (const std::size_t t : e.tramos_lentos) {
        const bool actual = t == tramo->tramo;
        if (!actual && contiene(e.informados, t)) continue;
        if (!contiene(e.informados, t)) e.informados.push_back(t);
        CongestionDetectada c;
        c.tramo = t;
        c.punto = actual ? l.posicion : mitad_del_tramo(red_->tramos()[t]);
        c.nivel = nivel;
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
  }
  if (!e.con_lectura) {
    estados_.erase({simulado, recorrido});  // no llegó ninguna lectura útil: no hay nada que recordar
  } else {
    e.visto = ahora;
  }
  return resultado;
}

}  // namespace churoviaje
