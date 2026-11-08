/// La sesión del conductor: el JWT con su vencimiento y el token de renovación (CU-08).
class Sesion {
  /// El token de renovación vale 30 días desde que se inició la sesión (P-05).
  static const Duration vigenciaRenovacion = Duration(days: 30);

  final String jwt;
  final DateTime expiraEn;
  final String tokenRenovacion;
  final DateTime iniciadaEn;

  const Sesion({
    required this.jwt,
    required this.expiraEn,
    required this.tokenRenovacion,
    required this.iniciadaEn,
  });

  /// El JWT sirve si le queda más que [margen]; así se renueva antes de que venza (RN-08.12).
  bool jwtVigente(DateTime ahora, {Duration margen = Duration.zero}) =>
      ahora.isBefore(expiraEn.subtract(margen));

  /// Con el token de renovación vigente se entra directo, aunque no haya red (RN-08.10).
  bool renovacionVigente(DateTime ahora) =>
      ahora.isBefore(iniciadaEn.add(vigenciaRenovacion));

  Sesion conJwt(String nuevoJwt, DateTime nuevaExpiracion) => Sesion(
    jwt: nuevoJwt,
    expiraEn: nuevaExpiracion,
    tokenRenovacion: tokenRenovacion,
    iniciadaEn: iniciadaEn,
  );

  Map<String, dynamic> toJson() => {
    'jwt': jwt,
    'expiraEn': expiraEn.toUtc().toIso8601String(),
    'tokenRenovacion': tokenRenovacion,
    'iniciadaEn': iniciadaEn.toUtc().toIso8601String(),
  };

  static Sesion fromJson(Map<String, dynamic> json) => Sesion(
    jwt: json['jwt'] as String,
    expiraEn: DateTime.parse(json['expiraEn'] as String),
    tokenRenovacion: json['tokenRenovacion'] as String,
    iniciadaEn: DateTime.parse(json['iniciadaEn'] as String),
  );
}
