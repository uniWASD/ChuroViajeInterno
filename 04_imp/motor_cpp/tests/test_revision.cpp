// Pruebas de revisar_ruta (#76, CU-04): sin cambios, sin alternativa, alternativa y ruta desactualizada.
// Usa la red irregular del prototipo (tests/datos/red_irregular.json). De la Plaza al Mirador la ruta más corta va
// por el Mercado (Plaza-Mercado 182,9 m, Mercado-Universidad 572,4 m, Universidad-Mirador 771,2 m: 1526,5 m); la
// otra, por el Hospital, cuesta 1700,0 m.
#include <gtest/gtest.h>

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "nucleo.hpp"
#include "red_vial.hpp"
#include "tiempo.hpp"

using churoviaje::Nucleo;
using churoviaje::RedVial;
using churoviaje::segundos_desde_iso;
using json = nlohmann::json;
namespace codigo = churoviaje::codigo_error;

namespace {

const std::int64_t kAhora = *segundos_desde_iso("2026-11-14T10:00:00-04:00");
const std::vector<std::string> kRutaPorElMercado = {"Plaza-Mercado", "Mercado-Universidad", "Universidad-Mirador"};

struct Prueba {
  std::shared_ptr<const RedVial> red = std::make_shared<const RedVial>(RedVial::cargar_archivo(RED_IRREGULAR));
  Nucleo nucleo{red};
  std::int64_t ahora = kAhora;
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

  json punto(const std::string& n) const {
    const auto& p = red->nodos()[*red->buscar_nodo(n)].posicion;
    return json::array({p.lat, p.lon});
  }

  json medio(const std::string& a, const std::string& b) const {
    const auto& na = red->nodos()[*red->buscar_nodo(a)].posicion;
    const auto& nb = red->nodos()[*red->buscar_nodo(b)].posicion;
    return json::array({(na.lat + nb.lat) / 2, (na.lon + nb.lon) / 2});
  }

  json evento(const std::string& id, const std::string& tipo, json ubicacion,
              const std::string& vence = "2026-11-14T11:00:00-04:00") {
    json aviso = {{"operacion", "actualizar_trafico"}, {"evento", id}, {"accion", "nace"}, {"tipo", tipo},
                  {"ubicacion", ubicacion}, {"vence", vence}};
    if (tipo == "CONGESTION") aviso["nivel"] = "MUY_CONGESTIONADO";
    return enviar(aviso);
  }

  json revisar(const std::string& desde, const std::vector<std::string>& tramos, const std::string& hasta,
               json avisados = json::array(), json paradas = json::array()) {
    return enviar({{"operacion", "revisar_ruta"},
                   {"posicion", punto(desde)},
                   {"tramos", tramos},
                   {"destino", punto(hasta)},
                   {"paradas", paradas},
                   {"eventos_avisados", avisados}});
  }
};

std::vector<std::string> ids_de_tramos(const json& respuesta) {
  std::vector<std::string> ids;
  for (const auto& t : respuesta.at("tramos")) ids.push_back(t.at("id").get<std::string>());
  return ids;
}

}  // namespace

TEST(RevisarRuta, SinEventosNoHayCambios) {
  Prueba p;
  const json r = p.revisar("Plaza", kRutaPorElMercado, "Mirador");
  EXPECT_EQ(r.at("ok"), true);
  EXPECT_EQ(r.at("estado"), "SIN_CAMBIOS");
  EXPECT_FALSE(r.contains("evento"));
}

TEST(RevisarRuta, CP124_UnAccidenteEnLoQueFaltaDaUnaAlternativaMasBarata) {
  Prueba p;
  p.evento("153", "ACCIDENTE", p.medio("Mercado", "Universidad"));  // ×4,0 en la Av. Las Américas

  const json r = p.revisar("Plaza", kRutaPorElMercado, "Mirador");
  ASSERT_EQ(r.at("estado"), "ALTERNATIVA") << r.dump();
  EXPECT_EQ(r.at("evento"), "153");
  EXPECT_EQ(ids_de_tramos(r), (std::vector<std::string>{"Plaza-Hospital", "Hospital-Universidad", "Universidad-Mirador"}));
  EXPECT_LT(r.at("costo").get<long long>(), r.at("costo_actual").get<long long>());
  EXPECT_EQ(r.at("costo"), 1700);                              // 313,442 + 615,428 + 771,172
  EXPECT_EQ(r.at("costo_actual"), 3244);                       // 182,883 + 572,424 × 4 + 771,172
  // La alternativa trae lo mismo que una ruta de calcular_ruta.
  EXPECT_TRUE(r.contains("distancia_m"));
  EXPECT_TRUE(r.contains("tiempo_s"));
  EXPECT_FALSE(r.at("trazado").empty());
  EXPECT_EQ(r.at("tramos")[0].at("calle"), "Av. Circunvalación");
}

