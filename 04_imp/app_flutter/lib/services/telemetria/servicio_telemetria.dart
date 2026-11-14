import '../../models/lectura_ubicacion.dart';
import '../api/api_client.dart';

/// `POST /telemetria` (CU-04, 05_contratos.md). El backend responde 202 sin esperar el análisis.
class ServicioTelemetria {
  final ApiClient _api;

  ServicioTelemetria(this._api);

  /// Lanza [ApiException] si no se pudo enviar.
  Future<void> enviar(List<LecturaUbicacion> lecturas) async {
    await _api.post('/telemetria', body: {'lecturas': [for (final l in lecturas) l.toJson()]});
  }
}
