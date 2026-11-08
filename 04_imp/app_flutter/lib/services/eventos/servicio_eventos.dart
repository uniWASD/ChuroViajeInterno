import '../../models/evento_vial.dart';
import '../../models/reporte_evento.dart';
import '../api/api_client.dart';

/// Respuesta de `GET /eventos` (RN-02.6). Sin marca trae todos los vigentes ([completa]); con la marca anterior,
/// solo los eventos nuevos o cambiados y los identificadores de los que hay que quitar.
class RespuestaEventos {
  final String marca;
  final bool completa;
  final List<EventoVial> eventos;
  final List<int> quitados;

  const RespuestaEventos({
    required this.marca,
    required this.completa,
    required this.eventos,
    required this.quitados,
  });

  factory RespuestaEventos.fromJson(Map<String, dynamic> json) => RespuestaEventos(
    marca: json['marca'] as String,
    completa: json['completa'] as bool? ?? false,
    eventos: [
      for (final e in (json['eventos'] as List? ?? const []))
        EventoVial.fromJson(e as Map<String, dynamic>),
    ],
    quitados: [
      for (final id in (json['quitados'] as List? ?? const [])) (id as num).toInt(),
    ],
  );
}

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

  /// `GET /eventos`: los vigentes o, con [marca], solo los cambios desde la consulta anterior (CU-02).
  Future<RespuestaEventos> consultar({String? marca}) async {
    final datos = await _api.get('/eventos', query: marca == null ? null : {'desde': marca});
    if (datos is! Map<String, dynamic>) {
      throw const ApiException(
        codigo: 'RESPUESTA_INVALIDA',
        mensaje: 'El servidor devolvió una respuesta inválida.',
      );
    }
    return RespuestaEventos.fromJson(datos);
  }
}
