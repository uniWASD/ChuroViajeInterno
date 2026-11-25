import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/views/map/dibujo_en_serie.dart';

/// Los dibujos de una capa del mapa van de a uno: dos cambios seguidos no dejan marcadores o líneas repetidas.
void main() {
  test('Mientras un dibujo está en curso, los pedidos nuevos esperan y se dibuja una sola vez más', () async {
    final serie = DibujoEnSerie();
    final orden = <String>[];
    var enCurso = 0;
    var maximo = 0;
    final primero = Completer<void>();

    Future<void> Function() dibujo(String nombre, {Completer<void>? espera}) => () async {
      enCurso++;
      maximo = enCurso > maximo ? enCurso : maximo;
      orden.add('empieza $nombre');
      if (espera != null) await espera.future;
      await Future<void>.delayed(Duration.zero);
      orden.add('termina $nombre');
      enCurso--;
    };

    final a = serie.pedir(dibujo('A', espera: primero));
    final b = serie.pedir(dibujo('B'));
    final c = serie.pedir(dibujo('C'));
    expect(serie.ocupado, isTrue);
    primero.complete();
    await Future.wait([a, b, c]);

    expect(maximo, 1, reason: 'nunca hay dos dibujos a la vez en la misma capa');
    expect(orden, ['empieza A', 'termina A', 'empieza C', 'termina C'], reason: 'B se salta: C ya lo incluye');
    expect(serie.ocupado, isFalse);
  });

  test('Un dibujo que falla no traba los siguientes', () async {
    final serie = DibujoEnSerie();
    final hechos = <int>[];
    await serie.pedir(() async => throw StateError('el SDK falló'));
    await serie.pedir(() async => hechos.add(1));
    expect(hechos, [1]);
    expect(serie.ocupado, isFalse);
  });

  test('Sin otro dibujo en curso, se dibuja enseguida', () async {
    final serie = DibujoEnSerie();
    var veces = 0;
    await serie.pedir(() async => veces++);
    await serie.pedir(() async => veces++);
    expect(veces, 2);
  });
}
