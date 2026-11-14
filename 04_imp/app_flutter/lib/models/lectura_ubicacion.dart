import 'coordenada.dart';

/// Una lectura del GPS para la telemetría (RN-04.1): posición, precisión y momento. La velocidad la calcula el motor
/// (RN-04.5); la app no la manda.
class LecturaUbicacion {
  final Coordenada posicion;

  /// Radio de incertidumbre en metros, como lo informa el teléfono.
  final double precisionM;
  final DateTime momento;

  const LecturaUbicacion(this.posicion, this.precisionM, this.momento);

  Map<String, dynamic> toJson() => {
    'ubicacion': {'latitud': posicion.latitud, 'longitud': posicion.longitud},
    'precisionM': precisionM,
    'momento': momento.toUtc().toIso8601String(),
  };
}
