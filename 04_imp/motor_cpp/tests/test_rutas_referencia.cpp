// Viajes de referencia de Tarija (T-120): se calculan con la red real y se comparan con lo anotado en
// 04_imp/datos/rutas_referencia.json. La red real no está en el repositorio (D-33), así que estas pruebas
// corren solo si RED_VIAL_REAL indica dónde está red_vial_tarija.json; si no, se saltan.
#include <gtest/gtest.h>

#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "red_vial.hpp"
#include "ruta.hpp"

using churoviaje::calcular_ruta;
using churoviaje::EstadoRuta;
using churoviaje::Punto;
using churoviaje::RedVial;
using churoviaje::Ruta;
using json = nlohmann::json;

namespace {

const json& referencia() {
  static const json doc = [] {
    std::ifstream archivo(RUTAS_REFERENCIA);
    return json::parse(archivo);
  }();
  return doc;
}

// La red real se carga una sola vez para todas las pruebas; nullptr si RED_VIAL_REAL no está definida.
const RedVial* red_real() {
  static const std::unique_ptr<RedVial> red = []() -> std::unique_ptr<RedVial> {
    const char* ruta = std::getenv("RED_VIAL_REAL");
    if (ruta == nullptr || *ruta == '\0') return nullptr;
    return std::make_unique<RedVial>(RedVial::cargar_archivo(ruta));
  }();
  return red.get();
}

Punto punto(const json& par) { return Punto{par.at(0).get<double>(), par.at(1).get<double>()}; }

Ruta calcular(const RedVial& red, const json& viaje) {
  std::vector<Punto> paradas;
  for (const auto& p : viaje.value("paradas", json::array())) paradas.push_back(punto(p));
  return calcular_ruta(red, punto(viaje.at("origen")), punto(viaje.at("destino")), paradas, nullptr);
}

// Las calles de la ruta en orden, sin repetir la misma calle cuando sigue en el tramo siguiente.
std::vector<std::string> calles_en_orden(const RedVial& red, const Ruta& ruta) {
  std::vector<std::string> calles;
  for (std::size_t t : ruta.tramos) {
    const std::string& nombre = red.tramos()[t].nombre;
    if (calles.empty() || calles.back() != nombre) calles.push_back(nombre);
  }
  return calles;
}

// ¿Aparecen todas las `esperadas` en `calles`, en ese mismo orden (aunque haya otras entre medio)?
bool aparecen_en_orden(const std::vector<std::string>& esperadas, const std::vector<std::string>& calles) {
  std::size_t i = 0;
  for (const auto& calle : calles) {
    if (i < esperadas.size() && calle == esperadas[i]) ++i;
  }
  return i == esperadas.size();
}

std::string unir(const std::vector<std::string>& calles) {
  std::string texto;
  for (const auto& c : calles) texto += (texto.empty() ? "" : " > ") + c;
  return texto;
}

#define SALTAR_SIN_RED_REAL()                                                                  \
  if (red_real() == nullptr) GTEST_SKIP() << "define RED_VIAL_REAL con la ruta de red_vial_tarija.json"

}  // namespace

TEST(RutasReferencia, ElArchivoTieneLosViajesQuePideLaTarea) {
  // Se revisa siempre, haya o no red real: al menos cinco viajes, uno con parada y uno con calle prohibida.
  const json& viajes = referencia().at("viajes");
  ASSERT_GE(viajes.size(), 5u);
  bool con_parada = false;
  bool con_calle_prohibida = false;
  for (const auto& v : viajes) {
    EXPECT_TRUE(v.contains("origen") && v.contains("destino") && v.contains("nodo_origen") &&
                v.contains("nodo_destino") && v.contains("calles_esperadas") && v.contains("distancia_aprox_m"))
        << v.value("nombre", "?");
    if (!v.value("paradas", json::array()).empty()) con_parada = true;
    if (v.contains("no_debe_pasar_por")) con_calle_prohibida = true;
  }
  EXPECT_TRUE(con_parada);
  EXPECT_TRUE(con_calle_prohibida);
}

TEST(RutasReferencia, CP167_LasRutasPasanPorLasCallesEsperadas) {
  SALTAR_SIN_RED_REAL();
  const RedVial& red = *red_real();
  if (red.fecha_extracto() != referencia().at("red").at("fecha_extracto").get<std::string>()) {
    std::cout << "[ aviso ] la red real es del " << red.fecha_extracto()
              << " y los viajes se anotaron con el extracto del "
              << referencia().at("red").at("fecha_extracto").get<std::string>() << '\n';
  }
  const double tolerancia = referencia().at("tolerancia_distancia").get<double>();
  for (const auto& viaje : referencia().at("viajes")) {
    const std::string nombre = viaje.at("nombre").get<std::string>();
    SCOPED_TRACE(nombre);
    const Ruta ruta = calcular(red, viaje);
    ASSERT_EQ(ruta.estado, EstadoRuta::encontrada);

    EXPECT_EQ(red.nodos()[ruta.nodos.front()].id, viaje.at("nodo_origen").get<std::string>());
    EXPECT_EQ(red.nodos()[ruta.nodos.back()].id, viaje.at("nodo_destino").get<std::string>());

    const auto calles = calles_en_orden(red, ruta);
    const auto esperadas = viaje.at("calles_esperadas").get<std::vector<std::string>>();
    EXPECT_TRUE(aparecen_en_orden(esperadas, calles))
        << "esperadas: " << unir(esperadas) << "\nla ruta pasa por: " << unir(calles);

    const double aprox = viaje.at("distancia_aprox_m").get<double>();
    EXPECT_LE(std::abs(ruta.distancia_m - aprox), tolerancia * aprox)
        << "distancia " << ruta.distancia_m << " m, anotada " << aprox << " m";
  }
}

TEST(RutasReferencia, CP168_NingunaRutaRecorreUnaCalleAContramano) {
  SALTAR_SIN_RED_REAL();
  const RedVial& red = *red_real();
  for (const auto& viaje : referencia().at("viajes")) {
    SCOPED_TRACE(viaje.at("nombre").get<std::string>());
    const Ruta ruta = calcular(red, viaje);
    ASSERT_EQ(ruta.estado, EstadoRuta::encontrada);
    // Cada tramo se recorre de `desde` a `hasta`: el siguiente sale de donde llegó el anterior (RN-03.6).
    for (std::size_t i = 1; i < ruta.tramos.size(); ++i) {
      EXPECT_EQ(red.tramos()[ruta.tramos[i - 1]].hasta, red.tramos()[ruta.tramos[i]].desde)
          << "entre " << red.tramos()[ruta.tramos[i - 1]].id << " y " << red.tramos()[ruta.tramos[i]].id;
    }
    // El viaje de vuelta no puede usar la calle de un solo sentido que solo va hacia el otro lado.
    const auto prohibidas = viaje.value("no_debe_pasar_por", std::vector<std::string>{});
    for (const auto& calle : calles_en_orden(red, ruta)) {
      for (const auto& prohibida : prohibidas) EXPECT_NE(calle, prohibida);
    }
  }
}
