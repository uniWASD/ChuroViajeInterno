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

  /// RN-04.25: el trazado del tramo de una congestión de la telemetría, para dibujarla a lo largo de la calle. Los
  /// demás eventos, y las congestiones guardadas antes de V9, no lo traen: se dibujan como un punto.
  final List<Coordenada> trazado;

  /// D-46: cuántos conductores votaron «real» y «falso». Solo lo traen los eventos que se votan; nunca dice quién.
  final int votosReal;
  final int votosFalso;

  const EventoVial({
    required this.id,
    required this.tipo,
    required this.ubicacion,
    required this.momentoOcurrencia,
    required this.estado,
    required this.confirmado,
    required this.origen,
    this.nivel,
    this.trazado = const [],
    this.votosReal = 0,
    this.votosFalso = 0,
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
      trazado: _leerTrazado(json['trazado']),
      votosReal: (json['votosReal'] as num?)?.toInt() ?? 0,
      votosFalso: (json['votosFalso'] as num?)?.toInt() ?? 0,
    );
  }

  static List<Coordenada> _leerTrazado(Object? valor) {
    if (valor is! List || valor.length < 2) return const [];
    final puntos = <Coordenada>[];
    for (final p in valor) {
      if (p is! Map || p['latitud'] is! num || p['longitud'] is! num) return const [];
      puntos.add(Coordenada((p['latitud'] as num).toDouble(), (p['longitud'] as num).toDouble()));
    }
    return List.unmodifiable(puntos);
  }

  /// Para guardarlo en el teléfono (HU-04): el mismo formato que llega de `GET /eventos`.
  Map<String, dynamic> toJson() => {
    'id': id,
    'tipo': tipo,
    'ubicacion': {'latitud': ubicacion.latitud, 'longitud': ubicacion.longitud},
    'momentoOcurrencia': momentoOcurrencia.toUtc().toIso8601String(),
    'estado': estado,
    'confirmado': confirmado,
    'origen': origen,
    'nivel': ?nivel,
    if (trazado.isNotEmpty)
      'trazado': [for (final c in trazado) {'latitud': c.latitud, 'longitud': c.longitud}],
    'votosReal': votosReal,
    'votosFalso': votosFalso,
  };

  /// D-20: la congestión que detecta el sistema con la telemetría no se vota.
  bool get votable => origen != 'TELEMETRIA';

  /// El mismo evento después de un voto: su estado y, si el backend los mandó, los votos que lleva.
  EventoVial conEstado(String estado, bool confirmado, {int? votosReal, int? votosFalso}) => EventoVial(
    id: id,
    tipo: tipo,
    ubicacion: ubicacion,
    momentoOcurrencia: momentoOcurrencia,
    estado: estado,
    confirmado: confirmado,
    origen: origen,
    nivel: nivel,
    trazado: trazado,
    votosReal: votosReal ?? this.votosReal,
    votosFalso: votosFalso ?? this.votosFalso,
  );

  /// Texto de «hace cuánto ocurrió» para la ficha del evento (RN-02.7).
  String haceCuanto(DateTime ahora) {
    final minutos = ahora.difference(momentoOcurrencia).inMinutes;
    if (minutos < 1) return 'Hace menos de un minuto';
    if (minutos == 1) return 'Hace 1 minuto';
    return 'Hace $minutos minutos';
  }
}
