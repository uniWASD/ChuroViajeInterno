import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/busqueda_controller.dart';
import 'package:app_flutter/controllers/combustible_controller.dart';
import 'package:app_flutter/controllers/eventos_controller.dart';
import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/navigation_controller.dart';
import 'package:app_flutter/controllers/reporte_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'busqueda_test.dart' show lugarJson, respuestaLugares;
import 'sesion_controller_test.dart' show catalogosJson;
import 'surtidores_test.dart' show datoJson, surtidorJson;

/// Al cambiar entre Mapa, Eventos y Surtidores no queda nada abierto de la pestaña anterior, y el mapa no rehace sus
/// cálculos con cada posición del GPS.
void main() {
  late MapaFalso mapa;
  late UbicacionFalsa ubicacion;

  Widget app() {
    mapa = MapaFalso();
    final hace5 = DateTime.now().toUtc().subtract(const Duration(minutes: 5)).toIso8601String();
    final hace10 = DateTime.now().subtract(const Duration(minutes: 10));
    final api = ApiClient(client: MockClient((pedido) async {
      if (pedido.url.path == '/eventos') {
        return http.Response.bytes(utf8.encode(jsonEncode({
          'marca': 'm1', 'completa': true, 'quitados': [],
          'eventos': [
            {'id': 1, 'tipo': 'ACCIDENTE', 'ubicacion': {'latitud': -21.53, 'longitud': -64.73},
             'momentoOcurrencia': hace5, 'estado': 'ACTIVO', 'confirmado': false, 'origen': 'CONDUCTOR'},
          ],
        })), 200);
      }
      if (pedido.url.path == '/surtidores') {
        return http.Response.bytes(utf8.encode(jsonEncode({'surtidores': [
          surtidorJson(1, 'Las Vegas', -21.5235, -64.7248, 850, [
            datoJson('GASOLINA', 'Gasolina', true, 'WHATSAPP', hace10, fila: 'CORTA'),
          ]),
          surtidorJson(2, 'Don Daniel', -21.5133, -64.7382, 2623, []),
        ]})), 200, headers: {'content-type': 'application/json'});
      }
      if (pedido.url.path == '/lugares') {
        return respuestaLugares([lugarJson('Mercado Campesino', 'MERCADO', -21.525, -64.733, 2181)]);
      }
      return http.Response('{}', 200);
    }));
    return MultiProvider(
      providers: createAppProviders(
        apiClient: api,
        ubicacion: ubicacion = UbicacionFalsa(),
        servicioRutas: ServicioRutasFalso(),
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

  T leer<T>(WidgetTester tester) => Provider.of<T>(tester.element(find.byType(MainScreen)), listen: false);

  Future<void> abrir(WidgetTester tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    ubicacion.emitir(const Coordenada(-21.5355, -64.7296));
    await tester.pumpAndSettle();
  }

  Future<void> irA(WidgetTester tester, String pestana) async {
    await tester.tap(find.text(pestana).last);
    await tester.pumpAndSettle();
  }

  testWidgets('el detalle de un surtidor abierto en Surtidores no aparece después en el Mapa', (tester) async {
    await abrir(tester);
    await irA(tester, 'Surtidores');
    await tester.tap(find.byKey(const Key('info-surtidor-1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-surtidor')), findsOneWidget);

    await irA(tester, 'Mapa');
    expect(find.byKey(const Key('hoja-surtidor-mapa')), findsNothing);
    expect(leer<CombustibleController>(tester).seleccionado, isNull);
  });

  testWidgets('el detalle de un evento abierto en el Mapa no tapa la lista de Eventos', (tester) async {
    await abrir(tester);
    mapa.ultimo!.alTocarEvento!(1);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);

    await irA(tester, 'Eventos');
    expect(find.byKey(const Key('lista-eventos')), findsOneWidget);
    expect(find.byKey(const Key('ficha-evento')), findsNothing);
  });

  testWidgets('con un evento abierto antes, «Cómo llegar» desde Surtidores muestra la ruta y no el evento',
      (tester) async {
    await abrir(tester);
    mapa.ultimo!.alTocarEvento!(1);
    await tester.pumpAndSettle();

    await irA(tester, 'Surtidores');
    await tester.tap(find.byKey(const Key('info-surtidor-1')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('como-llegar-surtidor')));
    await tester.pumpAndSettle();

    expect(leer<NavigationController>(tester).selectedIndex, AppTab.map.index);
    expect(find.byKey(const Key('hoja-ruta')), findsOneWidget);
    expect(find.byKey(const Key('ficha-evento')), findsNothing);
  });

  testWidgets('«Notificar evento» se cierra al ir a otra pestaña', (tester) async {
    await abrir(tester);
    await tester.tap(find.byKey(const Key('boton-notificar')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-notificar')), findsOneWidget);

    await irA(tester, 'Eventos');
    await irA(tester, 'Mapa');
    expect(find.byKey(const Key('hoja-notificar')), findsNothing);
    expect(leer<ReporteController>(tester).abierto, isFalse);
  });

  testWidgets('la búsqueda abierta se cierra al cambiar de pestaña', (tester) async {
    await abrir(tester);
    await tester.tap(find.byKey(const Key('campo-busqueda')));
    await tester.pumpAndSettle();
    expect(leer<BusquedaController>(tester).abierta, isTrue);

    await irA(tester, 'Surtidores');
    expect(leer<BusquedaController>(tester).abierta, isFalse);
  });

  testWidgets('el surtidor desplegado en la lista se pliega al salir de Surtidores', (tester) async {
    await abrir(tester);
    await irA(tester, 'Surtidores');
    await tester.tap(find.byKey(const Key('surtidor-1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ir-1')), findsOneWidget);

    await irA(tester, 'Mapa');
    await irA(tester, 'Surtidores');
    expect(find.byKey(const Key('ir-1')), findsNothing);
  });

  testWidgets('el lugar elegido y su ruta se conservan al cambiar de pestaña, como en Google Maps', (tester) async {
    await abrir(tester);
    mapa.ultimo!.alTocar(const Coordenada(-21.535, -64.73));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-lugar')), findsOneWidget);

    await irA(tester, 'Eventos');
    await irA(tester, 'Mapa');
    expect(find.byKey(const Key('hoja-lugar')), findsOneWidget);
  });

  testWidgets('en Eventos, tocar el mapa de fondo no elige un punto escondido', (tester) async {
    await abrir(tester);
    await irA(tester, 'Eventos');
    mapa.ultimo!.alTocar(const Coordenada(-21.535, -64.73));
    await tester.pumpAndSettle();
    expect(leer<MapController>(tester).puntoSeleccionado, isNull);

    await irA(tester, 'Mapa');
    expect(find.byKey(const Key('hoja-lugar')), findsNothing);
  });

  testWidgets('en Eventos, tocar un surtidor del mapa lleva a Surtidores con su detalle', (tester) async {
    await abrir(tester);
    await irA(tester, 'Eventos');
    mapa.ultimo!.alTocarSurtidor!(1);
    await tester.pumpAndSettle();

    expect(leer<NavigationController>(tester).selectedIndex, AppTab.stations.index);
    expect(find.byKey(const Key('ficha-surtidor')), findsOneWidget);
  });

  testWidgets('en Surtidores, tocar un evento del mapa lleva a Eventos con su detalle', (tester) async {
    await abrir(tester);
    await irA(tester, 'Surtidores');
    mapa.ultimo!.alTocarEvento!(1);
    await tester.pumpAndSettle();

    expect(leer<NavigationController>(tester).selectedIndex, AppTab.events.index);
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);
    expect(leer<EventosController>(tester).seleccionado?.id, 1);
  });

  testWidgets('con cada posición del GPS, el mapa recibe las mismas listas de eventos y surtidores', (tester) async {
    await abrir(tester);
    await irA(tester, 'Surtidores');
    final antes = mapa.ultimo!;

    ubicacion.emitir(const Coordenada(-21.5356, -64.7297));
    await tester.pump();
    ubicacion.emitir(const Coordenada(-21.5357, -64.7298));
    await tester.pump();

    final despues = mapa.ultimo!;
    expect(identical(despues.eventos, antes.eventos), isTrue, reason: 'no se vuelven a armar los marcadores');
    expect(identical(despues.surtidores, antes.surtidores), isTrue);
    expect(find.byKey(const Key('lista-surtidores')), findsOneWidget);
  });

  testWidgets('con un surtidor abierto en el Mapa, elegir un resultado de la búsqueda muestra la hoja del lugar',
      (tester) async {
    await abrir(tester);
    mapa.ultimo!.alTocarSurtidor!(1);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-surtidor-mapa')), findsOneWidget);

    await tester.tap(find.byKey(const Key('campo-busqueda')));
    await tester.enterText(find.byKey(const Key('campo-busqueda')), 'mercado');
    await tester.pump(BusquedaController.esperaPorDefecto);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('resultado-0')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('hoja-surtidor-mapa')), findsNothing);
    expect(find.byKey(const Key('hoja-lugar')), findsOneWidget);
    expect(find.textContaining('Mercado Campesino'), findsWidgets);
  });
}
