import 'coordenada.dart';

/// De dónde viene el dato de un combustible (RN-05.3, RNF-07).
enum OrigenDato {
  whatsapp('WhatsApp'),
  conductores('Conductores'),
  anh('ANH');

  final String nombre;

  const OrigenDato(this.nombre);

  static OrigenDato? desdeCodigo(String? codigo) => switch (codigo) {
    'WHATSAPP' => OrigenDato.whatsapp,
    'CONDUCTORES' => OrigenDato.conductores,
    'ANH' => OrigenDato.anh,
    _ => null,
  };
}

/// Cómo se ve un surtidor de un vistazo, por la barra de color de la lista y el color de su marcador.
enum EstadoGeneral { disponible, mixto, agotado, sinInformacion }

/// El dato vigente de un tipo de combustible en un surtidor, como lo entrega `GET /surtidores` (RN-05.4). Nunca dice
/// quién lo aportó (RN-05.7).
class EstadoCombustible {
  final String tipo;
  final String nombre;
  final bool disponible;
  final OrigenDato? origen;
  final DateTime fecha;

  /// SIN_FILA, CORTA, MEDIA o LARGA; null si no se conoce (P-15).
  final String? fila;

  /// Solo ANH (RN-05.4, D-31).
  final double? litros;
  final String? estadoAnh;
  final DateTime? consultadoEn;

  const EstadoCombustible({
    required this.tipo,
    required this.nombre,
    required this.disponible,
    required this.origen,
    required this.fecha,
    this.fila,
    this.litros,
    this.estadoAnh,
    this.consultadoEn,
  });

  factory EstadoCombustible.fromJson(Map<String, dynamic> json) => EstadoCombustible(
    tipo: json['tipo'] as String,
    nombre: json['nombre'] as String? ?? json['tipo'] as String,
    disponible: json['disponible'] as bool? ?? false,
    origen: OrigenDato.desdeCodigo(json['origen'] as String?),
    fecha: DateTime.parse(json['fecha'] as String),
    fila: json['fila'] as String?,
    litros: (json['litros'] as num?)?.toDouble(),
    estadoAnh: json['estadoAnh'] as String?,
    consultadoEn: json['consultadoEn'] == null ? null : DateTime.parse(json['consultadoEn'] as String),
  );

  /// RN-05.6: un dato de WhatsApp sin tipo sale como Desconocido.
  String get nombreParaMostrar => tipo == 'DESCONOCIDO' ? 'Desconocido' : nombre;

  /// La fila con palabras, o null si no se conoce.
  String? get filaParaMostrar => switch (fila) {
    'SIN_FILA' => 'Sin fila',
    'CORTA' => 'Fila corta',
    'MEDIA' => 'Fila media',
    'LARGA' => 'Fila larga',
    _ => null,
  };

  /// El estado de ANH con palabras: alto, medio o bajo (D-31).
  String? get estadoAnhParaMostrar => switch (estadoAnh) {
    'ALTO' => 'alto',
    'MEDIO' => 'medio',
    'BAJO' => 'bajo',
    _ => null,
  };
}

/// Un surtidor del catálogo con su distancia y sus datos vigentes (RN-05.2 a RN-05.6).
class Surtidor {
  final int id;
  final String nombre;
  final Coordenada ubicacion;

  /// En metros, desde donde se pidió la lista.
  final int? distanciaM;

  /// false si no tiene ningún dato vigente: se muestra «sin información reciente» (RN-05.5).
  final bool informacionReciente;
  final List<EstadoCombustible> combustibles;

  const Surtidor({
    required this.id,
    required this.nombre,
    required this.ubicacion,
    this.distanciaM,
    required this.informacionReciente,
    this.combustibles = const [],
  });

  factory Surtidor.fromJson(Map<String, dynamic> json) {
    final ubicacion = json['ubicacion'] as Map<String, dynamic>;
    return Surtidor(
      id: (json['id'] as num).toInt(),
      nombre: json['nombre'] as String,
      ubicacion: Coordenada((ubicacion['latitud'] as num).toDouble(), (ubicacion['longitud'] as num).toDouble()),
      distanciaM: (json['distanciaM'] as num?)?.toInt(),
      informacionReciente: json['informacionReciente'] as bool? ?? false,
      combustibles: [
        for (final c in (json['combustibles'] as List? ?? const []))
          EstadoCombustible.fromJson(c as Map<String, dynamic>),
      ],
    );
  }

  /// Sin datos vigentes, sin información; todos disponibles, disponible; ninguno, agotado; si no, mixto.
  EstadoGeneral get estadoGeneral {
    if (!informacionReciente || combustibles.isEmpty) return EstadoGeneral.sinInformacion;
    final disponibles = combustibles.where((c) => c.disponible).length;
    if (disponibles == combustibles.length) return EstadoGeneral.disponible;
    if (disponibles == 0) return EstadoGeneral.agotado;
    return EstadoGeneral.mixto;
  }

  /// El dato más reciente de todos sus combustibles, para el «hace 10 min» de la lista.
  EstadoCombustible? get ultimoDato {
    EstadoCombustible? ultimo;
    for (final c in combustibles) {
      if (ultimo == null || c.fecha.isAfter(ultimo.fecha)) ultimo = c;
    }
    return ultimo;
  }

  /// La distancia con palabras: «850 m» o «2,4 km».
  String? get distanciaParaMostrar {
    final d = distanciaM;
    if (d == null) return null;
    if (d < 1000) return '$d m';
    return '${(d / 1000).toStringAsFixed(1).replaceAll('.', ',')} km';
  }
}
