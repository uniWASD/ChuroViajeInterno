import 'dart:math' as math;

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/ruta.dart';

/// Rutas inventadas con forma conocida, cerca de la plaza Luis de Fuentes.
const Coordenada esquinaA = Coordenada(-21.5344, -64.7340);

/// Mueve un punto unos metros (aproximación plana, suficiente en unos cientos de metros).
Coordenada desplazar(Coordenada c, {double esteM = 0, double norteM = 0, double surM = 0}) {
  const metrosPorGrado = 111195.08;
  final lat = c.latitud + (norteM - surM) / metrosPorGrado;
  final lon = c.longitud + esteM / (metrosPorGrado * math.cos(c.latitud * math.pi / 180));
  return Coordenada(lat, lon);
}

final Coordenada esquinaB = desplazar(esquinaA, esteM: 300);
final Coordenada esquinaC = desplazar(esquinaB, surM: 200);

/// 300 m al este por Calle Sucre (dos tramos) y 200 m al sur por Avenida Las Américas: un giro a la derecha.
final RutaCalculada rutaEnL = RutaCalculada(
  estado: EstadoRuta.encontrada,
  distanciaM: 500,
  tiempoS: 100,
  trazado: [esquinaA, desplazar(esquinaA, esteM: 150), esquinaB, desplazar(esquinaB, surM: 100), esquinaC],
  tramos: const [
    TramoRuta('osm:1:0:ida', 'Calle Sucre'),
    TramoRuta('osm:1:1:ida', 'Calle Sucre'),
    TramoRuta('osm:2:0:ida', 'Avenida Las Américas'),
  ],
);

/// 300 m al este y 200 m al norte: un giro a la izquierda.
final RutaCalculada rutaHaciaElNorte = RutaCalculada(
  estado: EstadoRuta.encontrada,
  distanciaM: 500,
  tiempoS: 100,
  trazado: [esquinaA, esquinaB, desplazar(esquinaB, norteM: 200)],
  tramos: const [TramoRuta('a', 'Calle Sucre'), TramoRuta('b', 'Calle Bolívar')],
);

/// Siempre al este; a los 200 m la calle cambia de nombre.
final RutaCalculada rutaRecta = RutaCalculada(
  estado: EstadoRuta.encontrada,
  distanciaM: 400,
  tiempoS: 80,
  trazado: [esquinaA, desplazar(esquinaA, esteM: 200), desplazar(esquinaA, esteM: 400)],
  tramos: const [TramoRuta('a', 'Calle Sucre'), TramoRuta('b', 'Calle Colón')],
);
