import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/models/reporte_evento.dart';
import 'package:app_flutter/models/sesion.dart';
import 'package:app_flutter/services/conectividad/conectividad.dart';
import 'package:app_flutter/services/sesion/almacen_local.dart';
import 'package:app_flutter/services/sesion/proveedor_google.dart';
import 'package:app_flutter/storage/cola_reportes.dart';

/// Almacenamiento en memoria, en lugar del almacenamiento seguro del teléfono.
class AlmacenFalso implements AlmacenLocal {
  final Map<String, String> datos = {};

  AlmacenFalso();

  /// Con una sesión guardada que todavía vale: la app abre directo en el mapa.
  AlmacenFalso.conSesionVigente() {
    final ahora = DateTime.now();
    datos[SesionController.claveSesion] = jsonEncode(Sesion(
      jwt: 'jwt-de-prueba',
      expiraEn: ahora.add(const Duration(days: 1)),
      tokenRenovacion: 'renovacion-de-prueba',
      iniciadaEn: ahora,
    ).toJson());
  }

  @override
  Future<String?> leer(String clave) async => datos[clave];

  @override
  Future<void> guardar(String clave, String valor) async => datos[clave] = valor;

  @override
  Future<void> borrar(String clave) async => datos.remove(clave);

  @override
  Future<void> borrarTodo() async => datos.clear();
}

/// Selector de cuentas de Google falso: devuelve un token, o simula que el conductor lo cerró.
class GoogleFalso implements ProveedorGoogle {
  String? token;
  int pedidos = 0;
  int cierres = 0;

  GoogleFalso({this.token = 'token-de-google'});

  @override
  Future<String> pedirTokenDeIdentidad() async {
    pedidos++;
    final t = token;
    if (t == null) throw const InicioCancelado();
    return t;
  }

  @override
  Future<void> cerrar() async => cierres++;
}

/// Cola de reportes en memoria, en lugar de la base SQLite del teléfono.
class ColaFalsa implements ColaReportes {
  final List<ReportePendiente> filas = [];
  int _siguiente = 1;

  @override
  Future<int> agregar(ReporteEvento reporte) async {
    filas.add(ReportePendiente(id: _siguiente, reporte: reporte, intentos: 0));
    return _siguiente++;
  }

  @override
  Future<List<ReportePendiente>> pendientes() async => List.of(filas);

  @override
  Future<void> quitar(int id) async => filas.removeWhere((f) => f.id == id);

  @override
  Future<void> sumarIntento(int id) async {
    final i = filas.indexWhere((f) => f.id == id);
    if (i >= 0) {
      final f = filas[i];
      filas[i] = ReportePendiente(id: f.id, reporte: f.reporte, intentos: f.intentos + 1);
    }
  }

  @override
  Future<void> vaciar() async => filas.clear();
}

/// Conectividad falsa: la prueba decide cuándo vuelve la red.
class ConectividadFalsa implements Conectividad {
  final StreamController<bool> _cambios = StreamController<bool>.broadcast();

  void volvioLaRed() => _cambios.add(true);
  void seFueLaRed() => _cambios.add(false);

  @override
  Stream<bool> get cambios => _cambios.stream;
}

/// Responde `GET /eventos` con un mapa sin eventos y deja los demás pedidos a [resto].
/// Así las pruebas de pantalla no dependen del refresco de eventos (cada 5 s).
Future<http.Response> Function(http.Request) sinEventos(
  Future<http.Response> Function(http.Request) resto,
) {
  return (pedido) async {
    if (pedido.method != 'GET' || pedido.url.path != '/eventos') return resto(pedido);
    return http.Response(
      jsonEncode({
        'marca': DateTime.now().toUtc().toIso8601String(),
        'completa': true,
        'eventos': [],
        'quitados': [],
      }),
      200,
    );
  };
}
