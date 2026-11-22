import '../../models/coordenada.dart';
import '../../models/surtidor.dart';
import '../api/api_client.dart';

/// `GET /surtidores` (CU-05): los surtidores del catálogo, del más cercano al más lejano de [desde] (RN-05.2).
class ServicioSurtidores {
  final ApiClient _api;

  ServicioSurtidores(this._api);

  /// `POST /surtidores/{id}/confirmaciones` (HU-12): el conductor recargó [tipo] en ese surtidor; [parada] es donde
  /// la app lo detectó detenido. Si el backend la rechaza (409 repetida, 422 lejos o tipo no permitido), lanza
  /// [ApiException] con su mensaje.
  Future<void> confirmarRecarga(int surtidorId, String tipo, Coordenada parada) async {
    await _api.post('/surtidores/$surtidorId/confirmaciones', body: {
      'tipo': tipo,
      'ubicacion': {'latitud': parada.latitud, 'longitud': parada.longitud},
    });
  }

  Future<List<Surtidor>> consultar(Coordenada desde) async {
    final json = await _api.get('/surtidores', query: {
      'latitud': desde.latitud.toString(),
      'longitud': desde.longitud.toString(),
    });
    final lista = json is Map<String, dynamic> ? json['surtidores'] : null;
    return [
      for (final s in (lista as List? ?? const [])) Surtidor.fromJson(s as Map<String, dynamic>),
    ];
  }
}
