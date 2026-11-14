import 'coordenada.dart';

/// Un evento vial como lo entrega el backend (RN-02.5): tipo, ubicación, cuándo ocurrió, si está confirmado y su
/// origen. Nunca trae quién lo reportó.
class EventoVial {
  final int id;
  final String tipo;
  final Coordenada ubicacion;
  final DateTime momentoOcurrencia;
  final String estado;
  final bool confirmado;
  final String origen;

  /// Solo la congestión trae nivel.
  final String? nivel;

  const EventoVial({
    required this.id,
    required this.tipo,
    required this.ubicacion,
    required this.momentoOcurrencia,
    required this.estado,
    required this.confirmado,
    required this.origen,
    this.nivel,
  });

  factory EventoVial.fromJson(Map<String, dynamic> json) {
    final ubicacion = json['ubicacion'] as Map<String, dynamic>;
    return EventoVial(
      id: (json['id'] as num).toInt(),
      tipo: json['tipo'] as String,
      ubicacion: Coordenada(
        (ubicacion['latitud'] as num).toDouble(),
        (ubicacion['longitud'] as num).toDouble(),
      ),
      momentoOcurrencia: DateTime.parse(json['momentoOcurrencia'] as String),
      estado: json['estado'] as String? ?? 'ACTIVO',
      confirmado: json['confirmado'] as bool? ?? false,
      origen: json['origen'] as String? ?? 'CONDUCTOR',
      nivel: json['nivel'] as String?,
    );
  }

  /// D-20: la congestión que detecta el sistema con la telemetría no se vota.
  bool get votable => origen != 'TELEMETRIA';

  EventoVial conEstado(String estado, bool confirmado) => EventoVial(
    id: id,
    tipo: tipo,
    ubicacion: ubicacion,
    momentoOcurrencia: momentoOcurrencia,
    estado: estado,
    confirmado: confirmado,
    origen: origen,
    nivel: nivel,
  );

  /// Texto de «hace cuánto ocurrió» para la ficha del evento (RN-02.7).
  String haceCuanto(DateTime ahora) {
    final minutos = ahora.difference(momentoOcurrencia).inMinutes;
    if (minutos < 1) return 'Hace menos de un minuto';
    if (minutos == 1) return 'Hace 1 minuto';
    return 'Hace $minutos minutos';
  }
}
