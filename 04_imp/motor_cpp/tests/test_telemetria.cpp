// Pruebas de analizar_telemetria (#73, CU-04) con recorridos fabricados. Usa tests/datos/red_telemetria.json: la
// Av. La Banda (avenida principal, 40 km/h esperados) va de oeste a este por la latitud -21.54 y la cruza, en la
// longitud -64.73, la Calle Sucre (zona residencial, 20 km/h), que sube hacia el norte.
#include <gtest/gtest.h>

#include <cmath>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "nucleo.hpp"
#include "red_vial.hpp"
#include "telemetria.hpp"
#include "tiempo.hpp"

using churoviaje::diferencia_rumbo;
using churoviaje::Nucleo;
using churoviaje::Punto;
using churoviaje::RedVial;
using churoviaje::rumbo_grados;
using churoviaje::segundos_desde_iso;
using json = nlohmann::json;
namespace codigo = churoviaje::codigo_error;

namespace {

const std::int64_t kInicio = *segundos_desde_iso("2026-11-14T10:00:00-04:00");
constexpr double kLatAvenida = -21.54;
constexpr double kPi = 3.14159265358979323846;
constexpr double kRadio = 6371008.8;

// Grados de longitud y de latitud que equivalen a un metro en la zona de la red.
double grados_lon_por_metro() { return 1.0 / (kRadio * std::cos(kLatAvenida * kPi / 180.0) * kPi / 180.0); }
double grados_lat_por_metro() { return 1.0 / (kRadio * kPi / 180.0); }

std::string iso(std::int64_t segundos) {
  // Fecha UTC con segundos, que el núcleo lee igual que una con -04:00.
  const std::time_t t = static_cast<std::time_t>(segundos);
  std::tm tm{};
#ifdef _WIN32
  gmtime_s(&tm, &t);
#else
  gmtime_r(&t, &tm);
#endif
  char texto[32];
  std::strftime(texto, sizeof texto, "%Y-%m-%dT%H:%M:%SZ", &tm);
  return texto;
}

struct Lectura {
  double lat;
  double lon;
  std::int64_t momento;
  double precision = 8;
};

// Un vehículo que avanza en línea recta: rumbo 90 es hacia el este, 270 hacia el oeste, 0 al norte y 180 al sur.
// Agrega una lectura cada 3 segundos durante `segundos` (P-13), sin repetir la del punto de partida.
void avanzar(std::vector<Lectura>& lecturas, double kmh, double rumbo, int segundos) {
  const double mps = kmh / 3.6;
  for (int t = 3; t <= segundos; t += 3) {
    const Lectura& anterior = lecturas.back();
    const double metros = mps * 3;
    const double este = metros * std::sin(rumbo * kPi / 180.0);
    const double norte = metros * std::cos(rumbo * kPi / 180.0);
    lecturas.push_back({anterior.lat + norte * grados_lat_por_metro(), anterior.lon + este * grados_lon_por_metro(),
                        anterior.momento + 3});
  }
}

struct Prueba {
  std::shared_ptr<const RedVial> red = std::make_shared<const RedVial>(RedVial::cargar_archivo(RED_TELEMETRIA));
  Nucleo nucleo{red};
  std::int64_t ahora = kInicio;
  int siguiente_id = 1;

  Prueba() {
    nucleo.usar_reloj([this] { return ahora; });
  }

  json enviar(json peticion) {
    peticion["id"] = siguiente_id++;
    const auto r = nucleo.atender(peticion.dump());
    EXPECT_TRUE(r.has_value());
    return r ? json::parse(r->linea) : json();
  }

  json mensaje(const std::string& recorrido, bool simulado, const std::vector<Lectura>& lecturas) const {
    json lista = json::array();
    for (const Lectura& l : lecturas) {
      lista.push_back({{"posicion", {l.lat, l.lon}}, {"precision_m", l.precision}, {"momento", iso(l.momento)}});
    }
    return {{"operacion", "analizar_telemetria"},
            {"recorridos", json::array({{{"recorrido", recorrido}, {"simulado", simulado}, {"lecturas", lista}}})}};
  }

  // Manda las lecturas como la app: en grupos de 15 segundos (5 lecturas, P-13), con el reloj del núcleo al día.
  // Devuelve todas las congestiones que informó el núcleo.
  json enviar_en_grupos(const std::string& recorrido, bool simulado, const std::vector<Lectura>& lecturas) {
    json todas = json::array();
    for (std::size_t i = 0; i < lecturas.size(); i += 5) {
      const std::vector<Lectura> grupo(lecturas.begin() + static_cast<std::ptrdiff_t>(i),
                                       lecturas.begin() + static_cast<std::ptrdiff_t>(std::min(i + 5, lecturas.size())));
      ahora = grupo.back().momento;
      const json r = enviar(mensaje(recorrido, simulado, grupo));
      EXPECT_EQ(r.at("ok"), true) << r.dump();
      for (const json& c : r.at("congestiones")) todas.push_back(c);
    }
    return todas;
  }
};

std::vector<Lectura> desde(double lat, double lon) { return {{lat, lon, kInicio}}; }

// 200 m al este del extremo oeste de la avenida, bien lejos del cruce.
std::vector<Lectura> en_la_avenida() { return desde(kLatAvenida, -64.738); }

}  // namespace

