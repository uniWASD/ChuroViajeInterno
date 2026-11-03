// Pruebas del cálculo de ruta (#58): A*, paradas, distancia, tiempo, trazado y la respuesta de calcular_ruta.
// La red irregular es la del prototipo de 05_pro/PruebaCalculoRuta (redes_de_prueba.py), pasada al formato
// del archivo de la red vial: las rutas deben ser las mismas que daba en Python (CP-14 a CP-17).
#include <gtest/gtest.h>

#include <chrono>
#include <cmath>
#include <cstdlib>
#include <memory>
#include <random>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "nucleo.hpp"
#include "red_vial.hpp"
#include "ruta.hpp"
#include "trafico.hpp"

using churoviaje::Algoritmo;
using churoviaje::buscar_camino;
using churoviaje::calcular_ruta;
using churoviaje::EstadoRuta;
using churoviaje::NivelTrafico;
using churoviaje::Nucleo;
using churoviaje::Punto;
using churoviaje::RedVial;
using churoviaje::Trafico;
using json = nlohmann::json;
namespace codigo = churoviaje::codigo_error;

namespace {

const RedVial& irregular() {
  static const RedVial red = RedVial::cargar_archivo(RED_IRREGULAR);
  return red;
}

std::size_t nodo(const RedVial& red, const std::string& id) {
  const auto n = red.buscar_nodo(id);
  EXPECT_TRUE(n.has_value()) << "no existe el nodo " << id;
  return n.value_or(0);
}

Punto punto(const RedVial& red, const std::string& id) { return red.nodos()[nodo(red, id)].posicion; }

// Los ids de los nodos por los que pasa el camino más corto entre dos nodos (sin tráfico).
std::vector<std::string> ruta_nodos(const RedVial& red, const std::string& desde, const std::string& hasta,
                                    const Trafico* trafico = nullptr, Algoritmo algoritmo = Algoritmo::a_estrella) {
  const auto camino = buscar_camino(red, nodo(red, desde), nodo(red, hasta), trafico, algoritmo);
  std::vector<std::string> ids;
  if (!camino.encontrado) return ids;
  for (const auto n : churoviaje::nodos_del_camino(red, nodo(red, desde), camino.tramos)) ids.push_back(red.nodos()[n].id);
  return ids;
}

double longitud(const RedVial& red, const std::string& id_tramo) {
  for (const auto& t : red.tramos()) {
    if (t.id == id_tramo) return t.longitud_m;
  }
  ADD_FAILURE() << "no existe el tramo " << id_tramo;
  return 0;
}

std::size_t tramo(const RedVial& red, const std::string& id_tramo) {
  for (std::size_t i = 0; i < red.tramos().size(); ++i) {
    if (red.tramos()[i].id == id_tramo) return i;
  }
  ADD_FAILURE() << "no existe el tramo " << id_tramo;
  return 0;
}

using Ids = std::vector<std::string>;

}  // namespace

// ------------------------------------------------------------------ primer paso: de nodo a nodo

TEST(RutaEntreNodos, DevuelveLaListaDeNodosEnOrden) {
  EXPECT_EQ(ruta_nodos(irregular(), "Plaza", "Universidad"), (Ids{"Plaza", "Mercado", "Universidad"}));
}

TEST(RutaEntreNodos, DelMismoNodoAlMismoNodoNoHayTramos) {
  const auto camino = buscar_camino(irregular(), nodo(irregular(), "Plaza"), nodo(irregular(), "Plaza"), nullptr);
  EXPECT_TRUE(camino.encontrado);
  EXPECT_TRUE(camino.tramos.empty());
  EXPECT_EQ(camino.costo, 0);
}

// ------------------------------------------------------------------ CP-71: las mismas rutas que en Python

TEST(RutaPrototipo, CP14_SinEventosConCallesDeLargoDesigual) {
  EXPECT_EQ(ruta_nodos(irregular(), "Plaza", "Universidad"), (Ids{"Plaza", "Mercado", "Universidad"}));
}

TEST(RutaPrototipo, CP15_ElAtajoDeUnSoloSentidoSoloSirveDeIda) {
  EXPECT_EQ(ruta_nodos(irregular(), "Mercado", "Terminal"), (Ids{"Mercado", "Terminal"}));
  EXPECT_EQ(ruta_nodos(irregular(), "Terminal", "Mercado"), (Ids{"Terminal", "Plaza", "Mercado"}));
}

