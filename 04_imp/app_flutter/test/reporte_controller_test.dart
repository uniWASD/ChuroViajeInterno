import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/reporte_controller.dart';
import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/sesion.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/services/eventos/servicio_eventos.dart';
import 'package:app_flutter/services/sesion/servicio_sesion.dart';
import 'package:app_flutter/services/ubicacion/servicio_ubicacion.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

/// Backend falso para el reporte: sesión, catálogos y `POST /eventos`, con el rechazo que se le pida.
class BackendReportes {
  final List<http.Request> pedidos = [];
  DateTime ahora = DateTime.utc(2026, 11, 1, 12);
  bool sinRed = false;
  int estadoRenovar = 200;
  Map<String, dynamic>? rechazo;
  int? estadoRechazo;
  int jwt = 0;

  http.Client get cliente => MockClient((pedido) async {
    pedidos.add(pedido);
    if (sinRed) throw const SocketException('sin red');
    final autorizado = pedido.headers['Authorization'] == 'Bearer jwt-$jwt';
    switch (pedido.url.path) {
      case '/sesion/google':
        return _json({'jwt': 'jwt-${++jwt}', 'expiraEn': ahora.add(const Duration(hours: 1)).toIso8601String(),
            'tokenRenovacion': 'renovacion'}, 200);
      case '/sesion/renovar':
        if (estadoRenovar != 200) return _json({'codigo': 'SESION_VENCIDA', 'mensaje': 'Venció.'}, estadoRenovar);
        return _json({'jwt': 'jwt-${++jwt}', 'expiraEn': ahora.add(const Duration(hours: 1)).toIso8601String()}, 200);
      case '/catalogos':
        return http.Response.bytes(utf8.encode(catalogosJson), 200);
      case '/eventos':
        if (!autorizado) return _json({'codigo': 'NO_AUTENTICADO', 'mensaje': 'Inicia sesión.'}, 401);
        if (rechazo != null) return _json(rechazo!, estadoRechazo!);
        final cuerpo = jsonDecode(pedido.body) as Map<String, dynamic>;
        return _json({'id': 7, 'tipo': cuerpo['tipo'], 'ubicacion': cuerpo['ubicacion'],
            'momentoOcurrencia': '2026-11-01T07:55:00-04:00', 'estado': 'ACTIVO', 'confirmado': false,
            'origen': 'CONDUCTOR'}, 201);
      default:
        return _json({}, 200);
    }
  });

  static http.Response _json(Object cuerpo, int estado) =>
      http.Response.bytes(utf8.encode(jsonEncode(cuerpo)), estado);

  List<Map<String, dynamic>> reportesEnviados() => [
    for (final p in pedidos)
      if (p.url.path == '/eventos') jsonDecode(p.body) as Map<String, dynamic>,
  ];
}

