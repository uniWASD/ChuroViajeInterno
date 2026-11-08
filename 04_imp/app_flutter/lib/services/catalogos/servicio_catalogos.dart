import 'dart:convert';

import '../../models/catalogos.dart';
import '../api/api_client.dart';
import '../sesion/almacen_local.dart';

/// Trae `GET /catalogos` y lo guarda en el teléfono, para que la app y la cola sin conexión usen siempre los
/// mismos tipos, colores, vigencias y tiempos que el backend (D-06).
class ServicioCatalogos {
  static const String clave = 'catalogos';

  final ApiClient _api;
  final AlmacenLocal _almacen;

  ServicioCatalogos(this._api, this._almacen);

  Future<Catalogos> descargarYGuardar() async {
    final datos = await _api.get('/catalogos');
    if (datos is! Map<String, dynamic>) {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
    final catalogos = Catalogos.fromJson(datos);
    await _almacen.guardar(clave, jsonEncode(catalogos.toJson()));
    return catalogos;
  }

  Future<Catalogos?> leerGuardados() async {
    final texto = await _almacen.leer(clave);
    if (texto == null) return null;
    try {
      return Catalogos.fromJson(jsonDecode(texto) as Map<String, dynamic>);
    } on Object {
      return null;
    }
  }
}
