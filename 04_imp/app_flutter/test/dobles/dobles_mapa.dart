import 'dart:async';

import 'package:flutter/material.dart';

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/lectura_ubicacion.dart';
import 'package:app_flutter/models/revision_ruta.dart';
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
  final List<StreamController<LecturaUbicacion>> _flujosLecturas = [];

  /// El momento de cada lectura de telemetría; las pruebas con tiempo simulado lo reemplazan.
  DateTime Function() reloj = DateTime.now;

  UbicacionFalsa({this.permiso = PermisoUbicacion.concedido});

  /// Cuántas suscripciones a las lecturas de telemetría siguen abiertas.
  int get escuchandoLecturas => _flujosLecturas.where((f) => f.hasListener).length;

  /// Simula que el sistema retiró el permiso o apagó el GPS: los dos flujos dan error.
  void fallar(Object error) {
    for (final f in [..._flujos, ..._flujosLecturas]) {
      if (f.hasListener) f.addError(error);
    }
  }

  /// Cuántas suscripciones a las posiciones siguen abiertas.
  int get escuchando => _flujos.where((f) => f.hasListener).length;

  /// Una posición nueva: llega al mapa y, como lectura con 5 m de precisión, a la telemetría.
  void emitir(Coordenada c, {double precisionM = 5}) {
    for (final f in _flujos) {
      if (f.hasListener) f.add(c);
    }
    final lectura = LecturaUbicacion(c, precisionM, reloj());
    for (final f in _flujosLecturas) {
      if (f.hasListener) f.add(lectura);
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
  Stream<LecturaUbicacion> lecturas() {
    final flujo = StreamController<LecturaUbicacion>();
    _flujosLecturas.add(flujo);
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

  /// Lo que responde `POST /rutas/revision`; por defecto, sin cambios.
  RevisionRuta revision = const RevisionRuta(estado: EstadoRevision.sinCambios);
  ApiException? errorRevision;
  final List<PedidoRevision> revisiones = [];

  @override
  Future<RevisionRuta> revisar(PedidoRevision pedido) async {
    revisiones.add(pedido);
    final e = errorRevision;
    if (e != null) throw e;
    return revision;
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
