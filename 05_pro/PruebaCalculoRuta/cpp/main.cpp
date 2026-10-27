// Consola del motor de ruteo (CU-03): recibe coordenadas de origen y destino
// y devuelve por pantalla la mejor ruta (A* con multiplicadores de tráfico).
//
//   ruteo                                  modo interactivo
//   ruteo lat_origen lon_origen lat_destino lon_destino
//   ruteo --nodos                          lista los nodos y sus coordenadas
//
// Opciones: --red irregular|grande, --evento ORIGEN DESTINO TIPO (repetible).
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <functional>
#include <iomanip>
#include <iostream>
#include <set>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "red_de_calles.hpp"
#include "redes_de_prueba.hpp"

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#endif

using namespace churoviaje;

namespace {

// Un punto más lejos que esto de cualquier nodo se considera fuera de la red
// (CU-03, extension 1a: origen o destino invalido).
constexpr double MAX_DISTANCIA_A_NODO_M = 1000.0;

constexpr int SALIDA_OK = 0;
constexpr int SALIDA_SIN_RUTA = 1;
constexpr int SALIDA_ENTRADA_INVALIDA = 2;

// Las matrices de eventos y de costos se imprimen para redes pequeñas; en la red
// grande (10 000 nodos) serían ilegibles, así que se omiten.
constexpr int MAX_NODOS_PARA_MATRICES = 30;
bool g_matrices_activadas = true;   // se desactiva con --sin-matrices
bool g_con_tipos = false;           // se activa con --tipos

const char* const AYUDA =
    "Motor de ruteo ChuroViaje (CU-03) - prueba de concepto en C++\n"
    "\n"
    "Uso:\n"
    "  ruteo                                   modo interactivo (pide las coordenadas)\n"
    "  ruteo LAT_O LON_O LAT_D LON_D           calcula una ruta y termina\n"
    "  ruteo \"LAT_O,LON_O\" \"LAT_D,LON_D\"       idem, un par por argumento\n"
    "  ruteo --nodos                           lista los nodos de la red y sus coordenadas\n"
    "\n"
    "Opciones:\n"
    "  --red irregular|grande                  red a usar (por defecto: irregular)\n"
    "  --evento ORIGEN DESTINO TIPO            reporta un evento de tráfico sobre la calle\n"
    "                                          ORIGEN -> DESTINO (se puede repetir). Tipos:\n"
    "                                          congestion_leve, control_policial,\n"
    "                                          congestion_fuerte, choque\n"
    "  --tipos                                 clasifica las calles (residencial, avenida,\n"
    "                                          circunvalación) y penaliza su costo (solo red irregular)\n"
    "  --sin-matrices                          no imprime las matrices de eventos y de costos\n"
    "  -h, --ayuda                             muestra esta ayuda\n"
    "\n"
    "Las coordenadas son latitud y longitud en grados decimales (punto o coma decimal).\n"
    "Cada punto se asocia al nodo de la red más cercano.\n"
    "Códigos de salida: 0 ruta encontrada, 1 sin ruta, 2 entrada inválida.\n"
    "Ejemplo:  ruteo -21.5355 -64.7296 -21.5300 -64.7260\n";

// ---- Lectura de coordenadas -----------------------------------------------
bool parse_numero(const std::string& texto, double& valor) {
    if (texto.empty()) return false;
    char* fin = nullptr;
    valor = std::strtod(texto.c_str(), &fin);
    return fin == texto.c_str() + texto.size() && std::isfinite(valor);
}

// Acepta "lat lon", "lat, lon", "lat;lon" y coma decimal ("-21,5355 -64,7296").
bool parse_par(std::string linea, double& lat, double& lon) {
    for (char& c : linea)
        if (c == ';') c = ' ';
    const bool hay_punto = linea.find('.') != std::string::npos;
    for (char& c : linea)
        if (c == ',') c = hay_punto ? ' ' : '.';

    std::istringstream in(linea);
    std::string a, b, resto;
    if (!(in >> a >> b) || (in >> resto)) return false;
    return parse_numero(a, lat) && parse_numero(b, lon);
}

bool coordenada_valida(double lat, double lon) {
    return lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
}

// ---- Salida ---------------------------------------------------------------
void listar_nodos(const RedDeCalles& red) {
    std::cout << red.num_nodos() << " nodos en la red:\n";
    std::cout << std::fixed << std::setprecision(6);
    for (int i = 0; i < red.num_nodos(); ++i)
        std::cout << "  " << red.nombre(i) << "  (" << red.lat(i) << ", " << red.lon(i) << ")\n";
}

void mostrar_ruta(const RedDeCalles& red, const ResultadoRuta& r) {
    std::cout << "\nMejor ruta: ";
    for (std::size_t i = 0; i < r.ruta.size(); ++i) std::cout << (i ? " -> " : "") << r.ruta[i];
    std::cout << "\n\n";

    double distancia_real = 0.0;
    for (std::size_t i = 0; i < r.ruta.size(); ++i) {
        const int u = *red.indice(r.ruta[i]);
        std::cout << std::fixed << std::setprecision(6) << "  " << (i + 1) << ". " << r.ruta[i]
                  << "  (" << red.lat(u) << ", " << red.lon(u) << ")\n";
        if (i + 1 < r.ruta.size()) {
            const int v = *red.indice(r.ruta[i + 1]);
            const Calle* calle = red.buscar_calle(u, v);
            const NivelTrafico nivel = red.nivel_actual(u, v);
            distancia_real += calle->distancia;
            std::cout << std::setprecision(0) << "       | " << calle->nombre << ", "
                      << calle->distancia << " m";
            if (calle->tipo != TipoCalle::SinClasificar)
                std::cout << std::setprecision(1) << "  [" << etiqueta(calle->tipo) << " x"
                          << penalizacion(calle->tipo) << "]";
            if (nivel != NivelTrafico::Fluido)
                std::cout << std::setprecision(1) << "  [tráfico " << etiqueta(nivel) << " x"
                          << multiplicador(nivel) << "]";
            std::cout << "\n";
        }
    }

    std::cout << std::fixed << std::setprecision(0) << "\nNodos recorridos : " << r.ruta.size()
              << "\nDistancia real   : " << distancia_real << " m"
              << "\nCosto total      : " << *r.costo << " m-equivalentes\n";
}

// Resuelve un punto (lat, lon) al nodo mas cercano; informa si esta fuera de la red.
bool resolver_punto(const RedDeCalles& red, const char* etiqueta_punto, double lat, double lon,
                    int& nodo) {
    std::cout << std::fixed;
    if (!coordenada_valida(lat, lon)) {
        std::cout << MENSAJE_PUNTO_INVALIDO << ": " << etiqueta_punto
                  << " fuera de rango (latitud -90..90, longitud -180..180).\n";
        return false;
    }
    const auto cercano = red.nodo_mas_cercano(lat, lon);
    if (!cercano || cercano->distancia_m > MAX_DISTANCIA_A_NODO_M) {
        std::cout << MENSAJE_PUNTO_INVALIDO << ": " << etiqueta_punto << " está a "
                  << std::setprecision(0) << (cercano ? cercano->distancia_m : 0.0)
                  << " m del nodo más cercano (máximo " << MAX_DISTANCIA_A_NODO_M << " m).\n";
        return false;
    }
    nodo = cercano->indice;
    std::cout << std::left << std::setw(9) << (std::string(etiqueta_punto) + ":") << std::right << "(" << std::setprecision(6) << lat << ", " << lon
              << ") -> nodo " << red.nombre(nodo) << " (a " << std::setprecision(0)
              << cercano->distancia_m << " m)\n";
    return true;
}

// Nombre corto del nivel de tráfico para que quepa en una celda (sin tildes: así
// las columnas quedan alineadas en cualquier consola).
const char* etiqueta_corta(TipoCalle tipo) {
    switch (tipo) {
        case TipoCalle::SinClasificar: return "Sin clasif.";
        case TipoCalle::Circunvalacion: return "Circunval.";
        case TipoCalle::Avenida: return "Avenida";
        case TipoCalle::Residencial: return "Residencial";
    }
    return "";
}

const char* etiqueta_corta(NivelTrafico nivel) {
    switch (nivel) {
        case NivelTrafico::Fluido: return "Fluido";
        case NivelTrafico::Moderado: return "Moderado";
        case NivelTrafico::Congestionado: return "Congest.";
        case NivelTrafico::MuyCongestionado: return "Muy cong.";
    }
    return "";
}

// Imprime una matriz de todo el grafo: fila = nodo del que sale la calle,
// columna = nodo al que llega. `celda(u, v)` devuelve el texto de cada casilla.
void imprimir_matriz(const RedDeCalles& red, const std::string& titulo,
                     const std::function<std::string(int, int)>& celda) {
    int ancho = 12;
    for (int i = 0; i < red.num_nodos(); ++i)
        ancho = std::max(ancho, static_cast<int>(red.nombre(i).size()) + 2);

    std::cout << "\n" << titulo << "\n" << std::setw(ancho) << "";
    for (int j = 0; j < red.num_nodos(); ++j) std::cout << std::setw(ancho) << red.nombre(j);
    std::cout << "\n";
    for (int i = 0; i < red.num_nodos(); ++i) {
        std::cout << std::left << std::setw(ancho) << red.nombre(i) << std::right;
        for (int j = 0; j < red.num_nodos(); ++j) std::cout << std::setw(ancho) << celda(i, j);
        std::cout << "\n";
    }
}

void mostrar_matrices(const RedDeCalles& red, const ResultadoRuta& r) {
    if (!g_matrices_activadas) return;
    if (red.num_nodos() > MAX_NODOS_PARA_MATRICES) {
        std::cout << "\n(Matrices omitidas: la red tiene " << red.num_nodos()
                  << " nodos; se imprimen solo con " << MAX_NODOS_PARA_MATRICES
                  << " o menos.)\n";
        return;
    }

    // Tramos (u, v) que usa la mejor ruta, para marcarlos con * en la matriz de costos.
    std::set<std::pair<int, int>> usados;
    for (std::size_t i = 0; i + 1 < r.ruta.size(); ++i)
        usados.insert({*red.indice(r.ruta[i]), *red.indice(r.ruta[i + 1])});

    imprimir_matriz(red, "Matriz de eventos (nivel de tráfico de cada calle fila -> columna):",
                    [&](int u, int v) {
                        if (red.buscar_calle(u, v) == nullptr) return std::string("-");
                        return std::string(etiqueta_corta(red.nivel_actual(u, v)));
                    });
    std::cout << "  - = no existe esa calle\n";

    if (g_con_tipos) {
        imprimir_matriz(red, "Matriz de tipos de calle (penalización de costo: circunvalación x1.0, avenida x1.1, residencial x1.3):",
                        [&](int u, int v) {
                            const Calle* calle = red.buscar_calle(u, v);
                            return calle == nullptr ? std::string("-") : std::string(etiqueta_corta(calle->tipo));
                        });
        std::cout << "  - = no existe esa calle\n";
    }

    imprimir_matriz(red, "Matriz de costos (metros-equivalentes = distancia x tipo de calle x multiplicador de tráfico):",
                    [&](int u, int v) {
                        const Calle* calle = red.buscar_calle(u, v);
                        if (calle == nullptr) return std::string("-");
                        std::ostringstream texto;
                        texto << std::fixed << std::setprecision(0) << red.peso_efectivo(u, *calle)
                              << (usados.count({u, v}) ? "*" : " ");
                        return texto.str();
                    });
    std::cout << "  - = no existe esa calle     * = tramo usado en la mejor ruta\n";
}

int calcular_y_mostrar(const RedDeCalles& red, double lat_o, double lon_o, double lat_d,
                       double lon_d) {
    int origen = 0, destino = 0;
    const bool ok_o = resolver_punto(red, "Origen", lat_o, lon_o, origen);
    const bool ok_d = resolver_punto(red, "Destino", lat_d, lon_d, destino);
    if (!ok_o || !ok_d) return SALIDA_ENTRADA_INVALIDA;

    const ResultadoRuta r = red.calcular_ruta_idx(origen, destino);
    if (!r.encontrada) {
        std::cout << "\n" << r.mensaje << "\n";
        mostrar_matrices(red, r);
        return SALIDA_SIN_RUTA;
    }
    mostrar_ruta(red, r);
    mostrar_matrices(red, r);
    return SALIDA_OK;
}

// ---- Modo interactivo -----------------------------------------------------
bool pedir_punto(const char* texto, double& lat, double& lon) {
    std::string linea;
    while (true) {
        std::cout << texto << " (lat lon, Enter para salir): " << std::flush;
        if (!std::getline(std::cin, linea)) return false;
        if (linea.find_first_not_of(" \t\r") == std::string::npos) return false;
        if (parse_par(linea, lat, lon)) return true;
        std::cout << "  Entrada no válida. Escribe dos números, por ejemplo: -21.5355 -64.7296\n";
    }
}

int modo_interactivo(const RedDeCalles& red) {
    std::cout << "Motor de ruteo ChuroViaje: red con " << red.num_nodos() << " nodos.\n"
              << "Escribe coordenadas en grados decimales. Usa --nodos para ver las de la red.\n\n";
    int ultimo = SALIDA_OK;
    double lat_o, lon_o, lat_d, lon_d;
    while (pedir_punto("Origen", lat_o, lon_o) && pedir_punto("Destino", lat_d, lon_d)) {
        ultimo = calcular_y_mostrar(red, lat_o, lon_o, lat_d, lon_d);
        std::cout << "\n";
    }
    return ultimo;
}

}  // namespace

