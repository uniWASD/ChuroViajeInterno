import 'dart:ui';

/// Un tipo de evento del catálogo: nombre, color, vigencia y si un conductor lo puede reportar (D-06).
class TipoEventoCatalogo {
  final String codigo;
  final String nombre;
  final String color;
  final int vigenciaMinutos;
  final bool reportable;

  const TipoEventoCatalogo({
    required this.codigo,
    required this.nombre,
    required this.color,
    required this.vigenciaMinutos,
    required this.reportable,
  });

  /// El color como lo usa Flutter; el catálogo lo trae como `#RRGGBB`.
  Color get colorFlutter {
    final hex = color.replaceFirst('#', '');
    return Color(int.parse('FF$hex', radix: 16));
  }

  Duration get vigencia => Duration(minutes: vigenciaMinutos);

  factory TipoEventoCatalogo.fromJson(Map<String, dynamic> json) =>
      TipoEventoCatalogo(
        codigo: json['codigo'] as String,
        nombre: json['nombre'] as String,
        color: json['color'] as String,
        vigenciaMinutos: (json['vigenciaMinutos'] as num).toInt(),
        reportable: json['reportable'] as bool,
      );

  Map<String, dynamic> toJson() => {
    'codigo': codigo,
    'nombre': nombre,
    'color': color,
    'vigenciaMinutos': vigenciaMinutos,
    'reportable': reportable,
  };
}

/// Una opción de «hace cuánto ocurrió» del reporte (RN-01.4, D-09).
class TiempoReporte {
  final int minutos;
  final String nombre;
  final bool porDefecto;

  const TiempoReporte({
    required this.minutos,
    required this.nombre,
    required this.porDefecto,
  });

  factory TiempoReporte.fromJson(Map<String, dynamic> json) => TiempoReporte(
    minutos: (json['minutos'] as num).toInt(),
    nombre: json['nombre'] as String,
    porDefecto: json['porDefecto'] as bool,
  );

  Map<String, dynamic> toJson() => {
    'minutos': minutos,
    'nombre': nombre,
    'porDefecto': porDefecto,
  };
}

/// Lo que devuelve `GET /catalogos`. La app lo guarda al iniciar sesión y no usa valores propios (D-06). Los tipos
/// de combustible y los valores de la fila se guardan tal como llegan, para las vistas de surtidores.
class Catalogos {
  final List<TipoEventoCatalogo> tiposEvento;
  final List<TiempoReporte> tiemposReporte;
  final List<dynamic> tiposCombustible;
  final List<dynamic> valoresFila;

  const Catalogos({
    required this.tiposEvento,
    required this.tiemposReporte,
    this.tiposCombustible = const [],
    this.valoresFila = const [],
  });

  /// Los tipos que puede reportar un conductor; la congestión no aparece (RN-01.3).
  List<TipoEventoCatalogo> get reportables =>
      tiposEvento.where((t) => t.reportable).toList();

  TipoEventoCatalogo? tipo(String codigo) {
    for (final t in tiposEvento) {
      if (t.codigo == codigo) return t;
    }
    return null;
  }

  TiempoReporte? get tiempoPorDefecto {
    for (final t in tiemposReporte) {
      if (t.porDefecto) return t;
    }
    return tiemposReporte.isEmpty ? null : tiemposReporte.first;
  }

  factory Catalogos.fromJson(Map<String, dynamic> json) => Catalogos(
    tiposEvento: [
      for (final t in (json['tiposEvento'] as List? ?? const []))
        TipoEventoCatalogo.fromJson(t as Map<String, dynamic>),
    ],
    tiemposReporte: [
      for (final t in (json['tiemposReporte'] as List? ?? const []))
        TiempoReporte.fromJson(t as Map<String, dynamic>),
    ],
    tiposCombustible: List<dynamic>.from(json['tiposCombustible'] as List? ?? const []),
    valoresFila: List<dynamic>.from(json['valoresFila'] as List? ?? const []),
  );

  Map<String, dynamic> toJson() => {
    'tiposEvento': [for (final t in tiposEvento) t.toJson()],
    'tiemposReporte': [for (final t in tiemposReporte) t.toJson()],
    'tiposCombustible': tiposCombustible,
    'valoresFila': valoresFila,
  };
}