TEST(RutaPrototipo, CP16_NodoCasiAisladoConCalleCurva) {
  EXPECT_EQ(ruta_nodos(irregular(), "Plaza", "Mirador"), (Ids{"Plaza", "Mercado", "Universidad", "Mirador"}));
  const double recta = churoviaje::distancia_m(punto(irregular(), "Universidad"), punto(irregular(), "Mirador"));
  EXPECT_NEAR(longitud(irregular(), "Universidad-Mirador"), 1.8 * recta, 0.01);
}

TEST(RutaPrototipo, CP17_SinCalleDirectaLaRutaRodea) {
  EXPECT_EQ(ruta_nodos(irregular(), "Plaza", "Barrio_Sur"), (Ids{"Plaza", "Terminal", "Barrio_Sur"}));
}

TEST(RutaPrototipo, CP09_SinCaminoPosibleNoHayRuta) {
  const auto camino = buscar_camino(irregular(), nodo(irregular(), "Plaza"), nodo(irregular(), "Aislado"), nullptr);
  EXPECT_FALSE(camino.encontrado);
  EXPECT_TRUE(camino.tramos.empty());
}

// ------------------------------------------------------------------ reglas sobre todos los pares

TEST(RutaReglas, NingunaRutaRecorreUnTramoAContramanoYTodasSonContinuas) {
  const RedVial& red = irregular();
  for (std::size_t a = 0; a < red.nodos().size(); ++a) {
    for (std::size_t b = 0; b < red.nodos().size(); ++b) {
      const auto camino = buscar_camino(red, a, b, nullptr);
      std::size_t actual = a;
      for (const auto t : camino.tramos) {
        // Cada tramo se recorre de su `desde` a su `hasta` (RN-03.6): el que sigue sale de donde llegó el anterior.
        ASSERT_EQ(red.tramos()[t].desde, actual);
        actual = red.tramos()[t].hasta;
      }
      if (camino.encontrado) {
        EXPECT_EQ(actual, b);
      }
    }
  }
}

TEST(RutaReglas, AEstrellaYDijkstraDanElMismoCostoEnTodosLosPares) {
  const RedVial& red = irregular();
  for (std::size_t a = 0; a < red.nodos().size(); ++a) {
    for (std::size_t b = 0; b < red.nodos().size(); ++b) {
      const auto con_a = buscar_camino(red, a, b, nullptr, Algoritmo::a_estrella);
      const auto con_d = buscar_camino(red, a, b, nullptr, Algoritmo::dijkstra);
      ASSERT_EQ(con_a.encontrado, con_d.encontrado);
      EXPECT_NEAR(con_a.costo, con_d.costo, 1e-6) << red.nodos()[a].id << " -> " << red.nodos()[b].id;
    }
  }
}

TEST(RutaReglas, LaEstimacionNuncaSuperaElCostoReal) {
  const RedVial& red = irregular();
  EXPECT_LE(red.factor_estimacion(), 1.0);
  for (std::size_t a = 0; a < red.nodos().size(); ++a) {
    for (std::size_t b = 0; b < red.nodos().size(); ++b) {
      const auto camino = buscar_camino(red, a, b, nullptr, Algoritmo::dijkstra);
      if (!camino.encontrado) continue;
      EXPECT_LE(churoviaje::distancia_m(red.nodos()[a].posicion, red.nodos()[b].posicion) * red.factor_estimacion(),
                camino.costo + 1e-9);
    }
  }
}

TEST(RutaReglas, ConLongitudesMasCortasQueLaLineaRectaElFactorBajaYLaRutaSigueSiendoLaOptima) {
  // Un archivo que rompe RN-03.7 (longitud menor que la línea recta) no debe hacer que A* elija mal.
  json doc = {{"version", 1}, {"fuente", "OpenStreetMap"}, {"fecha_extracto", "2026-10-28"},
              {"nodos", json::array({{{"id", "a"}, {"lat", -21.5}, {"lon", -64.7}},
                                     {{"id", "b"}, {"lat", -21.5}, {"lon", -64.69}},
                                     {{"id", "c"}, {"lat", -21.505}, {"lon", -64.695}}})},
              {"tramos", json::array()}};
  auto agregar = [&](const char* id, const char* d, const char* h, double l) {
    doc["tramos"].push_back({{"id", id}, {"desde", d}, {"hasta", h}, {"opuesto", nullptr}, {"tipo_via", "zona_residencial"},
                             {"longitud_m", l}, {"geometria", json::array({json::array({-21.5, -64.7}), json::array({-21.5, -64.69})})}});
  };
  agregar("ab", "a", "b", 2000);  // la recta mide unos 1 035 m
  agregar("ac", "a", "c", 100);   // mucho menos que su recta (unos 760 m)
  agregar("cb", "c", "b", 100);
  const RedVial red = RedVial::desde_json(doc);
  EXPECT_LT(red.factor_estimacion(), 0.2);
  EXPECT_EQ(ruta_nodos(red, "a", "b"), (Ids{"a", "c", "b"}));
}

