import 'dart:math' as math;

import 'coordenada.dart';
import 'evento_vial.dart';
import 'navegacion.dart';

/// El tráfico como se dibuja en el mapa (#191): las congestiones de la telemetría unidas de donde empiezan a donde
/// acaban, y la ruta pintada de amarillo o de rojo donde pasa por una congestión. Son funciones puras: no tocan el
/// mapa ni el backend, así que se prueban solas.

const String nivelCongestionado = 'CONGESTIONADO';
const String nivelMuyCongestionado = 'MUY_CONGESTIONADO';

/// Una congestión que ya cubre varias cuadras seguidas de la misma calle y del mismo sentido.
class CongestionUnida {
  /// Los eventos que la forman, en el orden de la calle. El primero es el que se abre al tocarla.
  final List<int> ids;
  final String nivel;
  final List<Coordenada> trazado;

  const CongestionUnida({required this.ids, required this.nivel, required this.trazado});

  int get id => ids.first;

  @override
  bool operator ==(Object other) =>
      other is CongestionUnida &&
      _listasIguales(other.ids, ids) &&
      other.nivel == nivel &&
      _listasIguales(other.trazado, trazado);

  @override
  int get hashCode => Object.hash(Object.hashAll(ids), nivel, Object.hashAll(trazado));
}

/// Una parte de la ruta que pasa por una congestión, con su nivel.
class LineaTrafico {
  final List<Coordenada> puntos;
  final String nivel;

  const LineaTrafico({required this.puntos, required this.nivel});

  @override
  bool operator ==(Object other) =>
      other is LineaTrafico && other.nivel == nivel && _listasIguales(other.puntos, puntos);

  @override
  int get hashCode => Object.hash(nivel, Object.hashAll(puntos));

  @override
  String toString() => 'LineaTrafico($nivel, ${puntos.length} puntos)';
}

/// Dos cuadras se tocan si el final de una y el comienzo de la otra están a menos de esta distancia.
const double toleranciaUnionM = 3;

/// Y siguen la misma calle hacia adelante: en la unión el rumbo no gira más de estos grados. Así no se une con la
/// cuadra del otro sentido, que empieza en la misma esquina pero vuelve (D-18).
const double giroMaximoUnion = 60;

/// Un segmento de la ruta va sobre una congestión si su punto medio queda a menos de esta distancia de ella.
const double toleranciaRutaM = 8;

/// Y si los dos van en el mismo sentido: el rumbo no cambia más de estos grados (D-18: la congestión de la
/// telemetría es de un solo sentido).
const double toleranciaRumbo = 35;

/// Las congestiones con trazado se unen cuando una empieza donde termina la otra y tienen el mismo nivel. Así una
/// calle lenta se ve como una sola línea de donde empieza a donde acaba (RN-04.24: la racha ya informa todas las
/// cuadras). Si cambia el nivel o hay un hueco, quedan separadas. Las que no traen trazado no entran.
List<CongestionUnida> unirCongestiones(Iterable<EventoVial> eventos) {
  final lineas = [
    for (final e in eventos)
      if (e.tipo == 'CONGESTION' && e.trazado.length >= 2) e,
  ];
  // Para cada cuadra, la siguiente: la primera que empieza donde ella termina, con su mismo nivel.
  final siguiente = <int, int>{};
  final tieneAnterior = <int>{};
  for (var i = 0; i < lineas.length; i++) {
    for (var j = 0; j < lineas.length; j++) {
      if (i == j || tieneAnterior.contains(j)) continue;
      final a = lineas[i], b = lineas[j];
      if (a.nivel != b.nivel) continue;
      if (distanciaM(a.trazado.last, b.trazado.first) <= toleranciaUnionM && _sigueDeFrente(a.trazado, b.trazado)) {
        siguiente[i] = j;
        tieneAnterior.add(j);
        break;
      }
    }
  }
  final usadas = <int>{};
  final unidas = <CongestionUnida>[];
  void recorrerDesde(int inicio) {
    final ids = <int>[];
    final trazado = <Coordenada>[];
    int? actual = inicio;
    while (actual != null && usadas.add(actual)) {
      final e = lineas[actual];
      ids.add(e.id);
      // El primer punto de la cuadra siguiente es el último de la anterior: no se repite.
      trazado.addAll(trazado.isEmpty ? e.trazado : e.trazado.skip(1));
      actual = siguiente[actual];
    }
    unidas.add(CongestionUnida(ids: ids, nivel: lineas[inicio].nivel ?? nivelCongestionado, trazado: trazado));
  }

  for (var i = 0; i < lineas.length; i++) {
    if (!tieneAnterior.contains(i)) recorrerDesde(i);
  }
  // Lo que quedó es un círculo cerrado de cuadras: se empieza por cualquiera.
  for (var i = 0; i < lineas.length; i++) {
    if (!usadas.contains(i)) recorrerDesde(i);
  }
  return unidas;
}

