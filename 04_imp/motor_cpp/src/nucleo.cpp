#include "nucleo.hpp"

#include <array>
#include <exception>
#include <utility>

#include <nlohmann/json.hpp>

namespace churoviaje {
namespace {

// Se usa ordered_json para que las respuestas salgan con "id" y "ok" primero, como en el contrato.
using json = nlohmann::ordered_json;

constexpr std::array<std::string_view, 4> kOperacionesPendientes = {
    "calcular_ruta", "revisar_ruta", "actualizar_trafico", "analizar_telemetria"};
constexpr std::string_view kConsultarEstado = "consultar_estado";

bool es_pendiente(std::string_view operacion) {
  for (auto pendiente : kOperacionesPendientes) {
    if (pendiente == operacion) return true;
  }
  return false;
}

std::string_view recortar(std::string_view texto) {
  constexpr std::string_view blancos = " \t\r\n\f\v";
  const auto inicio = texto.find_first_not_of(blancos);
  if (inicio == std::string_view::npos) return {};
  const auto fin = texto.find_last_not_of(blancos);
  return texto.substr(inicio, fin - inicio + 1);
}

Respuesta respuesta_error(const json& id, const char* codigo, const std::string& descripcion) {
  json r = {{"id", id}, {"ok", false}, {"error", {{"codigo", codigo}, {"descripcion", descripcion}}}};
  // error_handler_t::replace: un byte que no sea UTF-8 no puede hacer fallar el volcado.
  return {r.dump(-1, ' ', false, json::error_handler_t::replace), false, codigo};
}

Respuesta respuesta_ok(json r) {
  return {r.dump(-1, ' ', false, json::error_handler_t::replace), true, {}};
}

// El id se devuelve tal cual llegó. Solo se acepta un entero o un texto.
bool id_valido(const json& id) { return id.is_number_integer() || id.is_string(); }

}  // namespace

Nucleo::Nucleo(EstadoRed estado) : estado_(std::move(estado)) {}

std::optional<Respuesta> Nucleo::atender(std::string_view linea) const noexcept {
  json id = nullptr;  // mientras no se sepa cuál es, las respuestas de error llevan "id":null
  try {
    linea = recortar(linea);
    if (linea.empty()) return std::nullopt;

    const json peticion = json::parse(linea.begin(), linea.end(), nullptr, /*allow_exceptions=*/false);
    if (peticion.is_discarded()) {
      return respuesta_error(id, codigo_error::json_invalido, "La línea no es un JSON válido.");
    }
    if (!peticion.is_object()) {
      return respuesta_error(id, codigo_error::peticion_invalida, "La petición debe ser un objeto JSON.");
    }

    // Primero el id, para poder repetirlo incluso en los errores que vienen después.
    const auto it_id = peticion.find("id");
    if (it_id == peticion.end()) {
      return respuesta_error(id, codigo_error::peticion_invalida, "Falta el campo \"id\".");
    }
    if (!id_valido(*it_id)) {
      return respuesta_error(id, codigo_error::peticion_invalida,
                             "El campo \"id\" debe ser un número entero o un texto.");
    }
    id = *it_id;

    const auto it_op = peticion.find("operacion");
    if (it_op == peticion.end() || !it_op->is_string()) {
      return respuesta_error(id, codigo_error::peticion_invalida,
                             "Falta el campo \"operacion\" o no es un texto.");
    }
    const std::string operacion = it_op->get<std::string>();

    if (operacion == kConsultarEstado) {
      return respuesta_ok({{"id", id},
                           {"ok", true},
                           {"red_cargada", estado_.cargada},
                           {"fecha_extracto", estado_.fecha_extracto ? json(*estado_.fecha_extracto) : json(nullptr)}});
    }
    if (es_pendiente(operacion)) {
      return respuesta_error(id, codigo_error::no_implementada,
                             "La operación \"" + operacion + "\" todavía no está implementada.");
    }
    return respuesta_error(id, codigo_error::operacion_desconocida,
                           "La operación \"" + operacion + "\" no existe en el contrato.");
  } catch (const std::exception& e) {
    try {
      return respuesta_error(id, codigo_error::error_interno, std::string("Fallo interno: ") + e.what());
    } catch (...) {
    }
  } catch (...) {
  }
  // Último recurso, sin asignaciones dinámicas que puedan fallar otra vez.
  return Respuesta{R"({"id":null,"ok":false,"error":{"codigo":"ERROR_INTERNO","descripcion":"Fallo interno."}})",
                   false, codigo_error::error_interno};
}

}  // namespace churoviaje