// ------------------------------------------------------------------ tráfico preparado para el issue de tráfico

TEST(RutaTrafico, LosFactoresSonLosDelPrototipo) {
  EXPECT_EQ(churoviaje::factor(NivelTrafico::fluido), 1.0);
  EXPECT_EQ(churoviaje::factor(NivelTrafico::moderado), 1.5);
  EXPECT_EQ(churoviaje::factor(NivelTrafico::congestionado), 2.5);
  EXPECT_EQ(churoviaje::factor(NivelTrafico::muy_congestionado), 4.0);
}

TEST(RutaTrafico, UnTramoMasCaroHaceTomarElRodeo) {
  const RedVial& red = irregular();
  Trafico trafico(red.tramos().size());
  EXPECT_EQ(ruta_nodos(red, "Hospital", "Universidad", &trafico), (Ids{"Hospital", "Universidad"}));
  trafico.fijar(tramo(red, "Hospital-Universidad"), NivelTrafico::congestionado);
  EXPECT_EQ(ruta_nodos(red, "Hospital", "Universidad", &trafico), (Ids{"Hospital", "Plaza", "Mercado", "Universidad"}));
}

TEST(RutaTrafico, UnTramoCerradoNoSeUsa) {
  const RedVial& red = irregular();
  Trafico trafico(red.tramos().size());
  trafico.cerrar(tramo(red, "Terminal-Barrio_Sur"));
  EXPECT_TRUE(ruta_nodos(red, "Plaza", "Barrio_Sur", &trafico).empty());
}

// ------------------------------------------------------------------ calcular_ruta con coordenadas

TEST(CalcularRuta, DevuelveDistanciaTiempoTrazadoYTramos) {
  const RedVial& red = irregular();
  const auto ruta = calcular_ruta(red, punto(red, "Plaza"), punto(red, "Universidad"), {}, nullptr);
  ASSERT_EQ(ruta.estado, EstadoRuta::encontrada);
  ASSERT_EQ(ruta.tramos.size(), 2u);
  const double esperada = longitud(red, "Plaza-Mercado") + longitud(red, "Mercado-Universidad");
  EXPECT_NEAR(ruta.distancia_m, esperada, 1e-6);
  // RN-03.14: todos los tramos son de zona residencial, a 20 km/h.
  EXPECT_NEAR(ruta.tiempo_s, esperada / (20 / 3.6), 1e-6);
  // El trazado empieza en el origen ajustado, termina en el destino y no repite los puntos de unión.
  ASSERT_EQ(ruta.trazado.size(), 3u);
  EXPECT_EQ(ruta.trazado.front().lat, punto(red, "Plaza").lat);
  EXPECT_EQ(ruta.trazado.back().lat, punto(red, "Universidad").lat);
  EXPECT_EQ(ruta.nodos.size(), 3u);
}

TEST(CalcularRuta, UnPuntoCercaDeUnaEsquinaSeAjustaAElla) {
  const RedVial& red = irregular();
  Punto cerca = punto(red, "Plaza");
  cerca.lat += 0.0003;  // unos 33 m al norte
  const auto ruta = calcular_ruta(red, cerca, punto(red, "Mercado"), {}, nullptr);
  ASSERT_EQ(ruta.estado, EstadoRuta::encontrada);
  EXPECT_EQ(red.nodos()[ruta.nodos.front()].id, "Plaza");
}