TEST(Telemetria, RumboYDiferenciaDeRumbo) {
  EXPECT_NEAR(rumbo_grados({-21.54, -64.74}, {-21.54, -64.73}), 90.0, 0.1);   // hacia el este
  EXPECT_NEAR(rumbo_grados({-21.54, -64.73}, {-21.53, -64.73}), 0.0, 0.1);    // hacia el norte
  EXPECT_NEAR(rumbo_grados({-21.53, -64.73}, {-21.54, -64.73}), 180.0, 0.1);  // hacia el sur
  EXPECT_NEAR(diferencia_rumbo(350, 10), 20.0, 1e-9);
  EXPECT_NEAR(diferencia_rumbo(90, 270), 180.0, 1e-9);
}

TEST(Telemetria, CP114_A8KmhDosMinutosEnUnaAvenidaHayCongestion) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 120);

  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  const json& c = congestiones.back();
  EXPECT_EQ(c.at("tramo"), "avenida:ida");          // el sentido hacia el este
  EXPECT_EQ(c.at("nivel"), "MUY_CONGESTIONADO");    // 8 km/h es menos de la cuarta parte de 40
  EXPECT_EQ(c.at("simulado"), false);
  EXPECT_EQ(c.at("punto")[0].get<double>(), std::round(lecturas.back().lat * 1e6) / 1e6);
  // La primera congestión llega recién con la lectura que completa los 2 minutos.
  EXPECT_EQ(congestiones.size(), 1u);
}

TEST(Telemetria, CP114_UnMinutoLentoNoEsCongestion) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 60);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, ElNivelDependeDeLaVelocidadEsperadaDeLaVia) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 15, 90, 126);  // entre la cuarta parte (10) y la mitad (20) de 40 km/h
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  EXPECT_EQ(congestiones.back().at("nivel"), "CONGESTIONADO");
}

TEST(Telemetria, AVelocidadNormalNoHayCongestion) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 30, 90, 90);  // más de la mitad de 40: fluido
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, DetenidoDosMinutosTambienEsCongestion) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 0.3, 90, 126);  // el GPS apenas se mueve: está detenido en la fila
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  EXPECT_EQ(congestiones.back().at("nivel"), "MUY_CONGESTIONADO");
}

TEST(Telemetria, CP115_FrenarParaDoblarLaEsquinaNoEsCongestion) {
  Prueba p;
  // Llega por la avenida hacia el este, frena para doblar hacia el norte en la Calle Sucre y sigue.
  auto lecturas = desde(kLatAvenida, -64.7330);
  avanzar(lecturas, 40, 90, 27);  // ~300 m a velocidad normal hasta la esquina
  avanzar(lecturas, 12, 90, 6);
  avanzar(lecturas, 10, 0, 9);    // dobla: el rumbo cambia 90 grados yendo lento
  avanzar(lecturas, 25, 0, 60);   // sigue por la Calle Sucre a velocidad normal para la zona
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, CP115_ElGiroCortaLaRachaLentaAunqueSigaCercaDeLaAvenida) {
  Prueba p;
  // 110 s lento en la avenida y después dobla hacia el sur, por una entrada que no está en la red. Las primeras
  // lecturas del giro siguen a menos de 30 m de la avenida: sin RN-04.7 completarían los 2 minutos.
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 110);
  avanzar(lecturas, 8, 180, 15);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());

  // La misma lentitud, sin doblar, sí completa los 2 minutos.
  Prueba sin_giro;
  auto recta = en_la_avenida();
  avanzar(recta, 8, 90, 125);
  EXPECT_FALSE(sin_giro.enviar_en_grupos("recorrido-1", false, recta).empty());
}

TEST(Telemetria, CP116_UnaLecturaConPrecisionDe80MetrosSeDescarta) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 12);
  lecturas[2].precision = 80;
  lecturas[2].lat += 0.003;  // un salto de 300 m: si se usara, daría una velocidad absurda
  p.ahora = lecturas.back().momento;
  const json r = p.enviar(p.mensaje("recorrido-1", false, lecturas));
  EXPECT_EQ(r.at("lecturas_descartadas"), 1);
  EXPECT_EQ(r.at("lecturas_validas"), 4);

  // Y no rompe la racha: con una lectura imprecisa cada 15 segundos, 2 minutos lentos siguen siendo congestión.
  Prueba q;
  auto lentas = en_la_avenida();
  avanzar(lentas, 8, 90, 126);
  for (std::size_t i = 3; i < lentas.size(); i += 5) {
    lentas[i].precision = 80;
    lentas[i].lat += 0.003;
  }
  EXPECT_FALSE(q.enviar_en_grupos("recorrido-1", false, lentas).empty());
}

