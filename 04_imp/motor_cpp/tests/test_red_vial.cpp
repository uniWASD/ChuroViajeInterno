// Pruebas de la red vial (T-096): carga, validación del formato, ajuste de puntos y consultar_estado.
#include <gtest/gtest.h>

#include <chrono>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>

#include <nlohmann/json.hpp>

#include "nucleo.hpp"
#include "red_vial.hpp"

using churoviaje::ErrorRedVial;
using churoviaje::Nucleo;
using churoviaje::RedVial;
using json = nlohmann::json;

namespace {

// Un tramo mínimo válido entre dos nodos de `doc`.
json tramo(const std::string& id, const std::string& desde, const std::string& hasta, json opuesto = nullptr) {
  return {{"id", id},
          {"desde", desde},
          {"hasta", hasta},
          {"opuesto", opuesto},
          {"nombre", "Calle de prueba"},
          {"tipo_via", "zona_residencial"},
          {"longitud_m", 100.0},
          {"geometria", json::array({json::array({-21.5, -64.7}), json::array({-21.5, -64.699})})}};
}

json red_minima() {
  return {{"version", 1},
          {"fuente", "OpenStreetMap"},
          {"fecha_extracto", "2026-10-28"},
          {"nodos", json::array({{{"id", "n1"}, {"lat", -21.5}, {"lon", -64.7}},
                                 {{"id", "n2"}, {"lat", -21.5}, {"lon", -64.699}}})},
          {"tramos", json::array({tramo("t1", "n1", "n2", "t2"), tramo("t2", "n2", "n1", "t1")})}};
}

std::filesystem::path escribir_temporal(const std::string& nombre, const std::string& contenido) {
  const auto ruta = std::filesystem::temp_directory_path() / ("churoviaje_" + nombre);
  std::ofstream(ruta, std::ios::binary) << contenido;
  return ruta;
}

// Carga `doc` y devuelve el mensaje del error, o "" si cargó bien.
std::string error_al_cargar(const json& doc) {
  try {
    RedVial::desde_json(doc);
  } catch (const ErrorRedVial& e) {
    return e.what();
  }
  return "";
}

// Desplazamiento en grados que equivale a `metros` hacia el norte / este cerca de Tarija.
double grados_lat(double metros) { return metros / 111320.0; }
double grados_lon(double metros, double lat = -21.5355) {
  return metros / (111320.0 * std::cos(lat * 3.14159265358979323846 / 180.0));
}

}  // namespace

// ------------------------------------------------------------------ carga

TEST(RedVialCarga, CargaElArchivoDeEjemploDelFormato) {
  const RedVial red = RedVial::cargar_archivo(RED_EJEMPLO_FORMATO);
  EXPECT_EQ(red.fecha_extracto(), "2026-10-28");
  EXPECT_EQ(red.nodos().size(), 3u);
  EXPECT_EQ(red.tramos().size(), 3u);
}

TEST(RedVialCarga, CargaLaRedPequenaDeLaPlaza) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  EXPECT_EQ(red.nodos().size(), 6u);
  EXPECT_EQ(red.tramos().size(), 10u);
}

TEST(RedVialCarga, LosTramosOpuestosSeApuntanEntreSi) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  std::size_t con_opuesto = 0;
  std::size_t sin_opuesto = 0;
  for (std::size_t i = 0; i < red.tramos().size(); ++i) {
    const auto& t = red.tramos()[i];
    if (!t.opuesto) {
      ++sin_opuesto;
      continue;
    }
    ++con_opuesto;
    const auto& otro = red.tramos()[*t.opuesto];
    EXPECT_EQ(otro.opuesto, i);
    EXPECT_EQ(otro.desde, t.hasta);
    EXPECT_EQ(otro.hasta, t.desde);
  }
  EXPECT_EQ(con_opuesto, 8u);  // los cuatro lados de la plaza, de doble sentido
  EXPECT_EQ(sin_opuesto, 2u);  // y dos calles de un solo sentido (RN-03.6)
}

