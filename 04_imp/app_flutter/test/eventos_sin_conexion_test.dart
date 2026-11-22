import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/eventos_controller.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

/// HU-04 (#62): los últimos eventos quedan guardados en el teléfono y se ven sin conexión (RN-02.9).
void main() {
  late MapaFalso mapa;
  late AlmacenFalso almacen;
  late bool sinRed;
  late List<Map<String, Object?>> vigentes;

  final hace5 = DateTime.now().toUtc().subtract(const Duration(minutes: 5)).toIso8601String();
  Map<String, Object?> evento(int id, String tipo, double lat) => {
    'id': id, 'tipo': tipo, 'ubicacion': {'latitud': lat, 'longitud': -64.73}, 'momentoOcurrencia': hace5,
    'estado': 'ACTIVO', 'confirmado': false, 'origen': 'CONDUCTOR', 'votosReal': 1, 'votosFalso': 0,
  };

  Widget app() {
    mapa = MapaFalso();
    final api = ApiClient(client: MockClient((pedido) async {
      if (sinRed) throw const SocketException('modo avión');
      if (pedido.url.path != '/eventos') return http.Response('{}', 200);
      return http.Response.bytes(utf8.encode(jsonEncode({
        'marca': DateTime.now().toUtc().toIso8601String(), 'completa': true, 'quitados': [], 'eventos': vigentes,
      })), 200);
    }));
    return MultiProvider(
      providers: createAppProviders(
        apiClient: api,
        ubicacion: UbicacionFalsa(),
        constructorMapa: mapa.construir,
        servicioRutas: ServicioRutasFalso(),
        almacen: almacen,
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
    );
  }

  setUp(() {
    almacen = AlmacenFalso.conSesionVigente()..datos[ServicioCatalogos.clave] = catalogosJson;
    sinRed = false;
    vigentes = [evento(1, 'ACCIDENTE', -21.53), evento(2, 'CONTROL_POLICIAL', -21.54), evento(3, 'BLOQUEO', -21.55)];
  });

  /// Abre la app con conexión, deja que consulte y la cierra (el almacén del teléfono queda).
  Future<void> usarConConexionYCerrar(WidgetTester tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(mapa.ultimo!.eventos, hasLength(3));
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  }

  final marca = find.byKey(const Key('aviso-desactualizados'));

  testWidgets('Cada consulta exitosa guarda en el teléfono los eventos recibidos', (tester) async {
    await usarConConexionYCerrar(tester);
    final guardado = jsonDecode(almacen.datos[EventosController.claveGuardados]!) as Map<String, dynamic>;
    expect((guardado['eventos'] as List).map((e) => (e as Map)['id']), [1, 2, 3]);
    expect(guardado['guardadoEn'], isNotNull);
  });

  testWidgets('CP-84: se cierra la app con 3 eventos en el mapa, se abre en modo avión y se ven los 3 con la marca de desactualizados', (tester) async {
    await usarConConexionYCerrar(tester);

    sinRed = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    expect(mapa.ultimo!.eventos.map((m) => m.id), [1, 2, 3]);
    expect(marca, findsOneWidget);
    expect(find.text('Sin conexión: eventos guardados'), findsOneWidget);
    expect(find.text(EventosController.mensajeSinActualizar), findsNothing, reason: 'una sola marca, la de RN-02.9');

    // En la lista también, con su marca.
    await tester.tap(find.text('Eventos').last);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('evento-1')), findsOneWidget);
    expect(find.byKey(const Key('evento-2')), findsOneWidget);
    expect(find.byKey(const Key('evento-3')), findsOneWidget);
    expect(marca, findsOneWidget);
  });

  testWidgets('CP-85: al volver la red, la marca desaparece y el mapa muestra los vigentes', (tester) async {
    await usarConConexionYCerrar(tester);
    sinRed = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(marca, findsOneWidget);

    // Mientras tanto, el evento 2 se retiró y apareció el 4.
    vigentes = [evento(1, 'ACCIDENTE', -21.53), evento(3, 'BLOQUEO', -21.55), evento(4, 'OTRO', -21.56)];
    sinRed = false;
    await tester.pump(EventosController.ciclo);
    await tester.pumpAndSettle();

    expect(marca, findsNothing);
    expect(mapa.ultimo!.eventos.map((m) => m.id), [1, 3, 4]);
    final guardado = jsonDecode(almacen.datos[EventosController.claveGuardados]!) as Map<String, dynamic>;
    expect((guardado['eventos'] as List).map((e) => (e as Map)['id']), [1, 3, 4], reason: 'lo guardado se reemplaza');
  });

  testWidgets('Con conexión desde el inicio no hay marca: mandan los datos frescos', (tester) async {
    await usarConConexionYCerrar(tester);
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(marca, findsNothing);
  });

  testWidgets('Un evento guardado que ya venció según el catálogo no se dibuja', (tester) async {
    final hace3h = DateTime.now().toUtc().subtract(const Duration(hours: 3)).toIso8601String();
    almacen.datos[EventosController.claveGuardados] = jsonEncode({
      'guardadoEn': hace3h,
      'eventos': [
        {...evento(1, 'ACCIDENTE', -21.53), 'momentoOcurrencia': hace3h},
        evento(2, 'CONTROL_POLICIAL', -21.54),
      ],
    });
    sinRed = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(mapa.ultimo!.eventos.map((m) => m.id), [2]);
    expect(marca, findsOneWidget);
  });

  testWidgets('Sin nada guardado y sin red, no hay marca de desactualizados (solo el aviso de RN-02.8)', (tester) async {
    sinRed = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(marca, findsNothing);
    expect(find.text(EventosController.mensajeSinActualizar), findsOneWidget);
  });
}