void main() {
  late BackendReportes backend;
  late AlmacenFalso almacen;
  late SesionController sesion;
  late UbicacionFalsa ubicacion;
  late MapController mapa;
  late ReporteController reporte;

  Future<void> preparar({bool conGps = true}) async {
    backend = BackendReportes();
    almacen = AlmacenFalso();
    final api = ApiClient(client: backend.cliente);
    sesion = SesionController(
      servicio: ServicioSesion(api),
      google: GoogleFalso(),
      almacen: almacen,
      catalogos: ServicioCatalogos(api, almacen),
      reloj: () => backend.ahora,
    );
    api.gestor = sesion;
    await sesion.cargar();
    await sesion.iniciarConGoogle();
    ubicacion = UbicacionFalsa(permiso: conGps ? PermisoUbicacion.concedido : PermisoUbicacion.servicioApagado);
    mapa = MapController(ubicacion);
    await mapa.activarUbicacion();
    if (conGps) {
      ubicacion.emitir(const Coordenada(-21.5355, -64.7296));
      await Future<void>.delayed(Duration.zero);
    }
    reporte = ReporteController(ServicioEventos(api), sesion, mapa, reloj: () => backend.ahora)..empezar();
  }

  test('RN-01.3 y RN-01.4: ofrece los tipos reportables, sin congestión, y «Hace 5 minutos» marcado', () async {
    await preparar();
    expect(reporte.tipos.map((t) => t.nombre), ['Accidente', 'Control policial', 'Bloqueo', 'Otro']);
    expect(reporte.tiempos.map((t) => t.nombre),
        ['Ahora mismo', 'Hace 5 minutos', 'Hace 10 minutos', 'Hace 30 minutos']);
    expect(reporte.minutos, 5);
    expect(reporte.puedeConfirmar, isFalse);
  });

  test('Envía tipo, ubicación del GPS, hora de confirmación y minutos; nunca el momento del evento', () async {
    await preparar();
    reporte.elegirTipo('CONTROL_POLICIAL');
    await reporte.confirmar();

    expect(reporte.estado, EstadoReporte.enviado);
    expect(reporte.mensaje, ReporteController.mensajeEnviado);
    expect(backend.reportesEnviados().single, {
      'tipo': 'CONTROL_POLICIAL',
      'ubicacion': {'latitud': -21.5355, 'longitud': -64.7296},
      'horaConfirmacion': '2026-11-01T12:00:00.000Z',
      'minutosElegidos': 5,
    });
  });

  test('CP-58: con el GPS apagado, el conductor ajusta el punto en el mapa y el reporte se envía con ese punto', () async {
    await preparar(conGps: false);
    reporte.elegirTipo('BLOQUEO');
    reporte.elegirTiempo(10);
    await reporte.confirmar();
    expect(reporte.estado, EstadoReporte.necesitaPunto);
    expect(backend.reportesEnviados(), isEmpty);

    reporte.usarPunto(const Coordenada(-21.53, -64.73));
    await reporte.confirmar();
    expect(reporte.estado, EstadoReporte.enviado);
    expect(backend.reportesEnviados().single['ubicacion'], {'latitud': -21.53, 'longitud': -64.73});
    expect(backend.reportesEnviados().single['minutosElegidos'], 10);
  });

  test('Muestra el mensaje del backend cuando el reporte es duplicado, supera el límite, está fuera o venció', () async {
    await preparar();
    backend.rechazo = {'codigo': 'REPORTE_DUPLICADO', 'mensaje': 'Ya reportaste este evento hace poco.'};
    backend.estadoRechazo = 409;
    reporte.elegirTipo('ACCIDENTE');
    await reporte.confirmar();
    expect(reporte.estado, EstadoReporte.rechazado);
    expect(reporte.mensaje, 'Ya reportaste este evento hace poco.');

    backend.rechazo = {'codigo': 'LIMITE_DE_REPORTES', 'mensaje': 'Hiciste muchos reportes seguidos.'};
    backend.estadoRechazo = 429;
    await reporte.confirmar();
    expect(reporte.mensaje, 'Hiciste muchos reportes seguidos.');
  });

  test('Sin conexión avisa que no hay red y conserva lo que el conductor eligió', () async {
    await preparar();
    reporte.elegirTipo('OTRO');
    reporte.elegirTiempo(30);
    backend.sinRed = true;
    await reporte.confirmar();

    expect(reporte.estado, EstadoReporte.sinRed);
    expect(reporte.mensaje, contains('conexión'));
    expect(reporte.tipo, 'OTRO');
    expect(reporte.minutos, 30);
  });

  test('CP-59: con el JWT vencido y el token de renovación vigente, el reporte se envía sin pedir iniciar sesión', () async {
    await preparar();
    backend.ahora = backend.ahora.add(const Duration(hours: 2));
    reporte.elegirTipo('ACCIDENTE');
    await reporte.confirmar();

    expect(reporte.estado, EstadoReporte.enviado);
    expect(sesion.conSesion, isTrue);
    expect([for (final p in backend.pedidos) p.url.path].sublist(2), ['/sesion/renovar', '/eventos']);
  });

  test('CP-60: con la renovación también vencida, pide iniciar sesión y después envía el reporte sin volver a llenarlo', () async {
    await preparar();
    backend.ahora = backend.ahora.add(const Duration(hours: 2));
    backend.estadoRenovar = 401;
    reporte.elegirTipo('CONTROL_POLICIAL');
    await reporte.confirmar();

    expect(reporte.estado, EstadoReporte.esperandoSesion);
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(backend.reportesEnviados(), isEmpty);

    await sesion.iniciarConGoogle();
    await Future<void>.delayed(Duration.zero);
    expect(reporte.estado, EstadoReporte.enviado);
    final enviado = backend.reportesEnviados().single;
    expect(enviado['tipo'], 'CONTROL_POLICIAL');
    expect(enviado['horaConfirmacion'], '2026-11-01T14:00:00.000Z', reason: 'la hora de la confirmación original');
  });

  testWidgets('CP-57: desde el mapa, el reporte se completa en 3 toques: «Reportar evento», el tipo y «Confirmar»', (tester) async {
    final enviados = <Map<String, dynamic>>[];
    final api = ApiClient(client: MockClient(sinEventos((pedido) async {
      if (pedido.url.path == '/eventos') {
        enviados.add(jsonDecode(pedido.body) as Map<String, dynamic>);
        return http.Response.bytes(utf8.encode(jsonEncode({'id': 1, 'tipo': 'CONTROL_POLICIAL',
            'ubicacion': {'latitud': -21.5355, 'longitud': -64.7296},
            'momentoOcurrencia': '2026-11-01T07:55:00-04:00', 'estado': 'ACTIVO', 'confirmado': false,
            'origen': 'CONDUCTOR'})), 201);
      }
      return http.Response('{}', 200);
    })));
    final almacenWidget = AlmacenFalso.conSesionVigente()..datos[ServicioCatalogos.clave] = catalogosJson;
    final ubicacionWidget = UbicacionFalsa();
    await tester.pumpWidget(MultiProvider(
      providers: createAppProviders(
        apiClient: api,
        ubicacion: ubicacionWidget,
        constructorMapa: MapaFalso().construir,
        almacen: almacenWidget,
        google: GoogleFalso(),
        cola: ColaFalsa(),
        conectividad: ConectividadFalsa(),
      ),
      child: const MaterialApp(
        locale: Locale('es'),
        supportedLocales: [Locale('es')],
        localizationsDelegates: [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        home: MainScreen(),
      ),
    ));
    await tester.pumpAndSettle();
    ubicacionWidget.emitir(const Coordenada(-21.5355, -64.7296));
    await tester.pump();

    await tester.tap(find.text('Reportar evento')); // 1
    await tester.pumpAndSettle();
    await tester.tap(find.text('Control policial')); // 2
    await tester.pump();
    await tester.tap(find.byKey(const Key('confirmar-reporte'))); // 3
    await tester.pumpAndSettle();

    expect(enviados.single['tipo'], 'CONTROL_POLICIAL');
    expect(enviados.single['minutosElegidos'], 5);
    expect(find.text(ReporteController.mensajeEnviado), findsOneWidget);
    expect(find.text('Cómo llegar'), findsOneWidget, reason: 'vuelve al mapa');
  });

  test('El modelo de sesión que se guarda incluye cuándo se inició, para contar los 30 días', () {
    final s = Sesion(jwt: 'a', expiraEn: DateTime.utc(2026), tokenRenovacion: 'b', iniciadaEn: DateTime.utc(2026));
    expect(Sesion.fromJson(s.toJson()).iniciadaEn, DateTime.utc(2026));
  });
}
