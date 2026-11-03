// Pruebas del tráfico de los eventos en los tramos (#66): actualizar_trafico, niveles, cierre y vencimiento.
// Usa la red irregular del prototipo (tests/datos/red_irregular.json).
#include <gtest/gtest.h>

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "eventos.hpp"
#include "nucleo.hpp"
#include "red_vial.hpp"
#include "tiempo.hpp"

using churoviaje::Nucleo;
using churoviaje::RedVial;
using churoviaje::segundos_desde_iso;
using json = nlohmann::json;
namespace codigo = churoviaje::codigo_error;

namespace {

// Hora fija de las pruebas: 03/11/2026 a las 10:00 en Tarija.
const std::int64_t kAhora = *segundos_desde_iso("2026-11-03T10:00:00-04:00");

std::shared_ptr<const RedVial> red_irregular() {
  return std::make_shared<const RedVial>(RedVial::cargar_archivo(RED_IRREGULAR));
}

// Núcleo con la red irregular y un reloj que la prueba puede adelantar.
struct Prueba {
  std::shared_ptr<const RedVial> red = red_irregular();
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

  // El punto medio de la calle entre dos nodos: ahí cae un evento reportado sobre esa calle.
  json medio(const std::string& a, const std::string& b) const {
    const auto& na = red->nodos()[*red->buscar_nodo(a)].posicion;
    const auto& nb = red->nodos()[*red->buscar_nodo(b)].posicion;
    return json::array({(na.lat + nb.lat) / 2, (na.lon + nb.lon) / 2});
  }

  json punto(const std::string& n) const {
    const auto& p = red->nodos()[*red->buscar_nodo(n)].posicion;
    return json::array({p.lat, p.lon});
  }

  json evento(const std::string& id, const std::string& tipo, json ubicacion,
              const std::string& vence = "2026-11-03T11:00:00-04:00", const std::string& accion = "nace") {
    return enviar({{"operacion", "actualizar_trafico"}, {"evento", id}, {"accion", accion}, {"tipo", tipo},
                   {"ubicacion", ubicacion}, {"vence", vence}});
  }

  json quitar(const std::string& id, const std::string& accion = "retira") {
    return enviar({{"operacion", "actualizar_trafico"}, {"evento", id}, {"accion", accion}});
  }

  json ruta(const std::string& desde, const std::string& hasta) {
    return enviar({{"operacion", "calcular_ruta"}, {"origen", punto(desde)}, {"destino", punto(hasta)}});
  }

  // Los ids de los tramos de la ruta entre dos nodos, o vacío si no hay ruta.
  std::vector<std::string> tramos_de_ruta(const std::string& desde, const std::string& hasta) {
    const json r = ruta(desde, hasta);
    std::vector<std::string> ids;
    if (r.value("estado", "") != "ENCONTRADA") return ids;
    for (const auto& t : r.at("tramos")) ids.push_back(t.at("id"));
    return ids;
  }
};

using Ids = std::vector<std::string>;

}  // namespace

// ------------------------------------------------------------------ casos de prueba del issue

TEST(Trafico, CP95_UnAccidenteEnLaCalleDirectaHaceTomarElRodeoSiCuestaMenos) {
  Prueba p;
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad"), (Ids{"Hospital-Universidad"}));
  const json r = p.evento("e1", "ACCIDENTE", p.medio("Hospital", "Universidad"));
  EXPECT_EQ(r.at("ok"), true);
  EXPECT_EQ(r.at("tramos"), json::array({"Hospital-Universidad"}));  // calle de un solo sentido: un tramo
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad"), (Ids{"Hospital-Plaza", "Plaza-Mercado", "Mercado-Universidad"}));
}

TEST(Trafico, CP96_UnBloqueoEnLaUnicaCalleDeAccesoDejaSinRuta) {
  Prueba p;
  p.evento("b1", "BLOQUEO", p.medio("Universidad", "Mirador"));
  EXPECT_EQ(p.ruta("Plaza", "Mirador").at("estado"), "SIN_RUTA");
  EXPECT_EQ(p.ruta("Mirador", "Plaza").at("estado"), "SIN_RUTA");
}

