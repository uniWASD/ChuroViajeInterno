import '../../models/sesion.dart';
import '../api/api_client.dart';

/// Las rutas `/sesion/*` del backend (05_contratos.md, «Sesión»). Son las únicas que no llevan JWT.
class ServicioSesion {
  final ApiClient _api;

  ServicioSesion(this._api);

  /// `POST /sesion/google`: cambia el token de identidad de Google por la sesión del backend.
  Future<Sesion> iniciarConGoogle(String tokenGoogle, DateTime ahora) async {
    final datos = await _api.post('/sesion/google', body: {'tokenGoogle': tokenGoogle});
    final json = _objeto(datos);
    return Sesion(
      jwt: json['jwt'] as String,
      expiraEn: DateTime.parse(json['expiraEn'] as String),
      tokenRenovacion: json['tokenRenovacion'] as String,
      iniciadaEn: ahora,
    );
  }

  /// `POST /sesion/renovar`: un JWT nuevo; el token de renovación sigue siendo el mismo.
  Future<({String jwt, DateTime expiraEn})> renovar(String tokenRenovacion) async {
    final datos = await _api.post('/sesion/renovar', body: {'tokenRenovacion': tokenRenovacion});
    final json = _objeto(datos);
    return (jwt: json['jwt'] as String, expiraEn: DateTime.parse(json['expiraEn'] as String));
  }

  /// `POST /sesion/cerrar`: el token de renovación deja de valer.
  Future<void> cerrar(String tokenRenovacion) async {
    await _api.post('/sesion/cerrar', body: {'tokenRenovacion': tokenRenovacion});
  }

  static Map<String, dynamic> _objeto(dynamic datos) {
    if (datos is Map<String, dynamic>) return datos;
    throw const ApiException(
      codigo: 'RESPUESTA_INVALIDA',
      mensaje: 'El servidor devolvió una respuesta inválida.',
    );
  }
}
