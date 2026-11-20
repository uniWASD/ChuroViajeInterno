// Pruebas de analizar_telemetria (#73, #172, CU-04) con recorridos fabricados. Usa tests/datos/red_telemetria.json: la
// Av. La Banda (avenida principal, 40 km/h esperados) va de oeste a este por la latitud -21.54 y la cruza, en la
// longitud -64.73, la Calle Sucre (zona residencial, 20 km/h), que sube hacia el norte.
#include <gtest/gtest.h>

#include <cmath>
#include <cstdint>
#include <fstream>
#include <memory>
#include <set>
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

json red_de_prueba() {
  std::ifstream archivo(RED_TELEMETRIA);
  return json::parse(archivo);
}

struct Prueba {
  std::shared_ptr<const RedVial> red;
  Nucleo nucleo;
  std::int64_t ahora = kInicio;
  int siguiente_id = 1;

  explicit Prueba(const json& documento = red_de_prueba())
      : red(std::make_shared<const RedVial>(RedVial::desde_json(documento))), nucleo(red) {
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

// 200 m al este del extremo oeste de la avenida, bien lejos del cruce. Empieza ahí, sin haber manejado antes.
std::vector<Lectura> en_la_avenida() { return desde(kLatAvenida, -64.738); }

// RN-04.22: llega manejando a (lat, lon) con el rumbo dado: 15 segundos a 40 km/h, que terminan en ese punto.
std::vector<Lectura> llega_manejando(double lat, double lon, double rumbo, int segundos = 15) {
  const double metros = 40 / 3.6 * segundos;
  auto lecturas = desde(lat - metros * std::cos(rumbo * kPi / 180.0) * grados_lat_por_metro(),
                        lon - metros * std::sin(rumbo * kPi / 180.0) * grados_lon_por_metro());
  avanzar(lecturas, 40, rumbo, segundos);
  return lecturas;
}

// El caso de siempre: llega manejando hacia el este por la avenida, a 200 m del extremo oeste.
std::vector<Lectura> llega_por_la_avenida() { return llega_manejando(kLatAvenida, -64.738, 90); }

// Un teléfono quieto: el GPS tiembla unos metros de un lado a otro, durante `segundos`, una lectura cada 3 s.
void quieto(std::vector<Lectura>& lecturas, int segundos) {
  const Lectura centro = lecturas.back();
  for (int t = 3; t <= segundos; t += 3) {
    const double norte = (t / 3 % 2 == 0 ? 3.0 : -3.0) * grados_lat_por_metro();
    const double este = (t / 3 % 4 < 2 ? 4.0 : -4.0) * grados_lon_por_metro();
    lecturas.push_back({centro.lat + norte, centro.lon + este, centro.momento + t});
  }
}

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
  auto lecturas = llega_por_la_avenida();
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
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 8, 90, 60);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, ElNivelDependeDeLaVelocidadEsperadaDeLaVia) {
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 15, 90, 126);  // entre la cuarta parte (10) y la mitad (20) de 40 km/h
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  EXPECT_EQ(congestiones.back().at("nivel"), "CONGESTIONADO");
}

TEST(Telemetria, AVelocidadNormalNoHayCongestion) {
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 30, 90, 90);  // más de la mitad de 40: fluido
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, DetenidoDosMinutosNoEsCongestion) {
  // Antes (Sprint 5) esto era congestión. Con RN-04.23 ya no: en 2 minutos a 0,3 km/h avanza 10 m, menos de los
  // 25 m de P-37. Puede ser alguien que estacionó junto a la avenida con la app abierta.
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 0.3, 90, 126);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
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
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 8, 90, 110);
  avanzar(lecturas, 8, 180, 15);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());

  // La misma lentitud, sin doblar, sí completa los 2 minutos.
  Prueba sin_giro;
  auto recta = llega_por_la_avenida();
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
  auto lentas = llega_por_la_avenida();
  avanzar(lentas, 8, 90, 126);
  for (std::size_t i = 8; i < lentas.size(); i += 5) {
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
  auto lecturas = llega_manejando(kLatAvenida, -64.7320, 270);
  avanzar(lecturas, 8, 270, 126);  // hacia el oeste
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  for (const json& c : congestiones) {
    EXPECT_EQ(c.at("tramo"), "avenida:vuelta") << c.dump();
  }
}

