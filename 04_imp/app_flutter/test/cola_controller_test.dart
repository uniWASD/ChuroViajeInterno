import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/controllers/cola_controller.dart';
import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/reporte_controller.dart';
import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/reporte_evento.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/services/eventos/servicio_eventos.dart';
import 'package:app_flutter/services/sesion/servicio_sesion.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

/// `POST /eventos` falso: sin red, caído (503), rechazando o aceptando.
class BackendCola {
  final List<Map<String, dynamic>> recibidos = [];
  String modo = 'acepta'; // acepta | sinRed | caido | duplicado | sesionVencida
  int jwt = 0;

  http.Client get cliente => MockClient((pedido) async {
    if (pedido.url.path == '/sesion/google') {
      return http.Response(jsonEncode({'jwt': 'jwt-${++jwt}', 'tokenRenovacion': 'renovacion',
          'expiraEn': DateTime.now().add(const Duration(hours: 1)).toUtc().toIso8601String()}), 200);
    }
    if (pedido.url.path == '/sesion/renovar') {
      return http.Response('{"codigo":"SESION_VENCIDA","mensaje":"Tu sesión venció."}', 401);
    }
    if (pedido.url.path != '/eventos') return http.Response('{}', 200);
    if (modo == 'sesionVencida' && pedido.headers['Authorization'] != 'Bearer jwt-$jwt') {
      return http.Response('{"codigo":"NO_AUTENTICADO","mensaje":"Inicia sesión."}', 401);
    }
    switch (modo) {
      case 'sinRed':
        throw const SocketException('modo avión');
      case 'caido':
        return http.Response('{"codigo":"ERROR_INTERNO","mensaje":"Falló."}', 503);
      case 'duplicado':
        return http.Response.bytes(
            utf8.encode('{"codigo":"REPORTE_DUPLICADO","mensaje":"Ya reportaste este evento hace poco."}'), 409);
    }
    final cuerpo = jsonDecode(pedido.body) as Map<String, dynamic>;
    recibidos.add(cuerpo);
    return http.Response.bytes(utf8.encode(jsonEncode({'id': recibidos.length, 'tipo': cuerpo['tipo'],
        'ubicacion': cuerpo['ubicacion'], 'momentoOcurrencia': '2026-11-01T07:55:00-04:00', 'estado': 'ACTIVO',
        'confirmado': false, 'origen': 'CONDUCTOR'})), 201);
  });
}

