#include "nucleo.hpp"

#include <array>
#include <cmath>
#include <optional>
#include <vector>
#include <exception>
#include <utility>

#include <nlohmann/json.hpp>

namespace churoviaje {
namespace {

// Se usa ordered_json para que las respuestas salgan con "id" y "ok" primero, como en el contrato.
using json = nlohmann::ordered_json;

constexpr std::array<std::string_view, 3> kOperacionesPendientes = {"revisar_ruta", "actualizar_trafico",
                                                                   "analizar_telemetria"};
constexpr std::string_view kConsultarEstado = "consultar_estado";
constexpr std::string_view kCalcularRuta = "calcular_ruta";

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

// Un punto del contrato es [latitud, longitud] en grados decimales.
std::optional<Punto> leer_punto(const json& valor) {
  if (!valor.is_array() || valor.size() != 2 || !valor[0].is_number() || !valor[1].is_number()) return std::nullopt;
  return Punto{valor[0].get<double>(), valor[1].get<double>()};
}

// Seis decimales son unos 10 cm: de sobra para dibujar la ruta y la respuesta queda más corta.
double redondear(double grados) { return std::round(grados * 1e6) / 1e6; }

const char* texto_estado(EstadoRuta estado) {
  switch (estado) {
    case EstadoRuta::encontrada: return "ENCONTRADA";
    case EstadoRuta::punto_invalido: return "PUNTO_INVALIDO";
    case EstadoRuta::sin_ruta: break;
  }
  return "SIN_RUTA";
}

// calcular_ruta: origen y destino obligatorios, paradas opcionales (RN-03.2). «Sin ruta» y «punto inválido»
// son respuestas correctas, con "ok":true (RN-03.12).
Respuesta responder_ruta(const json& id, const json& peticion, const RedVial* red, const Trafico& trafico,
                         Algoritmo algoritmo) {
  const auto it_origen = peticion.find("origen");
  const auto it_destino = peticion.find("destino");
  std::optional<Punto> origen, destino;
  if (it_origen != peticion.end()) origen = leer_punto(*it_origen);
  if (it_destino != peticion.end()) destino = leer_punto(*it_destino);
  if (!origen || !destino) {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "\"origen\" y \"destino\" son obligatorios y van como [latitud, longitud].");
  }
  std::vector<Punto> paradas;
  const auto it_paradas = peticion.find("paradas");
  if (it_paradas != peticion.end() && !it_paradas->is_null()) {
    if (!it_paradas->is_array()) {
      return respuesta_error(id, codigo_error::peticion_invalida, "\"paradas\" debe ser una lista de puntos.");
    }
    for (const json& p : *it_paradas) {
      const auto parada = leer_punto(p);
      if (!parada) {
        return respuesta_error(id, codigo_error::peticion_invalida,
                               "Cada parada va como [latitud, longitud].");
      }
      paradas.push_back(*parada);
    }
  }
  if (!red) {
    return respuesta_error(id, codigo_error::red_no_cargada, "El núcleo no tiene la red vial cargada.");
  }

  const Ruta ruta = calcular_ruta(*red, *origen, *destino, paradas, &trafico, algoritmo);
  json r = {{"id", id}, {"ok", true}, {"estado", texto_estado(ruta.estado)}};
  if (ruta.estado == EstadoRuta::encontrada) {
    json trazado = json::array();
    for (const Punto& p : ruta.trazado) trazado.push_back({redondear(p.lat), redondear(p.lon)});
    json tramos = json::array();
    for (const std::size_t t : ruta.tramos) {
      tramos.push_back({{"id", red->tramos()[t].id}, {"calle", red->tramos()[t].nombre}});
    }
    r["distancia_m"] = static_cast<long long>(std::llround(ruta.distancia_m));
    r["tiempo_s"] = static_cast<long long>(std::llround(ruta.tiempo_s));
    r["trazado"] = std::move(trazado);
    r["tramos"] = std::move(tramos);
  }
  return respuesta_ok(std::move(r));
}

}  // namespace

Nucleo::Nucleo(EstadoRed estado) : estado_(std::move(estado)) {}

Nucleo::Nucleo(std::shared_ptr<const RedVial> red, Algoritmo algoritmo)
    : red_(std::move(red)), algoritmo_(algoritmo), trafico_(red_ ? red_->tramos().size() : 0) {
  if (red_) {
    estado_.cargada = true;
    estado_.fecha_extracto = red_->fecha_extracto();
    estado_.nodos = red_->nodos().size();
    estado_.tramos = red_->tramos().size();
  }
}

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
                           {"fecha_extracto", estado_.fecha_extracto ? json(*estado_.fecha_extracto) : json(nullptr)},
                           {"nodos", estado_.nodos},
                           {"tramos", estado_.tramos}});
    }
    if (operacion == kCalcularRuta) {
      return responder_ruta(id, peticion, red_.get(), trafico_, algoritmo_);
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