TEST(Telemetria, CP118_UnRecorridoSimuladoLentoDaUnaCongestionSimulada) {
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  const json congestiones = p.enviar_en_grupos("fantasma-1", true, lecturas);
  ASSERT_FALSE(congestiones.empty());
  EXPECT_EQ(congestiones.back().at("simulado"), true);
}

TEST(Telemetria, LosRecorridosSimuladosYRealesNoSeMezclan) {
  Prueba p;
  // El mismo identificador: llega manejando y va lento 66 s como real, y sigue lento 60 s más como simulado. Si se
  // mezclaran, el simulado heredaría el manejo y la racha, y llegaría a 2 minutos.
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  const std::vector<Lectura> primera(lecturas.begin(), lecturas.begin() + 6 + 22);
  const std::vector<Lectura> segunda(lecturas.begin() + 6 + 22, lecturas.end());
  EXPECT_TRUE(p.enviar_en_grupos("mismo-id", false, primera).empty());
  EXPECT_TRUE(p.enviar_en_grupos("mismo-id", true, segunda).empty());
}

TEST(Telemetria, LejosDeTodaCalleLaLecturaSeIgnora) {
  Prueba p;
  auto lecturas = llega_manejando(kLatAvenida + 50 * grados_lat_por_metro(), -64.738, 90);  // 50 m al norte
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
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  const json congestiones = p.enviar_en_grupos("recorrido-secreto", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  for (const json& c : congestiones) {
    // Exactamente estos campos: si alguien agrega uno nuevo, la prueba obliga a revisar que no identifique al
    // recorrido (RN-04.4). El trazado es el del tramo, que es público, no el de las lecturas.
    std::set<std::string> campos;
    for (const auto& [campo, valor] : c.items()) campos.insert(campo);
    EXPECT_EQ(campos, (std::set<std::string>{"tramo", "punto", "nivel", "simulado", "trazado"})) << c.dump();
    EXPECT_EQ(c.dump().find("recorrido-secreto"), std::string::npos);
  }
}

TEST(Telemetria, VariosRecorridosEnUnMensajeDanUnaCongestionPorTramo) {
  Prueba p;
  auto a = llega_por_la_avenida();
  auto b = llega_manejando(kLatAvenida, -64.7381, 90);
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

// --- #172: «venía manejando», «tiene que avanzar» y la cuadra siguiente (RN-04.22 a RN-04.25) -------------------

TEST(Telemetria, CP171_ManejoYDespuesQuedoQuietoJuntoALaAvenidaNoEsCongestion) {
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  quieto(lecturas, 150);  // estacionó: el GPS tiembla 3 a 4 m de un lado a otro durante 2 minutos y medio
  // Sumando cada paso, el temblor daría más de 200 m «recorridos»; de punta a punta no llega a 25 m.
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, CP172_SinHaberManejadoAntesNoEsCongestion) {
  // Lento 2 minutos desde que se abrió la app: puede ser alguien que camina o trota por la vereda.
  Prueba p;
  auto lecturas = en_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());

  // Lo mismo, pero antes manejó 15 segundos a 40 km/h: sí es congestión.
  Prueba q;
  auto manejo = llega_por_la_avenida();
  avanzar(manejo, 8, 90, 126);
  EXPECT_FALSE(q.enviar_en_grupos("recorrido-1", false, manejo).empty());
}

TEST(Telemetria, CP172_ManejarMenosDe15SegundosNoAlcanza) {
  // Un tirón de 9 segundos puede ser un salto del GPS o una bicicleta en bajada: no cuenta como manejar.
  Prueba p;
  auto lecturas = llega_manejando(kLatAvenida, -64.738, 90, 9);
  avanzar(lecturas, 8, 90, 126);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, CP172_DiezMinutosDespuesDeManejarDejaDeContar) {
  // Llega manejando y después avanza a 2 km/h durante 11 minutos. Hay congestión desde los 2 minutos, pero a los
  // 10 minutos de la última vez que manejó deja de informarla (P-36).
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  const std::int64_t manejo = lecturas.back().momento;
  avanzar(lecturas, 2, 90, 11 * 60);
  bool antes = false, despues = false;
  for (std::size_t i = 0; i < lecturas.size(); i += 5) {
    const std::vector<Lectura> grupo(lecturas.begin() + static_cast<std::ptrdiff_t>(i),
                                     lecturas.begin() + static_cast<std::ptrdiff_t>(std::min(i + 5, lecturas.size())));
    p.ahora = grupo.back().momento;
    const json r = p.enviar(p.mensaje("recorrido-1", false, grupo));
    if (r.at("congestiones").empty()) continue;
    if (grupo.back().momento - manejo <= 10 * 60) antes = true;
    if (grupo.front().momento - manejo > 10 * 60) despues = true;
  }
  EXPECT_TRUE(antes);
  EXPECT_FALSE(despues);
}

TEST(Telemetria, CP173_UnaFilaQueAvanzaATironesEsCongestion) {
  // Después de manejar: 30 s detenido y 6 s a 10 km/h (unos 17 m), varias veces. En 2 minutos avanza unos 55 m.
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  for (int i = 0; i < 4; ++i) {
    avanzar(lecturas, 0, 90, 30);
    avanzar(lecturas, 10, 90, 6);
  }
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  EXPECT_EQ(congestiones.back().at("tramo"), "avenida:ida");
  EXPECT_EQ(congestiones.back().at("nivel"), "MUY_CONGESTIONADO");
}

TEST(Telemetria, CP174_LaRachaSigueEnLaCuadraSiguienteDeLaMismaCalle) {
  // Lento hacia el este, 100 m antes del cruce con la Calle Sucre: pasa de avenida:ida a avenida2:ida, la cuadra
  // siguiente de la Av. La Banda. Ninguna de las dos cuadras sola tiene 2 minutos, pero la racha sí.
  Prueba p;
  auto lecturas = llega_manejando(kLatAvenida, -64.7310, 90);
  avanzar(lecturas, 8, 90, 126);
  ASSERT_GT(lecturas.back().lon, -64.73);  // terminó pasado el cruce
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  std::set<std::string> tramos;
  for (const json& c : congestiones) tramos.insert(c.at("tramo"));
  EXPECT_EQ(tramos, (std::set<std::string>{"avenida:ida", "avenida2:ida"}));
  // La cuadra que ya dejó atrás se informa una sola vez y con el punto en la mitad de su trazado.
  int atras = 0;
  for (const json& c : congestiones) {
    if (c.at("tramo") != "avenida:ida") continue;
    ++atras;
    EXPECT_NEAR(c.at("punto")[1].get<double>(), -64.735, 1e-6);
  }
  EXPECT_EQ(atras, 1);
}

TEST(Telemetria, CP174_OtraCalleNoContinuaLaRacha) {
  // La misma red, pero la cuadra que sigue al cruce se llama distinto: es otra calle y la racha empieza de nuevo.
  json red = red_de_prueba();
  for (json& t : red["tramos"]) {
    if (t["id"] == "avenida2:ida" || t["id"] == "avenida2:vuelta") t["nombre"] = "Av. Otra";
  }
  Prueba p(red);
  auto lecturas = llega_manejando(kLatAvenida, -64.7310, 90);
  avanzar(lecturas, 8, 90, 126);
  EXPECT_TRUE(p.enviar_en_grupos("recorrido-1", false, lecturas).empty());
}

TEST(Telemetria, CP175_CadaCongestionTraeElTrazadoDeSuTramo) {
  Prueba p;
  auto lecturas = llega_por_la_avenida();
  avanzar(lecturas, 8, 90, 126);
  const json congestiones = p.enviar_en_grupos("recorrido-1", false, lecturas);
  ASSERT_FALSE(congestiones.empty());
  EXPECT_EQ(congestiones.back().at("trazado"), json::parse("[[-21.54,-64.74],[-21.54,-64.73]]"));
}
