import '../../models/evento_vial.dart';
import '../../models/reporte_evento.dart';
import '../api/api_client.dart';

/// Las rutas `/eventos` del backend (05_contratos.md).
class ServicioEventos {
  final ApiClient _api;

  ServicioEventos(this._api);

  /// `POST /eventos` (CU-01). Un rechazo llega como [ApiException] con el mensaje del backend.
  Future<EventoVial> reportar(ReporteEvento reporte) async {
    final datos = await _api.post('/eventos', body: reporte.toJson());
    if (datos is! Map<String, dynamic>) {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
    return EventoVial.fromJson(datos);
  }
}
