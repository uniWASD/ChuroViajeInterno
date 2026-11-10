// Herramienta de consola para probar el cálculo de ruta con la red vial: entra un origen y un destino, y sale la
// lista de nodos de la ruta (#58). No la usa el backend: el contrato sigue siendo calcular_ruta, con
// coordenadas, a través del wrapper.
//
//   ruta_nodos --red <archivo> [--algoritmo a_estrella|dijkstra] <origen> <destino>
//   ruta_nodos --red <archivo>          y luego, por la entrada, una línea "origen destino" por consulta
//
// Origen y destino son un id de nodo (Plaza, osm:1440030690) o unas coordenadas "lat,lon" sin espacios
// (-21.5355,-64.7296). Unas coordenadas se ajustan a la intersección más cercana, a menos de 100 m, igual que
// en calcular_ruta (RN-03.4); si no hay ninguna, el estado es PUNTO_INVALIDO.
//
// Cada respuesta es una línea de JSON con los nodos, sus coordenadas, la distancia, el tiempo estimado y las
// calles.
#include <cerrno>
#include <cmath>
#include <cstdlib>
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
    "Uso: ruta_nodos --red <archivo> [--algoritmo a_estrella|dijkstra] [<origen> <destino>]\n"
    "  <origen> y <destino>: un id de nodo o unas coordenadas lat,lon sin espacios (-21.5355,-64.7296)";

// Un número decimal completo, sin texto de sobra.
std::optional<double> leer_numero(const std::string& texto) {
  if (texto.empty()) return std::nullopt;
  char* fin = nullptr;
  errno = 0;
  const double valor = std::strtod(texto.c_str(), &fin);
  if (errno != 0 || fin != texto.c_str() + texto.size() || !std::isfinite(valor)) return std::nullopt;
  return valor;
}

// "lat,lon" -> punto. Un id de nodo nunca lleva dos números separados por una coma.
std::optional<churoviaje::Punto> leer_coordenadas(const std::string& texto) {
  const auto coma = texto.find(',');
  if (coma == std::string::npos) return std::nullopt;
  const auto lat = leer_numero(texto.substr(0, coma));
  const auto lon = leer_numero(texto.substr(coma + 1));
  if (!lat || !lon) return std::nullopt;
  return churoviaje::Punto{*lat, *lon};
}

// Un extremo de la consulta ya resuelto a un nodo de la red.
struct Extremo {
  std::size_t nodo = 0;
  std::optional<double> ajuste_m;  // solo si entró como coordenadas: distancia hasta la intersección elegida
};

enum class Resolucion { listo, punto_invalido, nodo_desconocido };

Resolucion resolver(const RedVial& red, const std::string& texto, Extremo& extremo) {
  if (const auto punto = leer_coordenadas(texto)) {
    const auto ajuste = red.ajustar(punto->lat, punto->lon);
    if (!ajuste) return Resolucion::punto_invalido;
    extremo.nodo = ajuste->nodo;
    extremo.ajuste_m = ajuste->distancia_m;
    return Resolucion::listo;
  }
  const auto nodo = red.buscar_nodo(texto);
  if (!nodo) return Resolucion::nodo_desconocido;
  extremo.nodo = *nodo;
  return Resolucion::listo;
}

json responder(const RedVial& red, const std::string& origen, const std::string& destino, Algoritmo algoritmo) {
  json r = {{"origen", origen}, {"destino", destino}};
  Extremo desde, hasta;
  const Resolucion de_origen = resolver(red, origen, desde);
  const Resolucion de_destino = resolver(red, destino, hasta);
  if (de_origen != Resolucion::listo || de_destino != Resolucion::listo) {
    const bool falla_origen = de_origen != Resolucion::listo;
    const Resolucion causa = falla_origen ? de_origen : de_destino;
    if (causa == Resolucion::punto_invalido) {
      r["estado"] = "PUNTO_INVALIDO";
      r["punto"] = falla_origen ? origen : destino;
    } else {
      r["estado"] = "NODO_DESCONOCIDO";
      r["nodo"] = falla_origen ? origen : destino;
    }
    return r;
  }
  r["nodo_origen"] = red.nodos()[desde.nodo].id;
  r["nodo_destino"] = red.nodos()[hasta.nodo].id;
  if (desde.ajuste_m) r["ajuste_origen_m"] = std::llround(*desde.ajuste_m);
  if (hasta.ajuste_m) r["ajuste_destino_m"] = std::llround(*hasta.ajuste_m);
  const churoviaje::Camino camino = churoviaje::buscar_camino(red, desde.nodo, hasta.nodo, nullptr, algoritmo);
  if (!camino.encontrado) {
    r["estado"] = "SIN_RUTA";
    return r;
  }
  json nodos = json::array();
  json coordenadas = json::array();
  for (const std::size_t n : churoviaje::nodos_del_camino(red, desde.nodo, camino.tramos)) {
    nodos.push_back(red.nodos()[n].id);
    coordenadas.push_back({red.nodos()[n].posicion.lat, red.nodos()[n].posicion.lon});
  }
  json calles = json::array();
  double tiempo_s = 0;
  for (const std::size_t t : camino.tramos) {
    const auto& tramo = red.tramos()[t];
    tiempo_s += tramo.longitud_m / churoviaje::velocidad_esperada_mps(tramo.tipo_via);
    if (calles.empty() || calles.back() != tramo.nombre) calles.push_back(tramo.nombre);
  }
  r["estado"] = "ENCONTRADA";
  r["nodos"] = std::move(nodos);
  r["coordenadas"] = std::move(coordenadas);
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
    } else if (arg.rfind("--", 0) == 0) {  // una coordenada negativa empieza con un solo "-"
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
            << " tramos. Escribe \"origen destino\" en cada línea, con ids de nodo o lat,lon (Ctrl+D o Ctrl+Z para salir)."
            << std::endl;
  std::string linea;
  while (std::getline(std::cin, linea)) {
    std::istringstream partes(linea);
    std::string origen, destino, sobra;
    if (!(partes >> origen)) continue;  // línea en blanco
    if (!(partes >> destino) || (partes >> sobra)) {
      std::cerr << "Cada línea lleva dos extremos (id de nodo o lat,lon sin espacios) separados por un espacio."
                << std::endl;
      continue;
    }
    std::cout << responder(*red, origen, destino, algoritmo).dump() << std::endl;
  }
  return 0;
}
