#include "bucle.hpp"

#include <cstddef>
#include <istream>
#include <ostream>
#include <string>

namespace churoviaje {

int ejecutar(std::istream& entrada, std::ostream& salida, std::ostream& registro, const Nucleo& nucleo) {
  registro << "[nucleo] listo: esperando una petición JSON por línea en la entrada estándar\n" << std::flush;

  std::size_t lineas = 0;
  std::size_t respondidas = 0;
  std::string linea;
  while (std::getline(entrada, linea)) {
    ++lineas;
    const auto respuesta = nucleo.atender(linea);
    if (!respuesta) continue;  // línea en blanco

    // El registro nunca repite el contenido de la petición (puede traer posiciones de conductores).
    if (!respuesta->ok) {
      registro << "[nucleo] línea " << lineas << ": " << respuesta->codigo << '\n';
    }
    salida << respuesta->linea << '\n' << std::flush;  // el wrapper espera la línea completa ya mismo
    ++respondidas;
  }

  registro << "[nucleo] entrada cerrada tras " << respondidas << " respuestas; terminando\n" << std::flush;
  return 0;
}

}  // namespace churoviaje