TEST(CalcularRuta, CP72_ConUnaParadaPasaPorEllaYLaDistanciaEsLaSumaDeLosDosTrechos) {
  const RedVial& red = irregular();
  const auto ruta = calcular_ruta(red, punto(red, "Plaza"), punto(red, "Terminal"), {punto(red, "Universidad")}, nullptr);
  ASSERT_EQ(ruta.estado, EstadoRuta::encontrada);
  Ids ids;
  for (const auto n : ruta.nodos) ids.push_back(red.nodos()[n].id);
  EXPECT_EQ(ids, (Ids{"Plaza", "Mercado", "Universidad", "Mercado", "Terminal"}));
  const auto a = calcular_ruta(red, punto(red, "Plaza"), punto(red, "Universidad"), {}, nullptr);
  const auto b = calcular_ruta(red, punto(red, "Universidad"), punto(red, "Terminal"), {}, nullptr);
  EXPECT_NEAR(ruta.distancia_m, a.distancia_m + b.distancia_m, 1e-6);
}

TEST(CalcularRuta, LasParadasSeRespetanEnElOrdenRecibido) {
  const RedVial& red = irregular();
  const auto ruta = calcular_ruta(red, punto(red, "Plaza"), punto(red, "Plaza"),
                                  {punto(red, "Hospital"), punto(red, "Mercado")}, nullptr);
  ASSERT_EQ(ruta.estado, EstadoRuta::encontrada);
  Ids ids;
  for (const auto n : ruta.nodos) ids.push_back(red.nodos()[n].id);
  // Hospital antes que Mercado, aunque al revés fuera más corto.
  EXPECT_EQ(ids, (Ids{"Plaza", "Hospital", "Plaza", "Mercado", "Plaza"}));
}

TEST(CalcularRuta, UnPuntoLejosDeLaRedEsPuntoInvalido) {
  const RedVial& red = irregular();
  const Punto lejos{-21.60, -64.80};
  EXPECT_EQ(calcular_ruta(red, lejos, punto(red, "Plaza"), {}, nullptr).estado, EstadoRuta::punto_invalido);
  EXPECT_EQ(calcular_ruta(red, punto(red, "Plaza"), lejos, {}, nullptr).estado, EstadoRuta::punto_invalido);
  EXPECT_EQ(calcular_ruta(red, punto(red, "Plaza"), punto(red, "Mercado"), {lejos}, nullptr).estado,
            EstadoRuta::punto_invalido);
}

TEST(CalcularRuta, SinCaminoEsSinRuta) {
  const RedVial& red = irregular();
  EXPECT_EQ(calcular_ruta(red, punto(red, "Plaza"), punto(red, "Aislado"), {}, nullptr).estado, EstadoRuta::sin_ruta);
}

TEST(CalcularRuta, LaVelocidadDependeDelTipoDeVia) {
  EXPECT_NEAR(churoviaje::velocidad_esperada_mps(churoviaje::TipoVia::via_rapida), 55 / 3.6, 1e-9);
  EXPECT_NEAR(churoviaje::velocidad_esperada_mps(churoviaje::TipoVia::avenida_principal), 40 / 3.6, 1e-9);
  EXPECT_NEAR(churoviaje::velocidad_esperada_mps(churoviaje::TipoVia::zona_residencial), 20 / 3.6, 1e-9);
}

// ------------------------------------------------------------------ la operación calcular_ruta del núcleo

namespace {

json responder(Nucleo& nucleo, const std::string& linea) {
  const auto r = nucleo.atender(linea);
  EXPECT_TRUE(r.has_value());
  if (!r) return json();
  EXPECT_EQ(r->linea.find('\n'), std::string::npos);
  return json::parse(r->linea);
}

std::shared_ptr<const RedVial> plaza() { return std::make_shared<const RedVial>(RedVial::cargar_archivo(RED_PRUEBA_PLAZA)); }

}  // namespace

TEST(NucleoCalcularRuta, EncontradaTraeDistanciaTiempoTrazadoYTramosConSuCalle) {
  Nucleo nucleo(plaza());
  // De la esquina suroeste (osm:9001) a la noreste (osm:9003) de la plaza.
  const json j = responder(nucleo, R"({"id":17,"operacion":"calcular_ruta","origen":[-21.536,-64.7301],"destino":[-21.535,-64.7291],"paradas":[]})");
  EXPECT_EQ(j.at("id"), 17);
  EXPECT_EQ(j.at("ok"), true);
  EXPECT_EQ(j.at("estado"), "ENCONTRADA");
  EXPECT_EQ(j.at("distancia_m"), 215);  // 103,433 + 111,196 por cualquiera de los dos lados
  EXPECT_GT(j.at("tiempo_s").get<int>(), 0);
  ASSERT_EQ(j.at("tramos").size(), 2u);
  EXPECT_TRUE(j.at("tramos")[0].contains("id"));
  EXPECT_FALSE(j.at("tramos")[0].at("calle").get<std::string>().empty());
  ASSERT_EQ(j.at("trazado").size(), 3u);
  EXPECT_EQ(j.at("trazado")[0], json::array({-21.536, -64.7301}));
  EXPECT_EQ(j.at("trazado")[2], json::array({-21.535, -64.7291}));
}

