import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/reporte_evento.dart';
import 'package:app_flutter/storage/cola_reportes.dart';

/// HU-02 (#61): la cola de reportes en SQLite, con la base de escritorio de `sqflite_common_ffi`.
void main() {
  late Directory carpeta;
  late List<ColaReportesSqflite> abiertas;

  setUpAll(sqfliteFfiInit);
  setUp(() async {
    carpeta = await Directory.systemTemp.createTemp('cola_reportes');
    abiertas = [];
  });
  tearDown(() async {
    for (final base in abiertas) {
      await base.cerrar(); // en Windows no se puede borrar un SQLite abierto
    }
    await carpeta.delete(recursive: true);
  });

  ColaReportesSqflite abrir() {
    final base = ColaReportesSqflite(
      fabrica: databaseFactoryFfi,
      nombre: '${carpeta.path}/cola.db',
    );
    abiertas.add(base);
    return base;
  }

  ReporteEvento reporte(String tipo, int minutos) => ReporteEvento(
    tipo: tipo,
    ubicacion: const Coordenada(-21.5355, -64.7296),
    horaConfirmacion: DateTime.utc(2026, 11, 1, 12),
    minutosElegidos: minutos,
  );

  test('RN-01.13: guarda el reporte con su hora de confirmación y sus minutos, en orden', () async {
    final cola = abrir();
    await cola.agregar(reporte('ACCIDENTE', 5));
    await cola.agregar(reporte('BLOQUEO', 10));
    expect(await File('${carpeta.path}/cola.db').exists(), isTrue,
        reason: 'la ruta absoluta de SQLite debe respetarse en Windows y Linux');

    final pendientes = await cola.pendientes();
    expect(pendientes.map((p) => p.reporte.tipo), ['ACCIDENTE', 'BLOQUEO']);
    expect(pendientes.first.reporte.horaConfirmacion, DateTime.utc(2026, 11, 1, 12));
    expect(pendientes.last.reporte.minutosElegidos, 10);
    expect(pendientes.first.intentos, 0);
  });

  test('La cola sobrevive a cerrar y abrir la app', () async {
    final antes = abrir();
    await antes.agregar(reporte('OTRO', 0));
    final idSegundo = await antes.agregar(reporte('ACCIDENTE', 30));
    await antes.sumarIntento(idSegundo);

    final despues = abrir(); // otra instancia sobre el mismo archivo, como al volver a abrir la app
    final pendientes = await despues.pendientes();
    expect(pendientes.map((p) => p.reporte.tipo), ['OTRO', 'ACCIDENTE']);
    expect(pendientes.last.intentos, 1);
  });

  test('Quitar uno y vaciar la cola', () async {
    final cola = abrir();
    final id = await cola.agregar(reporte('OTRO', 0));
    await cola.agregar(reporte('BLOQUEO', 5));
    await cola.quitar(id);
    expect((await cola.pendientes()).map((p) => p.reporte.tipo), ['BLOQUEO']);
    await cola.vaciar();
    expect(await cola.pendientes(), isEmpty);
  });
}
