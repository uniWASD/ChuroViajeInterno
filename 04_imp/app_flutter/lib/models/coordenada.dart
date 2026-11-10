/// Un punto geográfico en grados decimales (WGS84).
class Coordenada {
  final double latitud;
  final double longitud;

  const Coordenada(this.latitud, this.longitud);

  @override
  bool operator ==(Object other) =>
      other is Coordenada &&
      other.latitud == latitud &&
      other.longitud == longitud;

  @override
  int get hashCode => Object.hash(latitud, longitud);

  @override
  String toString() =>
      '(${latitud.toStringAsFixed(6)}, ${longitud.toStringAsFixed(6)})';
}

/// Centro de Tarija: plaza Luis de Fuentes. Es donde abre el mapa.
const Coordenada centroTarija = Coordenada(-21.5355, -64.7296);
