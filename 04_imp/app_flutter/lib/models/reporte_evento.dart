import 'coordenada.dart';

/// Lo que el conductor confirma al reportar un evento. La app nunca calcula el momento en que ocurrió: manda la
/// hora de confirmación y los minutos elegidos, y el backend hace la resta (RN-01.4, 05_contratos.md). Si el punto
/// lo eligió el conductor en el mapa, va también su posición: el backend revisa que estén a 1 km o menos (RN-01.5).
class ReporteEvento {
  final String tipo;
  final Coordenada ubicacion;
  final DateTime horaConfirmacion;
  final int minutosElegidos;
  final Coordenada? posicionConductor;

  const ReporteEvento({
    required this.tipo,
    required this.ubicacion,
    required this.horaConfirmacion,
    required this.minutosElegidos,
    this.posicionConductor,
  });

  Map<String, dynamic> toJson() => {
    'tipo': tipo,
    'ubicacion': _coordenada(ubicacion),
    'horaConfirmacion': horaConfirmacion.toUtc().toIso8601String(),
    'minutosElegidos': minutosElegidos,
    if (posicionConductor != null) 'posicionConductor': _coordenada(posicionConductor!),
  };

  factory ReporteEvento.fromJson(Map<String, dynamic> json) {
    final posicion = json['posicionConductor'];
    return ReporteEvento(
      tipo: json['tipo'] as String,
      ubicacion: _leer(json['ubicacion'] as Map<String, dynamic>),
      horaConfirmacion: DateTime.parse(json['horaConfirmacion'] as String),
      minutosElegidos: (json['minutosElegidos'] as num).toInt(),
      posicionConductor: posicion is Map<String, dynamic> ? _leer(posicion) : null,
    );
  }

  static Map<String, double> _coordenada(Coordenada c) => {'latitud': c.latitud, 'longitud': c.longitud};

  static Coordenada _leer(Map<String, dynamic> c) =>
      Coordenada((c['latitud'] as num).toDouble(), (c['longitud'] as num).toDouble());
}