TEST(Trafico, CP97_AlRetirarseElBloqueoLaRutaVuelveAEncontrarse) {
  Prueba p;
  p.evento("b1", "BLOQUEO", p.medio("Universidad", "Mirador"));
  ASSERT_EQ(p.ruta("Plaza", "Mirador").at("estado"), "SIN_RUTA");
  const json r = p.quitar("b1");
  EXPECT_EQ(r.at("ok"), true);
  EXPECT_EQ(r.at("tramos").size(), 2u);
  EXPECT_EQ(p.ruta("Plaza", "Mirador").at("estado"), "ENCONTRADA");
}

TEST(Trafico, CP98_UnBloqueoCierraLosDosSentidosYUnaCongestionSoloElSuyo) {
  {
    Prueba p;
    const json r = p.evento("b1", "BLOQUEO", p.medio("Plaza", "Terminal"));
    EXPECT_EQ(r.at("tramos").size(), 2u);  // Av. Domingo Paredes es de doble sentido
    EXPECT_EQ(p.tramos_de_ruta("Plaza", "Terminal"), (Ids{"Plaza-Mercado", "Mercado-Terminal"}));
    EXPECT_EQ(p.ruta("Terminal", "Plaza").at("estado"), "SIN_RUTA");  // la vuelta solo era por esa avenida
  }
  {
    Prueba p;
    const json r = p.enviar({{"operacion", "actualizar_trafico"}, {"evento", "c1"}, {"accion", "nace"},
                             {"tipo", "CONGESTION"}, {"nivel", "MUY_CONGESTIONADO"}, {"tramo", "Plaza-Terminal"},
                             {"ubicacion", p.medio("Plaza", "Terminal")}, {"vence", "2026-11-03T10:30:00-04:00"}});
    EXPECT_EQ(r.at("tramos"), json::array({"Plaza-Terminal"}));
    EXPECT_EQ(p.tramos_de_ruta("Plaza", "Terminal"), (Ids{"Plaza-Mercado", "Mercado-Terminal"}));
    EXPECT_EQ(p.tramos_de_ruta("Terminal", "Plaza"), (Ids{"Terminal-Plaza"}));  // el otro sentido sigue igual
  }
}

// ------------------------------------------------------------------ CP-18 y CP-19 del prototipo

TEST(Trafico, CP18_UnaCongestionFuerteObligaADesviarse) {
  Prueba p;
  p.enviar({{"operacion", "actualizar_trafico"}, {"evento", "c1"}, {"accion", "nace"}, {"tipo", "CONGESTION"},
            {"nivel", "CONGESTIONADO"}, {"tramo", "Hospital-Universidad"}, {"ubicacion", p.medio("Hospital", "Universidad")},
            {"vence", "2026-11-03T10:30:00-04:00"}});
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad"), (Ids{"Hospital-Plaza", "Plaza-Mercado", "Mercado-Universidad"}));
}

TEST(Trafico, CP19_PasarDeCongestionAAccidenteNoCambiaUnaRutaOptima) {
  Prueba p;
  p.enviar({{"operacion", "actualizar_trafico"}, {"evento", "c1"}, {"accion", "nace"}, {"tipo", "CONGESTION"},
            {"nivel", "CONGESTIONADO"}, {"tramo", "Hospital-Universidad"}, {"ubicacion", p.medio("Hospital", "Universidad")},
            {"vence", "2026-11-03T10:30:00-04:00"}});
  const auto con_congestion = p.tramos_de_ruta("Hospital", "Universidad");
  p.evento("e1", "ACCIDENTE", p.medio("Hospital", "Universidad"));
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad"), con_congestion);
}

// ------------------------------------------------------------------ niveles y vencimiento

TEST(Trafico, ConVariosEventosValeElMasGrave) {
  Prueba p;
  p.evento("cp", "CONTROL_POLICIAL", p.medio("Hospital", "Universidad"));
  // ×1,5: la calle directa (unos 616 m) sigue costando menos que el rodeo (unos 1 068 m).
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad"), (Ids{"Hospital-Universidad"}));
  p.evento("e1", "ACCIDENTE", p.medio("Hospital", "Universidad"));
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad").size(), 3u);  // ×4,0
  p.quitar("e1");
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad"), (Ids{"Hospital-Universidad"}));  // vuelve a ×1,5
}

TEST(Trafico, UnEventoVencidoDejaDeContarAunqueNoLlegueElAviso) {
  Prueba p;
  p.evento("e1", "ACCIDENTE", p.medio("Hospital", "Universidad"), "2026-11-03T10:10:00-04:00");
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad").size(), 3u);
  p.ahora += 10 * 60;  // 10:10: justo vence
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad"), (Ids{"Hospital-Universidad"}));
  EXPECT_EQ(p.nucleo.eventos_vigentes(), 0u);
}

