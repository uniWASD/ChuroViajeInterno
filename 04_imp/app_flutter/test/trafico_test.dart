import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/evento_vial.dart';
import 'package:app_flutter/models/trafico.dart';

/// #191: la congestión unida de donde empieza a donde acaba (CP-198) y la ruta pintada por el tráfico (CP-197).
/// Una calle de oeste a este: cada cuadra mide unos 100 m (0,00096 grados de longitud en Tarija).
const double lat = -21.5355;
const double paso = 0.00096;

Coordenada esquina(int n) => Coordenada(lat, -64.7350 + n * paso);

EventoVial congestion(int id, int desde, int hasta, {String nivel = nivelCongestionado}) => EventoVial(
  id: id,
  tipo: 'CONGESTION',
  ubicacion: esquina(desde),
  momentoOcurrencia: DateTime.utc(2026, 11, 24),
  estado: 'ACTIVO',
  confirmado: false,
  origen: 'TELEMETRIA',
  nivel: nivel,
  trazado: desde < hasta
      ? [for (var n = desde; n <= hasta; n++) esquina(n)]
      : [for (var n = desde; n >= hasta; n--) esquina(n)],
);

void main() {
  group('CP-198: unir las cuadras congestionadas', () {
    test('tres cuadras seguidas del mismo nivel son una sola línea, en el orden de la calle', () {
      // Llegan desordenadas, como del backend.
      final unidas = unirCongestiones([congestion(12, 2, 3), congestion(10, 0, 1), congestion(11, 1, 2)]);
      expect(unidas, hasLength(1));
      expect(unidas.single.ids, [10, 11, 12]);
      expect(unidas.single.id, 10, reason: 'al tocarla se abre la primera');
      expect(unidas.single.trazado, [esquina(0), esquina(1), esquina(2), esquina(3)],
          reason: 'el punto donde se tocan no se repite');
      expect(unidas.single.nivel, nivelCongestionado);
    });

    test('si cambia el nivel, quedan separadas', () {
      final unidas = unirCongestiones([
        congestion(10, 0, 1),
        congestion(11, 1, 2, nivel: nivelMuyCongestionado),
      ]);
      expect(unidas.map((u) => u.ids), [
        [10],
        [11],
      ]);
    });

    test('si hay un hueco entre dos cuadras, quedan separadas', () {
      final unidas = unirCongestiones([congestion(10, 0, 1), congestion(11, 2, 3)]);
      expect(unidas, hasLength(2));
    });

    test('el otro sentido de la misma calle no se une (D-18)', () {
      // 10 va hacia el este y 11 empieza donde termina 10, pero vuelve hacia el oeste por la misma cuadra.
      final unidas = unirCongestiones([congestion(10, 0, 1), congestion(11, 1, 0)]);
      expect(unidas, hasLength(2));
    });

    test('las congestiones sin trazado y los demás eventos no entran', () {
      final sinTrazado = EventoVial(
        id: 20,
        tipo: 'CONGESTION',
        ubicacion: esquina(5),
        momentoOcurrencia: DateTime.utc(2026),
        estado: 'ACTIVO',
        confirmado: false,
        origen: 'TELEMETRIA',
        nivel: nivelCongestionado,
      );
      final accidente = EventoVial(
        id: 21,
        tipo: 'ACCIDENTE',
        ubicacion: esquina(6),
        momentoOcurrencia: DateTime.utc(2026),
        estado: 'ACTIVO',
        confirmado: false,
        origen: 'CONDUCTOR',
      );
      expect(unirCongestiones([sinTrazado, accidente]), isEmpty);
    });

    test('un círculo de cuadras se une sin quedarse dando vueltas', () {
      final a = congestion(1, 0, 1);
      final b = EventoVial(
        id: 2,
        tipo: 'CONGESTION',
        ubicacion: esquina(1),
        momentoOcurrencia: DateTime.utc(2026),
        estado: 'ACTIVO',
        confirmado: false,
        origen: 'TELEMETRIA',
        nivel: nivelCongestionado,
        // Un rodeo suave que vuelve a la esquina 0 (una plaza, por ejemplo).
        trazado: [esquina(1), esquina(2), Coordenada(lat - 0.001, -64.7350 + 2.5 * paso),
          Coordenada(lat - 0.0015, -64.7350 + paso), Coordenada(lat - 0.001, -64.7350 - 0.5 * paso), esquina(0)],
      );
      final unidas = unirCongestiones([a, b]);
      expect(unidas, hasLength(1));
      expect(unidas.single.ids.toSet(), {1, 2});
    });
  });

  group('CP-197: pintar la ruta por el tráfico', () {
    final ruta = [for (var n = 0; n <= 6; n++) esquina(n)];

    test('solo la parte de la ruta que pasa por la congestión se pinta, con su nivel', () {
      final unidas = unirCongestiones([congestion(10, 2, 3), congestion(11, 3, 4)]);
      final lineas = pintarRutaPorTrafico(ruta, unidas);
      expect(lineas, [
        LineaTrafico(puntos: [esquina(2), esquina(3), esquina(4)], nivel: nivelCongestionado),
      ]);
    });

    test('amarillo y rojo quedan en partes distintas', () {
      final unidas = unirCongestiones([
        congestion(10, 0, 1),
        congestion(11, 4, 6, nivel: nivelMuyCongestionado),
      ]);
      final lineas = pintarRutaPorTrafico(ruta, unidas);
      expect(lineas.map((l) => l.nivel), [nivelCongestionado, nivelMuyCongestionado]);
      expect(lineas.last.puntos, [esquina(4), esquina(5), esquina(6)]);
    });

    test('la congestión del otro sentido no pinta la ruta (D-18)', () {
      final unidas = unirCongestiones([congestion(10, 3, 1)]);
      expect(pintarRutaPorTrafico(ruta, unidas), isEmpty);
    });

    test('una ruta que solo cruza la calle congestionada no se pinta', () {
      final cruce = [
        Coordenada(lat - 0.001, -64.7350 + 2 * paso),
        Coordenada(lat + 0.001, -64.7350 + 2 * paso),
      ];
      final unidas = unirCongestiones([congestion(10, 1, 3)]);
      expect(pintarRutaPorTrafico(cruce, unidas), isEmpty);
    });

    test('con dos congestiones sobre la misma cuadra manda la peor', () {
      final unidas = [
        CongestionUnida(ids: const [1], nivel: nivelCongestionado, trazado: [esquina(0), esquina(1)]),
        CongestionUnida(ids: const [2], nivel: nivelMuyCongestionado, trazado: [esquina(0), esquina(1)]),
      ];
      expect(pintarRutaPorTrafico(ruta, unidas).single.nivel, nivelMuyCongestionado);
    });

    test('sin congestiones o sin ruta no hay nada que pintar', () {
      expect(pintarRutaPorTrafico(ruta, const []), isEmpty);
      expect(pintarRutaPorTrafico([esquina(0)], unirCongestiones([congestion(1, 0, 1)])), isEmpty);
    });
  });
}