int main(int argc, char** argv) {
#ifdef _WIN32
    SetConsoleOutputCP(CP_UTF8);   // para mostrar tildes en la consola de Windows
#endif

    struct EventoPedido { std::string u, v, tipo; };
    std::vector<EventoPedido> eventos;
    std::vector<std::string> posicionales;
    std::string nombre_red = "irregular";
    bool solo_nodos = false;

    for (int i = 1; i < argc; ++i) {
        const std::string a = argv[i];
        if (a == "-h" || a == "--ayuda" || a == "--help") {
            std::cout << AYUDA;
            return SALIDA_OK;
        } else if (a == "--nodos") {
            solo_nodos = true;
        } else if (a == "--sin-matrices") {
            g_matrices_activadas = false;
        } else if (a == "--tipos") {
            g_con_tipos = true;
        } else if (a == "--red" && i + 1 < argc) {
            nombre_red = argv[++i];
        } else if (a == "--evento" && i + 3 < argc) {
            eventos.push_back({argv[i + 1], argv[i + 2], argv[i + 3]});
            i += 3;
        } else if (a.rfind("--", 0) == 0) {
            std::cerr << "Opción desconocida o incompleta: " << a << "\n\n" << AYUDA;
            return SALIDA_ENTRADA_INVALIDA;
        } else {
            posicionales.push_back(a);
        }
    }

    RedDeCalles red;
    if (nombre_red == "irregular") {
        red = construir_red_irregular(g_con_tipos);
    } else if (nombre_red == "grande") {
        if (g_con_tipos) {
            std::cerr << "--tipos solo está disponible para la red irregular.\n";
            return SALIDA_ENTRADA_INVALIDA;
        }
        red = construir_red_grande();
    } else {
        std::cerr << "Red desconocida: " << nombre_red << " (usa irregular o grande)\n";
        return SALIDA_ENTRADA_INVALIDA;
    }

    for (const EventoPedido& e : eventos) {
        try {
            red.reportar_evento(e.u, e.v, e.tipo);
        } catch (const std::invalid_argument& ex) {
            std::cerr << "Evento inválido: " << ex.what() << "\n";
            return SALIDA_ENTRADA_INVALIDA;
        }
    }

    if (solo_nodos) {
        listar_nodos(red);
        return SALIDA_OK;
    }

    if (posicionales.empty()) return modo_interactivo(red);

    double lat_o, lon_o, lat_d, lon_d;
    bool ok = false;
    if (posicionales.size() == 4) {
        ok = parse_par(posicionales[0] + " " + posicionales[1], lat_o, lon_o) &&
             parse_par(posicionales[2] + " " + posicionales[3], lat_d, lon_d);
    } else if (posicionales.size() == 2) {
        ok = parse_par(posicionales[0], lat_o, lon_o) && parse_par(posicionales[1], lat_d, lon_d);
    }
    if (!ok) {
        std::cerr << "Coordenadas inválidas. Se esperan 4 números: LAT_O LON_O LAT_D LON_D\n\n"
                  << AYUDA;
        return SALIDA_ENTRADA_INVALIDA;
    }
    return calcular_y_mostrar(red, lat_o, lon_o, lat_d, lon_d);
}
