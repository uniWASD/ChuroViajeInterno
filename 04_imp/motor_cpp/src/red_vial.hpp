// Red vial de Tarija en memoria: nodos (intersecciones), tramos (calles con sentido) y un índice espacial
// para ajustar un punto a la intersección más cercana.
// Formato del archivo: 03_des/02_Arquitectura/05_contratos.md, sección «Archivo de la red vial» (versión 1).
#pragma once

#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <iosfwd>
#include <memory>
#include <optional>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include <nlohmann/json_fwd.hpp>

namespace churoviaje {

// El archivo no se pudo cargar: falta, no es JSON, tiene otra versión o rompe una regla del formato.
class ErrorRedVial : public std::runtime_error {
 public:
  using std::runtime_error::runtime_error;
};

enum class TipoVia { via_rapida, avenida_principal, zona_residencial };

// Punto geográfico en grados decimales (WGS84).
struct Punto {
  double lat = 0;
  double lon = 0;
};

struct Nodo {
  std::string id;
  Punto posicion;
  double x = 0;  // metros al este y al norte del centro de la red (proyección local, para el índice)
  double y = 0;
};

struct Tramo {
  std::string id;
  std::size_t desde = 0;  // posición del nodo en RedVial::nodos()
  std::size_t hasta = 0;
  std::optional<std::size_t> opuesto;  // el tramo de la misma calle en sentido contrario, si lo hay
  std::string nombre;
  TipoVia tipo_via = TipoVia::zona_residencial;
  double longitud_m = 0;
  std::vector<Punto> geometria;  // de `desde` a `hasta`
};

// Distancia en línea recta entre dos puntos, en metros (haversine, con el mismo radio que usa preparacion/).
double distancia_m(const Punto& a, const Punto& b);

// Resultado de ajustar un punto a la red.
struct Ajuste {
  std::size_t nodo = 0;   // posición en RedVial::nodos()
  double distancia_m = 0;  // distancia entre el punto y el nodo
};

class RedVial {
 public:
  // RN-03.4 y P-23: un punto se ajusta a la intersección más cercana si hay una a menos de 100 m.
  static constexpr double kRadioAjusteMetros = 100.0;

  // Construye la red desde el JSON ya leído. Lanza ErrorRedVial si algo no cumple el formato.
  static RedVial desde_json(const nlohmann::json& documento);
  // Lee y construye. Lanza ErrorRedVial si el archivo falta, no se puede leer o no cumple el formato.
  static RedVial cargar_archivo(const std::filesystem::path& ruta);

  const std::string& fecha_extracto() const { return fecha_extracto_; }
  const std::vector<Nodo>& nodos() const { return nodos_; }
  const std::vector<Tramo>& tramos() const { return tramos_; }
  // Tramos que salen de cada nodo (posiciones en tramos()).
  const std::vector<std::size_t>& tramos_que_salen(std::size_t nodo) const { return salientes_[nodo]; }
  // Posición del nodo con ese id, o std::nullopt si no existe.
  std::optional<std::size_t> buscar_nodo(const std::string& id) const;
  // Por cuánto se multiplica la distancia en línea recta para que la estimación del A* nunca supere el costo
  // real (RN-03.11). Es 1 si toda longitud es al menos la línea recta entre sus extremos (RN-03.7), con un
  // pequeño margen por redondeo; si algún tramo del archivo fuera más corto, baja lo necesario.
  double factor_estimacion() const { return factor_estimacion_; }

  // La intersección más cercana a (lat, lon), o std::nullopt si no hay ninguna a menos de
  // kRadioAjusteMetros o las coordenadas no son válidas. Ante un empate gana el nodo que aparece primero.
  std::optional<Ajuste> ajustar(double lat, double lon) const;

 private:
  RedVial() = default;
  void construir_indice();
  std::pair<double, double> proyectar(double lat, double lon) const;
  static std::int64_t clave_celda(std::int64_t cx, std::int64_t cy);

  std::string fecha_extracto_;
  std::vector<Nodo> nodos_;
  std::vector<Tramo> tramos_;
  std::vector<std::vector<std::size_t>> salientes_;
  std::unordered_map<std::string, std::size_t> posicion_nodo_;
  double factor_estimacion_ = 1.0;
  double lat0_ = 0;
  double lon0_ = 0;
  double metros_por_grado_lon_ = 0;
  std::unordered_map<std::int64_t, std::vector<std::size_t>> grilla_;  // celdas de kRadioAjusteMetros de lado
};

// Carga la red de `ruta` para el arranque del núcleo. Si falla, lo escribe en `registro` y devuelve nullptr:
// el núcleo arranca igual, sin red, y consultar_estado informa «red_cargada»:false.
std::shared_ptr<const RedVial> cargar_red_para_arranque(const std::filesystem::path& ruta, std::ostream& registro);

}  // namespace churoviaje
