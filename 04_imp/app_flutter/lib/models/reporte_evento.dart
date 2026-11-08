import 'coordenada.dart';

/// Lo que el conductor confirma al reportar un evento. La app nunca calcula el momento en que ocurrió: manda la
/// hora de confirmación y los minutos elegidos, y el backend hace la resta (RN-01.4, 05_contratos.md).
class ReporteEvento {
  final String tipo;
  final Coordenada ubicacion;
  final DateTime horaConfirmacion;
  final int minutosElegidos;

  const ReporteEvento({
    required this.tipo,
    required this.ubicacion,
    required this.horaConfirmacion,
    required this.minutosElegidos,
  });

  Map<String, dynamic> toJson() => {
    'tipo': tipo,
    'ubicacion': {'latitud': ubicacion.latitud, 'longitud': ubicacion.longitud},
    'horaConfirmacion': horaConfirmacion.toUtc().toIso8601String(),
    'minutosElegidos': minutosElegidos,
  };

  factory ReporteEvento.fromJson(Map<String, dynamic> json) {
    final ubicacion = json['ubicacion'] as Map<String, dynamic>;
    return ReporteEvento(
      tipo: json['tipo'] as String,
      ubicacion: Coordenada(
        (ubicacion['latitud'] as num).toDouble(),
        (ubicacion['longitud'] as num).toDouble(),
      ),
      horaConfirmacion: DateTime.parse(json['horaConfirmacion'] as String),
      minutosElegidos: (json['minutosElegidos'] as num).toInt(),
    );
  }
}