TEST(NucleoCalcularRuta, SinParadasTambienSePuedeOmitirElCampo) {
  Nucleo nucleo(plaza());
  const json j = responder(nucleo, R"({"id":1,"operacion":"calcular_ruta","origen":[-21.536,-64.7301],"destino":[-21.536,-64.7291]})");
  EXPECT_EQ(j.at("estado"), "ENCONTRADA");
  EXPECT_EQ(j.at("tramos").size(), 1u);
}

TEST(NucleoCalcularRuta, ContraElSentidoDeUnaCalleDeUnSoloSentidoNoHayRuta) {
  Nucleo nucleo(plaza());
  // osm:9006 solo tiene una calle que llega: desde ahí no se puede salir.
  const json j = responder(nucleo, R"({"id":2,"operacion":"calcular_ruta","origen":[-21.534,-64.7291],"destino":[-21.536,-64.7301]})");
  EXPECT_EQ(j.at("ok"), true);
  EXPECT_EQ(j.at("estado"), "SIN_RUTA");
  EXPECT_FALSE(j.contains("trazado"));
}

TEST(NucleoCalcularRuta, UnPuntoFueraDeLaRedEsPuntoInvalidoConOkTrue) {
  Nucleo nucleo(plaza());
  const json j = responder(nucleo, R"({"id":3,"operacion":"calcular_ruta","origen":[-21.536,-64.7301],"destino":[-21.53,-64.726]})");
  EXPECT_EQ(j.at("ok"), true);
  EXPECT_EQ(j.at("estado"), "PUNTO_INVALIDO");
}

TEST(NucleoCalcularRuta, PeticionMalFormadaEsPeticionInvalida) {
  Nucleo nucleo(plaza());
  for (const char* linea : {
           R"({"id":4,"operacion":"calcular_ruta","destino":[-21.536,-64.7301]})",
           R"({"id":4,"operacion":"calcular_ruta","origen":[-21.536],"destino":[-21.536,-64.7301]})",
           R"({"id":4,"operacion":"calcular_ruta","origen":"plaza","destino":[-21.536,-64.7301]})",
           R"({"id":4,"operacion":"calcular_ruta","origen":[-21.536,-64.7301],"destino":[-21.536,-64.7291],"paradas":5})",
           R"({"id":4,"operacion":"calcular_ruta","origen":[-21.536,-64.7301],"destino":[-21.536,-64.7291],"paradas":[[1]]})"}) {
    const json j = responder(nucleo, linea);
    EXPECT_EQ(j.at("id"), 4);
    EXPECT_EQ(j.at("ok"), false) << linea;
    EXPECT_EQ(j.at("error").at("codigo"), codigo::peticion_invalida) << linea;
  }
}

TEST(NucleoCalcularRuta, SinRedVialRespondeRedNoCargada) {
  Nucleo nucleo;
  const json j = responder(nucleo, R"({"id":5,"operacion":"calcular_ruta","origen":[-21.536,-64.7301],"destino":[-21.535,-64.7291]})");
  EXPECT_EQ(j.at("ok"), false);
  EXPECT_EQ(j.at("error").at("codigo"), codigo::red_no_cargada);
}

TEST(NucleoCalcularRuta, ConDijkstraLaRespuestaEsLaMisma) {
  Nucleo a_estrella(plaza());
  Nucleo dijkstra(plaza(), Algoritmo::dijkstra);
  const std::string linea = R"({"id":6,"operacion":"calcular_ruta","origen":[-21.536,-64.732],"destino":[-21.534,-64.7291]})";
  const json a = responder(a_estrella, linea);
  const json d = responder(dijkstra, linea);
  EXPECT_EQ(a.at("estado"), "ENCONTRADA");
  EXPECT_EQ(a.at("distancia_m"), d.at("distancia_m"));
}

