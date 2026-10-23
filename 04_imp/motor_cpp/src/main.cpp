// Punto de entrada del núcleo C++. Lo arranca el wrapper Java como proceso hijo (D-15):
//   entrada estándar  <- una petición JSON por línea
//   salida estándar   -> una respuesta JSON por línea (nada más)
//   salida de errores -> registros para el log del backend
#include <iostream>

#include "bucle.hpp"
#include "nucleo.hpp"

#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#endif

int main() {
#ifdef _WIN32
  // Sin esto, Windows convierte "\n" en "\r\n" en la salida y el wrapper recibiría líneas distintas.
  _setmode(_fileno(stdin), _O_BINARY);
  _setmode(_fileno(stdout), _O_BINARY);
#endif
  std::ios::sync_with_stdio(false);

  // La red vial se carga en el issue T-111; por ahora arranca sin red (consultar_estado lo informa).
  const churoviaje::Nucleo nucleo;
  return churoviaje::ejecutar(std::cin, std::cout, std::cerr, nucleo);
}