TEST(RedVialCarga, GuardaNombreTipoDeViaLongitudYGeometria) {
  const RedVial red = RedVial::cargar_archivo(RED_EJEMPLO_FORMATO);
  const auto& t = red.tramos().front();
  EXPECT_EQ(t.id, "osm:5001:1:ida");
  EXPECT_EQ(t.nombre, "Calle de ejemplo");
  EXPECT_EQ(t.tipo_via, churoviaje::TipoVia::zona_residencial);
  EXPECT_NEAR(t.longitud_m, 93.944, 1e-9);
  ASSERT_EQ(t.geometria.size(), 3u);
  EXPECT_NEAR(t.geometria[0].lat, -21.5339, 1e-9);
  EXPECT_NEAR(t.geometria[0].lon, -64.7342, 1e-9);
}

TEST(RedVialCarga, LosTramosQueSalenDeUnNodoSonLosQueSeRecorrenDesdeEl) {
  const RedVial red = RedVial::cargar_archivo(RED_EJEMPLO_FORMATO);
  // osm:1002 es el centro del ejemplo: de él sale la vuelta de la calle de doble sentido y la avenida
  std::size_t nodo_1002 = 0;
  for (std::size_t i = 0; i < red.nodos().size(); ++i) {
    if (red.nodos()[i].id == "osm:1002") nodo_1002 = i;
  }
  EXPECT_EQ(red.tramos_que_salen(nodo_1002).size(), 2u);
}

TEST(RedVialCarga, IgnoraLosCamposQueNoConoce) {
  json doc = red_minima();
  doc["campo_nuevo"] = {{"a", 1}};
  doc["nodos"][0]["altura"] = 3000;
  doc["tramos"][0]["velocidad_maxima"] = 40;
  EXPECT_EQ(error_al_cargar(doc), "");
}

TEST(RedVialCarga, SinNombreEnElTramoUsaSinNombre) {
  json doc = red_minima();
  doc["tramos"][0].erase("nombre");
  const RedVial red = RedVial::desde_json(doc);
  EXPECT_EQ(red.tramos()[0].nombre, "Sin nombre");
}

TEST(RedVialCarga, UnaRedSinTramosNiNodosEsValida) {
  json doc = red_minima();
  doc["nodos"] = json::array();
  doc["tramos"] = json::array();
  const RedVial red = RedVial::desde_json(doc);
  EXPECT_TRUE(red.nodos().empty());
  EXPECT_FALSE(red.ajustar(-21.5, -64.7).has_value());
}

// ------------------------------------------------------------------ rechazos

TEST(RedVialRechazos, OtraVersionNoSeCarga) {
  json doc = red_minima();
  doc["version"] = 2;
  EXPECT_NE(error_al_cargar(doc).find("versión 2"), std::string::npos);
  doc.erase("version");
  EXPECT_NE(error_al_cargar(doc).find("version"), std::string::npos);
}

TEST(RedVialRechazos, ArchivoInexistenteORoto) {
  EXPECT_THROW(RedVial::cargar_archivo("/no/existe/red.json"), ErrorRedVial);
  const auto roto = escribir_temporal("roto.json", "{ esto no es json");
  EXPECT_THROW(RedVial::cargar_archivo(roto), ErrorRedVial);
  const auto lista = escribir_temporal("lista.json", "[1,2,3]");
  EXPECT_THROW(RedVial::cargar_archivo(lista), ErrorRedVial);
  std::filesystem::remove(roto);
  std::filesystem::remove(lista);
}

TEST(RedVialRechazos, FechaDelExtractoInvalida) {
  json doc = red_minima();
  doc["fecha_extracto"] = "28/10/2026";
  EXPECT_NE(error_al_cargar(doc).find("fecha_extracto"), std::string::npos);
  doc.erase("fecha_extracto");
  EXPECT_NE(error_al_cargar(doc), "");
}

TEST(RedVialRechazos, NodoRepetidoOFueraDeRango) {
  json doc = red_minima();
  doc["nodos"][1]["id"] = "n1";
  EXPECT_NE(error_al_cargar(doc).find("repetido"), std::string::npos);

  doc = red_minima();
  doc["nodos"][0]["lat"] = 95.0;
  EXPECT_NE(error_al_cargar(doc).find("fuera de rango"), std::string::npos);

  doc = red_minima();
  doc["nodos"][0]["lon"] = "-64.7";
  EXPECT_NE(error_al_cargar(doc), "");
}

