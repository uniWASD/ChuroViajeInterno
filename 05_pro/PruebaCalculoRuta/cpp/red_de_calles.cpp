#include "red_de_calles.hpp"

#include <cmath>
#include <functional>
#include <limits>
#include <queue>
#include <sstream>
#include <stdexcept>
#include <utility>

namespace churoviaje {

namespace {

constexpr double PI = 3.14159265358979323846;
constexpr double RADIO_TIERRA_M = 6'371'000.0;

double radianes(double grados) { return grados * (PI / 180.0); }

struct InfoEvento {
    const char* tipo;
    NivelTrafico nivel;
    int duracion_min;
};

// Eventos
constexpr InfoEvento EVENTOS[] = {
    {"congestion_leve", NivelTrafico::Moderado, 20},
    {"control_policial", NivelTrafico::Moderado, 30},
    {"congestion_fuerte", NivelTrafico::Congestionado, 25},
    {"choque", NivelTrafico::MuyCongestionado, 40},
};

const InfoEvento* buscar_evento(const std::string& tipo) {
    for (const InfoEvento& e : EVENTOS)
        if (tipo == e.tipo) return &e;
    return nullptr;
}

} 




double multiplicador(NivelTrafico nivel) {
    switch (nivel) {
        case NivelTrafico::Fluido: return 1.0;
        case NivelTrafico::Moderado: return 1.5;
        case NivelTrafico::Congestionado: return 2.5;
        case NivelTrafico::MuyCongestionado: return 4.0;
    }
    return 1.0;
}





const char* etiqueta(NivelTrafico nivel) {
    switch (nivel) {
        case NivelTrafico::Fluido: return "Fluido";
        case NivelTrafico::Moderado: return "Moderado";
        case NivelTrafico::Congestionado: return "Congestionado";
        case NivelTrafico::MuyCongestionado: return "Muy congestionado";
    }
    return "";
}





double penalizacion(TipoCalle tipo) {
    switch (tipo) {
        case TipoCalle::SinClasificar: return 1.0;
        case TipoCalle::Circunvalacion: return 1.0;
        case TipoCalle::Avenida: return 1.1;
        case TipoCalle::Residencial: return 1.3;
    }
    return 1.0;
}

const char* etiqueta(TipoCalle tipo) {
    switch (tipo) {
        case TipoCalle::SinClasificar: return "sin clasificar";
        case TipoCalle::Circunvalacion: return "circunvalación";
        case TipoCalle::Avenida: return "avenida";
        case TipoCalle::Residencial: return "residencial";
    }
    return "";
}

double haversine_metros(double lat1, double lon1, double lat2, double lon2) {
    const double d_lat = radianes(lat2 - lat1);
    const double d_lon = radianes(lon2 - lon1);
    const double a = std::pow(std::sin(d_lat / 2), 2) +
                     std::cos(radianes(lat1)) * std::cos(radianes(lat2)) *
                         std::pow(std::sin(d_lon / 2), 2);
    return RADIO_TIERRA_M * 2 * std::atan2(std::sqrt(a), std::sqrt(1 - a));
}




void RedDeCalles::agregar_nodo(const std::string& nombre, double lat, double lon) {
    auto it = indice_por_nombre_.find(nombre);
    if (it != indice_por_nombre_.end()) {
        lat_[it->second] = lat;
        lon_[it->second] = lon;
        return;
    }
    const int i = static_cast<int>(nombres_.size());
    indice_por_nombre_.emplace(nombre, i);
    nombres_.push_back(nombre);
    lat_.push_back(lat);
    lon_.push_back(lon);
    calles_.emplace_back();
}




int RedDeCalles::indice_o_error(const std::string& nombre) const {
    auto it = indice_por_nombre_.find(nombre);
    if (it == indice_por_nombre_.end())
        throw std::invalid_argument("No existe el nodo " + nombre);
    return it->second;
}




void RedDeCalles::agregar_calle(const std::string& u, const std::string& v,
                                const std::string& nombre_calle, double factor_curvatura,
                                TipoCalle tipo) {
    if (factor_curvatura < 1.0) {
        std::ostringstream msg;
        msg << "factor_curvatura debe ser >= 1.0 para que la heurística Haversine siga siendo "
               "admisible (recibido: "
            << factor_curvatura << ")";
        throw std::invalid_argument(msg.str());
    }
    const int iu = indice_o_error(u);
    const int iv = indice_o_error(v);
    const double distancia =
        haversine_metros(lat_[iu], lon_[iu], lat_[iv], lon_[iv]) * factor_curvatura;

    for (Calle& c : calles_[iu]) {
        if (c.destino == iv) {   // reemplaza la arista existente
            c.distancia = distancia;
            c.nombre = nombre_calle;
            c.tipo = tipo;
            return;
        }
    }
    calles_[iu].push_back(Calle{iv, distancia, nombre_calle, tipo});
}




void RedDeCalles::agregar_calle_doble(const std::string& u, const std::string& v,
                                      const std::string& nombre_calle, double factor_curvatura,
                                      TipoCalle tipo) {
    agregar_calle(u, v, nombre_calle, factor_curvatura, tipo);
    agregar_calle(v, u, nombre_calle, factor_curvatura, tipo);
}

// ---------------------------------------------------------------------------
// Eventos de trafico
// ---------------------------------------------------------------------------
void RedDeCalles::reportar_evento(const std::string& u, const std::string& v,
                                  const std::string& tipo_evento) {
    const InfoEvento* info = buscar_evento(tipo_evento);
    if (info == nullptr) {
        std::string validos;
        for (const InfoEvento& e : EVENTOS) {
            if (!validos.empty()) validos += ", ";
            validos += e.tipo;
        }
        throw std::invalid_argument("Tipo de evento desconocido: '" + tipo_evento +
                                    "'. Válidos: " + validos);
    }
    auto iu = indice(u);
    auto iv = indice(v);
    if (!iu || !iv || buscar_calle(*iu, *iv) == nullptr) {
        throw std::invalid_argument("No existe la calle " + u + " -> " + v +
                                    "; no se puede reportar un evento sobre ella "
                                    "(¿sentido contrario de una calle de un solo sentido?)");
    }
    eventos_[clave(*iu, *iv)] = Evento{info->nivel, reloj_ + info->duracion_min};
}

void RedDeCalles::avanzar_reloj(int minutos) {
    reloj_ += minutos;
    for (auto it = eventos_.begin(); it != eventos_.end();) {
        if (it->second.expira_en <= reloj_)
            it = eventos_.erase(it);   // vuelve a FLUIDO
        else
            ++it;
    }
}

NivelTrafico RedDeCalles::nivel_actual(int u, int v) const {
    if (eventos_.empty()) return NivelTrafico::Fluido;
    auto it = eventos_.find(clave(u, v));
    return it == eventos_.end() ? NivelTrafico::Fluido : it->second.nivel;
}

double RedDeCalles::peso_efectivo(int u, const Calle& calle) const {
    // peso = distancia x penalización por tipo de calle x multiplicador de tráfico
    return calle.distancia * penalizacion(calle.tipo) * multiplicador(nivel_actual(u, calle.destino));
}

// ---------------------------------------------------------------------------
// Consultas
// ---------------------------------------------------------------------------
std::optional<int> RedDeCalles::indice(const std::string& nombre) const {
    auto it = indice_por_nombre_.find(nombre);
    if (it == indice_por_nombre_.end()) return std::nullopt;
    return it->second;
}

const Calle* RedDeCalles::buscar_calle(int u, int v) const {
    for (const Calle& c : calles_[u])
        if (c.destino == v) return &c;
    return nullptr;
}

bool RedDeCalles::existe_calle(const std::string& u, const std::string& v) const {
    auto iu = indice(u);
    auto iv = indice(v);
    return iu && iv && buscar_calle(*iu, *iv) != nullptr;
}

double RedDeCalles::costo_tramo(const std::string& u, const std::string& v) const {
    auto iu = indice(u);
    auto iv = indice(v);
    const Calle* c = (iu && iv) ? buscar_calle(*iu, *iv) : nullptr;
    if (c == nullptr) throw std::out_of_range("No existe la calle " + u + " -> " + v);
    return peso_efectivo(*iu, *c);
}

double RedDeCalles::costo_ruta(const std::vector<std::string>& ruta) const {
    double total = 0.0;
    for (std::size_t i = 0; i + 1 < ruta.size(); ++i) total += costo_tramo(ruta[i], ruta[i + 1]);
    return total;
}

double RedDeCalles::heuristica_idx(int a, int b) const {
    return haversine_metros(lat_[a], lon_[a], lat_[b], lon_[b]);
}

double RedDeCalles::heuristica(const std::string& a, const std::string& b) const {
    return heuristica_idx(indice_o_error(a), indice_o_error(b));
}

std::optional<NodoCercano> RedDeCalles::nodo_mas_cercano(double lat, double lon) const {
    std::optional<NodoCercano> mejor;
    for (int i = 0; i < num_nodos(); ++i) {
        const double d = haversine_metros(lat, lon, lat_[i], lon_[i]);
        if (!mejor || d < mejor->distancia_m) mejor = NodoCercano{i, d};
    }
    return mejor;
}

// ---------------------------------------------------------------------------
// A*
// ---------------------------------------------------------------------------
ResultadoRuta RedDeCalles::calcular_ruta(const std::string& origen, const std::string& destino) const {
    auto io = indice(origen);
    auto id = indice(destino);
    if (!io || !id) {
        ResultadoRuta r;
        r.mensaje = MENSAJE_PUNTO_INVALIDO;
        return r;
    }
    return calcular_ruta_idx(*io, *id);
}

ResultadoRuta RedDeCalles::calcular_ruta_idx(int origen, int destino) const {
    const int n = num_nodos();
    if (origen < 0 || origen >= n || destino < 0 || destino >= n) {
        ResultadoRuta r;
        r.mensaje = MENSAJE_PUNTO_INVALIDO;
        return r;
    }

    constexpr double INF = std::numeric_limits<double>::infinity();
    std::vector<double> g(n, INF);
    std::vector<int> padre(n, -1);
    std::vector<char> cerrado(n, 0);

    using Entrada = std::pair<double, int>;   // (f = g + h, nodo)
    std::priority_queue<Entrada, std::vector<Entrada>, std::greater<Entrada>> cola;

    g[origen] = 0.0;
    cola.emplace(heuristica_idx(origen, destino), origen);

    while (!cola.empty()) {
        const int u = cola.top().second;
        cola.pop();
        if (u == destino) break;
        if (cerrado[u]) continue;   // entrada vieja de la cola
        cerrado[u] = 1;

        for (const Calle& c : calles_[u]) {
            const double nuevo_g = g[u] + peso_efectivo(u, c);
            if (nuevo_g < g[c.destino]) {
                g[c.destino] = nuevo_g;
                padre[c.destino] = u;
                cerrado[c.destino] = 0;   // reabre si se encontro un camino mejor
                cola.emplace(nuevo_g + heuristica_idx(c.destino, destino), c.destino);
            }
        }
    }

    ResultadoRuta r;
    if (g[destino] == INF) {
        r.mensaje = MENSAJE_SIN_RUTA;
        return r;
    }

    std::vector<int> camino;
    for (int v = destino; v != -1; v = padre[v]) camino.push_back(v);
    r.encontrada = true;
    double costo = 0.0;
    for (std::size_t k = camino.size(); k-- > 0;) {
        r.ruta.push_back(nombres_[camino[k]]);
        if (k > 0) costo += peso_efectivo(camino[k], *buscar_calle(camino[k], camino[k - 1]));
    }
    r.costo = costo;
    return r;
}

}  // namespace churoviaje
