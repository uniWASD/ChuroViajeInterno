#include "redes_de_prueba.hpp"

#include <utility>

#include "aleatorio_python.hpp"

namespace churoviaje {

RedDeCalles construir_red_irregular(bool con_tipos_de_calle) {
    RedDeCalles red;
    auto tipo = [con_tipos_de_calle](TipoCalle t) {
        return con_tipos_de_calle ? t : TipoCalle::SinClasificar;
    };

    // Posiciones a mano, NO en cuadricula (las mismas del prototipo Python).
    struct Nodo { const char* nombre; double lat, lon; };
    const Nodo nodos[] = {
        {"Plaza",       -21.5355, -64.7296},  // centro, punto de referencia
        {"Mercado",     -21.5348, -64.7280},  // cerca, al noreste
        {"Terminal",    -21.5410, -64.7350},  // lejos, al suroeste
        {"Hospital",    -21.5330, -64.7310},  // cerca, al norte
        {"Universidad", -21.5300, -64.7260},  // mas lejos, al noreste
        {"Barrio_Sur",  -21.5420, -64.7270},  // lejos, al sur
        {"Mirador",     -21.5290, -64.7220},  // el mas lejano, casi aislado
    };
    for (const Nodo& n : nodos) red.agregar_nodo(n.nombre, n.lat, n.lon);

    // Plaza <-> Mercado: calle corta y directa, doble sentido.
    red.agregar_calle_doble("Plaza", "Mercado", "Calle Comercio", 1.0, tipo(TipoCalle::Residencial));
    // Plaza <-> Hospital: doble sentido.
    red.agregar_calle_doble("Plaza", "Hospital", "Av. Circunvalación", 1.0, tipo(TipoCalle::Circunvalacion));
    // Mercado <-> Universidad: doble sentido.
    red.agregar_calle_doble("Mercado", "Universidad", "Av. Las Américas", 1.0, tipo(TipoCalle::Avenida));
    // Hospital -> Universidad: UN SOLO SENTIDO (calle de bajada).
    red.agregar_calle("Hospital", "Universidad", "Calle La Cuesta (bajada)", 1.0, tipo(TipoCalle::Residencial));
    // Universidad <-> Mirador: unica via de acceso, larga y con curvas.
    red.agregar_calle_doble("Universidad", "Mirador", "Camino al Mirador", 1.8, tipo(TipoCalle::Residencial));
    // Plaza <-> Terminal: avenida larga que no es recta.
    red.agregar_calle_doble("Plaza", "Terminal", "Av. Domingo Paredes", 1.2, tipo(TipoCalle::Avenida));
    // Terminal <-> Barrio_Sur: doble sentido.
    red.agregar_calle_doble("Terminal", "Barrio_Sur", "Calle del Sur", 1.0, tipo(TipoCalle::Residencial));
    // Plaza <-> Barrio_Sur: NO hay calle directa (hay que pasar por Terminal).
    // Mercado -> Terminal: atajo de un solo sentido.
    red.agregar_calle("Mercado", "Terminal", "Pasaje El Atajo", 1.0, tipo(TipoCalle::Residencial));

    return red;
}

namespace {
constexpr double LAT_CENTRO = -21.5355;      // Plaza principal
constexpr double LON_CENTRO = -64.7296;
constexpr double PASO_GRADOS = 0.0009;       // ~100 m entre intersecciones
constexpr double DESPLAZAMIENTO_MAX = 0.0002;  // ~20 m: las esquinas no quedan alineadas
}  // namespace

std::string nombre_interseccion(int fila, int columna) {
    return "I" + std::to_string(fila) + "_" + std::to_string(columna);
}

RedDeCalles construir_red_grande(int filas, int columnas, unsigned semilla,
                                 double prob_un_sentido, double curvatura_max) {
    AleatorioPython rnd(semilla);
    RedDeCalles red;

    // El orden de las llamadas a rnd es el mismo que en el prototipo Python.
    for (int f = 0; f < filas; ++f) {
        for (int c = 0; c < columnas; ++c) {
            const double lat = LAT_CENTRO + (f - filas / 2.0) * PASO_GRADOS +
                               rnd.uniform(-DESPLAZAMIENTO_MAX, DESPLAZAMIENTO_MAX);
            const double lon = LON_CENTRO + (c - columnas / 2.0) * PASO_GRADOS +
                               rnd.uniform(-DESPLAZAMIENTO_MAX, DESPLAZAMIENTO_MAX);
            red.agregar_nodo(nombre_interseccion(f, c), lat, lon);
        }
    }

    auto conectar = [&](std::string a, std::string b) {
        const double curvatura = rnd.uniform(1.0, curvatura_max);
        if (rnd.random() < prob_un_sentido) {
            if (rnd.random() < 0.5) std::swap(a, b);
            red.agregar_calle(a, b, a + "-" + b, curvatura);
        } else {
            red.agregar_calle_doble(a, b, a + "-" + b, curvatura);
        }
    };

    for (int f = 0; f < filas; ++f) {
        for (int c = 0; c < columnas; ++c) {
            if (c + 1 < columnas) conectar(nombre_interseccion(f, c), nombre_interseccion(f, c + 1));
            if (f + 1 < filas) conectar(nombre_interseccion(f, c), nombre_interseccion(f + 1, c));
        }
    }
    return red;
}

}  // namespace churoviaje
