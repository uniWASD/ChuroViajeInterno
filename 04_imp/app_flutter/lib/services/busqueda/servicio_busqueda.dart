import '../../models/coordenada.dart';
import '../../models/lugar.dart';
import '../api/api_client.dart';

/// `GET /lugares` del backend (D-47, 05_contratos.md): calles, lugares y surtidores para un texto.
class ServicioBusqueda {
  final ApiClient _api;

  ServicioBusqueda(this._api);

  /// Con [cerca], el backend pone primero lo más cercano y devuelve la distancia.
  Future<List<LugarEncontrado>> buscar(
    String texto, {
    Coordenada? cerca,
  }) async {
    final datos = await _api.get(
      '/lugares',
      query: {
        'texto': texto,
        if (cerca != null) 'latitud': cerca.latitud.toStringAsFixed(6),
        if (cerca != null) 'longitud': cerca.longitud.toStringAsFixed(6),
      },
    );
    final lista = datos is Map<String, dynamic> ? datos['lugares'] : null;
    if (lista is! List) {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
    try {
      return [
        for (final l in lista)
          LugarEncontrado.fromJson(l as Map<String, dynamic>),
      ];
    } on Object {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
  }
}
