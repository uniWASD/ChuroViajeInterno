// Herramienta de consola para probar el cálculo de ruta con la red vial: entra un nodo de origen y uno de
// destino, y sale la lista de nodos de la ruta (#58). No la usa el backend: el contrato sigue siendo
// calcular_ruta, con coordenadas, a través del wrapper.
//
//   ruta_nodos --red <archivo> [--algoritmo a_estrella|dijkstra] <nodo_origen> <nodo_destino>
//   ruta_nodos --red <archivo>          y luego, por la entrada, una línea "origen destino" por consulta
//
// Cada respuesta es una línea de JSON con los nodos, la distancia, el tiempo estimado y las calles.
#include <iostream>
#include <optional>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

#include <nlohmann/json.hpp>

#include "red_vial.hpp"
#include "ruta.hpp"

namespace {

using json = nlohmann::ordered_json;
using churoviaje::Algoritmo;
using churoviaje::RedVial;

constexpr const char* kUso =
    "Uso: ruta_nodos --red <archivo> [--algoritmo a_estrella|dijkstra] [<nodo_origen> <nodo_destino>]";

json responder(const RedVial& red, const std::string& origen, const std::string& destino, Algoritmo algoritmo) {
  json r = {{"origen", origen}, {"destino", destino}};
  const auto desde = red.buscar_nodo(origen);
  const auto hasta = red.buscar_nodo(destino);
  if (!desde || !hasta) {
    r["estado"] = "NODO_DESCONOCIDO";
    r["nodo"] = !desde ? origen : destino;
    return r;
  }
  const churoviaje::Camino camino = churoviaje::buscar_camino(red, *desde, *hasta, nullptr, algoritmo);
  if (!camino.encontrado) {
    r["estado"] = "SIN_RUTA";
    return r;
  }
  json nodos = json::array();
  for (const std::size_t n : churoviaje::nodos_del_camino(red, *desde, camino.tramos)) nodos.push_back(red.nodos()[n].id);
  json calles = json::array();
  double tiempo_s = 0;
  for (const std::size_t t : camino.tramos) {
    const auto& tramo = red.tramos()[t];
    tiempo_s += tramo.longitud_m / churoviaje::velocidad_esperada_mps(tramo.tipo_via);
    if (calles.empty() || calles.back() != tramo.nombre) calles.push_back(tramo.nombre);
  }
  r["estado"] = "ENCONTRADA";
  r["nodos"] = std::move(nodos);
  r["distancia_m"] = std::llround(camino.costo);
  r["tiempo_s"] = std::llround(tiempo_s);
  r["calles"] = std::move(calles);
  return r;
}

}  // namespace

int main(int argc, char** argv) {
  std::string ruta_red;
  Algoritmo algoritmo = Algoritmo::a_estrella;
  std::vector<std::string> nodos;
  for (int i = 1; i < argc; ++i) {
    const std::string_view arg = argv[i];
    if (arg == "--red" && i + 1 < argc) {
      ruta_red = argv[++i];
    } else if (arg == "--algoritmo" && i + 1 < argc) {
      const auto elegido = churoviaje::algoritmo_desde_texto(argv[++i]);
      if (!elegido) {
        std::cerr << kUso << std::endl;
        return 2;
      }
      algoritmo = *elegido;
    } else if (!arg.empty() && arg[0] == '-') {
      std::cerr << "Argumento desconocido: " << arg << ". " << kUso << std::endl;
      return 2;
    } else {
      nodos.emplace_back(arg);
    }
  }
  if (ruta_red.empty() || (nodos.size() != 0 && nodos.size() != 2)) {
    std::cerr << kUso << std::endl;
    return 2;
  }

  std::optional<RedVial> red;
  try {
    red = RedVial::cargar_archivo(ruta_red);
  } catch (const std::exception& e) {
    std::cerr << "No se pudo cargar la red vial: " << e.what() << std::endl;
    return 1;
  }

  if (nodos.size() == 2) {
    std::cout << responder(*red, nodos[0], nodos[1], algoritmo).dump() << std::endl;
    return 0;
  }
  std::cerr << "Red cargada: " << red->nodos().size() << " nodos y " << red->tramos().size()
            << " tramos. Escribe \"origen destino\" en cada línea (Ctrl+D o Ctrl+Z para salir)." << std::endl;
  std::string linea;
  while (std::getline(std::cin, linea)) {
    std::istringstream partes(linea);
    std::string origen, destino, sobra;
    if (!(partes >> origen)) continue;  // línea en blanco
    if (!(partes >> destino) || (partes >> sobra)) {
      std::cerr << "Cada línea lleva dos ids de nodo separados por un espacio." << std::endl;
      continue;
    }
    std::cout << responder(*red, origen, destino, algoritmo).dump() << std::endl;
  }
  return 0;
}
