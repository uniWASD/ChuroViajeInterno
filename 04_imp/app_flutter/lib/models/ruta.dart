import 'coordenada.dart';

/// Resultado de `POST /rutas` (05_contratos.md, «Rutas»).
enum EstadoRuta { encontrada, sinRuta, puntoInvalido }

class TramoRuta {
  final String id;
  final String calle;

  const TramoRuta(this.id, this.calle);
}

class RutaCalculada {
  static const String mensajeSinRuta = 'No se encontró una ruta válida';
  static const String mensajePuntoInvalido = 'Origen o destino inválido';

  final EstadoRuta estado;

  /// Para `SIN_RUTA` y `PUNTO_INVALIDO`: lo que se le muestra al conductor (RN-03.3, RN-03.12).
  final String? mensaje;
  final int distanciaM;
  final int tiempoS;
  final List<Coordenada> trazado;

  /// En el orden de la ruta; sus `id` se guardan para revisar la ruta (D-12).
  final List<TramoRuta> tramos;

  const RutaCalculada({
    required this.estado,
    this.mensaje,
    this.distanciaM = 0,
    this.tiempoS = 0,
    this.trazado = const [],
    this.tramos = const [],
  });

  bool get encontrada => estado == EstadoRuta.encontrada;

  factory RutaCalculada.fromJson(Map<String, dynamic> json) {
    final estado = switch (json['estado']) {
      'ENCONTRADA' => EstadoRuta.encontrada,
      'SIN_RUTA' => EstadoRuta.sinRuta,
      'PUNTO_INVALIDO' => EstadoRuta.puntoInvalido,
      final otro => throw FormatException('Estado de ruta desconocido: $otro'),
    };
    return RutaCalculada(
      estado: estado,
      mensaje:
          json['mensaje'] as String? ??
          switch (estado) {
            EstadoRuta.sinRuta => mensajeSinRuta,
            EstadoRuta.puntoInvalido => mensajePuntoInvalido,
            EstadoRuta.encontrada => null,
          },
      distanciaM: (json['distanciaM'] as num?)?.round() ?? 0,
      tiempoS: (json['tiempoS'] as num?)?.round() ?? 0,
      trazado: [
        for (final p in (json['trazado'] as List<dynamic>? ?? const []))
          Coordenada(
            (p['latitud'] as num).toDouble(),
            (p['longitud'] as num).toDouble(),
          ),
      ],
      tramos: [
        for (final t in (json['tramos'] as List<dynamic>? ?? const []))
          TramoRuta(t['id'] as String, t['calle'] as String),
      ],
    );
  }
}

/// «750 m», «1,5 km».
String textoDistancia(int metros) {
  if (metros < 1000) return '${(metros / 10).round() * 10} m';
  final km = (metros / 100).round() / 10;
  return '${km.toStringAsFixed(km == km.roundToDouble() ? 0 : 1).replaceAll('.', ',')} km';
}

/// «3 min», «1 h 5 min». Se redondea hacia arriba: nunca promete llegar antes.
String textoTiempo(int segundos) {
  final minutos = (segundos / 60).ceil().clamp(1, 1 << 30);
  if (minutos < 60) return '$minutos min';
  final h = minutos ~/ 60;
  final m = minutos % 60;
  return m == 0 ? '$h h' : '$h h $m min';
}
