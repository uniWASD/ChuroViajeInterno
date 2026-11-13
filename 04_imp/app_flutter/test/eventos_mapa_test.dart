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

void main() {
  late MapaFalso mapa;
  late bool backendCaido;

  Widget app() {
    mapa = MapaFalso();
    final hace5 = DateTime.now().toUtc().subtract(const Duration(minutes: 5)).toIso8601String();
    final api = ApiClient(client: MockClient((pedido) async {
      if (pedido.url.path != '/eventos') return http.Response('{}', 200);
      if (backendCaido) throw const SocketException('backend apagado');
      return http.Response.bytes(utf8.encode(jsonEncode({
        'marca': 'm1', 'completa': true, 'quitados': [],
        'eventos': [
          {'id': 1, 'tipo': 'CONTROL_POLICIAL', 'ubicacion': {'latitud': -21.53, 'longitud': -64.73},
           'momentoOcurrencia': hace5, 'estado': 'ACTIVO', 'confirmado': false, 'origen': 'CONDUCTOR'},
          {'id': 2, 'tipo': 'ACCIDENTE', 'ubicacion': {'latitud': -21.54, 'longitud': -64.72},
           'momentoOcurrencia': hace5, 'estado': 'CONFIRMADO', 'confirmado': true, 'origen': 'CONDUCTOR'},
        ],
      })), 200);
    }));
    return MultiProvider(
      providers: createAppProviders(
        apiClient: api,
        ubicacion: UbicacionFalsa(),
        constructorMapa: mapa.construir,
        almacen: AlmacenFalso.conSesionVigente()..datos[ServicioCatalogos.clave] = catalogosJson,
        google: GoogleFalso(),
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

  setUp(() => backendCaido = false);

  testWidgets('RN-02.7 y RN-02.14: cada evento se dibuja con el color de su tipo y el confirmado lleva su marca', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    final marcas = mapa.ultimo!.eventos;
    expect(marcas.map((m) => m.id), [1, 2]);
    expect(marcas[0].color, const Color(0xFF1E88E5), reason: 'control policial, del catálogo');
    expect(marcas[1].color, const Color(0xFFE53935), reason: 'accidente, del catálogo');
    expect(marcas[0].confirmado, isFalse);
    expect(marcas[1].confirmado, isTrue);
  });

  testWidgets('RN-02.7: al tocar un evento se ven su tipo y hace cuánto ocurrió', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    mapa.ultimo!.alTocarEvento!(1);
    await tester.pump();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);
    expect(find.text('Control policial'), findsOneWidget);
    expect(find.textContaining('Hace 5 minutos'), findsOneWidget);

    await tester.tap(find.byTooltip('Cerrar'));
    await tester.pump();
    expect(find.byKey(const Key('ficha-evento')), findsNothing);
  });

  testWidgets('CP-66: con el backend apagado, el mapa conserva los eventos y avisa «No se pudo actualizar eventos»', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(mapa.ultimo!.eventos, hasLength(2));

    backendCaido = true;
    await tester.pump(EventosController.ciclo);
    await tester.pumpAndSettle();
    expect(find.text(EventosController.mensajeSinActualizar), findsOneWidget);
    expect(mapa.ultimo!.eventos, hasLength(2));
  });
}