void main() {
  late BackendCola backend;
  late DateTime ahora;
  late ColaFalsa almacenCola;
  late ConectividadFalsa red;
  late SesionController sesion;
  late ColaController cola;
  late ServicioEventos servicio;

  Future<void> preparar() async {
    backend = BackendCola();
    ahora = DateTime.utc(2026, 11, 1, 12);
    almacenCola = ColaFalsa();
    red = ConectividadFalsa();
    final almacen = AlmacenFalso.conSesionVigente()..datos[ServicioCatalogos.clave] = catalogosJson;
    final api = ApiClient(client: backend.cliente);
    sesion = SesionController(
      servicio: ServicioSesion(api),
      google: GoogleFalso(),
      almacen: almacen,
      catalogos: ServicioCatalogos(api, almacen),
    );
    api.gestor = sesion;
    await sesion.cargar();
    servicio = ServicioEventos(api);
    cola = ColaController(almacenCola, servicio, sesion,
        reloj: () => ahora, conectividad: red, cadaCuanto: Duration.zero);
  }

  ReporteEvento reporte(String tipo, {required DateTime confirmado, int minutos = 5}) => ReporteEvento(
    tipo: tipo,
    ubicacion: const Coordenada(-21.5355, -64.7296),
    horaConfirmacion: confirmado,
    minutosElegidos: minutos,
  );

  tearDown(() => cola.dispose());

  test('CP-81: en modo avión el reporte se guarda; al volver la red se envía solo y aparece el aviso', () async {
    await preparar();
    final ubicacion = UbicacionFalsa();
    final mapa = MapController(ubicacion);
    await mapa.activarUbicacion();
    ubicacion.emitir(const Coordenada(-21.5355, -64.7296));
    await Future<void>.delayed(Duration.zero);
    final reportes = ReporteController(servicio, sesion, mapa, cola: cola, reloj: () => ahora)..empezar();

    backend.modo = 'sinRed';
    reportes.elegirTipo('ACCIDENTE');
    await reportes.confirmar();
    expect(reportes.estado, EstadoReporte.guardadoEnCola);
    expect(reportes.mensaje, ColaController.mensajeGuardado);
    expect(cola.pendientes, hasLength(1));
    expect(cola.pendientes.single.reporte.horaConfirmacion, ahora);
    expect(cola.pendientes.single.reporte.minutosElegidos, 5);

    backend.modo = 'acepta';
    ahora = ahora.add(const Duration(minutes: 3));
    red.volvioLaRed();
    await Future<void>.delayed(const Duration(milliseconds: 50));

    expect(backend.recibidos.single['tipo'], 'ACCIDENTE');
    expect(backend.recibidos.single['horaConfirmacion'], '2026-11-01T12:00:00.000Z',
        reason: 'la hora en que el conductor confirmó, no la del reenvío');
    expect(cola.pendientes, isEmpty);
    expect(cola.tomarAviso(), contains('Se envió tu reporte de accidente'));
  });

  test('CP-82: un control policial guardado hace 70 minutos no se envía y se avisa que perdió vigencia', () async {
    await preparar();
    await cola.guardar(reporte('CONTROL_POLICIAL', confirmado: ahora.subtract(const Duration(minutes: 70)), minutos: 0));
    await cola.reenviar();

    expect(backend.recibidos, isEmpty);
    expect(cola.pendientes, isEmpty);
    expect(cola.tomarAviso(), 'Tu reporte de control policial no se envió porque perdió vigencia.');
  });

  test('RN-01.14: la vigencia cuenta desde que ocurrió: confirmado hace 50 minutos «hace 10 minutos» ya venció', () async {
    await preparar();
    await cola.guardar(reporte('ACCIDENTE', confirmado: ahora.subtract(const Duration(minutes: 50)), minutos: 10));
    await cola.guardar(reporte('BLOQUEO', confirmado: ahora.subtract(const Duration(minutes: 49)), minutos: 10));
    await cola.reenviar();
    expect(backend.recibidos.map((r) => r['tipo']), ['BLOQUEO']);
  });

  test('CP-83: con el backend caído, el reporte queda como pendiente después de varios intentos', () async {
    await preparar();
    backend.modo = 'caido';
    await cola.guardar(reporte('BLOQUEO', confirmado: ahora));
    for (var i = 0; i < ColaController.intentosParaMostrarPendiente; i++) {
      await cola.reenviar();
    }
    expect(cola.pendientes, hasLength(1), reason: 'nunca se descarta en silencio');
    expect(cola.atascados, hasLength(1));
    expect(cola.avisos, isEmpty);

    backend.modo = 'acepta';
    await cola.reenviar();
    expect(cola.pendientes, isEmpty);
  });

  test('Sin red se detiene en el primero y los demás esperan, en orden', () async {
    await preparar();
    backend.modo = 'sinRed';
    await cola.guardar(reporte('ACCIDENTE', confirmado: ahora));
    await cola.guardar(reporte('BLOQUEO', confirmado: ahora));
    await cola.reenviar();
    expect(almacenCola.filas.map((f) => f.intentos), [1, 0]);

    backend.modo = 'acepta';
    await cola.reenviar();
    expect(backend.recibidos.map((r) => r['tipo']), ['ACCIDENTE', 'BLOQUEO']);
  });

  test('Si el backend lo rechaza (por ejemplo, duplicado), sale de la cola con el aviso del backend', () async {
    await preparar();
    backend.modo = 'duplicado';
    await cola.guardar(reporte('OTRO', confirmado: ahora));
    await cola.reenviar();
    expect(cola.pendientes, isEmpty);
    expect(cola.tomarAviso(), 'Tu reporte de otro no se envió: Ya reportaste este evento hace poco.');
  });

  test('Al cerrar sesión, la cola queda vacía', () async {
    await preparar();
    backend.modo = 'sinRed';
    await cola.guardar(reporte('OTRO', confirmado: ahora));
    await sesion.cerrarSesion();
    expect(almacenCola.filas, isEmpty);
  });

  test('RN-01.16: si la sesión venció, el reporte queda en la cola y se envía solo al volver a iniciar sesión', () async {
    await preparar();
    final ubicacion = UbicacionFalsa();
    final mapa = MapController(ubicacion);
    await mapa.activarUbicacion();
    ubicacion.emitir(const Coordenada(-21.5355, -64.7296));
    await Future<void>.delayed(Duration.zero);
    final reportes = ReporteController(servicio, sesion, mapa, cola: cola, reloj: () => ahora)..empezar();

    backend.modo = 'sesionVencida'; // el backend rechaza el JWT y tampoco deja renovar
    reportes.elegirTipo('BLOQUEO');
    await reportes.confirmar();
    expect(reportes.estado, EstadoReporte.esperandoSesion);
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(almacenCola.filas, hasLength(1), reason: 'queda guardado aunque la app se cierre antes de iniciar sesión');

    await sesion.iniciarConGoogle();
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(backend.recibidos.single['tipo'], 'BLOQUEO');
    expect(almacenCola.filas, isEmpty);
  });
}
