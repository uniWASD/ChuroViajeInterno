import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../../models/coordenada.dart';

enum TipoMarca { punto, origen, parada, destino }

/// Un punto marcado en el mapa: el origen, una parada o el destino de una ruta.
class MarcaMapa {
  final Coordenada coordenada;
  final TipoMarca tipo;

  const MarcaMapa(this.coordenada, this.tipo);

  @override
  bool operator ==(Object other) =>
      other is MarcaMapa &&
      other.coordenada == coordenada &&
      other.tipo == tipo;

  @override
  int get hashCode => Object.hash(coordenada, tipo);
}

/// Un evento vial dibujado en el mapa con el color de su tipo (RN-02.7); los confirmados llevan su marca (RN-02.14).
/// Una congestión con [trazado] se dibuja como una línea a lo largo de la calle, con el color de su [nivel]
/// (RN-04.25, D-45); sin trazado, como un punto.
class MarcaEvento {
  final int id;
  final Coordenada coordenada;
  final Color color;
  final bool confirmado;

  /// El tipo de evento (ACCIDENTE, CONGESTION…), para el ícono del marcador.
  final String tipo;
  final String? nivel;
  final List<Coordenada> trazado;

  const MarcaEvento({
    required this.id,
    required this.coordenada,
    required this.color,
    required this.confirmado,
    this.tipo = '',
    this.nivel,
    this.trazado = const [],
  });

  /// Si se dibuja como línea: solo una congestión que trae el trazado de su tramo.
  bool get esLinea => trazado.length >= 2;

  @override
  bool operator ==(Object other) =>
      other is MarcaEvento &&
      other.id == id &&
      other.coordenada == coordenada &&
      other.color == color &&
      other.confirmado == confirmado &&
      other.tipo == tipo &&
      other.nivel == nivel &&
      listEquals(other.trazado, trazado);

  @override
  int get hashCode => Object.hash(id, coordenada, color, confirmado, tipo, nivel, Object.hashAll(trazado));
}

/// Los colores de la congestión dibujada como línea (P-06): naranja si está congestionada y rojo si está muy
/// congestionada, como el tráfico de Google Maps.
const Color colorCongestionado = Color(0xFFF29900);
const Color colorMuyCongestionado = Color(0xFFD93025);

Color colorDeNivel(String? nivel) =>
    nivel == 'MUY_CONGESTIONADO' ? colorMuyCongestionado : colorCongestionado;

/// Lo que una vista le pide al mapa. Las vistas no usan el SDK de Mapbox directamente: reciben un
/// [ConstructorMapa], y así se pueden probar con un mapa falso.
class DatosMapa {
  /// Dónde abre el mapa.
  final Coordenada centro;

  /// Si se muestra la posición del conductor (solo con permiso y con la app en pantalla).
  final bool mostrarUbicacion;

  /// Si la cámara sigue la posición del conductor, como en la Navegación activa (CP-107). Sin la ubicación a la
  /// vista no tiene efecto.
  final bool seguirUbicacion;

  /// El punto que tocó el conductor, si hay uno; el mapa lo marca.
  final Coordenada? puntoSeleccionado;

  /// Otros puntos que hay que marcar (origen, paradas y destino de una ruta).
  final List<MarcaMapa> marcas;

  /// El trazado de la ruta calculada, si hay una; el mapa la dibuja y la encuadra.
  final List<Coordenada> trazado;

  /// Los eventos viales vigentes (CU-02).
  final List<MarcaEvento> eventos;

  /// El conductor tocó un evento: se abre su ficha.
  final ValueChanged<int>? alTocarEvento;

  /// #172: el evento abierto. Cuando cambia, la cámara va hasta él.
  final Coordenada? foco;

  /// Qué parte de abajo del mapa tapa una hoja (de 0 a 1): la cámara deja el [foco] en la parte que se ve.
  final double tapadoAbajo;

  /// El conductor tocó el mapa en esa coordenada.
  final ValueChanged<Coordenada> alTocar;

  /// El mapa base no pudo cargar; el texto es el detalle técnico, para el registro.
  final ValueChanged<String> alFallar;

  const DatosMapa({
    this.centro = centroTarija,
    this.mostrarUbicacion = false,
    this.seguirUbicacion = false,
    this.puntoSeleccionado,
    this.marcas = const [],
    this.trazado = const [],
    this.eventos = const [],
    this.alTocarEvento,
    this.foco,
    this.tapadoAbajo = 0,
    required this.alTocar,
    required this.alFallar,
  });
}

typedef ConstructorMapa = Widget Function(
  BuildContext context,
  DatosMapa datos,
);