// ------------------------------------------------------------------ CP-73: rendimiento

namespace {

// Malla deformada de `lado` x `lado` esquinas (~100 m) con calles curvas y algunas de un solo sentido, como la
// red grande del prototipo (redes_de_prueba.py). Semilla fija: siempre es la misma red.
RedVial red_grande(int lado) {
  std::mt19937 azar(2026);
  std::uniform_real_distribution<double> corrimiento(-0.0002, 0.0002), curva(1.0, 1.4), moneda(0.0, 1.0);
  json doc = {{"version", 1}, {"fuente", "OpenStreetMap"}, {"fecha_extracto", "2026-10-28"},
              {"nodos", json::array()}, {"tramos", json::array()}};
  std::vector<Punto> pos;
  for (int i = 0; i < lado; ++i) {
    for (int j = 0; j < lado; ++j) {
      Punto p{-21.5355 + (i - lado / 2) * 0.0009 + corrimiento(azar), -64.7296 + (j - lado / 2) * 0.0009 + corrimiento(azar)};
      pos.push_back(p);
      doc["nodos"].push_back({{"id", "n" + std::to_string(i * lado + j)}, {"lat", p.lat}, {"lon", p.lon}});
    }
  }
  int k = 0;
  auto calle = [&](int a, int b) {
    const double l = churoviaje::distancia_m(pos[a], pos[b]) * curva(azar);
    const bool un_sentido = moneda(azar) < 0.2;
    if (un_sentido && moneda(azar) < 0.5) std::swap(a, b);
    const std::string ida = "t" + std::to_string(k++);
    const std::string vuelta = ida + "v";
    auto agregar = [&](const std::string& id, int d, int h, json opuesto) {
      doc["tramos"].push_back({{"id", id}, {"desde", "n" + std::to_string(d)}, {"hasta", "n" + std::to_string(h)},
                               {"opuesto", opuesto}, {"tipo_via", "zona_residencial"}, {"longitud_m", l},
                               {"geometria", json::array({{pos[d].lat, pos[d].lon}, {pos[h].lat, pos[h].lon}})}});
    };
    agregar(ida, a, b, un_sentido ? json(nullptr) : json(vuelta));
    if (!un_sentido) agregar(vuelta, b, a, ida);
  };
  for (int i = 0; i < lado; ++i) {
    for (int j = 0; j < lado; ++j) {
      if (j + 1 < lado) calle(i * lado + j, i * lado + j + 1);
      if (i + 1 < lado) calle(i * lado + j, (i + 1) * lado + j);
    }
  }
  return RedVial::desde_json(doc);
}

void medir_consultas(const RedVial& red, int consultas, double limite_s) {
  std::mt19937 azar(13);
  std::uniform_int_distribution<std::size_t> cualquiera(0, red.nodos().size() - 1);
  double peor = 0;
  int encontradas = 0;
  for (int c = 0; c < consultas; ++c) {
    const std::size_t a = c == 0 ? 0 : cualquiera(azar);
    const std::size_t b = c == 0 ? red.nodos().size() - 1 : cualquiera(azar);
    const auto inicio = std::chrono::steady_clock::now();
    const auto camino = buscar_camino(red, a, b, nullptr);
    peor = std::max(peor, std::chrono::duration<double>(std::chrono::steady_clock::now() - inicio).count());
    encontradas += camino.encontrado;
  }
  EXPECT_LT(peor, limite_s) << "la consulta más lenta tardó " << peor << " s";
  EXPECT_GT(encontradas, consultas / 2);
}

}  // namespace

TEST(RutaRendimiento, CP73_CienConsultasEnUnaRedDe10000EsquinasTardanMenosDe1SegundoCadaUna) {
  medir_consultas(red_grande(100), 100, 1.0);
}

TEST(RutaRendimiento, CP73_ConLaRedRealDeTarija) {
  // La red real no está en el repositorio (D-33): esta prueba corre solo si se indica dónde está.
  const char* ruta = std::getenv("RED_VIAL_REAL");
  if (ruta == nullptr || *ruta == '\0') GTEST_SKIP() << "define RED_VIAL_REAL con la ruta de red_vial_tarija.json";
  medir_consultas(RedVial::cargar_archivo(ruta), 100, 1.0);
}
