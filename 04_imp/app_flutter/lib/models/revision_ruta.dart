import 'coordenada.dart';
import 'ruta.dart';

/// Respuesta de `POST /rutas/revision` (05_contratos.md, «Revisión de la ruta activa»).
enum EstadoRevision { sinCambios, sinAlternativa, alternativa, rutaDesactualizada }

class RevisionRuta {
  final EstadoRevision estado;

  /// El evento que provocó el aviso; solo en «sin alternativa» y «alternativa».
  final int? eventoId;

  /// Solo en «alternativa»: la ruta nueva, con los mismos campos que `POST /rutas`.
  final RutaCalculada? alternativa;

  /// Costo de la alternativa y de seguir por la ruta actual, en metros ponderados por el tráfico. Si un bloqueo corta
  /// la ruta actual, no viene [costoActual].
  final double? costo;
  final double? costoActual;

  const RevisionRuta({required this.estado, this.eventoId, this.alternativa, this.costo, this.costoActual});

  factory RevisionRuta.fromJson(Map<String, dynamic> json) {
    final estado = switch (json['estado']) {
      'SIN_CAMBIOS' => EstadoRevision.sinCambios,
      'SIN_ALTERNATIVA' => EstadoRevision.sinAlternativa,
      'ALTERNATIVA' => EstadoRevision.alternativa,
      'RUTA_DESACTUALIZADA' => EstadoRevision.rutaDesactualizada,
      final otro => throw FormatException('Estado de revisión desconocido: $otro'),
    };
    final evento = json['eventoId'] as num?;
    if ((estado == EstadoRevision.sinAlternativa || estado == EstadoRevision.alternativa) && evento == null) {
      throw const FormatException('Falta el evento que provocó el aviso');
    }
    return RevisionRuta(
      estado: estado,
      eventoId: evento?.toInt(),
      alternativa: estado == EstadoRevision.alternativa
          ? RutaCalculada.fromJson({...json, 'estado': 'ENCONTRADA'})
          : null,
      costo: (json['costo'] as num?)?.toDouble(),
      costoActual: (json['costoActual'] as num?)?.toDouble(),
    );
  }
}

/// Lo que la app manda al revisar: lo que le falta de la ruta Activa (D-12).
class PedidoRevision {
  final Coordenada posicion;
  final List<String> tramos;
  final Coordenada destino;
  final List<Coordenada> paradas;
  final List<int> eventosAvisados;

  const PedidoRevision({
    required this.posicion,
    required this.tramos,
    required this.destino,
    this.paradas = const [],
    this.eventosAvisados = const [],
  });
}
