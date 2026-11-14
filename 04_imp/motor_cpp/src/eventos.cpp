#include "eventos.hpp"

#include <algorithm>

namespace churoviaje {

std::optional<TipoEvento> tipo_evento_desde_texto(std::string_view codigo) {
  if (codigo == "ACCIDENTE") return TipoEvento::accidente;
  if (codigo == "CONTROL_POLICIAL") return TipoEvento::control_policial;
  if (codigo == "BLOQUEO") return TipoEvento::bloqueo;
  if (codigo == "OTRO") return TipoEvento::otro;
  if (codigo == "CONGESTION") return TipoEvento::congestion;
  return std::nullopt;
}

TablaEventos::TablaEventos(const RedVial* red)
    : red_(red), por_tramo_(red ? red->tramos().size() : 0), trafico_(red ? red->tramos().size() : 0) {}

std::vector<std::size_t> TablaEventos::registrar(const AvisoEvento& aviso, std::int64_t ahora) {
  // Un aviso nuevo del mismo evento reemplaza al anterior (por ejemplo, un vencimiento que se extiende).
  std::vector<std::size_t> antes = quitar(aviso.id);
  if (!red_ || aviso.vence <= ahora) return {};

  Guardado g;
  g.tipo = aviso.tipo;
  g.vence = aviso.vence;
  g.cierra = aviso.tipo == TipoEvento::bloqueo;
  switch (aviso.tipo) {
    case TipoEvento::accidente: g.nivel = NivelTrafico::muy_congestionado; break;  // ×4,0
    case TipoEvento::congestion: g.nivel = aviso.nivel_congestion; break;          // ×2,5 o ×4,0
    case TipoEvento::bloqueo: g.nivel = NivelTrafico::fluido; break;               // cierra el tramo
    case TipoEvento::control_policial:
    case TipoEvento::otro: g.nivel = NivelTrafico::moderado; break;                // ×1,5
  }

  if (aviso.tipo == TipoEvento::congestion && aviso.tramo_medido && *aviso.tramo_medido < red_->tramos().size()) {
    g.tramos.push_back(*aviso.tramo_medido);  // D-18: solo el sentido donde se midió
  } else if (const auto cercano = red_->tramo_mas_cercano(aviso.ubicacion.lat, aviso.ubicacion.lon)) {
    g.tramos.push_back(cercano->tramo);
    const auto& opuesto = red_->tramos()[cercano->tramo].opuesto;
    if (aviso.tipo != TipoEvento::congestion && opuesto) g.tramos.push_back(*opuesto);  // los dos sentidos
  }

  const std::vector<std::size_t> afectados = g.tramos;
  for (const std::size_t t : afectados) por_tramo_[t].push_back(aviso.id);
  eventos_.emplace(aviso.id, std::move(g));

  antes.insert(antes.end(), afectados.begin(), afectados.end());
  recalcular(antes);
  return afectados;
}

std::vector<std::size_t> TablaEventos::quitar(const std::string& id) {
  const auto it = eventos_.find(id);
  if (it == eventos_.end()) return {};
  std::vector<std::size_t> tramos = std::move(it->second.tramos);
  eventos_.erase(it);
  for (const std::size_t t : tramos) {
    auto& lista = por_tramo_[t];
    lista.erase(std::remove(lista.begin(), lista.end(), id), lista.end());
  }
  recalcular(tramos);
  return tramos;
}

const std::vector<std::string>& TablaEventos::eventos_en(std::size_t tramo) const {
  static const std::vector<std::string> ninguno;
  return tramo < por_tramo_.size() ? por_tramo_[tramo] : ninguno;
}

const Trafico& TablaEventos::trafico(std::int64_t ahora) {
  std::vector<std::string> vencidos;
  for (const auto& [id, g] : eventos_) {
    if (g.vence <= ahora) vencidos.push_back(id);
  }
  for (const auto& id : vencidos) quitar(id);
  return trafico_;
}

// El nivel de cada tramo sale de sus eventos vigentes: vale el más grave, y un bloqueo lo cierra (RN-03.9).
void TablaEventos::recalcular(const std::vector<std::size_t>& tramos) {
  for (const std::size_t t : tramos) {
    NivelTrafico nivel = NivelTrafico::fluido;
    bool cierra = false;
    for (const auto& id : por_tramo_[t]) {
      const Guardado& g = eventos_.at(id);
      cierra = cierra || g.cierra;
      nivel = std::max(nivel, g.nivel);
    }
    trafico_.fijar(t, nivel);
    trafico_.cerrar(t, cierra);
  }
}

}  // namespace churoviaje