/// Las partes de la [ruta] que pasan por una congestión, de amarillo o de rojo según su nivel; donde hay dos, manda
/// la peor. Un segmento cuenta si su punto medio está sobre la congestión y va en su mismo sentido. Lo que no pasa
/// por ninguna congestión no se devuelve: la ruta sigue azul ahí.
List<LineaTrafico> pintarRutaPorTrafico(List<Coordenada> ruta, Iterable<CongestionUnida> congestiones) {
  if (ruta.length < 2) return const [];
  final lista = congestiones.where((c) => c.trazado.length >= 2).toList();
  if (lista.isEmpty) return const [];

  final niveles = <String?>[
    for (var i = 0; i + 1 < ruta.length; i++) _nivelDelSegmento(ruta[i], ruta[i + 1], lista),
  ];
  final lineas = <LineaTrafico>[];
  var i = 0;
  while (i < niveles.length) {
    final nivel = niveles[i];
    if (nivel == null) {
      i++;
      continue;
    }
    final puntos = [ruta[i]];
    while (i < niveles.length && niveles[i] == nivel) {
      puntos.add(ruta[i + 1]);
      i++;
    }
    lineas.add(LineaTrafico(puntos: puntos, nivel: nivel));
  }
  return lineas;
}

bool _sigueDeFrente(List<Coordenada> a, List<Coordenada> b) {
  final llegada = rumbo(a[a.length - 2], a.last);
  final salida = rumbo(b.first, b[1]);
  return cambioDeRumbo(llegada, salida).abs() <= giroMaximoUnion;
}

String? _nivelDelSegmento(Coordenada a, Coordenada b, List<CongestionUnida> congestiones) {
  if (distanciaM(a, b) < 0.5) return null;
  final medio = Coordenada((a.latitud + b.latitud) / 2, (a.longitud + b.longitud) / 2);
  final sentido = rumbo(a, b);
  String? nivel;
  for (final c in congestiones) {
    for (var k = 0; k + 1 < c.trazado.length; k++) {
      final p = c.trazado[k], q = c.trazado[k + 1];
      if (distanciaM(p, q) < 0.5) continue;
      if (cambioDeRumbo(sentido, rumbo(p, q)).abs() > toleranciaRumbo) continue;
      if (_distanciaAlSegmentoM(medio, p, q) > toleranciaRutaM) continue;
      if (nivel == null || c.nivel == nivelMuyCongestionado) nivel = c.nivel;
      break;
    }
    if (nivel == nivelMuyCongestionado) return nivel;
  }
  return nivel;
}

/// Distancia de [x] al segmento [p]-[q], en metros, proyectando en un plano local (sobra para una cuadra).
double _distanciaAlSegmentoM(Coordenada x, Coordenada p, Coordenada q) {
  const mPorGrado = 111320.0;
  final cosLat = math.cos(x.latitud * math.pi / 180);
  double ex(Coordenada c) => (c.longitud - x.longitud) * mPorGrado * cosLat;
  double ey(Coordenada c) => (c.latitud - x.latitud) * mPorGrado;
  final px = ex(p), py = ey(p), qx = ex(q), qy = ey(q);
  final dx = qx - px, dy = qy - py;
  final largo2 = dx * dx + dy * dy;
  var t = largo2 == 0 ? 0.0 : -(px * dx + py * dy) / largo2;
  t = t.clamp(0.0, 1.0);
  final cx = px + t * dx, cy = py + t * dy;
  return math.sqrt(cx * cx + cy * cy);
}

bool _listasIguales<T>(List<T> a, List<T> b) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i] != b[i]) return false;
  }
  return true;
}
