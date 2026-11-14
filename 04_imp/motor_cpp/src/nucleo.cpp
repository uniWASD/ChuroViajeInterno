#include "nucleo.hpp"

#include <algorithm>
#include <cmath>
#include <optional>
#include <vector>
#include <exception>
#include <utility>

#include "revision.hpp"
#include "tiempo.hpp"

#include <nlohmann/json.hpp>

namespace churoviaje {
namespace {

// Se usa ordered_json para que las respuestas salgan con "id" y "ok" primero, como en el contrato.
using json = nlohmann::ordered_json;

constexpr std::string_view kConsultarEstado = "consultar_estado";
constexpr std::string_view kCalcularRuta = "calcular_ruta";
constexpr std::string_view kRevisarRuta = "revisar_ruta";
constexpr std::string_view kActualizarTrafico = "actualizar_trafico";
constexpr std::string_view kAnalizarTelemetria = "analizar_telemetria";

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

// Los campos de una ruta encontrada: distancia, tiempo, trazado y tramos con su calle (05_contratos.md).
void agregar_ruta(json& r, const RedVial& red, const Ruta& ruta) {
  json trazado = json::array();
  for (const Punto& p : ruta.trazado) trazado.push_back({redondear(p.lat), redondear(p.lon)});
  json tramos = json::array();
  for (const std::size_t t : ruta.tramos) {
    tramos.push_back({{"id", red.tramos()[t].id}, {"calle", red.tramos()[t].nombre}});
  }
  r["distancia_m"] = static_cast<long long>(std::llround(ruta.distancia_m));
  r["tiempo_s"] = static_cast<long long>(std::llround(ruta.tiempo_s));
  r["trazado"] = std::move(trazado);
  r["tramos"] = std::move(tramos);
}

// calcular_ruta: origen y destino obligatorios, paradas opcionales (RN-03.2). «Sin ruta» y «punto inválido»
// son respuestas correctas, con "ok":true (RN-03.12).
Respuesta responder_ruta(const json& id, const json& peticion, const RedVial* red, TablaEventos& eventos,
                         std::int64_t ahora, Algoritmo algoritmo) {
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

  const Ruta ruta = calcular_ruta(*red, *origen, *destino, paradas, &eventos.trafico(ahora), algoritmo);
  json r = {{"id", id}, {"ok", true}, {"estado", texto_estado(ruta.estado)}};
  if (ruta.estado == EstadoRuta::encontrada) agregar_ruta(r, *red, ruta);
  return respuesta_ok(std::move(r));
}

const char* texto_revision(EstadoRevision estado) {
  switch (estado) {
    case EstadoRevision::sin_alternativa: return "SIN_ALTERNATIVA";
    case EstadoRevision::alternativa: return "ALTERNATIVA";
    case EstadoRevision::ruta_desactualizada: return "RUTA_DESACTUALIZADA";
    case EstadoRevision::sin_cambios: break;
  }
  return "SIN_CAMBIOS";
}

// Un id de evento llega como texto o como entero; se guarda como texto, igual que en actualizar_trafico.
std::optional<std::string> leer_id_evento(const json& valor) {
  if (valor.is_string()) return valor.get<std::string>();
  if (valor.is_number_integer()) return valor.dump();
  return std::nullopt;
}

// revisar_ruta (#76): posición, tramos que faltan y destino obligatorios; paradas y eventos ya avisados,
// opcionales. Las cuatro respuestas son correctas, con "ok":true.
Respuesta responder_revision(const json& id, const json& peticion, const RedVial* red, TablaEventos& eventos,
                             std::int64_t ahora, Algoritmo algoritmo) {
  PeticionRevision revision;
  const auto it_posicion = peticion.find("posicion");
  const auto it_destino = peticion.find("destino");
  const auto posicion = it_posicion != peticion.end() ? leer_punto(*it_posicion) : std::nullopt;
  const auto destino = it_destino != peticion.end() ? leer_punto(*it_destino) : std::nullopt;
  if (!posicion || !destino) {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "\"posicion\" y \"destino\" son obligatorios y van como [latitud, longitud].");
  }
  revision.posicion = *posicion;
  revision.destino = *destino;

