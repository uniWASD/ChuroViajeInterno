import 'package:flutter/widgets.dart';

import '../../models/coordenada.dart';

/// Lo que una vista le pide al mapa. Las vistas no usan el SDK de Mapbox directamente: reciben un
/// [ConstructorMapa], y así se pueden probar con un mapa falso.
class DatosMapa {
  /// Dónde abre el mapa.
  final Coordenada centro;

  /// Si se muestra la posición del conductor (solo con permiso y con la app en pantalla).
  final bool mostrarUbicacion;

  /// El punto que tocó el conductor, si hay uno; el mapa lo marca.
  final Coordenada? puntoSeleccionado;

  /// El conductor tocó el mapa en esa coordenada.
  final ValueChanged<Coordenada> alTocar;

  /// El mapa base no pudo cargar; el texto es el detalle técnico, para el registro.
  final ValueChanged<String> alFallar;

  const DatosMapa({
    this.centro = centroTarija,
    this.mostrarUbicacion = false,
    this.puntoSeleccionado,
    required this.alTocar,
    required this.alFallar,
  });
}

typedef ConstructorMapa = Widget Function(
  BuildContext context,
  DatosMapa datos,
);
