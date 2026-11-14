import 'dart:convert';

import 'package:flutter/services.dart';

import '../../models/coordenada.dart';
import '../../models/revision_ruta.dart';
import '../../models/ruta.dart';
import '../api/api_client.dart';

/// Quién calcula la ruta. Las reglas (puntos válidos, sentidos, tráfico) las aplica el backend: la app solo
/// manda los puntos y muestra la respuesta.
abstract class ServicioRutas {
  /// true si la respuesta no viene del backend sino de un ejemplo fijo.
  bool get esDeEjemplo;

  /// Lanza [ApiException] si el backend no responde o responde con un error (por ejemplo, 503).
  Future<RutaCalculada> calcular(
    Coordenada origen,
    Coordenada destino,
    List<Coordenada> paradas,
  );

  /// `POST /rutas/revision` (CU-04). Lanza [ApiException] si falla (sin red, 503); la app sigue con su ruta.
  Future<RevisionRuta> revisar(PedidoRevision pedido);
}

class ServicioRutasApi implements ServicioRutas {
  final ApiClient _api;

  ServicioRutasApi(this._api);

  @override
  bool get esDeEjemplo => false;

  @override
  Future<RutaCalculada> calcular(
    Coordenada origen,
    Coordenada destino,
    List<Coordenada> paradas,
  ) async {
    final respuesta = await _api.post(
      '/rutas',
      body: {
        'origen': _json(origen),
        'destino': _json(destino),
        'paradas': [for (final p in paradas) _json(p)],
      },
    );
    if (respuesta is! Map<String, dynamic>) {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
    try {
      return RutaCalculada.fromJson(respuesta);
    } on Object {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
  }

  @override
  Future<RevisionRuta> revisar(PedidoRevision pedido) async {
    final respuesta = await _api.post(
      '/rutas/revision',
      body: {
        'posicion': _json(pedido.posicion),
        'tramos': pedido.tramos,
        'destino': _json(pedido.destino),
        'paradas': [for (final p in pedido.paradas) _json(p)],
        'eventosAvisados': pedido.eventosAvisados,
      },
    );
    try {
      return RevisionRuta.fromJson(respuesta as Map<String, dynamic>);
    } on Object {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
  }

  static Map<String, double> _json(Coordenada c) => {
    'latitud': c.latitud,
    'longitud': c.longitud,
  };
}

/// Mientras la app no inicia sesión (HU-10), `POST /rutas` responde 401. Para probar la vista se usa la respuesta
/// de uno de los viajes de referencia de Tarija (T-120): de la plaza Luis de Fuentes a la ex terminal. Es la
/// misma ruta sean cuales sean los puntos elegidos.
class ServicioRutasDeEjemplo implements ServicioRutas {
  static const String archivo = 'assets/datos/ruta_ejemplo_post_rutas.json';

  final AssetBundle _bundle;

  ServicioRutasDeEjemplo([AssetBundle? bundle])
    : _bundle = bundle ?? rootBundle;

  @override
  bool get esDeEjemplo => true;

  @override
  Future<RutaCalculada> calcular(
    Coordenada origen,
    Coordenada destino,
    List<Coordenada> paradas,
  ) async {
    final texto = await _bundle.loadString(archivo);
    return RutaCalculada.fromJson(jsonDecode(texto) as Map<String, dynamic>);
  }

  /// La ruta de ejemplo no tiene tráfico: nunca hay nada que avisar.
  @override
  Future<RevisionRuta> revisar(PedidoRevision pedido) async =>
      const RevisionRuta(estado: EstadoRevision.sinCambios);
}
