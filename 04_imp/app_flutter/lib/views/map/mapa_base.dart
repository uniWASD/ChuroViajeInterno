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
class MarcaEvento {
  final int id;
  final Coordenada coordenada;
  final Color color;
  final bool confirmado;

  const MarcaEvento({
    required this.id,
    required this.coordenada,
    required this.color,
    required this.confirmado,
  });

  @override
  bool operator ==(Object other) =>
      other is MarcaEvento &&
      other.id == id &&
      other.coordenada == coordenada &&
      other.color == color &&
      other.confirmado == confirmado;

  @override
  int get hashCode => Object.hash(id, coordenada, color, confirmado);
}

/// Lo que una vista le pide al mapa. Las vistas no usan el SDK de Mapbox directamente: reciben un
/// [ConstructorMapa], y así se pueden probar con un mapa falso.
class DatosMapa {
  /// Dónde abre el mapa.
  final Coordenada centro;

  /// Si se muestra la posición del conductor (solo con permiso y con la app en pantalla).
  final bool mostrarUbicacion;

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

  /// El conductor tocó el mapa en esa coordenada.
  final ValueChanged<Coordenada> alTocar;

  /// El mapa base no pudo cargar; el texto es el detalle técnico, para el registro.
  final ValueChanged<String> alFallar;

  const DatosMapa({
    this.centro = centroTarija,
    this.mostrarUbicacion = false,
    this.puntoSeleccionado,
    this.marcas = const [],
    this.trazado = const [],
    this.eventos = const [],
    this.alTocarEvento,
    required this.alTocar,
    required this.alFallar,
  });
}

typedef ConstructorMapa = Widget Function(
  BuildContext context,
  DatosMapa datos,
);