TEST(Telemetria, UnaLecturaAMasDe150KmhSeDescarta) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 9);
  lecturas.push_back({lecturas.back().lat, lecturas.back().lon + 200 * grados_lon_por_metro(), lecturas.back().momento + 3});
  p.ahora = lecturas.back().momento;
  const json r = p.enviar(p.mensaje("recorrido-1", false, lecturas));
  EXPECT_EQ(r.at("lecturas_descartadas"), 1);  // 200 m en 3 s son 240 km/h
}

TEST(Telemetria, CP117_EnUnaCalleDeDobleSentidoSoloSeCongestionaElSentidoMedido) {
  Prueba p;
  auto lecturas = desde(kLatAvenida, -64.7320);
  avanzar(lecturas, 8, 270, 126);  // hacia el oeste
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  for (const json& c : congestiones) {
    EXPECT_EQ(c.at("tramo"), "avenida:vuelta") << c.dump();
  }
}

TEST(Telemetria, CP118_UnRecorridoSimuladoLentoDaUnaCongestionSimulada) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  const json congestiones = p.enviar_en_grupos("fantasma-1", true, lecturas);
  ASSERT_FALSE(congestiones.empty());
  EXPECT_EQ(congestiones.back().at("simulado"), true);
}

TEST(Telemetria, LosRecorridosSimuladosYRealesNoSeMezclan) {
  Prueba p;
  // El mismo identificador, la mitad del tiempo como real y la otra mitad como simulado: ninguno llega a 2 minutos.
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  const std::vector<Lectura> primera(lecturas.begin(), lecturas.begin() + 21);
  const std::vector<Lectura> segunda(lecturas.begin() + 21, lecturas.end());
  EXPECT_TRUE(p.enviar_en_grupos("mismo-id", false, primera).empty());
  EXPECT_TRUE(p.enviar_en_grupos("mismo-id", true, segunda).empty());
}

TEST(Telemetria, LejosDeTodaCalleLaLecturaSeIgnora) {
  Prueba p;
  auto lecturas = desde(kLatAvenida + 50 * grados_lat_por_metro(), -64.738);  // 50 m al norte de la avenida
  avanzar(lecturas, 8, 90, 126);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, ElNucleoOlvidaUnRecorridoSinLecturasNuevasEn5Minutos) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 15);
  p.enviar_en_grupos("recorrido-1", false, lecturas);
  EXPECT_EQ(p.nucleo.recorridos_en_memoria(), 1u);
  p.ahora += 5 * 60;
  EXPECT_EQ(p.nucleo.recorridos_en_memoria(), 1u);
  p.ahora += 1;
  EXPECT_EQ(p.nucleo.recorridos_en_memoria(), 0u);
}

TEST(Telemetria, LaRespuestaNoTraeElRecorridoNiDatosDelConductor) {
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  const json congestiones = p.enviar_en_grupos("recorrido-secreto", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  for (const json& c : congestiones) {
    EXPECT_EQ(c.size(), 4u) << c.dump();  // tramo, punto, nivel y simulado
    EXPECT_EQ(c.dump().find("recorrido-secreto"), std::string::npos);
  }
}

TEST(Telemetria, VariosRecorridosEnUnMensajeDanUnaCongestionPorTramo) {
  Prueba p;
  auto a = en_la_avenida();
  auto b = desde(kLatAvenida, -64.7381);
  avanzar(a, 8, 90, 126);
  avanzar(b, 15, 90, 126);
  json mensaje = p.mensaje("a", false, a);
  mensaje["recorridos"].push_back(p.mensaje("b", false, b)["recorridos"][0]);
  p.ahora = a.back().momento;
  const json r = p.enviar(mensaje);
  ASSERT_EQ(r.at("congestiones").size(), 1u) << r.dump();
  EXPECT_EQ(r.at("congestiones")[0].at("nivel"), "MUY_CONGESTIONADO");  // vale la más grave
}

TEST(Telemetria, PeticionMalFormada) {
  Prueba p;
  EXPECT_EQ(p.enviar({{"operacion", "analizar_telemetria"}}).at("error").at("codigo"), codigo::peticion_invalida);
  const json sin_momento = p.enviar({{"operacion", "analizar_telemetria"},
                                     {"recorridos", json::array({{{"recorrido", "r"},
                                                                  {"lecturas", json::array({{{"posicion", {-21.54, -64.738}},
                                                                                             {"precision_m", 5}}})}}})}});
  EXPECT_EQ(sin_momento.at("error").at("codigo"), codigo::peticion_invalida);
  const json sin_recorrido = p.enviar({{"operacion", "analizar_telemetria"},
                                       {"recorridos", json::array({{{"lecturas", json::array()}}})}});
  EXPECT_EQ(sin_recorrido.at("error").at("codigo"), codigo::peticion_invalida);
}

TEST(Telemetria, SinRedVialRespondeRedNoCargada) {
  Nucleo sin_red;
  const auto r = sin_red.atender(R"({"id":1,"operacion":"analizar_telemetria","recorridos":[]})");
  ASSERT_TRUE(r.has_value());
  EXPECT_EQ(json::parse(r->linea).at("error").at("codigo"), codigo::red_no_cargada);
}
