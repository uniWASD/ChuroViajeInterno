// Pruebas de Nucleo::atender: una línea entra, una línea sale.
#include <string>

#include <gtest/gtest.h>
#include <nlohmann/json.hpp>

#include "nucleo.hpp"

using churoviaje::EstadoRed;
using churoviaje::Nucleo;
using json = nlohmann::json;
namespace codigo = churoviaje::codigo_error;

namespace {

// Atiende la línea, comprueba que la respuesta cabe en una sola línea y la devuelve ya leída.
json responder(const Nucleo& nucleo, const std::string& linea) {
  const auto r = nucleo.atender(linea);
  EXPECT_TRUE(r.has_value()) << "no hubo respuesta para: " << linea;
  if (!r) return json();
  EXPECT_EQ(r->linea.find('\n'), std::string::npos) << "la respuesta ocupa más de una línea";
  EXPECT_EQ(r->linea.find('\r'), std::string::npos);
  const json j = json::parse(r->linea, nullptr, /*allow_exceptions=*/false);
  EXPECT_FALSE(j.is_discarded()) << "la respuesta no es JSON: " << r->linea;
  return j;
}

}  // namespace

// CP-24
TEST(Nucleo, ConsultarEstadoResponde) {
  const Nucleo nucleo;
  const auto r = nucleo.atender(R"({"id":1,"operacion":"consultar_estado"})");
  ASSERT_TRUE(r.has_value());
  EXPECT_TRUE(r->ok);
  EXPECT_EQ(r->linea.find('\n'), std::string::npos);
  EXPECT_EQ(r->linea.rfind(R"({"id":1,"ok":true)", 0), 0u) << r->linea;  // "id" y "ok" primero

  const json j = json::parse(r->linea);
  EXPECT_EQ(j.at("id"), 1);
  EXPECT_EQ(j.at("ok"), true);
  EXPECT_EQ(j.at("red_cargada"), false);
  EXPECT_TRUE(j.at("fecha_extracto").is_null());
}

TEST(Nucleo, ConsultarEstadoConRedCargada) {
  const Nucleo nucleo(EstadoRed{true, std::string("2026-10-05")});
  const json j = responder(nucleo, R"({"id":7,"operacion":"consultar_estado"})");
  EXPECT_EQ(j.at("id"), 7);
  EXPECT_EQ(j.at("ok"), true);
  EXPECT_EQ(j.at("red_cargada"), true);
  EXPECT_EQ(j.at("fecha_extracto"), "2026-10-05");
}

TEST(Nucleo, EcoDelIdTextoYEnteroGrande) {
  const Nucleo nucleo;
  EXPECT_EQ(responder(nucleo, R"({"id":"abc-1","operacion":"consultar_estado"})").at("id"), "abc-1");
  EXPECT_EQ(responder(nucleo, R"({"id":9007199254740993,"operacion":"consultar_estado"})").at("id"),
            9007199254740993LL);
}

TEST(Nucleo, IgnoraCamposDeMas) {
  const Nucleo nucleo;
  const json j = responder(nucleo, R"({"id":2,"operacion":"consultar_estado","extra":[1,2,3]})");
  EXPECT_EQ(j.at("ok"), true);
}

// Las otras cuatro operaciones del contrato responden «no implementada» hasta que lleguen sus issues.
class OperacionPendiente : public ::testing::TestWithParam<std::string> {};

TEST_P(OperacionPendiente, RespondeNoImplementada) {
  const Nucleo nucleo;
  const json j = responder(nucleo, R"({"id":5,"operacion":")" + GetParam() + R"("})");
  EXPECT_EQ(j.at("id"), 5);
  EXPECT_EQ(j.at("ok"), false);
  EXPECT_EQ(j.at("error").at("codigo"), codigo::no_implementada);
  EXPECT_NE(j.at("error").at("descripcion").get<std::string>().find(GetParam()), std::string::npos);
}

INSTANTIATE_TEST_SUITE_P(Contrato, OperacionPendiente,
                         ::testing::Values("calcular_ruta", "revisar_ruta", "actualizar_trafico",
                                           "analizar_telemetria"));