  const auto it_tramos = peticion.find("tramos");
  if (it_tramos == peticion.end() || !it_tramos->is_array()) {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "\"tramos\" es obligatorio: la lista de ids de los tramos que faltan.");
  }
  for (const json& t : *it_tramos) {
    if (!t.is_string()) {
      return respuesta_error(id, codigo_error::peticion_invalida, "Cada tramo va como el texto de su id.");
    }
    revision.tramos.push_back(t.get<std::string>());
  }

  const auto it_paradas = peticion.find("paradas");
  if (it_paradas != peticion.end() && !it_paradas->is_null()) {
    if (!it_paradas->is_array()) {
      return respuesta_error(id, codigo_error::peticion_invalida, "\"paradas\" debe ser una lista de puntos.");
    }
    for (const json& p : *it_paradas) {
      const auto parada = leer_punto(p);
      if (!parada) {
        return respuesta_error(id, codigo_error::peticion_invalida, "Cada parada va como [latitud, longitud].");
      }
      revision.paradas.push_back(*parada);
    }
  }

  const auto it_avisados = peticion.find("eventos_avisados");
  if (it_avisados != peticion.end() && !it_avisados->is_null()) {
    if (!it_avisados->is_array()) {
      return respuesta_error(id, codigo_error::peticion_invalida,
                             "\"eventos_avisados\" debe ser una lista de ids de eventos.");
    }
    for (const json& e : *it_avisados) {
      const auto evento = leer_id_evento(e);
      if (!evento) {
        return respuesta_error(id, codigo_error::peticion_invalida, "Cada evento avisado va como texto o entero.");
      }
      revision.avisados.push_back(*evento);
    }
  }
  if (!red) {
    return respuesta_error(id, codigo_error::red_no_cargada, "El núcleo no tiene la red vial cargada.");
  }

  const Revision resultado = revisar_ruta(*red, eventos, ahora, revision, algoritmo);
  json r = {{"id", id}, {"ok", true}, {"estado", texto_revision(resultado.estado)}};
  if (!resultado.evento.empty()) r["evento"] = resultado.evento;
  if (resultado.estado == EstadoRevision::alternativa) {
    r["costo"] = static_cast<long long>(std::llround(resultado.alternativa.costo));
    if (std::isfinite(resultado.costo_actual)) {
      r["costo_actual"] = static_cast<long long>(std::llround(resultado.costo_actual));
    }
    agregar_ruta(r, *red, resultado.alternativa);
  }
  return respuesta_ok(std::move(r));
}

json ids_de_tramos(const RedVial& red, const std::vector<std::size_t>& tramos) {
  json ids = json::array();
  for (const std::size_t t : tramos) ids.push_back(red.tramos()[t].id);
  return ids;
}

