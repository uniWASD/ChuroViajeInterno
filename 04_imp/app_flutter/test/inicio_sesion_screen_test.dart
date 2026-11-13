import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/views/inicio.dart';
import 'package:app_flutter/views/main/main_screen.dart';
import 'package:app_flutter/views/sesion/inicio_sesion_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

void main() {
  late AlmacenFalso almacen;
  late GoogleFalso google;
  late List<String> rutas;

  Widget app() {
    rutas = [];
    final api = ApiClient(
      client: MockClient((pedido) async {
        rutas.add(pedido.url.path);
        return switch (pedido.url.path) {
          '/sesion/google' => http.Response(
            jsonEncode({
              'jwt': 'jwt-1',
              'expiraEn': DateTime.now().add(const Duration(hours: 1)).toUtc().toIso8601String(),
              'tokenRenovacion': 'renovacion-1',
            }),
            200,
          ),
          '/catalogos' => http.Response.bytes(utf8.encode(catalogosJson), 200),
          '/sesion/cerrar' => http.Response('', 204),
          _ => http.Response('{}', 200),
        };
      }),
    );
    return MultiProvider(
      providers: createAppProviders(
        apiClient: api,
        ubicacion: UbicacionFalsa(),
        constructorMapa: MapaFalso().construir,
        almacen: almacen,
        google: google,
      ),
      child: const MaterialApp(
        locale: Locale('es'),
        supportedLocales: [Locale('es')],
        localizationsDelegates: [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        home: Inicio(),
      ),
    );
  }

  setUp(() {
    almacen = AlmacenFalso();
    google = GoogleFalso();
  });

  testWidgets('RN-08.1 y RN-08.2: sin sesión, la única vista es Inicio de sesión con el botón de Google', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    expect(find.byType(InicioSesionScreen), findsOneWidget);
    expect(find.text('Iniciar sesión con Google'), findsOneWidget);
    expect(find.byType(MainScreen), findsNothing);
  });

  testWidgets('Al elegir la cuenta, abre el mapa con la sesión iniciada', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('boton-google')));
    await tester.pumpAndSettle();

    expect(find.byType(MainScreen), findsOneWidget);
    expect(rutas, ['/sesion/google', '/catalogos']);
  });

  testWidgets('CP-38: al cancelar el selector, sigue en Inicio de sesión sin mostrar un error', (tester) async {
    google.token = null;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('boton-google')));
    await tester.pumpAndSettle();

    expect(find.byType(InicioSesionScreen), findsOneWidget);
    expect(find.byKey(const Key('error-sesion')), findsNothing);
  });

  testWidgets('CP-40: «Cerrar sesión» en el menú de la cuenta vuelve a Inicio de sesión sin tokens guardados', (tester) async {
    almacen = AlmacenFalso.conSesionVigente();
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(find.byType(MainScreen), findsOneWidget);

    await tester.tap(find.byKey(const Key('menu-cuenta')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cerrar sesión'));
    await tester.pumpAndSettle();

    expect(find.byType(InicioSesionScreen), findsOneWidget);
    expect(almacen.datos.containsKey(SesionController.claveSesion), isFalse);
    expect(almacen.datos, isEmpty);
    expect(rutas.last, '/sesion/cerrar');
  });
}
