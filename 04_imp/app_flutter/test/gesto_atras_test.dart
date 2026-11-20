import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/navigation_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

/// #172 (CP-179): el gesto de atrás en la pantalla principal no saca de la app de golpe.
void main() {
  late MapaFalso mapa;
  late int salidas;

  Widget app() {
    mapa = MapaFalso();
    final hace5 = DateTime.now().toUtc().subtract(const Duration(minutes: 5)).toIso8601String();
    final api = ApiClient(client: MockClient((pedido) async {
      if (pedido.url.path != '/eventos') return http.Response('{}', 200);
      return http.Response.bytes(utf8.encode(jsonEncode({
        'marca': 'm1', 'completa': true, 'quitados': [],
        'eventos': [
          {'id': 1, 'tipo': 'ACCIDENTE', 'ubicacion': {'latitud': -21.53, 'longitud': -64.73},
           'momentoOcurrencia': hace5, 'estado': 'ACTIVO', 'confirmado': false, 'origen': 'CONDUCTOR'},
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
    salidas = 0;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (llamada) async {
        if (llamada.method == 'SystemNavigator.pop') salidas++;
        return null;
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, null);
  });

  /// El gesto de atrás, tal como lo entrega Android (botón o gesto predictivo terminado).
  Future<void> atras(WidgetTester tester) async {
    await tester.binding.handlePopRoute();
    await tester.pump();
  }

  NavigationController pestanas(WidgetTester tester) =>
      Provider.of<NavigationController>(tester.element(find.byType(MainScreen)), listen: false);

  testWidgets('con la ficha de un evento abierta, el gesto la cierra y no sale', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    mapa.ultimo!.alTocarEvento!(1);
    await tester.pump();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);

    await atras(tester);
    expect(find.byKey(const Key('ficha-evento')), findsNothing);
    expect(find.text(MainScreen.avisoSalir), findsNothing);
    expect(salidas, 0);
  });

  testWidgets('con un punto elegido en el mapa, el gesto lo quita', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    mapa.ultimo!.alTocar(const Coordenada(-21.535, -64.73));
    await tester.pump();
    expect(find.text('Punto elegido'), findsOneWidget);

    await atras(tester);
    expect(find.text('Punto elegido'), findsNothing);
    final controller = Provider.of<MapController>(tester.element(find.byType(MainScreen)), listen: false);
    expect(controller.puntoSeleccionado, isNull);
    expect(salidas, 0);
  });

  testWidgets('desde otra pestaña, el gesto vuelve a la pestaña Mapa', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Eventos').last);
    await tester.pumpAndSettle();
    expect(pestanas(tester).selectedIndex, AppTab.events.index);

    await atras(tester);
    expect(pestanas(tester).selectedIndex, AppTab.map.index);
    expect(find.text(MainScreen.avisoSalir), findsNothing);
    expect(salidas, 0);
  });

  testWidgets('en Eventos, con el detalle de un evento abierto, el gesto vuelve a la lista', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Eventos').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('evento-1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);

    await atras(tester);
    expect(find.byKey(const Key('ficha-evento')), findsNothing);
    expect(find.byKey(const Key('lista-eventos')), findsOneWidget);
    expect(pestanas(tester).selectedIndex, AppTab.events.index, reason: 'sigue en Eventos');
    expect(salidas, 0);
  });

  testWidgets('en el Mapa avisa «Presiona atrás otra vez para salir» y sale si se repite en 2 segundos', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    await atras(tester);
    expect(find.text(MainScreen.avisoSalir), findsOneWidget);
    expect(salidas, 0, reason: 'el primer gesto solo avisa');

    await tester.pump(const Duration(seconds: 1));
    await atras(tester);
    expect(salidas, 1, reason: 'el segundo gesto, dentro de los 2 segundos, sale de la app');
  });

  testWidgets('si pasan más de 2 segundos, el gesto vuelve a avisar en vez de salir', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    await atras(tester);
    await tester.pump(const Duration(milliseconds: 2100));
    await tester.pumpAndSettle();
    await atras(tester);
    expect(salidas, 0);
    expect(find.text(MainScreen.avisoSalir), findsOneWidget);
  });

  testWidgets('el gesto predictivo solo puede salir cuando de verdad se va a salir', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    PopScope scope() => tester.widget<PopScope>(find
        .descendant(of: find.byType(MainScreen), matching: find.byWidgetPredicate((w) => w is PopScope))
        .first);

    expect(scope().canPop, isFalse, reason: 'sin el primer aviso, Android no muestra la animación de salir');
    await atras(tester);
    expect(scope().canPop, isTrue);
    await tester.tap(find.text('Surtidores').last);
    await tester.pump();
    expect(scope().canPop, isFalse, reason: 'en otra pestaña el gesto vuelve al Mapa');
  });
}
