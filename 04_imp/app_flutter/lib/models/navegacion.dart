import 'dart:math' as math;

import 'coordenada.dart';
import 'ruta.dart';

/// Geometría de la navegación activa (CU-03, D-22): dónde está el conductor sobre el trazado, cuánto le falta y
/// en qué esquina cambia de calle. Todo se calcula en el teléfono con el trazado y los nombres de calle de la ruta.

const double _radioTierraM = 6371008.8;

double _rad(double grados) => grados * math.pi / 180;

/// Distancia sobre la esfera, en metros.
double distanciaM(Coordenada a, Coordenada b) {
  final dLat = _rad(b.latitud - a.latitud);
  final dLon = _rad(b.longitud - a.longitud);
  final h = math.pow(math.sin(dLat / 2), 2) +
      math.cos(_rad(a.latitud)) * math.cos(_rad(b.latitud)) * math.pow(math.sin(dLon / 2), 2);
  return 2 * _radioTierraM * math.asin(math.sqrt(h.toDouble()));
}

/// Rumbo de [a] a [b], en grados desde el norte en sentido horario (0 a 360).
double rumbo(Coordenada a, Coordenada b) {
  final lat1 = _rad(a.latitud), lat2 = _rad(b.latitud);
  final dLon = _rad(b.longitud - a.longitud);
  final y = math.sin(dLon) * math.cos(lat2);
  final x = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dLon);
  return (math.atan2(y, x) * 180 / math.pi + 360) % 360;
}

/// Cuánto cambia el rumbo, entre −180 y 180: positivo a la derecha, negativo a la izquierda.
double cambioDeRumbo(double desde, double hasta) {
  final d = (hasta - desde + 540) % 360 - 180;
  return d == -180 ? 180 : d;
}

/// Dónde cae un punto sobre el trazado.
class Proyeccion {
  /// Segmento del trazado (entre el punto `segmento` y el siguiente).
  final int segmento;

  /// Metros recorridos desde el comienzo del trazado hasta el punto proyectado.
  final double recorridoM;

  /// A cuántos metros del trazado está el punto.
  final double distanciaM;

  const Proyeccion(this.segmento, this.recorridoM, this.distanciaM);
}

/// El trazado de una ruta con las distancias acumuladas de cada punto.
class Trazado {
  final List<Coordenada> puntos;
  final List<double> _acumulado;

  Trazado(List<Coordenada> puntos)
    : puntos = List.unmodifiable(puntos),
      _acumulado = _acumular(puntos);

  static List<double> _acumular(List<Coordenada> puntos) {
    final acumulado = <double>[if (puntos.isNotEmpty) 0];
    for (var i = 1; i < puntos.length; i++) {
      acumulado.add(acumulado.last + distanciaM(puntos[i - 1], puntos[i]));
    }
    return acumulado;
  }

  double get longitudM => _acumulado.isEmpty ? 0 : _acumulado.last;

  /// Metros desde el comienzo hasta el punto [i] del trazado.
  double recorridoHasta(int i) => _acumulado[i];

  /// El punto del trazado a [recorridoM] metros del comienzo.
  Coordenada puntoEn(double recorridoM) {
    if (puntos.length == 1 || recorridoM <= 0) return puntos.first;
    if (recorridoM >= longitudM) return puntos.last;
    var i = 1;
    while (_acumulado[i] < recorridoM) {
      i++;
    }
    final largo = _acumulado[i] - _acumulado[i - 1];
    final t = largo == 0 ? 0.0 : (recorridoM - _acumulado[i - 1]) / largo;
    final a = puntos[i - 1], b = puntos[i];
    return Coordenada(a.latitud + (b.latitud - a.latitud) * t, a.longitud + (b.longitud - a.longitud) * t);
  }

  /// El punto del trazado más cercano a [p]. Con [desdeSegmento] no se busca antes de ese segmento, para que una
  /// ruta que vuelve sobre sí misma no haga retroceder al conductor.
  Proyeccion proyectar(Coordenada p, {int desdeSegmento = 0}) {
    if (puntos.length < 2) return Proyeccion(0, 0, puntos.isEmpty ? 0 : distanciaM(p, puntos.first));
    Proyeccion? mejor;
    for (var i = math.max(0, desdeSegmento); i < puntos.length - 1; i++) {
      final candidata = _proyectarEnSegmento(p, i);
      if (mejor == null || candidata.distanciaM < mejor.distanciaM) mejor = candidata;
    }
    return mejor ?? Proyeccion(puntos.length - 2, longitudM, distanciaM(p, puntos.last));
  }