// actualizar_trafico: un evento nace, cambia su vencimiento, se retira o vence (D-14). Responde los tramos
// que afecta, o que afectaba si se quitó.
Respuesta responder_trafico(const json& id, const json& peticion, const RedVial* red, TablaEventos& eventos,
                            std::int64_t ahora) {
  const auto it_evento = peticion.find("evento");
  const auto it_accion = peticion.find("accion");
  if (it_evento == peticion.end() || !(it_evento->is_string() || it_evento->is_number_integer()) ||
      it_accion == peticion.end() || !it_accion->is_string()) {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "Faltan \"evento\" (texto o entero) o \"accion\" (texto).");
  }
  const std::string evento = it_evento->is_string() ? it_evento->get<std::string>() : it_evento->dump();
  const std::string accion = it_accion->get<std::string>();

  if (accion == "retira" || accion == "vence") {
    if (!red) return respuesta_error(id, codigo_error::red_no_cargada, "El núcleo no tiene la red vial cargada.");
    return respuesta_ok({{"id", id}, {"ok", true}, {"tramos", ids_de_tramos(*red, eventos.quitar(evento))}});
  }
  if (accion != "nace" && accion != "cambia_vencimiento") {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "\"accion\" debe ser nace, cambia_vencimiento, retira o vence.");
  }

  AvisoEvento aviso;
  aviso.id = evento;
  const auto it_tipo = peticion.find("tipo");
  const auto tipo = (it_tipo != peticion.end() && it_tipo->is_string())
                        ? tipo_evento_desde_texto(it_tipo->get<std::string>())
                        : std::nullopt;
  if (!tipo) {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "\"tipo\" debe ser ACCIDENTE, CONTROL_POLICIAL, BLOQUEO, OTRO o CONGESTION.");
  }
  aviso.tipo = *tipo;
  const auto it_ubicacion = peticion.find("ubicacion");
  const auto ubicacion = it_ubicacion != peticion.end() ? leer_punto(*it_ubicacion) : std::nullopt;
  if (!ubicacion) {
    return respuesta_error(id, codigo_error::peticion_invalida, "\"ubicacion\" va como [latitud, longitud].");
  }
  aviso.ubicacion = *ubicacion;
  const auto it_vence = peticion.find("vence");
  const auto vence = (it_vence != peticion.end() && it_vence->is_string())
                         ? segundos_desde_iso(it_vence->get<std::string>())
                         : std::nullopt;
  if (!vence) {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "\"vence\" va como fecha ISO 8601 con zona horaria, por ejemplo 2026-10-06T16:02:00-04:00.");
  }
  aviso.vence = *vence;
  if (aviso.tipo == TipoEvento::congestion) {
    const auto it_nivel = peticion.find("nivel");
    const std::string nivel = (it_nivel != peticion.end() && it_nivel->is_string()) ? it_nivel->get<std::string>() : "";
    if (nivel == "CONGESTIONADO") {
      aviso.nivel_congestion = NivelTrafico::congestionado;
    } else if (nivel == "MUY_CONGESTIONADO") {
      aviso.nivel_congestion = NivelTrafico::muy_congestionado;
    } else {
      return respuesta_error(id, codigo_error::peticion_invalida,
                             "Una congestión lleva \"nivel\": CONGESTIONADO o MUY_CONGESTIONADO.");
    }
  }
  if (!red) return respuesta_error(id, codigo_error::red_no_cargada, "El núcleo no tiene la red vial cargada.");
  if (aviso.tipo == TipoEvento::congestion) {
    const auto it_tramo = peticion.find("tramo");
    if (it_tramo != peticion.end() && it_tramo->is_string()) {
      for (std::size_t t = 0; t < red->tramos().size(); ++t) {
        if (red->tramos()[t].id == it_tramo->get_ref<const std::string&>()) {
          aviso.tramo_medido = t;
          break;
        }
      }
    }
  }
  return respuesta_ok({{"id", id}, {"ok", true}, {"tramos", ids_de_tramos(*red, eventos.registrar(aviso, ahora))}});
}

const char* texto_nivel(NivelTrafico nivel) {
  return nivel == NivelTrafico::muy_congestionado ? "MUY_CONGESTIONADO" : "CONGESTIONADO";
}

