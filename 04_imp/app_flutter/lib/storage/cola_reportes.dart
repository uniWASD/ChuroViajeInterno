import 'dart:convert';

import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import '../models/reporte_evento.dart';

/// Un reporte guardado sin conexión, con cuántas veces falló su reenvío (HU-02).
class ReportePendiente {
  final int id;
  final ReporteEvento reporte;
  final int intentos;

  const ReportePendiente({required this.id, required this.reporte, required this.intentos});
}

/// La cola de reportes sin conexión (RN-01.13 a RN-01.15). Sobrevive a cerrar y abrir la app.
abstract class ColaReportes {
  Future<int> agregar(ReporteEvento reporte);

  /// Los reportes guardados, en el orden en que se confirmaron.
  Future<List<ReportePendiente>> pendientes();
  Future<void> quitar(int id);
  Future<void> sumarIntento(int id);

  /// Al cerrar sesión no queda ningún reporte guardado.
  Future<void> vaciar();
}

/// La cola en una base SQLite del teléfono, con `sqflite`.
class ColaReportesSqflite implements ColaReportes {
  static const String _tabla = 'reporte_pendiente';

  final DatabaseFactory? _fabrica;
  final String _nombre;
  Future<Database>? _base;

  /// [fabrica] y [nombre] permiten usar otra base en las pruebas (por ejemplo, `sqflite_common_ffi` en memoria).
  ColaReportesSqflite({this._fabrica, this._nombre = 'cola_reportes.db'});

  Future<Database> get _abierta => _base ??= _abrir();

  Future<Database> _abrir() async {
    final fabrica = _fabrica ?? databaseFactory;
    final ruta = _nombre == inMemoryDatabasePath
        ? _nombre
        : p.join(await fabrica.getDatabasesPath(), _nombre);
    return fabrica.openDatabase(
      ruta,
      options: OpenDatabaseOptions(
        version: 1,
        onCreate: (base, _) => base.execute('''
          CREATE TABLE $_tabla (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            datos TEXT NOT NULL,
            intentos INTEGER NOT NULL DEFAULT 0
          )
        '''),
      ),
    );
  }

  @override
  Future<int> agregar(ReporteEvento reporte) async =>
      (await _abierta).insert(_tabla, {'datos': jsonEncode(reporte.toJson()), 'intentos': 0});

  @override
  Future<List<ReportePendiente>> pendientes() async {
    final filas = await (await _abierta).query(_tabla, orderBy: 'id');
    return [
      for (final f in filas)
        ReportePendiente(
          id: f['id'] as int,
          reporte: ReporteEvento.fromJson(jsonDecode(f['datos'] as String) as Map<String, dynamic>),
          intentos: f['intentos'] as int,
        ),
    ];
  }

  @override
  Future<void> quitar(int id) async =>
      (await _abierta).delete(_tabla, where: 'id = ?', whereArgs: [id]);

  @override
  Future<void> sumarIntento(int id) async =>
      (await _abierta).rawUpdate('UPDATE $_tabla SET intentos = intentos + 1 WHERE id = ?', [id]);

  @override
  Future<void> vaciar() async => (await _abierta).delete(_tabla);
}