  /// Proyección en un plano local: en unos cientos de metros el error es despreciable.
  Proyeccion _proyectarEnSegmento(Coordenada p, int i) {
    final a = puntos[i], b = puntos[i + 1];
    final escalaLon = math.cos(_rad(a.latitud));
    final bx = (b.longitud - a.longitud) * escalaLon, by = b.latitud - a.latitud;
    final px = (p.longitud - a.longitud) * escalaLon, py = p.latitud - a.latitud;
    final largo2 = bx * bx + by * by;
    final t = largo2 == 0 ? 0.0 : ((px * bx + py * by) / largo2).clamp(0.0, 1.0);
    final sobre = Coordenada(a.latitud + (b.latitud - a.latitud) * t, a.longitud + (b.longitud - a.longitud) * t);
    final recorrido = _acumulado[i] + (_acumulado[i + 1] - _acumulado[i]) * t;
    return Proyeccion(i, recorrido, distanciaM(p, sobre));
  }
}

enum Giro { izquierda, derecha, recto }

/// Un cambio de calle: dónde está sobre el trazado, a qué calle se entra y hacia qué lado.
class Maniobra {
  final double recorridoM;
  final String calle;
  final Giro giro;

  /// Posición en `tramos` del primer tramo de la calle a la que se entra.
  final int primerTramo;

  const Maniobra({required this.recorridoM, required this.calle, required this.giro, required this.primerTramo});
}

/// Un giro de menos de 30° es seguir derecho.
const double giroMinimoGrados = 30;

/// Se mide el rumbo 15 m antes y 15 m después de cada esquina: así un trazado con muchos puntos cortos no se toma
/// como una curva.
const double _ventanaRumboM = 15;

/// Las esquinas donde la ruta cambia de calle.
///
/// La ruta trae los tramos en orden con su calle, pero no dice en qué punto del trazado empieza cada uno. Los tramos
/// seguidos de la misma calle forman una sola calle; si hay k calles, hay k − 1 cambios, y se ubican en los k − 1
/// puntos del trazado donde más gira el rumbo, en el orden en que aparecen. Una calle que cambia de nombre sin girar,
/// junto a una curva más cerrada, puede quedar mal ubicada; el giro, en cambio, siempre sale de la geometría.
List<Maniobra> planificarManiobras(Trazado trazado, List<TramoRuta> tramos) {
  final calles = <(String, int)>[];
  for (var i = 0; i < tramos.length; i++) {
    if (calles.isEmpty || calles.last.$1 != tramos[i].calle) calles.add((tramos[i].calle, i));
  }
  final cambios = calles.length - 1;
  final n = trazado.puntos.length;
  if (cambios <= 0 || n < 2) return const [];

  final giros = <int, double>{};
  for (var i = 1; i < n - 1; i++) {
    final r = trazado.recorridoHasta(i);
    final antes = trazado.puntoEn(r - _ventanaRumboM);
    final despues = trazado.puntoEn(r + _ventanaRumboM);
    final punto = trazado.puntos[i];
    if (antes == punto || despues == punto) continue;
    giros[i] = cambioDeRumbo(rumbo(antes, punto), rumbo(punto, despues));
  }
  final esquinas = (giros.keys.toList()..sort((a, b) => giros[b]!.abs().compareTo(giros[a]!.abs())))
      .take(cambios)
      .toList()
    ..sort();

  final maniobras = <Maniobra>[];
  for (var j = 0; j < cambios; j++) {
    final (calle, primerTramo) = calles[j + 1];
    if (j < esquinas.length) {
      final grados = giros[esquinas[j]]!;
      maniobras.add(Maniobra(
        recorridoM: trazado.recorridoHasta(esquinas[j]),
        calle: calle,
        giro: grados.abs() < giroMinimoGrados ? Giro.recto : (grados > 0 ? Giro.derecha : Giro.izquierda),
        primerTramo: primerTramo,
      ));
    } else {
      // Un trazado con menos esquinas que calles: el cambio se reparte a lo largo de la ruta.
      maniobras.add(Maniobra(
        recorridoM: trazado.longitudM * (j + 1) / (cambios + 1),
        calle: calle,
        giro: Giro.recto,
        primerTramo: primerTramo,
      ));
    }
  }
  maniobras.sort((a, b) => a.recorridoM.compareTo(b.recorridoM));
  return maniobras;
}

/// «gira a la derecha en Av. Las Américas».
String textoManiobra(Maniobra m) => switch (m.giro) {
  Giro.derecha => 'Gira a la derecha en ${m.calle}',
  Giro.izquierda => 'Gira a la izquierda en ${m.calle}',
  Giro.recto => 'Sigue derecho por ${m.calle}',
};

/// Para la distancia a la próxima esquina: «50 m», «1,2 km».
String textoDistanciaCorta(double metros) => textoDistancia(metros.round());

/// Para comparar con la ruta: el tiempo que falta es proporcional a lo que falta recorrer.
int tiempoProporcional(int tiempoTotalS, double faltaM, double totalM) =>
    totalM <= 0 ? 0 : (tiempoTotalS * (faltaM / totalM)).round();