TEST(Trafico, UnVencimientoNuevoReemplazaAlAnterior) {
  Prueba p;
  p.evento("e1", "ACCIDENTE", p.medio("Hospital", "Universidad"), "2026-11-03T10:10:00-04:00");
  p.evento("e1", "ACCIDENTE", p.medio("Hospital", "Universidad"), "2026-11-03T10:40:00-04:00", "cambia_vencimiento");
  EXPECT_EQ(p.nucleo.eventos_vigentes(), 1u);
  p.ahora += 20 * 60;
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad").size(), 3u);
  p.ahora += 20 * 60;
  EXPECT_EQ(p.tramos_de_ruta("Hospital", "Universidad").size(), 1u);
}

TEST(Trafico, UnEventoQueYaVencioNoAfectaNada) {
  Prueba p;
  const json r = p.evento("e1", "ACCIDENTE", p.medio("Hospital", "Universidad"), "2026-11-03T09:00:00-04:00");
  EXPECT_EQ(r.at("ok"), true);
  EXPECT_TRUE(r.at("tramos").empty());
  EXPECT_EQ(p.nucleo.eventos_vigentes(), 0u);
}

TEST(Trafico, AlVencerSeQuitaComoAlRetirarse) {
  Prueba p;
  p.evento("b1", "BLOQUEO", p.medio("Universidad", "Mirador"));
  EXPECT_EQ(p.quitar("b1", "vence").at("tramos").size(), 2u);
  EXPECT_EQ(p.ruta("Plaza", "Mirador").at("estado"), "ENCONTRADA");
}

TEST(Trafico, UnEventoAMasDe100MetrosDeUnaCalleNoAfectaNingunTramo) {
  Prueba p;
  const json r = p.evento("e1", "BLOQUEO", json::array({-21.5380, -64.7230}));  // descampado lejos de las calles
  EXPECT_EQ(r.at("ok"), true);
  EXPECT_TRUE(r.at("tramos").empty());
  EXPECT_EQ(p.ruta("Plaza", "Mirador").at("estado"), "ENCONTRADA");
}

TEST(Trafico, RetirarUnEventoQueNoExisteNoEsUnError) {
  Prueba p;
  const json r = p.quitar("no-existe");
  EXPECT_EQ(r.at("ok"), true);
  EXPECT_TRUE(r.at("tramos").empty());
}

TEST(Trafico, ElIdDelEventoPuedeSerUnEntero) {
  Prueba p;
  p.enviar({{"operacion", "actualizar_trafico"}, {"evento", 153}, {"accion", "nace"}, {"tipo", "BLOQUEO"},
            {"ubicacion", p.medio("Universidad", "Mirador")}, {"vence", "2026-11-03T15:00:00Z"}});  // 11:00 en Tarija
  ASSERT_EQ(p.ruta("Plaza", "Mirador").at("estado"), "SIN_RUTA");
  p.enviar({{"operacion", "actualizar_trafico"}, {"evento", 153}, {"accion", "retira"}});
  EXPECT_EQ(p.ruta("Plaza", "Mirador").at("estado"), "ENCONTRADA");
}

TEST(Trafico, ElTiempoEstimadoSubeConElTrafico) {
  Prueba p;
  const int antes = p.ruta("Terminal", "Plaza").at("tiempo_s");
  p.evento("cp", "CONTROL_POLICIAL", p.medio("Plaza", "Terminal"));
  const json despues = p.ruta("Terminal", "Plaza");
  ASSERT_EQ(despues.at("tramos").size(), 1u);
  EXPECT_NEAR(despues.at("tiempo_s").get<double>(), antes * 1.5, 1.0);  // RN-03.14: corregido por el nivel
}

// ------------------------------------------------------------------ peticiones mal formadas

