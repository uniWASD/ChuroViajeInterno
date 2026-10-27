// Redes de calles de prueba para el motor de ruteo (CU-03).
#pragma once

#include <string>

#include "red_de_calles.hpp"

namespace churoviaje {

// Grafo IRREGULAR: 7 nodos sin alinear, calles de largo distinto, algunas de
// un solo sentido, un nodo casi aislado (Mirador) y calles curvas.
// Con `con_tipos_de_calle` cada calle recibe su tipo (residencial, avenida o
// circunvalación) según su nombre; por defecto no se clasifican y no penalizan.
RedDeCalles construir_red_irregular(bool con_tipos_de_calle = false);

// Red GRANDE (CP-13): cuadricula deformada de filas x columnas intersecciones
// alrededor de la Plaza, cuadras de ~100 m y ~20 % de calles de un solo
// sentido. Misma semilla => misma red que el prototipo Python.
RedDeCalles construir_red_grande(int filas = 100, int columnas = 100, unsigned semilla = 2026,
                                 double prob_un_sentido = 0.2, double curvatura_max = 1.4);

std::string nombre_interseccion(int fila, int columna);

}  // namespace churoviaje