// analizar_telemetria (#73): lecturas agrupadas por recorrido, con la marca de simulado y sin datos del conductor.
// Responde las congestiones que se cumplen: tramo, punto, nivel y si salen de recorridos simulados (RN-04.10).
Respuesta responder_telemetria(const json& id, const json& peticion, const RedVial* red,
                               AnalizadorTelemetria& telemetria, std::int64_t ahora) {
  struct Grupo {
    std::string recorrido;
    bool simulado = false;
    std::vector<LecturaTelemetria> lecturas;
  };
  const auto it_recorridos = peticion.find("recorridos");
  if (it_recorridos == peticion.end() || !it_recorridos->is_array()) {
    return respuesta_error(id, codigo_error::peticion_invalida,
                           "\"recorridos\" es obligatorio: una lista de {recorrido, simulado, lecturas}.");
  }
  std::vector<Grupo> grupos;
  for (const json& g : *it_recorridos) {
    const auto it_rec = g.is_object() ? g.find("recorrido") : g.end();
    if (!g.is_object() || it_rec == g.end() || !it_rec->is_string() || it_rec->get_ref<const std::string&>().empty()) {
      return respuesta_error(id, codigo_error::peticion_invalida,
                             "Cada recorrido lleva \"recorrido\": su identificador temporal, como texto.");
    }
    Grupo grupo;
    grupo.recorrido = it_rec->get<std::string>();
    const auto it_sim = g.find("simulado");
    if (it_sim != g.end() && !it_sim->is_null()) {
      if (!it_sim->is_boolean()) {
        return respuesta_error(id, codigo_error::peticion_invalida, "\"simulado\" debe ser true o false.");
      }
      grupo.simulado = it_sim->get<bool>();
    }
    const auto it_lecturas = g.find("lecturas");
    if (it_lecturas == g.end() || !it_lecturas->is_array()) {
      return respuesta_error(id, codigo_error::peticion_invalida, "Cada recorrido lleva \"lecturas\": una lista.");
    }
    for (const json& l : *it_lecturas) {
      const auto it_pos = l.is_object() ? l.find("posicion") : l.end();
      const auto posicion = it_pos != l.end() ? leer_punto(*it_pos) : std::nullopt;
      const auto it_prec = l.is_object() ? l.find("precision_m") : l.end();
      const auto it_mom = l.is_object() ? l.find("momento") : l.end();
      const auto momento = (it_mom != l.end() && it_mom->is_string())
                               ? segundos_desde_iso(it_mom->get<std::string>())
                               : std::nullopt;
      if (!posicion || it_prec == l.end() || !it_prec->is_number() || !momento) {
        return respuesta_error(id, codigo_error::peticion_invalida,
                               "Cada lectura lleva \"posicion\" [latitud, longitud], \"precision_m\" (número) y "
                               "\"momento\" (ISO 8601 con zona horaria).");
      }
      grupo.lecturas.push_back({*posicion, it_prec->get<double>(), *momento});
    }
    grupos.push_back(std::move(grupo));
  }
  if (!red) return respuesta_error(id, codigo_error::red_no_cargada, "El núcleo no tiene la red vial cargada.");

  // Una congestión por tramo y por marca de simulado: si varios recorridos la confirman, vale la más grave.
  std::vector<CongestionDetectada> congestiones;
  std::size_t validas = 0, descartadas = 0;
  for (Grupo& grupo : grupos) {
    const ResultadoTelemetria r = telemetria.analizar(grupo.recorrido, grupo.simulado, std::move(grupo.lecturas), ahora);
    validas += r.validas;
    descartadas += r.descartadas;
    for (const CongestionDetectada& c : r.congestiones) {
      auto ya = std::find_if(congestiones.begin(), congestiones.end(), [&](const CongestionDetectada& x) {
        return x.tramo == c.tramo && x.simulado == c.simulado;
      });
      if (ya == congestiones.end()) {
        congestiones.push_back(c);
      } else if (c.nivel > ya->nivel) {
        *ya = c;
      }
    }
  }
  json lista = json::array();
  for (const CongestionDetectada& c : congestiones) {
    lista.push_back({{"tramo", red->tramos()[c.tramo].id},
                     {"punto", {redondear(c.punto.lat), redondear(c.punto.lon)}},
                     {"nivel", texto_nivel(c.nivel)},
                     {"simulado", c.simulado}});
  }
  return respuesta_ok({{"id", id},
                       {"ok", true},
                       {"congestiones", std::move(lista)},
                       {"lecturas_validas", validas},
                       {"lecturas_descartadas", descartadas}});
}

}  // namespace

Nucleo::Nucleo(EstadoRed estado) : estado_(std::move(estado)), reloj_(ahora_en_segundos) {}

Nucleo::Nucleo(std::shared_ptr<const RedVial> red, Algoritmo algoritmo)
    : red_(std::move(red)), algoritmo_(algoritmo), eventos_(red_.get()), telemetria_(red_.get()),
      reloj_(ahora_en_segundos) {
  if (red_) {
    estado_.cargada = true;
    estado_.fecha_extracto = red_->fecha_extracto();
    estado_.nodos = red_->nodos().size();
    estado_.tramos = red_->tramos().size();
  }
}

std::optional<Respuesta> Nucleo::atender(std::string_view linea) noexcept {
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
      return responder_ruta(id, peticion, red_.get(), eventos_, reloj_(), algoritmo_);
    }
    if (operacion == kRevisarRuta) {
      return responder_revision(id, peticion, red_.get(), eventos_, reloj_(), algoritmo_);
    }
    if (operacion == kActualizarTrafico) {
      return responder_trafico(id, peticion, red_.get(), eventos_, reloj_());
    }
    if (operacion == kAnalizarTelemetria) {
      return responder_telemetria(id, peticion, red_.get(), telemetria_, reloj_());
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
