import '../../models/coordenada.dart';
import '../../models/surtidor.dart';
import '../api/api_client.dart';

/// `GET /surtidores` (CU-05): los surtidores del catálogo, del más cercano al más lejano de [desde] (RN-05.2).
class ServicioSurtidores {
  final ApiClient _api;

  ServicioSurtidores(this._api);

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