TEST(RevisarRuta, CP125_SiLaAlternativaCuestaMasNoSeProponeYSeInformaElEvento) {
  Prueba p;
  p.evento("154", "CONTROL_POLICIAL", p.medio("Plaza", "Mercado"));  // ×1,5: seguir cuesta 1618, la otra 1700

  const json r = p.revisar("Plaza", kRutaPorElMercado, "Mirador");
  EXPECT_EQ(r.at("estado"), "SIN_ALTERNATIVA") << r.dump();
  EXPECT_EQ(r.at("evento"), "154");
  EXPECT_FALSE(r.contains("tramos"));
}

TEST(RevisarRuta, CP126_UnEventoYaAvisadoNoProvocaOtraPropuesta) {
  Prueba p;
  p.evento("153", "ACCIDENTE", p.medio("Mercado", "Universidad"));

  const json r = p.revisar("Plaza", kRutaPorElMercado, "Mirador", json::array({"153"}));
  EXPECT_EQ(r.at("estado"), "SIN_CAMBIOS") << r.dump();
  EXPECT_FALSE(r.contains("evento"));
}

TEST(RevisarRuta, LosAvisadosPuedenLlegarComoEnteros) {
  Prueba p;
  p.evento("153", "ACCIDENTE", p.medio("Mercado", "Universidad"));
  EXPECT_EQ(p.revisar("Plaza", kRutaPorElMercado, "Mirador", json::array({153})).at("estado"), "SIN_CAMBIOS");
}

TEST(RevisarRuta, UnEventoNuevoSeRevisaAunqueHayaOtroYaAvisado) {
  Prueba p;
  p.evento("154", "CONTROL_POLICIAL", p.medio("Plaza", "Mercado"));
  p.evento("155", "ACCIDENTE", p.medio("Mercado", "Universidad"));

  const json r = p.revisar("Plaza", kRutaPorElMercado, "Mirador", json::array({"154"}));
  EXPECT_EQ(r.at("estado"), "ALTERNATIVA") << r.dump();
  EXPECT_EQ(r.at("evento"), "155");
}

TEST(RevisarRuta, UnEventoEnUnTramoYaRecorridoNoCuenta) {
  Prueba p;
  p.evento("153", "ACCIDENTE", p.medio("Plaza", "Mercado"));
  // El conductor ya está en el Mercado: le falta Mercado-Universidad-Mirador.
  const json r = p.revisar("Mercado", {"Mercado-Universidad", "Universidad-Mirador"}, "Mirador");
  EXPECT_EQ(r.at("estado"), "SIN_CAMBIOS");
}

TEST(RevisarRuta, UnEventoVencidoNoCuenta) {
  Prueba p;
  p.evento("153", "ACCIDENTE", p.medio("Mercado", "Universidad"), "2026-11-14T10:30:00-04:00");
  p.ahora += 31 * 60;
  EXPECT_EQ(p.revisar("Plaza", kRutaPorElMercado, "Mirador").at("estado"), "SIN_CAMBIOS");
}

TEST(RevisarRuta, UnBloqueoEnLoQueFaltaSiempreBuscaAlternativa) {
  Prueba p;
  p.evento("156", "BLOQUEO", p.medio("Mercado", "Universidad"));

  const json r = p.revisar("Plaza", kRutaPorElMercado, "Mirador");
  ASSERT_EQ(r.at("estado"), "ALTERNATIVA") << r.dump();
  EXPECT_EQ(ids_de_tramos(r), (std::vector<std::string>{"Plaza-Hospital", "Hospital-Universidad", "Universidad-Mirador"}));
  EXPECT_FALSE(r.contains("costo_actual"));  // seguir no es posible: la ruta actual está cortada
}

