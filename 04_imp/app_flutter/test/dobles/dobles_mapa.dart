import 'dart:async';

import 'package:flutter/material.dart';

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/ruta.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/rutas/servicio_rutas.dart';
import 'package:app_flutter/services/ubicacion/servicio_ubicacion.dart';
import 'package:app_flutter/views/map/mapa_base.dart';
import 'package:app_flutter/views/navegacion/navegacion_activa_screen.dart';
import 'package:app_flutter/views/ruta/como_llegar_screen.dart';

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

/// Mapa falso: un recuadro que guarda lo último que le pidió cada vista.
class MapaFalso {
  /// Lo último que pidió la vista Mapa principal.
  DatosMapa? ultimo;

  /// Lo último que pidió la vista Cómo llegar (que se abre encima de la principal).
  DatosMapa? comoLlegar;

  /// Lo último que pidió la vista Navegación activa.
  DatosMapa? navegacion;
  int construcciones = 0;

  Widget construir(BuildContext context, DatosMapa datos) {
    final enComoLlegar =
        context.widget is ComoLlegarScreen ||
        context.findAncestorWidgetOfExactType<ComoLlegarScreen>() != null;
    final enNavegacion = context.widget is NavegacionActivaScreen ||
        context.findAncestorWidgetOfExactType<NavegacionActivaScreen>() != null;
    if (enNavegacion) {
      navegacion = datos;
    } else if (enComoLlegar) {
      comoLlegar = datos;
    } else {
      ultimo = datos;
    }
    construcciones++;
    return const ColoredBox(key: Key('mapa-falso'), color: Color(0xFFEEEEEE));
  }
}

/// Servicio de rutas falso: guarda lo que se le pidió y responde lo que se le diga.
class ServicioRutasFalso implements ServicioRutas {
  RutaCalculada respuesta;
  ApiException? error;
  final List<(Coordenada, Coordenada, List<Coordenada>)> pedidos = [];

  ServicioRutasFalso({RutaCalculada? respuesta})
    : respuesta = respuesta ?? rutaDePrueba;

  @override
  bool get esDeEjemplo => false;

  @override
  Future<RutaCalculada> calcular(
    Coordenada origen,
    Coordenada destino,
    List<Coordenada> paradas,
  ) async {
    pedidos.add((origen, destino, paradas));
    final e = error;
    if (e != null) throw e;
    return respuesta;
  }
}

const RutaCalculada rutaDePrueba = RutaCalculada(
  estado: EstadoRuta.encontrada,
  distanciaM: 1524,
  tiempoS: 163,
  trazado: [
    Coordenada(-21.534425, -64.733999),
    Coordenada(-21.541756, -64.725011),
  ],
  tramos: [TramoRuta('osm:1:0:ida', 'Avenida Las Américas')],
);
