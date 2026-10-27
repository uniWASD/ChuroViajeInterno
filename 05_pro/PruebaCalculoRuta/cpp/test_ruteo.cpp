// Pruebas del motor de ruteo en C++ - CU-03 Calcular Ruta Optima (HU-05).
//
// Mismos casos que tests/test_cu03_ruteo.py (CP-09 a CP-19), sin dependencias:
// compila a un ejecutable que termina con codigo 0 si todo pasa.
#include <algorithm>
#include <chrono>
#include <cmath>
#include <functional>
#include <iostream>
#include <limits>
#include <queue>
#include <random>
#include <stdexcept>
#include <string>
#include <vector>

#include "red_de_calles.hpp"
#include "redes_de_prueba.hpp"

using namespace churoviaje;
using Ruta = std::vector<std::string>;

namespace {

int g_fallos_caso = 0;

#define VERIFICAR(cond)                                                                 \
    do {                                                                                \
        if (!(cond)) {                                                                  \
            ++g_fallos_caso;                                                            \
            std::cerr << "    FALLA " << __FILE__ << ":" << __LINE__ << "  " #cond "\n"; \
        }                                                                               \
    } while (0)

// Verifica que `expr` lance std::invalid_argument cuyo mensaje contiene `fragmento`.
#define VERIFICAR_INVALIDO(expr, fragmento)                                                  \
    do {                                                                                     \
        bool lanzo = false;                                                                  \
        try {                                                                                \
            expr;                                                                            \
        } catch (const std::invalid_argument& e) {                                           \
            lanzo = std::string(e.what()).find(fragmento) != std::string::npos;              \
        }                                                                                    \
        VERIFICAR(lanzo);                                                                    \
    } while (0)

// Dijkstra de referencia (independiente de A*) con los mismos pesos efectivos.
std::vector<double> dijkstra(const RedDeCalles& red, int origen) {
    const double INF = std::numeric_limits<double>::infinity();
    std::vector<double> dist(red.num_nodos(), INF);
    using E = std::pair<double, int>;
    std::priority_queue<E, std::vector<E>, std::greater<E>> cola;
    dist[origen] = 0;
    cola.emplace(0.0, origen);
    while (!cola.empty()) {
        auto [d, u] = cola.top();
        cola.pop();
        if (d > dist[u]) continue;
        for (const Calle& c : red.calles_desde(u)) {
            const double nd = d + red.peso_efectivo(u, c);
            if (nd < dist[c.destino]) {
                dist[c.destino] = nd;
                cola.emplace(nd, c.destino);
            }
        }
    }
    return dist;
}

bool casi_igual(double a, double b) { return std::fabs(a - b) <= 1e-6 * std::max(1.0, std::fabs(b)); }

// ---------------------------------------------------------------------------
// Issue #15 (grafo irregular)
// ---------------------------------------------------------------------------
void cp14_ruta_sin_eventos_con_calles_de_largo_desigual() {
    RedDeCalles red = construir_red_irregular();
    ResultadoRuta r = red.calcular_ruta("Plaza", "Universidad");
    VERIFICAR(!red.existe_calle("Plaza", "Universidad"));
    VERIFICAR(r.encontrada);
    VERIFICAR((r.ruta == Ruta{"Plaza", "Mercado", "Universidad"}));
    VERIFICAR(*r.costo < red.costo_ruta({"Plaza", "Hospital", "Universidad"}));
}

void cp15_atajo_de_un_solo_sentido_solo_sirve_de_ida() {
    RedDeCalles red = construir_red_irregular();
    VERIFICAR(red.existe_calle("Mercado", "Terminal"));
    VERIFICAR(!red.existe_calle("Terminal", "Mercado"));
    VERIFICAR((red.calcular_ruta("Mercado", "Terminal").ruta == Ruta{"Mercado", "Terminal"}));
    VERIFICAR((red.calcular_ruta("Terminal", "Mercado").ruta == Ruta{"Terminal", "Plaza", "Mercado"}));
    const double atajo = red.costo_ruta({"Mercado", "Terminal"});
    const double por_plaza = red.costo_ruta({"Mercado", "Plaza", "Terminal"});
    VERIFICAR(por_plaza >= 1.10 * atajo);   // el atajo gana con margen claro (>= 10 %)
}

void cp16_nodo_casi_aislado_con_calle_curva() {
    RedDeCalles red = construir_red_irregular();
    ResultadoRuta r = red.calcular_ruta("Plaza", "Mirador");
    VERIFICAR((r.ruta == Ruta{"Plaza", "Mercado", "Universidad", "Mirador"}));
    VERIFICAR(casi_igual(red.costo_tramo("Universidad", "Mirador"),
                         1.8 * red.heuristica("Universidad", "Mirador")));
}

void cp17_sin_calle_directa_la_ruta_debe_rodear() {
    RedDeCalles red = construir_red_irregular();
    ResultadoRuta r = red.calcular_ruta("Plaza", "Barrio_Sur");
    VERIFICAR(!red.existe_calle("Plaza", "Barrio_Sur"));
    VERIFICAR((r.ruta == Ruta{"Plaza", "Terminal", "Barrio_Sur"}));
    VERIFICAR(*r.costo < red.costo_ruta({"Plaza", "Mercado", "Terminal", "Barrio_Sur"}));
}

void cp18_congestion_fuerte_obliga_a_desviarse() {
    RedDeCalles red = construir_red_irregular();
    VERIFICAR((red.calcular_ruta("Hospital", "Universidad").ruta == Ruta{"Hospital", "Universidad"}));

    red.reportar_evento("Hospital", "Universidad", "congestion_fuerte");
    ResultadoRuta r = red.calcular_ruta("Hospital", "Universidad");

    const Ruta rodeo{"Hospital", "Plaza", "Mercado", "Universidad"};
    VERIFICAR(r.ruta == rodeo);
    VERIFICAR(red.costo_ruta(rodeo) < red.costo_tramo("Hospital", "Universidad"));
}

void cp19_pasar_de_congestion_a_choque_no_cambia_una_ruta_optima() {
    RedDeCalles red = construir_red_irregular();
    red.reportar_evento("Hospital", "Universidad", "congestion_fuerte");
    const Ruta con_congestion = red.calcular_ruta("Hospital", "Universidad").ruta;
    red.reportar_evento("Hospital", "Universidad", "choque");
    const Ruta con_choque = red.calcular_ruta("Hospital", "Universidad").ruta;
    VERIFICAR(con_choque == con_congestion);
    VERIFICAR((con_choque == Ruta{"Hospital", "Plaza", "Mercado", "Universidad"}));
}

// ---------------------------------------------------------------------------
// Criterios generales, sobre todos los pares de nodos
// ---------------------------------------------------------------------------
void ninguna_ruta_recorre_una_calle_en_contramano() {
    RedDeCalles red = construir_red_irregular();
    for (int a = 0; a < red.num_nodos(); ++a)
        for (int b = 0; b < red.num_nodos(); ++b) {
            if (a == b) continue;
            ResultadoRuta r = red.calcular_ruta(red.nombre(a), red.nombre(b));
            for (std::size_t i = 0; i + 1 < r.ruta.size(); ++i)
                VERIFICAR(red.existe_calle(r.ruta[i], r.ruta[i + 1]));
        }
}

void la_heuristica_nunca_sobreestima_el_costo_real() {
    RedDeCalles red = construir_red_irregular();
    for (int a = 0; a < red.num_nodos(); ++a) {
        const std::vector<double> dist = dijkstra(red, a);
        for (int b = 0; b < red.num_nodos(); ++b)
            if (a != b && std::isfinite(dist[b])) VERIFICAR(red.heuristica_idx(a, b) <= dist[b]);
    }
}

void astar_coincide_con_dijkstra_en_todos_los_pares_con_y_sin_eventos() {
    for (int con_eventos = 0; con_eventos < 2; ++con_eventos) {
        RedDeCalles red = construir_red_irregular();
        if (con_eventos) {
            red.reportar_evento("Hospital", "Universidad", "choque");
            red.reportar_evento("Plaza", "Terminal", "congestion_fuerte");
            red.reportar_evento("Mercado", "Plaza", "control_policial");
        }
        for (int a = 0; a < red.num_nodos(); ++a) {
            const std::vector<double> dist = dijkstra(red, a);
            for (int b = 0; b < red.num_nodos(); ++b) {
                ResultadoRuta r = red.calcular_ruta_idx(a, b);
                VERIFICAR(r.encontrada == std::isfinite(dist[b]));
                if (r.encontrada) VERIFICAR(casi_igual(*r.costo, dist[b]));
            }
        }
    }
}

void astar_coincide_con_dijkstra_en_red_aleatoria() {
    RedDeCalles red = construir_red_grande(40, 40);
    std::mt19937 gen(7);
    std::uniform_int_distribution<int> nodo(0, red.num_nodos() - 1);
    for (int i = 0; i < 60; ++i) {
        const int a = nodo(gen), b = nodo(gen);
        const std::vector<double> dist = dijkstra(red, a);
        ResultadoRuta r = red.calcular_ruta_idx(a, b);
        VERIFICAR(r.encontrada == std::isfinite(dist[b]));
        if (r.encontrada) VERIFICAR(casi_igual(*r.costo, dist[b]));
    }
}

// ---------------------------------------------------------------------------
// Errores, validaciones y rendimiento
// ---------------------------------------------------------------------------
void cp09_sin_ruta_posible_se_informa_ruta_no_encontrada() {
    RedDeCalles red = construir_red_irregular();
    red.agregar_nodo("Aislado", -21.5500, -64.7400);
    ResultadoRuta r = red.calcular_ruta("Plaza", "Aislado");
    VERIFICAR(!r.encontrada);
    VERIFICAR(r.mensaje == MENSAJE_SIN_RUTA);
    VERIFICAR(r.ruta.empty());
    VERIFICAR(!r.costo.has_value());
}

void cp10_destino_u_origen_inexistente_se_informa_como_invalido() {
    RedDeCalles red = construir_red_irregular();
    for (const ResultadoRuta& r : {red.calcular_ruta("Plaza", "NoExiste"),
                                   red.calcular_ruta("NoExiste", "Plaza")}) {
        VERIFICAR(!r.encontrada);
        VERIFICAR(r.mensaje == MENSAJE_PUNTO_INVALIDO);
        VERIFICAR(r.ruta.empty());
    }
}

void cp11_se_rechaza_factor_curvatura_menor_a_uno() {
    RedDeCalles red = construir_red_irregular();
    VERIFICAR_INVALIDO(red.agregar_calle("Plaza", "Universidad", "Calle imposible", 0.5),
                       "factor_curvatura");
    VERIFICAR(!red.existe_calle("Plaza", "Universidad"));
}

void cp12_se_rechaza_evento_sobre_calle_en_contramano() {
    RedDeCalles red = construir_red_irregular();
    VERIFICAR_INVALIDO(red.reportar_evento("Universidad", "Hospital", "choque"), "No existe la calle");
    VERIFICAR(red.cantidad_eventos() == 0);
}

void se_rechaza_tipo_de_evento_desconocido() {
    RedDeCalles red = construir_red_irregular();
    VERIFICAR_INVALIDO(red.reportar_evento("Hospital", "Universidad", "inundacion"),
                       "Tipo de evento desconocido");
    VERIFICAR(red.cantidad_eventos() == 0);
}

void origen_igual_a_destino_devuelve_ruta_de_un_nodo() {
    RedDeCalles red = construir_red_irregular();
    ResultadoRuta r = red.calcular_ruta("Plaza", "Plaza");
    VERIFICAR(r.encontrada);
    VERIFICAR((r.ruta == Ruta{"Plaza"}));
    VERIFICAR(r.costo.has_value() && *r.costo == 0.0);
}

void el_evento_expira_con_el_reloj_y_la_ruta_vuelve_a_la_directa() {
    RedDeCalles red = construir_red_irregular();
    red.reportar_evento("Hospital", "Universidad", "choque");   // dura 40 min
    VERIFICAR(red.cantidad_eventos() == 1);
    VERIFICAR((red.calcular_ruta("Hospital", "Universidad").ruta.size() == 4));
    red.avanzar_reloj(39);
    VERIFICAR(red.cantidad_eventos() == 1);
    red.avanzar_reloj(1);
    VERIFICAR(red.cantidad_eventos() == 0);
    VERIFICAR((red.calcular_ruta("Hospital", "Universidad").ruta == Ruta{"Hospital", "Universidad"}));
}

void nodo_mas_cercano_asocia_coordenadas_a_la_red() {
    RedDeCalles red = construir_red_irregular();
    auto exacto = red.nodo_mas_cercano(-21.5355, -64.7296);
    VERIFICAR(exacto && red.nombre(exacto->indice) == "Plaza" && exacto->distancia_m < 1e-6);
    auto cerca = red.nodo_mas_cercano(-21.5347, -64.7279);   // a pocos metros del Mercado
    VERIFICAR(cerca && red.nombre(cerca->indice) == "Mercado" && cerca->distancia_m < 30.0);
}

void cp13_calculo_de_ruta_en_menos_de_3_segundos_en_red_grande() {
    constexpr int FILAS = 100, COLUMNAS = 100;   // 10 000 intersecciones (issue #29)
    constexpr double TIEMPO_MAXIMO_S = 3.0;
    RedDeCalles red = construir_red_grande(FILAS, COLUMNAS);
    VERIFICAR(red.num_nodos() == FILAS * COLUMNAS);

    std::mt19937 gen(13);
    std::uniform_int_distribution<int> f(0, FILAS - 1), c(0, COLUMNAS - 1);
    // Peor caso: de una esquina a la opuesta, mas consultas al azar.
    std::vector<std::pair<std::string, std::string>> consultas{
        {nombre_interseccion(0, 0), nombre_interseccion(FILAS - 1, COLUMNAS - 1)}};
    for (int i = 0; i < 20; ++i)
        consultas.emplace_back(nombre_interseccion(f(gen), c(gen)), nombre_interseccion(f(gen), c(gen)));

    for (const auto& [origen, destino] : consultas) {
        const auto inicio = std::chrono::steady_clock::now();
        red.calcular_ruta(origen, destino);
        const double s = std::chrono::duration<double>(std::chrono::steady_clock::now() - inicio).count();
        VERIFICAR(s < TIEMPO_MAXIMO_S);
    }
}


// ---------------------------------------------------------------------------
// Tipo de calle (residencial / avenida / circunvalacion)
// ---------------------------------------------------------------------------
void las_penalizaciones_por_tipo_nunca_bajan_de_uno() {
    // Condicion para que la heuristica Haversine siga siendo admisible.
    for (TipoCalle t : {TipoCalle::SinClasificar, TipoCalle::Circunvalacion, TipoCalle::Avenida,
                        TipoCalle::Residencial})
        VERIFICAR(penalizacion(t) >= 1.0);
}

void sin_clasificar_no_penaliza_la_red_por_defecto() {
    RedDeCalles red = construir_red_irregular();
    for (int u = 0; u < red.num_nodos(); ++u)
        for (const Calle& c : red.calles_desde(u)) {
            VERIFICAR(c.tipo == TipoCalle::SinClasificar);
            VERIFICAR(red.peso_efectivo(u, c) == c.distancia);
        }
}

void el_tipo_de_calle_encarece_el_costo_y_se_combina_con_el_trafico() {
    RedDeCalles red;
    red.agregar_nodo("A", -21.5355, -64.7296);
    red.agregar_nodo("B", -21.5348, -64.7280);
    red.agregar_calle("A", "B", "residencial", 1.0, TipoCalle::Residencial);
    red.agregar_calle("B", "A", "avenida", 1.0, TipoCalle::Avenida);
    const double d = haversine_metros(-21.5355, -64.7296, -21.5348, -64.7280);

    VERIFICAR(casi_igual(red.costo_tramo("A", "B"), 1.3 * d));
    VERIFICAR(casi_igual(red.costo_tramo("B", "A"), 1.1 * d));
    red.reportar_evento("A", "B", "congestion_fuerte");   // x2.5
    VERIFICAR(casi_igual(red.costo_tramo("A", "B"), 1.3 * 2.5 * d));
    VERIFICAR(casi_igual(red.costo_tramo("B", "A"), 1.1 * d));
}

void el_tipo_de_calle_puede_cambiar_la_ruta_elegida() {
    auto construir = [](TipoCalle tipo_directa) {
        RedDeCalles red;
        red.agregar_nodo("A", -21.5355, -64.7296);
        red.agregar_nodo("B", -21.5348, -64.7280);
        red.agregar_nodo("C", -21.5300, -64.7260);
        red.agregar_calle("A", "C", "directa", 1.0, tipo_directa);
        red.agregar_calle("A", "B", "ab", 1.0, TipoCalle::Circunvalacion);
        red.agregar_calle("B", "C", "bc", 1.0, TipoCalle::Circunvalacion);
        return red;
    };
    // Sin clasificar, la calle directa (mas corta) gana; si es residencial se rodea por B.
    VERIFICAR((construir(TipoCalle::SinClasificar).calcular_ruta("A", "C").ruta == Ruta{"A", "C"}));
    VERIFICAR((construir(TipoCalle::Residencial).calcular_ruta("A", "C").ruta == Ruta{"A", "B", "C"}));
}

void con_tipos_astar_coincide_con_dijkstra_y_la_heuristica_es_admisible() {
    for (int con_eventos = 0; con_eventos < 2; ++con_eventos) {
        RedDeCalles red = construir_red_irregular(true);
        if (con_eventos) {
            red.reportar_evento("Hospital", "Universidad", "choque");
            red.reportar_evento("Plaza", "Terminal", "congestion_fuerte");
        }
        for (int a = 0; a < red.num_nodos(); ++a) {
            const std::vector<double> dist = dijkstra(red, a);
            for (int b = 0; b < red.num_nodos(); ++b) {
                ResultadoRuta r = red.calcular_ruta_idx(a, b);
                VERIFICAR(r.encontrada == std::isfinite(dist[b]));
                if (r.encontrada) VERIFICAR(casi_igual(*r.costo, dist[b]));
                if (a != b && std::isfinite(dist[b])) VERIFICAR(red.heuristica_idx(a, b) <= dist[b]);
            }
        }
    }
}

void la_red_irregular_con_tipos_encarece_las_calles_residenciales() {
    RedDeCalles base = construir_red_irregular();
    RedDeCalles tipos = construir_red_irregular(true);
    VERIFICAR(casi_igual(tipos.costo_tramo("Plaza", "Mercado"), 1.3 * base.costo_tramo("Plaza", "Mercado")));
    VERIFICAR(casi_igual(tipos.costo_tramo("Mercado", "Universidad"), 1.1 * base.costo_tramo("Mercado", "Universidad")));
    VERIFICAR(casi_igual(tipos.costo_tramo("Plaza", "Hospital"), base.costo_tramo("Plaza", "Hospital")));
}

struct Caso {
    const char* nombre;
    void (*funcion)();
};

}  // namespace

