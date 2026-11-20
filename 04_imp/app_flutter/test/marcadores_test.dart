import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/views/map/marcadores.dart';
import 'package:app_flutter/views/tema/tema.dart';

/// #172: los marcadores del mapa llevan el ícono de su tipo de evento.
void main() {
  test('cada tipo de evento tiene su ícono', () {
    expect(iconoDeEvento('ACCIDENTE'), Icons.warning_amber_rounded);
    expect(iconoDeEvento('CONTROL_POLICIAL'), Icons.local_police_outlined);
    expect(iconoDeEvento('BLOQUEO'), Icons.remove_road);
    expect(iconoDeEvento('CONGESTION'), Icons.traffic_outlined);
    expect(iconoDeEvento('OTRO'), Icons.report_outlined);
    expect(iconoDeEvento('ALGO_NUEVO'), Icons.report_outlined, reason: 'un tipo desconocido no rompe el mapa');
  });

  testWidgets('el marcador es un PNG del tamaño del diseño por la densidad de la pantalla', (tester) async {
    await tester.runAsync(() async {
      final png = await dibujarMarcador(icono: Icons.remove_road, color: Colors.red, confirmado: true, escala: 2);
      expect(png.sublist(1, 4), 'PNG'.codeUnits);
      final imagen = await (await ui.instantiateImageCodec(png)).getNextFrame();
      expect(imagen.image.width, 88);
      expect(imagen.image.height, 112);
    });
  });

  testWidgets('se dibuja una vez por tipo, color y estado', (tester) async {
    await tester.runAsync(() async {
      final m = Marcadores(escala: 1);
      final a = await m.de(tipo: 'ACCIDENTE', color: Colors.red, confirmado: false);
      expect(identical(await m.de(tipo: 'ACCIDENTE', color: Colors.red, confirmado: false), a), isTrue);
      expect(identical(await m.de(tipo: 'ACCIDENTE', color: Colors.red, confirmado: true), a), isFalse);
    });
  });
}