TEST(RedVialRechazos, TramoConNodoInexistente) {
  json doc = red_minima();
  doc["tramos"][0]["hasta"] = "n9";
  const std::string error = error_al_cargar(doc);
  EXPECT_NE(error.find("tramos[0]"), std::string::npos) << error;
  EXPECT_NE(error.find("n9"), std::string::npos) << error;
}

TEST(RedVialRechazos, TramoRepetidoTipoDeViaDesconocidoOLongitudInvalida) {
  json doc = red_minima();
  doc["tramos"][1]["id"] = "t1";
  EXPECT_NE(error_al_cargar(doc).find("repetido"), std::string::npos);

  doc = red_minima();
  doc["tramos"][0]["tipo_via"] = "autopista";
  EXPECT_NE(error_al_cargar(doc).find("tipo_via"), std::string::npos);

  doc = red_minima();
  doc["tramos"][0]["longitud_m"] = 0;
  EXPECT_NE(error_al_cargar(doc).find("longitud_m"), std::string::npos);

  doc = red_minima();
  doc["tramos"][0]["geometria"] = json::array({json::array({-21.5, -64.7})});
  EXPECT_NE(error_al_cargar(doc).find("geometria"), std::string::npos);
}

TEST(RedVialRechazos, OpuestoQueNoExisteONoEsReciproco) {
  json doc = red_minima();
  doc["tramos"][0]["opuesto"] = "t9";
  EXPECT_NE(error_al_cargar(doc).find("t9"), std::string::npos);

  doc = red_minima();
  doc["tramos"][1]["opuesto"] = nullptr;  // t1 apunta a t2 pero t2 no apunta a t1
  EXPECT_NE(error_al_cargar(doc).find("de vuelta"), std::string::npos);

  doc = red_minima();
  doc["tramos"][0]["opuesto"] = "t1";  // un tramo no puede ser su propio opuesto
  EXPECT_NE(error_al_cargar(doc), "");
}

// ------------------------------------------------------------------ ajuste de puntos (CP-47)

// Las esquinas de la plaza Luis de Fuentes de la red de prueba son los nodos 0 a 3.
TEST(RedVialAjuste, CP47_UnPuntoEnMedioDeLaPlazaSeAjustaAUnaEsquinaVecina) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  const auto ajuste = red.ajustar(-21.53550, -64.72960);  // el centro de la plaza
  ASSERT_TRUE(ajuste.has_value());
  EXPECT_LT(ajuste->nodo, 4u);  // una de las cuatro esquinas
  EXPECT_NEAR(ajuste->distancia_m, 76.0, 2.0);
}

TEST(RedVialAjuste, CP47_UnPuntoFueraDeLaMancha_EsInvalido) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  EXPECT_FALSE(red.ajustar(-21.4417, -64.7650).has_value());  // San Lorenzo
  EXPECT_FALSE(red.ajustar(0.0, 0.0).has_value());
}

TEST(RedVialAjuste, UnNodoExactoSeAjustaASiMismoConDistanciaCero) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  const auto& esquina = red.nodos()[2];
  const auto ajuste = red.ajustar(esquina.posicion.lat, esquina.posicion.lon);
  ASSERT_TRUE(ajuste.has_value());
  EXPECT_EQ(ajuste->nodo, 2u);
  EXPECT_NEAR(ajuste->distancia_m, 0.0, 1e-6);
}

TEST(RedVialAjuste, ElLimiteEsDe100Metros) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  const auto& e = red.nodos()[0].posicion;  // esquina suroeste; hacia el sur no hay otros nodos cerca
  EXPECT_TRUE(red.ajustar(e.lat - grados_lat(99.0), e.lon).has_value());
  EXPECT_FALSE(red.ajustar(e.lat - grados_lat(101.0), e.lon).has_value());
  // lo mismo en diagonal, cruzando celdas del índice
  EXPECT_TRUE(red.ajustar(e.lat - grados_lat(69.0), e.lon - grados_lon(69.0)).has_value());
  EXPECT_FALSE(red.ajustar(e.lat - grados_lat(72.0), e.lon - grados_lon(72.0)).has_value());
}

