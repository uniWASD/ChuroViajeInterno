import 'dart:async';

import 'package:flutter/material.dart';

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/ubicacion/servicio_ubicacion.dart';
import 'package:app_flutter/views/map/mapa_base.dart';

/// Ubicación falsa: decide qué responde el permiso y deja emitir posiciones a mano.
class UbicacionFalsa implements ServicioUbicacion {
  PermisoUbicacion permiso;
  int vecesPedido = 0;
  int ajustesAbiertos = 0;
  final List<StreamController<Coordenada>> _flujos = [];

  UbicacionFalsa({this.permiso = PermisoUbicacion.concedido});

  /// Cuántas suscripciones a las posiciones siguen abiertas.
  int get escuchando => _flujos.where((f) => f.hasListener).length;

  void emitir(Coordenada c) {
    for (final f in _flujos) {
      if (f.hasListener) f.add(c);
    }
  }

  @override
  Future<PermisoUbicacion> pedirPermiso() async {
    vecesPedido++;
    return permiso;
  }

  @override
  Stream<Coordenada> posiciones() {
    final flujo = StreamController<Coordenada>();
    _flujos.add(flujo);
    return flujo.stream;
  }

  @override
  Future<void> abrirAjustes() async {
    ajustesAbiertos++;
  }
}

/// Mapa falso: un recuadro que guarda lo último que le pidió la vista.
class MapaFalso {
  DatosMapa? ultimo;
  int construcciones = 0;

  Widget construir(BuildContext context, DatosMapa datos) {
    ultimo = datos;
    construcciones++;
    return const ColoredBox(key: Key('mapa-falso'), color: Color(0xFFEEEEEE));
  }
}
