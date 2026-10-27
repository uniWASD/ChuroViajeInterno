// Modelo del motor de ruteo (CU-03 - Calcular Ruta Optima, HU-05).
//
// Port a C++ del prototipo Python (red_de_calles.py): red de calles dirigida +
// A* con multiplicadores de trafico por evento. Este modulo no imprime nada en
// pantalla; la consola vive en main.cpp.
#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace churoviaje {

enum class NivelTrafico { Fluido, Moderado, Congestionado, MuyCongestionado };

double multiplicador(NivelTrafico nivel);   // 1.0 / 1.5 / 2.5 / 4.0
const char* etiqueta(NivelTrafico nivel);   // "Fluido", "Moderado", ...

// Tipo de calle: penaliza las vías de menor jerarquía (el ruteo de Waze también prefiere
// las vías principales). TODAS las penalizaciones deben ser >= 1.0: así el costo de una
// calle nunca baja de su distancia y la heurística Haversine sigue siendo admisible.
// Valores iniciales, a ajustar con las pruebas.
enum class TipoCalle { SinClasificar, Circunvalacion, Avenida, Residencial };

double penalizacion(TipoCalle tipo);   // 1.0 / 1.0 / 1.1 / 1.3
const char* etiqueta(TipoCalle tipo);   // "sin clasificar", "circunvalación", ...

inline constexpr const char* MENSAJE_SIN_RUTA = "No se encontró una ruta válida";   // CU-03, ext. 3b
inline constexpr const char* MENSAJE_PUNTO_INVALIDO = "Origen o destino inválido";  // CU-03, ext. 1a

// Si `encontrada` es false, `ruta` queda vacia, `costo` no tiene valor y
// `mensaje` explica por que no se pudo calcular.
struct ResultadoRuta {
    bool encontrada = false;
    std::vector<std::string> ruta;
    std::optional<double> costo;
    std::string mensaje;
};

// Calle de un solo sentido hacia `destino` (indice de nodo). `distancia` en metros.
struct Calle {
    int destino;
    double distancia;
    std::string nombre;
    TipoCalle tipo = TipoCalle::SinClasificar;
};

struct NodoCercano {
    int indice;
    double distancia_m;
};

double haversine_metros(double lat1, double lon1, double lat2, double lon2);

class RedDeCalles {
public:
    // ---- Construccion de la red (misma API que el prototipo Python) --------
    void agregar_nodo(const std::string& nombre, double lat, double lon);

    // Calle de UN SOLO SENTIDO u -> v. `factor_curvatura` >= 1.0 (la calle es
    // mas larga que la linea recta); un valor menor rompe la admisibilidad de
    // la heuristica Haversine y se rechaza con std::invalid_argument.
    void agregar_calle(const std::string& u, const std::string& v,
                       const std::string& nombre_calle, double factor_curvatura = 1.0,
                       TipoCalle tipo = TipoCalle::SinClasificar);
    void agregar_calle_doble(const std::string& u, const std::string& v,
                             const std::string& nombre_calle, double factor_curvatura = 1.0,
                             TipoCalle tipo = TipoCalle::SinClasificar);

    // ---- Eventos de trafico -------------------------------------------------
    // Lanza std::invalid_argument si el tipo no existe o si la calle u -> v no existe.
    void reportar_evento(const std::string& u, const std::string& v, const std::string& tipo_evento);
    void avanzar_reloj(int minutos);
    std::size_t cantidad_eventos() const { return eventos_.size(); }
    int reloj() const { return reloj_; }

    // ---- Consultas por nombre ----------------------------------------------
    bool existe_calle(const std::string& u, const std::string& v) const;
    double costo_tramo(const std::string& u, const std::string& v) const;   // std::out_of_range si no existe
    double costo_ruta(const std::vector<std::string>& ruta) const;
    double heuristica(const std::string& a, const std::string& b) const;
    ResultadoRuta calcular_ruta(const std::string& origen, const std::string& destino) const;

    // ---- Acceso por indice (usado por la consola y las pruebas) -------------
    int num_nodos() const { return static_cast<int>(nombres_.size()); }
    const std::string& nombre(int i) const { return nombres_[i]; }
    double lat(int i) const { return lat_[i]; }
    double lon(int i) const { return lon_[i]; }
    std::optional<int> indice(const std::string& nombre) const;
    const std::vector<Calle>& calles_desde(int u) const { return calles_[u]; }
    const Calle* buscar_calle(int u, int v) const;
    NivelTrafico nivel_actual(int u, int v) const;
    double peso_efectivo(int u, const Calle& calle) const;
    double heuristica_idx(int a, int b) const;
    ResultadoRuta calcular_ruta_idx(int origen, int destino) const;

    // Nodo de la red mas cercano a una coordenada (nullopt si la red esta vacia).
    std::optional<NodoCercano> nodo_mas_cercano(double lat, double lon) const;

private:
    struct Evento {
        NivelTrafico nivel;
        int expira_en;
    };
    static std::uint64_t clave(int u, int v) {
        return (static_cast<std::uint64_t>(static_cast<std::uint32_t>(u)) << 32) |
               static_cast<std::uint32_t>(v);
    }
    int indice_o_error(const std::string& nombre) const;

    std::vector<std::string> nombres_;
    std::vector<double> lat_, lon_;
    std::vector<std::vector<Calle>> calles_;
    std::unordered_map<std::string, int> indice_por_nombre_;
    std::unordered_map<std::uint64_t, Evento> eventos_;
    int reloj_ = 0;
};

}  // namespace churoviaje
