import 'coordenada.dart';

/// Un destino que devuelve la búsqueda (D-47): una calle, un lugar conocido o un surtidor.
class LugarEncontrado {
  final String nombre;

  /// CALLE, PLAZA, PARQUE, SALUD, EDUCACION, MERCADO, GOBIERNO, TRANSPORTE, TURISMO, HOTEL, BANCO, COMIDA, DEPORTE,
  /// RELIGION, COMERCIO o SURTIDOR.
  final String categoria;
  final Coordenada ubicacion;

  /// Solo si la app mandó la posición del conductor.
  final int? distanciaM;

  const LugarEncontrado({
    required this.nombre,
    required this.categoria,
    required this.ubicacion,
    this.distanciaM,
  });

  factory LugarEncontrado.fromJson(Map<String, dynamic> json) {
    final ubicacion = json['ubicacion'];
    if (json['nombre'] is! String ||
        json['categoria'] is! String ||
        ubicacion is! Map ||
        ubicacion['latitud'] is! num ||
        ubicacion['longitud'] is! num) {
      throw const FormatException('Lugar con formato inesperado');
    }
    return LugarEncontrado(
      nombre: json['nombre'] as String,
      categoria: json['categoria'] as String,
      ubicacion: Coordenada(
        (ubicacion['latitud'] as num).toDouble(),
        (ubicacion['longitud'] as num).toDouble(),
      ),
      distanciaM: (json['distanciaM'] as num?)?.toInt(),
    );
  }

  /// Cómo se nombra la categoría en la lista de resultados.
  String get nombreCategoria => switch (categoria) {
    'CALLE' => 'Calle',
    'PLAZA' => 'Plaza',
    'PARQUE' => 'Parque',
    'SALUD' => 'Salud',
    'EDUCACION' => 'Educación',
    'MERCADO' => 'Mercado',
    'GOBIERNO' => 'Institución pública',
    'TRANSPORTE' => 'Transporte',
    'TURISMO' => 'Turismo',
    'HOTEL' => 'Hospedaje',
    'BANCO' => 'Banco',
    'COMIDA' => 'Comida',
    'DEPORTE' => 'Deporte',
    'RELIGION' => 'Templo',
    'COMERCIO' => 'Comercio',
    'SURTIDOR' => 'Surtidor',
    _ => 'Lugar',
  };
}

/// La distancia corta para la lista: «350 m», «1,2 km».
String distanciaCorta(int metros) {
  if (metros < 1000) return '$metros m';
  final km = (metros / 1000).toStringAsFixed(1).replaceAll('.', ',');
  return '$km km';
}