TEST(RedVialAjuste, ElegueElNodoMasCercanoNoElPrimero) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  const auto& cerca_de_c = red.nodos()[2].posicion;
  const auto ajuste = red.ajustar(cerca_de_c.lat + grados_lat(10.0), cerca_de_c.lon + grados_lon(10.0));
  ASSERT_TRUE(ajuste.has_value());
  EXPECT_EQ(ajuste->nodo, 2u);
}

TEST(RedVialAjuste, CoordenadasInvalidasSonInvalidas) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  const double nan = std::nan("");
  EXPECT_FALSE(red.ajustar(nan, -64.73).has_value());
  EXPECT_FALSE(red.ajustar(-21.5, nan).has_value());
  EXPECT_FALSE(red.ajustar(120.0, -64.73).has_value());
  EXPECT_FALSE(red.ajustar(-21.5, 400.0).has_value());
}

TEST(RedVialAjuste, ElIndiceDaLoMismoQueRecorrerTodosLosNodos) {
  const RedVial red = RedVial::cargar_archivo(RED_PRUEBA_PLAZA);
  // Una malla de puntos alrededor de la plaza: el resultado debe coincidir con la búsqueda por fuerza bruta.
  for (int i = -20; i <= 20; ++i) {
    for (int j = -20; j <= 20; ++j) {
      const double lat = -21.5355 + i * 0.0001;
      const double lon = -64.7296 + j * 0.0001;
      std::optional<churoviaje::Ajuste> esperado;
      for (std::size_t n = 0; n < red.nodos().size(); ++n) {
        const auto& p = red.nodos()[n].posicion;
        const double dx = (p.lon - lon) * 111320.0 * std::cos(-21.5355 * 3.14159265358979323846 / 180.0);
        const double dy = (p.lat - lat) * 111320.0;
        const double d = std::hypot(dx, dy);
        if (d < 99.0 && (!esperado || d < esperado->distancia_m)) esperado = churoviaje::Ajuste{n, d};
      }
      const auto obtenido = red.ajustar(lat, lon);
      if (esperado) {  // si la fuerza bruta lo halla claramente dentro, el índice también
        ASSERT_TRUE(obtenido.has_value()) << lat << "," << lon;
        EXPECT_EQ(obtenido->nodo, esperado->nodo) << lat << "," << lon;
      }
    }
  }
}

// ------------------------------------------------------------------ el núcleo con la red

TEST(NucleoConRed, ConsultarEstadoInformaFechaNodosYTramos) {
  auto red = std::make_shared<const RedVial>(RedVial::cargar_archivo(RED_PRUEBA_PLAZA));
  Nucleo nucleo(red);
  const auto r = nucleo.atender(R"({"id":1,"operacion":"consultar_estado"})");
  ASSERT_TRUE(r.has_value());
  const json j = json::parse(r->linea);
  EXPECT_EQ(j.at("id"), 1);
  EXPECT_EQ(j.at("ok"), true);
  EXPECT_EQ(j.at("red_cargada"), true);
  EXPECT_EQ(j.at("fecha_extracto"), "2026-10-28");
  EXPECT_EQ(j.at("nodos"), 6);
  EXPECT_EQ(j.at("tramos"), 10);
}

TEST(NucleoConRed, SinRedInformaFalsoNuloYCeros) {
  Nucleo nucleo{std::shared_ptr<const RedVial>{}};
  const json j = json::parse(nucleo.atender(R"({"id":1,"operacion":"consultar_estado"})")->linea);
  EXPECT_EQ(j.at("red_cargada"), false);
  EXPECT_TRUE(j.at("fecha_extracto").is_null());
  EXPECT_EQ(j.at("nodos"), 0);
  EXPECT_EQ(j.at("tramos"), 0);
}

TEST(NucleoConRed, SiLaRedNoSeCargaElNucleoSigueSinRedYLoAnotaEnElRegistro) {
  std::ostringstream registro;
  const auto red = churoviaje::cargar_red_para_arranque("/no/existe/red.json", registro);
  EXPECT_EQ(red, nullptr);
  EXPECT_NE(registro.str().find("No se cargó la red vial"), std::string::npos);

  Nucleo nucleo(red);
  const json j = json::parse(nucleo.atender(R"({"id":1,"operacion":"consultar_estado"})")->linea);
  EXPECT_EQ(j.at("red_cargada"), false);
}

