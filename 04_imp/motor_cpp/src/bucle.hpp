// Bucle de mensajes: lee una petición por línea, escribe una respuesta por línea.
#pragma once

#include <iosfwd>

#include "nucleo.hpp"

namespace churoviaje {

// Atiende peticiones de `entrada` hasta que se cierre (fin de archivo).
//  - `salida`: SOLO respuestas, una por línea, vaciada después de cada una.
//  - `registro`: mensajes para el humano o para el log del backend (la salida de errores).
// Devuelve el código de salida del proceso: 0 si la entrada se cerró con normalidad.
int ejecutar(std::istream& entrada, std::ostream& salida, std::ostream& registro, const Nucleo& nucleo);

}  // namespace churoviaje
