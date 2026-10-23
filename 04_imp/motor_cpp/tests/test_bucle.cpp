// Pruebas del bucle de mensajes: flujo completo de entrada a salida, sin lanzar un proceso.
#include <sstream>
#include <string>
#include <vector>

#include <gtest/gtest.h>
#include <nlohmann/json.hpp>

#include "bucle.hpp"
#include "nucleo.hpp"

using churoviaje::Nucleo;
using json = nlohmann::json;

namespace {

struct Resultado {
  int codigo_salida;
  std::vector<std::string> lineas;  // lo que salió por la salida estándar, línea por línea
  std::string registro;             // lo que salió por la salida de errores
};

Resultado correr(const std::string& entrada, const Nucleo& nucleo = Nucleo()) {
  std::istringstream in(entrada);
  std::ostringstream out, log;
  Resultado r;
  r.codigo_salida = churoviaje::ejecutar(in, out, log, nucleo);
  r.registro = log.str();
  std::istringstream salida(out.str());
  for (std::string l; std::getline(salida, l);) r.lineas.push_back(l);
  return r;
}

}  // namespace

// CP-25
TEST(Bucle, UnaLineaQueNoEsJsonNoDetieneAlNucleo) {
  const auto r = correr("esto no es json\n{\"id\":2,\"operacion\":\"consultar_estado\"}\n");
  ASSERT_EQ(r.lineas.size(), 2u);

  const json primera = json::parse(r.lineas[0]);
  EXPECT_EQ(primera.at("ok"), false);
  EXPECT_TRUE(primera.at("id").is_null());
  EXPECT_EQ(primera.at("error").at("codigo"), churoviaje::codigo_error::json_invalido);

  const json segunda = json::parse(r.lineas[1]);
  EXPECT_EQ(segunda.at("id"), 2);
  EXPECT_EQ(segunda.at("ok"), true);
  EXPECT_EQ(r.codigo_salida, 0);
}

TEST(Bucle, UnaRespuestaPorPeticionEnElMismoOrden) {
  const auto r = correr(
      "{\"id\":1,\"operacion\":\"consultar_estado\"}\n"
      "{\"id\":2,\"operacion\":\"calcular_ruta\"}\n"
      "{\"id\":3,\"operacion\":\"nada\"}\n"
      "{\"id\":4,\"operacion\":\"consultar_estado\"}\n");
  ASSERT_EQ(r.lineas.size(), 4u);
  const bool esperado_ok[] = {true, false, false, true};
  for (int i = 0; i < 4; ++i) {
    const json j = json::parse(r.lineas[i]);
    EXPECT_EQ(j.at("id"), i + 1);
    EXPECT_EQ(j.at("ok"), esperado_ok[i]);
  }
}

TEST(Bucle, SalidaEstandarSoloLlevaRespuestas) {
  const auto r = correr("basura\n{\"id\":1,\"operacion\":\"consultar_estado\"}\n{\"id\":2,\"operacion\":\"x\"}\n");
  ASSERT_EQ(r.lineas.size(), 3u);
  for (const auto& l : r.lineas) {
    const json j = json::parse(l, nullptr, false);
    EXPECT_FALSE(j.is_discarded()) << "línea que no es una respuesta: " << l;
    EXPECT_TRUE(j.contains("ok"));
  }
  // Los registros van aparte y no repiten el contenido de las peticiones.
  EXPECT_FALSE(r.registro.empty());
  EXPECT_NE(r.registro.find("JSON_INVALIDO"), std::string::npos);
  EXPECT_EQ(r.registro.find("basura"), std::string::npos);
}

TEST(Bucle, EntradaCerradaTerminaLimpio) {
  const auto r = correr("");
  EXPECT_EQ(r.codigo_salida, 0);
  EXPECT_TRUE(r.lineas.empty());
}

TEST(Bucle, UltimaLineaSinSaltoFinalSeAtiende) {
  const auto r = correr("{\"id\":1,\"operacion\":\"consultar_estado\"}");
  ASSERT_EQ(r.lineas.size(), 1u);
  EXPECT_EQ(json::parse(r.lineas[0]).at("ok"), true);
}

TEST(Bucle, LineasEnBlancoYFinDeLineaWindowsSeToleran) {
  const auto r = correr("\n\r\n{\"id\":1,\"operacion\":\"consultar_estado\"}\r\n   \n{\"id\":2,\"operacion\":\"consultar_estado\"}\r\n");
  ASSERT_EQ(r.lineas.size(), 2u);
  for (const auto& l : r.lineas) EXPECT_EQ(l.find('\r'), std::string::npos);
}

TEST(Bucle, SobreviveAMuchasPeticionesMalFormadas) {
  std::string entrada;
  for (int i = 0; i < 1000; ++i) entrada += "{\"id\":" + std::to_string(i) + ",\n";
  entrada += "{\"id\":1000,\"operacion\":\"consultar_estado\"}\n";
  const auto r = correr(entrada);
  ASSERT_EQ(r.lineas.size(), 1001u);
  EXPECT_EQ(json::parse(r.lineas.back()).at("ok"), true);
}