TEST(RevisarRuta, UnaCongestionPorTelemetriaTambienCuenta) {
  Prueba p;
  p.evento("157", "CONGESTION", p.medio("Mercado", "Universidad"));  // muy congestionado: ×4,0
  EXPECT_EQ(p.revisar("Plaza", kRutaPorElMercado, "Mirador").at("estado"), "ALTERNATIVA");
}

TEST(RevisarRuta, LaAlternativaConservaLasParadasQueFaltan) {
  Prueba p;
  // Ruta de la Plaza al Mirador con parada en el Hospital, que todavía no se alcanzó.
  const std::vector<std::string> por_el_hospital = {"Plaza-Hospital", "Hospital-Universidad", "Universidad-Mirador"};
  p.evento("158", "ACCIDENTE", p.medio("Hospital", "Universidad"));  // la bajada es de un solo sentido

  const json r = p.revisar("Plaza", por_el_hospital, "Mirador", json::array(), json::array({p.punto("Hospital")}));
  ASSERT_EQ(r.at("estado"), "ALTERNATIVA") << r.dump();
  // Sale de la Plaza, pasa por el Hospital y vuelve por el Mercado para evitar la bajada.
  EXPECT_EQ(ids_de_tramos(r), (std::vector<std::string>{"Plaza-Hospital", "Hospital-Plaza", "Plaza-Mercado",
                                                        "Mercado-Universidad", "Universidad-Mirador"}));
}

TEST(RevisarRuta, ConTramosQueYaNoExistenLaRutaEstaDesactualizada) {
  Prueba p;
  const json r = p.revisar("Plaza", {"Plaza-Mercado", "osm:999:1:ida"}, "Mirador");
  EXPECT_EQ(r.at("ok"), true);
  EXPECT_EQ(r.at("estado"), "RUTA_DESACTUALIZADA");
}

TEST(RevisarRuta, SinTramosQueFaltanNoHayCambios) {
  Prueba p;
  p.evento("153", "ACCIDENTE", p.medio("Mercado", "Universidad"));
  EXPECT_EQ(p.revisar("Mirador", {}, "Mirador").at("estado"), "SIN_CAMBIOS");
}

TEST(RevisarRuta, PeticionMalFormada) {
  Prueba p;
  const json sin_tramos = p.enviar({{"operacion", "revisar_ruta"}, {"posicion", p.punto("Plaza")},
                                    {"destino", p.punto("Mirador")}});
  EXPECT_EQ(sin_tramos.at("ok"), false);
  EXPECT_EQ(sin_tramos.at("error").at("codigo"), codigo::peticion_invalida);

  const json sin_posicion = p.enviar({{"operacion", "revisar_ruta"}, {"tramos", kRutaPorElMercado},
                                      {"destino", p.punto("Mirador")}});
  EXPECT_EQ(sin_posicion.at("error").at("codigo"), codigo::peticion_invalida);

  const json tramo_numero = p.enviar({{"operacion", "revisar_ruta"}, {"posicion", p.punto("Plaza")},
                                      {"tramos", json::array({1, 2})}, {"destino", p.punto("Mirador")}});
  EXPECT_EQ(tramo_numero.at("error").at("codigo"), codigo::peticion_invalida);

  const json avisados_mal = p.enviar({{"operacion", "revisar_ruta"}, {"posicion", p.punto("Plaza")},
                                      {"tramos", kRutaPorElMercado}, {"destino", p.punto("Mirador")},
                                      {"eventos_avisados", "153"}});
  EXPECT_EQ(avisados_mal.at("error").at("codigo"), codigo::peticion_invalida);
}

TEST(RevisarRuta, SinRedVialRespondeRedNoCargada) {
  Nucleo sin_red;
  const auto r = sin_red.atender(
      R"({"id":1,"operacion":"revisar_ruta","posicion":[-21.5,-64.7],"tramos":["a"],"destino":[-21.5,-64.7]})");
  ASSERT_TRUE(r.has_value());
  EXPECT_EQ(json::parse(r->linea).at("error").at("codigo"), codigo::red_no_cargada);
}
