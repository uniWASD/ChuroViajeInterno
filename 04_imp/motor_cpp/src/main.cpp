// Punto de entrada del núcleo C++. Lo arranca el wrapper Java como proceso hijo (D-15):
//   entrada estándar  <- una petición JSON por línea
//   salida estándar   -> una respuesta JSON por línea (nada más)
//   salida de errores -> registros para el log del backend
#include <iostream>
#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include "bucle.hpp"
#include "nucleo.hpp"
#include "red_vial.hpp"

#ifdef _WIN32
#include <fcntl.h>
#include <io.h>
#endif

namespace {

// Uso: motor_nucleo [--red <archivo>]. Sin --red el núcleo arranca sin red vial (consultar_estado lo informa).
// Devuelve false si los argumentos no se entienden.
bool leer_argumentos(int argc, char** argv, std::string& ruta_red) {
  for (int i = 1; i < argc; ++i) {
    const std::string_view arg = argv[i];
    if (arg == "--red") {
      if (i + 1 >= argc) {
        std::cerr << "Falta la ruta después de --red." << std::endl;
        return false;
      }
      ruta_red = argv[++i];
    } else if (arg.rfind("--red=", 0) == 0) {
      ruta_red = std::string(arg.substr(6));
    } else {
      std::cerr << "Argumento desconocido: " << arg << ". Uso: motor_nucleo [--red <archivo>]" << std::endl;
      return false;
    }
  }
  return true;
}

}  // namespace

int main(int argc, char** argv) {
#ifdef _WIN32
  // Sin esto, Windows convierte "\n" en "\r\n" en la salida y el wrapper recibiría líneas distintas.
  _setmode(_fileno(stdin), _O_BINARY);
  _setmode(_fileno(stdout), _O_BINARY);
#endif
  std::ios::sync_with_stdio(false);

  std::string ruta_red;
  if (!leer_argumentos(argc, argv, ruta_red)) return 2;

  // Si la red no se puede cargar, el núcleo arranca igual sin red: lo anota en stderr (log del backend) y
  // consultar_estado responde "red_cargada":false, así el wrapper no lo da por listo.
  std::shared_ptr<const churoviaje::RedVial> red;
  if (!ruta_red.empty()) red = churoviaje::cargar_red_para_arranque(ruta_red, std::cerr);
  const churoviaje::Nucleo nucleo(std::move(red));
  return churoviaje::ejecutar(std::cin, std::cout, std::cerr, nucleo);
}