TEST(Nucleo, OperacionDesconocida) {
  const Nucleo nucleo;
  const json j = responder(nucleo, R"({"id":9,"operacion":"hacer_cafe"})");
  EXPECT_EQ(j.at("id"), 9);
  EXPECT_EQ(j.at("ok"), false);
  EXPECT_EQ(j.at("error").at("codigo"), codigo::operacion_desconocida);
  EXPECT_FALSE(j.at("error").at("descripcion").get<std::string>().empty());
}

TEST(Nucleo, NombreDeOperacionConComillasYSaltosNoRompeLaLinea) {
  const Nucleo nucleo;
  const json j = responder(nucleo, R"({"id":3,"operacion":"a\"b\nc\\d ñ"})");
  EXPECT_EQ(j.at("ok"), false);
  EXPECT_EQ(j.at("error").at("codigo"), codigo::operacion_desconocida);
}

TEST(Nucleo, LineaQueNoEsJson) {
  const Nucleo nucleo;
  for (const std::string linea : {"esto no es json", "{", R"({"id":1,)", "{'id':1}", "\xff\xfe\x01"}) {
    const json j = responder(nucleo, linea);
    EXPECT_TRUE(j.at("id").is_null()) << linea;
    EXPECT_EQ(j.at("ok"), false) << linea;
    EXPECT_EQ(j.at("error").at("codigo"), codigo::json_invalido) << linea;
  }
}

TEST(Nucleo, JsonQueNoEsObjeto) {
  const Nucleo nucleo;
  for (const std::string linea : {"[]", "123", "\"texto\"", "null", "true"}) {
    const json j = responder(nucleo, linea);
    EXPECT_EQ(j.at("ok"), false) << linea;
    EXPECT_EQ(j.at("error").at("codigo"), codigo::peticion_invalida) << linea;
  }
}

TEST(Nucleo, FaltaOperacionOMalTipada) {
  const Nucleo nucleo;
  for (const std::string linea : {R"({"id":4})", R"({"id":4,"operacion":null})", R"({"id":4,"operacion":12})"}) {
    const json j = responder(nucleo, linea);
    EXPECT_EQ(j.at("id"), 4) << linea;  // el id se repite aunque lo demás falle
    EXPECT_EQ(j.at("ok"), false) << linea;
    EXPECT_EQ(j.at("error").at("codigo"), codigo::peticion_invalida) << linea;
  }
}

TEST(Nucleo, FaltaIdOMalTipado) {
  const Nucleo nucleo;
  for (const std::string linea : {R"({"operacion":"consultar_estado"})", R"({"id":null,"operacion":"consultar_estado"})",
                                  R"({"id":1.5,"operacion":"consultar_estado"})", R"({"id":[1],"operacion":"consultar_estado"})"}) {
    const json j = responder(nucleo, linea);
    EXPECT_TRUE(j.at("id").is_null()) << linea;
    EXPECT_EQ(j.at("ok"), false) << linea;
    EXPECT_EQ(j.at("error").at("codigo"), codigo::peticion_invalida) << linea;
  }
}

TEST(Nucleo, LineaEnBlancoNoGeneraRespuesta) {
  const Nucleo nucleo;
  EXPECT_FALSE(nucleo.atender("").has_value());
  EXPECT_FALSE(nucleo.atender("   \t ").has_value());
  EXPECT_FALSE(nucleo.atender("\r").has_value());
}

TEST(Nucleo, AceptaFinDeLineaWindows) {
  const Nucleo nucleo;
  const json j = responder(nucleo, "{\"id\":1,\"operacion\":\"consultar_estado\"}\r");
  EXPECT_EQ(j.at("ok"), true);
}

TEST(Nucleo, ErroresMarcanOkFalseYCodigo) {
  const Nucleo nucleo;
  const auto r = nucleo.atender("basura");
  ASSERT_TRUE(r.has_value());
  EXPECT_FALSE(r->ok);
  EXPECT_EQ(r->codigo, codigo::json_invalido);
}