int main() {
    const Caso casos[] = {
        {"CP-14 ruta sin eventos con calles de largo desigual", cp14_ruta_sin_eventos_con_calles_de_largo_desigual},
        {"CP-15 atajo de un solo sentido solo sirve de ida", cp15_atajo_de_un_solo_sentido_solo_sirve_de_ida},
        {"CP-16 nodo casi aislado con calle curva", cp16_nodo_casi_aislado_con_calle_curva},
        {"CP-17 sin calle directa la ruta debe rodear", cp17_sin_calle_directa_la_ruta_debe_rodear},
        {"CP-18 congestion fuerte obliga a desviarse", cp18_congestion_fuerte_obliga_a_desviarse},
        {"CP-19 congestion -> choque no cambia una ruta optima", cp19_pasar_de_congestion_a_choque_no_cambia_una_ruta_optima},
        {"ninguna ruta recorre una calle en contramano", ninguna_ruta_recorre_una_calle_en_contramano},
        {"la heuristica nunca sobreestima el costo real", la_heuristica_nunca_sobreestima_el_costo_real},
        {"A* == Dijkstra en todos los pares (con y sin eventos)", astar_coincide_con_dijkstra_en_todos_los_pares_con_y_sin_eventos},
        {"A* == Dijkstra en red aleatoria 40x40", astar_coincide_con_dijkstra_en_red_aleatoria},
        {"CP-09 sin ruta posible", cp09_sin_ruta_posible_se_informa_ruta_no_encontrada},
        {"CP-10 origen o destino inexistente", cp10_destino_u_origen_inexistente_se_informa_como_invalido},
        {"CP-11 factor de curvatura < 1 rechazado", cp11_se_rechaza_factor_curvatura_menor_a_uno},
        {"CP-12 evento sobre calle en contramano rechazado", cp12_se_rechaza_evento_sobre_calle_en_contramano},
        {"tipo de evento desconocido rechazado", se_rechaza_tipo_de_evento_desconocido},
        {"origen == destino", origen_igual_a_destino_devuelve_ruta_de_un_nodo},
        {"el evento expira con el reloj", el_evento_expira_con_el_reloj_y_la_ruta_vuelve_a_la_directa},
        {"nodo mas cercano a una coordenada", nodo_mas_cercano_asocia_coordenadas_a_la_red},
        {"penalizaciones por tipo >= 1.0 (heuristica admisible)", las_penalizaciones_por_tipo_nunca_bajan_de_uno},
        {"sin clasificar no penaliza (red por defecto)", sin_clasificar_no_penaliza_la_red_por_defecto},
        {"tipo de calle encarece y se combina con el trafico", el_tipo_de_calle_encarece_el_costo_y_se_combina_con_el_trafico},
        {"tipo de calle puede cambiar la ruta elegida", el_tipo_de_calle_puede_cambiar_la_ruta_elegida},
        {"con tipos: A* == Dijkstra y heuristica admisible", con_tipos_astar_coincide_con_dijkstra_y_la_heuristica_es_admisible},
        {"red irregular con tipos de calle", la_red_irregular_con_tipos_encarece_las_calles_residenciales},
        {"CP-13 < 3 s en red de 100x100", cp13_calculo_de_ruta_en_menos_de_3_segundos_en_red_grande},
    };

    int fallidos = 0;
    for (const Caso& caso : casos) {
        g_fallos_caso = 0;
        try {
            caso.funcion();
        } catch (const std::exception& e) {
            ++g_fallos_caso;
            std::cerr << "    EXCEPCION: " << e.what() << "\n";
        }
        std::cout << (g_fallos_caso == 0 ? "[ OK ] " : "[FALLA] ") << caso.nombre << "\n";
        if (g_fallos_caso != 0) ++fallidos;
    }
    const int total = static_cast<int>(sizeof(casos) / sizeof(casos[0]));
    std::cout << "\n" << (total - fallidos) << "/" << total << " pruebas pasaron\n";
    return fallidos == 0 ? 0 : 1;
}
