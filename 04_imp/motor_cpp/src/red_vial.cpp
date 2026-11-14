#include "red_vial.hpp"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <fstream>
#include <limits>
#include <ostream>
#include <string_view>
#include <unordered_map>
#include <utility>

#include <nlohmann/json.hpp>

namespace churoviaje {
namespace {

using json = nlohmann::json;

constexpr int kVersionSoportada = 1;
constexpr double kMetrosPorGradoLat = 111320.0;  // aproximación de esfera: sobra para ajustar a 100 m
constexpr double kPi = 3.14159265358979323846;
constexpr double kRadioTerrestreM = 6371008.8;  // el mismo de preparacion/preparar_red.py
constexpr double kMargenEstimacion = 0.999;      // la longitud del archivo viene redondeada a milímetros

[[noreturn]] void fallar(const std::string& donde, const std::string& que) {
  throw ErrorRedVial(donde.empty() ? que : donde + ": " + que);
}

const json& campo(const json& objeto, const char* nombre, const std::string& donde) {
  const auto it = objeto.find(nombre);
  if (it == objeto.end()) fallar(donde, std::string("falta el campo \"") + nombre + "\"");
  return *it;
}

std::string texto(const json& objeto, const char* nombre, const std::string& donde) {
  const json& valor = campo(objeto, nombre, donde);
  if (!valor.is_string()) fallar(donde, std::string("el campo \"") + nombre + "\" debe ser un texto");
  return valor.get<std::string>();
}

double numero(const json& valor, const std::string& donde, const char* nombre) {
  if (!valor.is_number()) fallar(donde, std::string("\"") + nombre + "\" debe ser un número");
  const double v = valor.get<double>();
  if (!std::isfinite(v)) fallar(donde, std::string("\"") + nombre + "\" no es un número finito");
  return v;
}

Punto leer_punto(const json& par, const std::string& donde) {
  if (!par.is_array() || par.size() != 2) fallar(donde, "cada punto de la geometría es [latitud, longitud]");
  Punto p{numero(par[0], donde, "latitud"), numero(par[1], donde, "longitud")};
  if (p.lat < -90 || p.lat > 90 || p.lon < -180 || p.lon > 180) fallar(donde, "coordenadas fuera de rango");
  return p;
}

TipoVia leer_tipo_via(const std::string& valor, const std::string& donde) {
  if (valor == "via_rapida") return TipoVia::via_rapida;
  if (valor == "avenida_principal") return TipoVia::avenida_principal;
  if (valor == "zona_residencial") return TipoVia::zona_residencial;
  fallar(donde, "tipo_via desconocido: \"" + valor + "\"");
}

bool fecha_iso(const std::string& f) {
  if (f.size() != 10 || f[4] != '-' || f[7] != '-') return false;
  for (std::size_t i = 0; i < f.size(); ++i) {
    if (i != 4 && i != 7 && (f[i] < '0' || f[i] > '9')) return false;
  }
  return true;
}

}  // namespace

double distancia_m(const Punto& a, const Punto& b) {
  const double rad = kPi / 180.0;
  const double dlat = (b.lat - a.lat) * rad;
  const double dlon = (b.lon - a.lon) * rad;
  const double h = std::sin(dlat / 2) * std::sin(dlat / 2) +
                   std::cos(a.lat * rad) * std::cos(b.lat * rad) * std::sin(dlon / 2) * std::sin(dlon / 2);
  return 2 * kRadioTerrestreM * std::asin(std::min(1.0, std::sqrt(h)));
}

RedVial RedVial::desde_json(const json& documento) {
  if (!documento.is_object()) fallar("", "el archivo debe ser un objeto JSON");

  const auto it_version = documento.find("version");
  if (it_version == documento.end() || !it_version->is_number_integer()) {
    fallar("", "falta la \"version\" del formato o no es un entero");
  }
  if (it_version->get<long long>() != kVersionSoportada) {
    fallar("", "versión " + std::to_string(it_version->get<long long>()) + " del formato no soportada (se esperaba " +
                   std::to_string(kVersionSoportada) + ")");
  }

  RedVial red;
  red.fecha_extracto_ = texto(documento, "fecha_extracto", "");
  if (!fecha_iso(red.fecha_extracto_)) fallar("", "\"fecha_extracto\" debe ser AAAA-MM-DD");

  const json& nodos = campo(documento, "nodos", "");
  const json& tramos = campo(documento, "tramos", "");
  if (!nodos.is_array() || !tramos.is_array()) fallar("", "\"nodos\" y \"tramos\" deben ser listas");

  // --- nodos ---
  std::unordered_map<std::string, std::size_t> posicion_nodo;
  posicion_nodo.reserve(nodos.size());
  red.nodos_.reserve(nodos.size());
  for (std::size_t i = 0; i < nodos.size(); ++i) {
    const std::string donde = "nodos[" + std::to_string(i) + "]";
    const json& n = nodos[i];
    if (!n.is_object()) fallar(donde, "debe ser un objeto");
    Nodo nodo;
    nodo.id = texto(n, "id", donde);
    nodo.posicion.lat = numero(campo(n, "lat", donde), donde, "lat");
    nodo.posicion.lon = numero(campo(n, "lon", donde), donde, "lon");
    if (nodo.posicion.lat < -90 || nodo.posicion.lat > 90 || nodo.posicion.lon < -180 || nodo.posicion.lon > 180) {
      fallar(donde, "coordenadas fuera de rango");
    }
    if (!posicion_nodo.emplace(nodo.id, i).second) fallar(donde, "el id \"" + nodo.id + "\" está repetido");
    red.nodos_.push_back(std::move(nodo));
  }

  // --- tramos ---
  std::unordered_map<std::string, std::size_t> posicion_tramo;
  posicion_tramo.reserve(tramos.size());
  red.tramos_.reserve(tramos.size());
  std::vector<std::optional<std::string>> opuestos;  // se resuelven al final: pueden apuntar hacia adelante
  opuestos.reserve(tramos.size());
  for (std::size_t i = 0; i < tramos.size(); ++i) {
    const std::string donde = "tramos[" + std::to_string(i) + "]";
    const json& t = tramos[i];
    if (!t.is_object()) fallar(donde, "debe ser un objeto");
    Tramo tramo;
    tramo.id = texto(t, "id", donde);
    if (!posicion_tramo.emplace(tramo.id, i).second) fallar(donde, "el id \"" + tramo.id + "\" está repetido");

    for (const char* extremo : {"desde", "hasta"}) {
      const std::string id_nodo = texto(t, extremo, donde);
      const auto it = posicion_nodo.find(id_nodo);
      if (it == posicion_nodo.end()) fallar(donde, std::string("\"") + extremo + "\" apunta a un nodo que no existe: " + id_nodo);
      (extremo[0] == 'd' ? tramo.desde : tramo.hasta) = it->second;
    }

    const auto it_op = t.find("opuesto");
    if (it_op == t.end() || it_op->is_null()) {
      opuestos.emplace_back(std::nullopt);
    } else if (it_op->is_string()) {
      opuestos.emplace_back(it_op->get<std::string>());
    } else {
      fallar(donde, "\"opuesto\" debe ser un texto o null");
    }

    const auto it_nombre = t.find("nombre");
    tramo.nombre = (it_nombre != t.end() && it_nombre->is_string()) ? it_nombre->get<std::string>() : "Sin nombre";
    tramo.tipo_via = leer_tipo_via(texto(t, "tipo_via", donde), donde);
    tramo.longitud_m = numero(campo(t, "longitud_m", donde), donde, "longitud_m");
    if (tramo.longitud_m <= 0) fallar(donde, "\"longitud_m\" debe ser mayor que cero");

    const json& geometria = campo(t, "geometria", donde);
    if (!geometria.is_array() || geometria.size() < 2) fallar(donde, "\"geometria\" necesita al menos dos puntos");
    tramo.geometria.reserve(geometria.size());
    for (const json& par : geometria) tramo.geometria.push_back(leer_punto(par, donde));

    red.tramos_.push_back(std::move(tramo));
  }

  // --- opuestos: deben existir y ser recíprocos ---
  for (std::size_t i = 0; i < red.tramos_.size(); ++i) {
    if (!opuestos[i]) continue;
    const std::string donde = "tramos[" + std::to_string(i) + "]";
    const auto it = posicion_tramo.find(*opuestos[i]);
    if (it == posicion_tramo.end()) fallar(donde, "\"opuesto\" apunta a un tramo que no existe: " + *opuestos[i]);
    red.tramos_[i].opuesto = it->second;
  }
  for (std::size_t i = 0; i < red.tramos_.size(); ++i) {
    const auto& op = red.tramos_[i].opuesto;
    if (op && (*op == i || red.tramos_[*op].opuesto != i)) {
      fallar("tramos[" + std::to_string(i) + "]", "su \"opuesto\" no lo apunta de vuelta");
    }
  }

  red.salientes_.assign(red.nodos_.size(), {});
  for (std::size_t i = 0; i < red.tramos_.size(); ++i) red.salientes_[red.tramos_[i].desde].push_back(i);
  red.posicion_nodo_ = std::move(posicion_nodo);
  red.posicion_tramo_ = std::move(posicion_tramo);

  // La estimación del A* es la línea recta por este factor: así nunca supera el costo de ningún tramo.
  double factor = 1.0;
  for (const Tramo& t : red.tramos_) {
    const double recta = distancia_m(red.nodos_[t.desde].posicion, red.nodos_[t.hasta].posicion);
    if (recta > 0) factor = std::min(factor, t.longitud_m / recta);
  }
  red.factor_estimacion_ = factor * kMargenEstimacion;

  red.construir_indice();
  return red;
}

RedVial RedVial::cargar_archivo(const std::filesystem::path& ruta) {
  std::ifstream archivo(ruta, std::ios::binary);
  if (!archivo) throw ErrorRedVial("no se pudo abrir el archivo de la red vial: " + ruta.string());
  json documento;
  try {
    documento = json::parse(archivo);
  } catch (const json::parse_error& e) {
    throw ErrorRedVial(std::string("el archivo de la red vial no es JSON válido: ") + e.what());
  }
  return desde_json(documento);
}

// ------------------------------------------------------------------ índice espacial

void RedVial::construir_indice() {
  if (nodos_.empty()) return;
  double lat_min = 90, lat_max = -90, lon_min = 180, lon_max = -180;
  for (const Nodo& n : nodos_) {
    lat_min = std::min(lat_min, n.posicion.lat);
    lat_max = std::max(lat_max, n.posicion.lat);
    lon_min = std::min(lon_min, n.posicion.lon);
    lon_max = std::max(lon_max, n.posicion.lon);
  }
  lat0_ = (lat_min + lat_max) / 2;
  lon0_ = (lon_min + lon_max) / 2;
  metros_por_grado_lon_ = kMetrosPorGradoLat * std::cos(lat0_ * kPi / 180.0);

  grilla_.reserve(nodos_.size());
  for (std::size_t i = 0; i < nodos_.size(); ++i) {
    const auto [x, y] = proyectar(nodos_[i].posicion.lat, nodos_[i].posicion.lon);
    nodos_[i].x = x;
    nodos_[i].y = y;
    const auto cx = static_cast<std::int64_t>(std::floor(x / kRadioAjusteMetros));
    const auto cy = static_cast<std::int64_t>(std::floor(y / kRadioAjusteMetros));
    grilla_[clave_celda(cx, cy)].push_back(i);
  }

  // Cada tramo se anota en todas las celdas que cubre el rectángulo de cada uno de sus segmentos: así, un punto
  // a 100 m o menos de un tramo siempre encuentra ese tramo en su celda o en una vecina.
  for (std::size_t t = 0; t < tramos_.size(); ++t) {
    const auto& g = tramos_[t].geometria;
    for (std::size_t k = 0; k + 1 < g.size(); ++k) {
      const auto [x1, y1] = proyectar(g[k].lat, g[k].lon);
      const auto [x2, y2] = proyectar(g[k + 1].lat, g[k + 1].lon);
      const auto cx1 = static_cast<std::int64_t>(std::floor(std::min(x1, x2) / kRadioAjusteMetros));
      const auto cx2 = static_cast<std::int64_t>(std::floor(std::max(x1, x2) / kRadioAjusteMetros));
      const auto cy1 = static_cast<std::int64_t>(std::floor(std::min(y1, y2) / kRadioAjusteMetros));
      const auto cy2 = static_cast<std::int64_t>(std::floor(std::max(y1, y2) / kRadioAjusteMetros));
      for (std::int64_t cx = cx1; cx <= cx2; ++cx) {
        for (std::int64_t cy = cy1; cy <= cy2; ++cy) {
          auto& celda = grilla_tramos_[clave_celda(cx, cy)];
          if (celda.empty() || celda.back() != t) celda.push_back(t);
        }
      }
    }
  }
}

std::pair<double, double> RedVial::proyectar(double lat, double lon) const {
  return {(lon - lon0_) * metros_por_grado_lon_, (lat - lat0_) * kMetrosPorGradoLat};
}

std::int64_t RedVial::clave_celda(std::int64_t cx, std::int64_t cy) {
  return cx * 4000003 + cy;  // cabe de sobra: las celdas de una ciudad no llegan a miles
}

std::optional<std::size_t> RedVial::buscar_tramo(const std::string& id) const {
  const auto it = posicion_tramo_.find(id);
  if (it == posicion_tramo_.end()) return std::nullopt;
  return it->second;
}

std::optional<std::size_t> RedVial::buscar_nodo(const std::string& id) const {
  const auto it = posicion_nodo_.find(id);
  if (it == posicion_nodo_.end()) return std::nullopt;
  return it->second;
}

std::optional<Ajuste> RedVial::ajustar(double lat, double lon) const {
  if (nodos_.empty() || !std::isfinite(lat) || !std::isfinite(lon) || lat < -90 || lat > 90 || lon < -180 ||
      lon > 180) {
    return std::nullopt;
  }
  const auto [x, y] = proyectar(lat, lon);
  // Con celdas del mismo tamaño que el radio, los nodos a 100 m o menos están en la celda del punto o en una vecina.
  if (std::fabs(x) > 1e9 || std::fabs(y) > 1e9) return std::nullopt;
  const auto cx = static_cast<std::int64_t>(std::floor(x / kRadioAjusteMetros));
  const auto cy = static_cast<std::int64_t>(std::floor(y / kRadioAjusteMetros));

  std::optional<Ajuste> mejor;
  for (std::int64_t dx = -1; dx <= 1; ++dx) {
    for (std::int64_t dy = -1; dy <= 1; ++dy) {
      const auto it = grilla_.find(clave_celda(cx + dx, cy + dy));
      if (it == grilla_.end()) continue;
      for (const std::size_t i : it->second) {
        const double d = std::hypot(nodos_[i].x - x, nodos_[i].y - y);
        if (d > kRadioAjusteMetros) continue;
        if (!mejor || d < mejor->distancia_m || (d == mejor->distancia_m && i < mejor->nodo)) mejor = Ajuste{i, d};
      }
    }
  }
  return mejor;
}

std::optional<TramoCercano> RedVial::tramo_mas_cercano(double lat, double lon) const {
  if (tramos_.empty() || !std::isfinite(lat) || !std::isfinite(lon) || lat < -90 || lat > 90 || lon < -180 ||
      lon > 180) {
    return std::nullopt;
  }
  const auto [px, py] = proyectar(lat, lon);
  if (std::fabs(px) > 1e9 || std::fabs(py) > 1e9) return std::nullopt;
  const auto cx = static_cast<std::int64_t>(std::floor(px / kRadioAjusteMetros));
  const auto cy = static_cast<std::int64_t>(std::floor(py / kRadioAjusteMetros));

  std::optional<TramoCercano> mejor;
  for (std::int64_t dx = -1; dx <= 1; ++dx) {
    for (std::int64_t dy = -1; dy <= 1; ++dy) {
      const auto it = grilla_tramos_.find(clave_celda(cx + dx, cy + dy));
      if (it == grilla_tramos_.end()) continue;
      for (const std::size_t t : it->second) {
        if (mejor && mejor->tramo == t) continue;
        const auto& g = tramos_[t].geometria;
        double d = std::numeric_limits<double>::infinity();
        for (std::size_t k = 0; k + 1 < g.size(); ++k) {
          // Distancia del punto al segmento, en la proyección local (metros).
          const auto [x1, y1] = proyectar(g[k].lat, g[k].lon);
          const auto [x2, y2] = proyectar(g[k + 1].lat, g[k + 1].lon);
          const double vx = x2 - x1, vy = y2 - y1;
          const double largo2 = vx * vx + vy * vy;
          double u = largo2 > 0 ? ((px - x1) * vx + (py - y1) * vy) / largo2 : 0;
          u = std::clamp(u, 0.0, 1.0);
          d = std::min(d, std::hypot(px - (x1 + u * vx), py - (y1 + u * vy)));
        }
        if (d > kRadioAjusteMetros) continue;
        if (!mejor || d < mejor->distancia_m || (d == mejor->distancia_m && t < mejor->tramo)) mejor = TramoCercano{t, d};
      }
    }
  }
  return mejor;
}

std::optional<TramoCercano> RedVial::tramo_en_sentido(double lat, double lon, std::optional<double> rumbo,
                                                      double radio_m) const {
  if (tramos_.empty() || !std::isfinite(lat) || !std::isfinite(lon) || lat < -90 || lat > 90 || lon < -180 ||
      lon > 180) {
    return std::nullopt;
  }
  radio_m = std::min(radio_m, kRadioAjusteMetros);  // la grilla solo mira las celdas vecinas
  const auto [px, py] = proyectar(lat, lon);
  if (std::fabs(px) > 1e9 || std::fabs(py) > 1e9) return std::nullopt;
  const auto cx = static_cast<std::int64_t>(std::floor(px / kRadioAjusteMetros));
  const auto cy = static_cast<std::int64_t>(std::floor(py / kRadioAjusteMetros));

  // Diferencia entre dos rumbos, de 0 a 180 grados.
  auto diferencia = [](double a, double b) {
    const double d = std::fmod(std::fabs(a - b), 360.0);
    return d > 180.0 ? 360.0 - d : d;
  };

  struct Candidato {
    std::size_t tramo;
    double distancia;
    double rumbo_tramo;  // rumbo del segmento más cercano, en el sentido del tramo
  };
  std::optional<Candidato> mejor;
  std::vector<std::size_t> vistos;
  for (std::int64_t dx = -1; dx <= 1; ++dx) {
    for (std::int64_t dy = -1; dy <= 1; ++dy) {
      const auto it = grilla_tramos_.find(clave_celda(cx + dx, cy + dy));
      if (it == grilla_tramos_.end()) continue;
      for (const std::size_t t : it->second) {
        if (std::find(vistos.begin(), vistos.end(), t) != vistos.end()) continue;
        vistos.push_back(t);
        const auto& g = tramos_[t].geometria;
        double d = std::numeric_limits<double>::infinity();
        double rumbo_segmento = 0;
        for (std::size_t k = 0; k + 1 < g.size(); ++k) {
          const auto [x1, y1] = proyectar(g[k].lat, g[k].lon);
          const auto [x2, y2] = proyectar(g[k + 1].lat, g[k + 1].lon);
          const double vx = x2 - x1, vy = y2 - y1;
          const double largo2 = vx * vx + vy * vy;
          double u = largo2 > 0 ? ((px - x1) * vx + (py - y1) * vy) / largo2 : 0;
          u = std::clamp(u, 0.0, 1.0);
          const double dk = std::hypot(px - (x1 + u * vx), py - (y1 + u * vy));
          if (dk < d) {
            d = dk;
            // x crece hacia el este e y hacia el norte: atan2(este, norte) es el rumbo desde el norte.
            rumbo_segmento = std::fmod(std::atan2(vx, vy) * 180.0 / kPi + 360.0, 360.0);
          }
        }
        if (d > radio_m) continue;
        const Candidato c{t, d, rumbo_segmento};
        if (!mejor) {
          mejor = c;
          continue;
        }
        // Los dos sentidos de una calle están a la misma distancia: decide el rumbo del vehículo.
        const bool misma_distancia = std::fabs(d - mejor->distancia) < 1e-6;
        if (d < mejor->distancia - 1e-6) {
          mejor = c;
        } else if (misma_distancia) {
          if (rumbo) {
            const double dc = diferencia(*rumbo, c.rumbo_tramo);
            const double dm = diferencia(*rumbo, mejor->rumbo_tramo);
            if (dc < dm - 1e-9 || (std::fabs(dc - dm) <= 1e-9 && t < mejor->tramo)) mejor = c;
          } else if (t < mejor->tramo) {
            mejor = c;
          }
        }
      }
    }
  }
  if (!mejor) return std::nullopt;
  return TramoCercano{mejor->tramo, mejor->distancia};
}

std::shared_ptr<const RedVial> cargar_red_para_arranque(const std::filesystem::path& ruta, std::ostream& registro) {
  const auto inicio = std::chrono::steady_clock::now();
  try {
    auto red = std::make_shared<const RedVial>(RedVial::cargar_archivo(ruta));
    const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - inicio);
    registro << "Red vial cargada: " << red->nodos().size() << " nodos, " << red->tramos().size()
             << " tramos, extracto del " << red->fecha_extracto() << " (" << ms.count() << " ms)." << std::endl;
    return red;
  } catch (const std::exception& e) {
    registro << "No se cargó la red vial (el núcleo sigue sin red): " << e.what() << std::endl;
    return nullptr;
  }
}

}  // namespace churoviaje