TEST(NucleoConRed, CargaCorrectaSeAnotaEnElRegistro) {
  std::ostringstream registro;
  const auto red = churoviaje::cargar_red_para_arranque(RED_PRUEBA_PLAZA, registro);
  ASSERT_NE(red, nullptr);
  EXPECT_NE(registro.str().find("6 nodos, 10 tramos"), std::string::npos) << registro.str();
}

// ------------------------------------------------------------------ rendimiento (criterio: menos de 10 s)

TEST(RedVialRendimiento, UnaRedGrandeCargaEnMenosDe10Segundos) {
  // Una malla de 150 x 150 esquinas con calles de doble sentido: unos 22 500 nodos y 89 000 tramos,
  // bastante más de lo que tendrá la mancha urbana de Tarija.
  constexpr int lado = 150;
  json doc = {{"version", 1}, {"fuente", "OpenStreetMap"}, {"fecha_extracto", "2026-10-28"},
              {"nodos", json::array()}, {"tramos", json::array()}};
  auto id_nodo = [lado](int i, int j) { return "n" + std::to_string(i * lado + j); };
  for (int i = 0; i < lado; ++i) {
    for (int j = 0; j < lado; ++j) {
      doc["nodos"].push_back({{"id", id_nodo(i, j)}, {"lat", -21.55 + i * 0.0009}, {"lon", -64.75 + j * 0.0009}});
    }
  }
  auto agregar_par = [&](const std::string& a, const std::string& b, int k, double lat1, double lon1, double lat2,
                         double lon2) {
    const std::string ida = "t" + std::to_string(k) + "i";
    const std::string vuelta = "t" + std::to_string(k) + "v";
    json g = json::array({json::array({lat1, lon1}), json::array({lat2, lon2})});
    json g_inv = json::array({json::array({lat2, lon2}), json::array({lat1, lon1})});
    doc["tramos"].push_back({{"id", ida}, {"desde", a}, {"hasta", b}, {"opuesto", vuelta}, {"nombre", "Calle"},
                             {"tipo_via", "zona_residencial"}, {"longitud_m", 100.0}, {"geometria", g}});
    doc["tramos"].push_back({{"id", vuelta}, {"desde", b}, {"hasta", a}, {"opuesto", ida}, {"nombre", "Calle"},
                             {"tipo_via", "zona_residencial"}, {"longitud_m", 100.0}, {"geometria", g_inv}});
  };
  int k = 0;
  for (int i = 0; i < lado; ++i) {
    for (int j = 0; j < lado; ++j) {
      const double lat = -21.55 + i * 0.0009;
      const double lon = -64.75 + j * 0.0009;
      if (j + 1 < lado) agregar_par(id_nodo(i, j), id_nodo(i, j + 1), k++, lat, lon, lat, lon + 0.0009);
      if (i + 1 < lado) agregar_par(id_nodo(i, j), id_nodo(i + 1, j), k++, lat, lon, lat + 0.0009, lon);
    }
  }
  const auto ruta = escribir_temporal("grande.json", doc.dump());

  const auto inicio = std::chrono::steady_clock::now();
  const RedVial red = RedVial::cargar_archivo(ruta);
  const auto segundos = std::chrono::duration<double>(std::chrono::steady_clock::now() - inicio).count();
  std::filesystem::remove(ruta);

  EXPECT_EQ(red.nodos().size(), static_cast<std::size_t>(lado * lado));
  EXPECT_GT(red.tramos().size(), 80000u);
  EXPECT_LT(segundos, 10.0) << "la carga tardó " << segundos << " s";

  // y ajustar un punto sigue siendo inmediato con tantos nodos
  const auto inicio_busqueda = std::chrono::steady_clock::now();
  for (int n = 0; n < 10000; ++n) red.ajustar(-21.55 + (n % 100) * 0.0013, -64.75 + (n % 97) * 0.0013);
  const auto ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - inicio_busqueda).count();
  EXPECT_LT(ms, 1000.0) << "10 000 ajustes tardaron " << ms << " ms";
}