TEST(Trafico, PeticionesMalFormadasSonPeticionInvalida) {
  Prueba p;
  const json ub = p.medio("Plaza", "Mercado");
  const std::vector<json> malas = {
      {{"operacion", "actualizar_trafico"}, {"accion", "nace"}, {"tipo", "OTRO"}, {"ubicacion", ub}, {"vence", "2026-11-03T11:00:00Z"}},
      {{"operacion", "actualizar_trafico"}, {"evento", "x"}, {"tipo", "OTRO"}, {"ubicacion", ub}, {"vence", "2026-11-03T11:00:00Z"}},
      {{"operacion", "actualizar_trafico"}, {"evento", "x"}, {"accion", "explota"}},
      {{"operacion", "actualizar_trafico"}, {"evento", "x"}, {"accion", "nace"}, {"tipo", "INUNDACION"}, {"ubicacion", ub}, {"vence", "2026-11-03T11:00:00Z"}},
      {{"operacion", "actualizar_trafico"}, {"evento", "x"}, {"accion", "nace"}, {"tipo", "OTRO"}, {"vence", "2026-11-03T11:00:00Z"}},
      {{"operacion", "actualizar_trafico"}, {"evento", "x"}, {"accion", "nace"}, {"tipo", "OTRO"}, {"ubicacion", ub}, {"vence", "mañana"}},
      {{"operacion", "actualizar_trafico"}, {"evento", "x"}, {"accion", "nace"}, {"tipo", "OTRO"}, {"ubicacion", ub}, {"vence", "2026-11-03T11:00:00"}},
      {{"operacion", "actualizar_trafico"}, {"evento", "x"}, {"accion", "nace"}, {"tipo", "CONGESTION"}, {"ubicacion", ub}, {"vence", "2026-11-03T11:00:00Z"}},
  };
  for (const auto& m : malas) {
    const json r = p.enviar(m);
    EXPECT_EQ(r.at("ok"), false) << m.dump();
    EXPECT_EQ(r.at("error").at("codigo"), codigo::peticion_invalida) << m.dump();
  }
  EXPECT_EQ(p.nucleo.eventos_vigentes(), 0u);
}

TEST(Trafico, SinRedVialRespondeRedNoCargada) {
  Nucleo nucleo;
  const auto r = nucleo.atender(R"({"id":1,"operacion":"actualizar_trafico","evento":"e1","accion":"nace","tipo":"OTRO","ubicacion":[-21.5,-64.7],"vence":"2026-11-03T11:00:00Z"})");
  ASSERT_TRUE(r.has_value());
  EXPECT_EQ(json::parse(r->linea).at("error").at("codigo"), codigo::red_no_cargada);
}

// ------------------------------------------------------------------ piezas: fechas y tramo más cercano

TEST(Fechas, LeeIso8601ConZonaHoraria) {
  EXPECT_EQ(segundos_desde_iso("1970-01-01T00:00:00Z"), 0);
  EXPECT_EQ(segundos_desde_iso("2026-10-06T15:02:00-04:00"), segundos_desde_iso("2026-10-06T19:02:00Z"));
  EXPECT_EQ(segundos_desde_iso("2026-10-06T15:02:00.250+00:00"), segundos_desde_iso("2026-10-06T15:02:00Z"));
  EXPECT_EQ(segundos_desde_iso("2024-02-29T12:00:00Z").value() - segundos_desde_iso("2024-02-28T12:00:00Z").value(), 86400);
}

TEST(Fechas, RechazaLoQueNoEsUnaFechaConZona) {
  for (const char* t : {"", "2026-10-06", "2026-10-06T15:02:00", "2026-13-01T00:00:00Z", "2026-02-30T00:00:00Z",
                        "2026-10-06 15:02:00Z", "2026-10-06T15:02:00+4", "2026-10-06T15:02:00Zx"}) {
    EXPECT_FALSE(segundos_desde_iso(t).has_value()) << t;
  }
}

TEST(TramoCercano, UnPuntoSobreLaCalleDevuelveEseTramoYMasLejosNinguno) {
  const auto red = red_irregular();
  const auto& a = red->nodos()[*red->buscar_nodo("Plaza")].posicion;
  const auto& b = red->nodos()[*red->buscar_nodo("Mercado")].posicion;
  const auto cercano = red->tramo_mas_cercano((a.lat + b.lat) / 2, (a.lon + b.lon) / 2);
  ASSERT_TRUE(cercano.has_value());
  const std::string id = red->tramos()[cercano->tramo].id;
  EXPECT_TRUE(id == "Plaza-Mercado" || id == "Mercado-Plaza") << id;
  EXPECT_LT(cercano->distancia_m, 1.0);
  EXPECT_FALSE(red->tramo_mas_cercano(-21.5380, -64.7230).has_value());
}
