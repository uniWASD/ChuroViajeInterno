import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/busqueda_controller.dart';
import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/navigation_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/lugar.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/busqueda/servicio_busqueda.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';

Map<String, dynamic> lugarJson(String nombre, String categoria, double lat, double lon, [int? distancia]) => {
  'nombre': nombre,
  'categoria': categoria,
  'ubicacion': {'latitud': lat, 'longitud': lon},
  if (distancia != null) 'distanciaM': distancia,
};

http.Response respuestaLugares(List<Map<String, dynamic>> lugares) =>
    http.Response.bytes(utf8.encode(jsonEncode({'lugares': lugares})), 200,
        headers: {'content-type': 'application/json'});

/// Un servicio que responde cuando la prueba quiere, para ver respuestas que llegan tarde.
class ServicioBusquedaManual implements ServicioBusqueda {
  final pedidos = <String>[];
  final cercas = <Coordenada?>[];
  final respuestas = <String, List<LugarEncontrado>>{};
  Object? error;

  @override
  Future<List<LugarEncontrado>> buscar(String texto, {Coordenada? cerca}) async {
    pedidos.add(texto);
    cercas.add(cerca);
    if (error != null) throw error!;
    // La primera búsqueda tarda más que la segunda.
    await Future<void>.delayed(Duration(milliseconds: texto == 'sa' ? 300 : 10));
    return respuestas[texto] ?? const [];
  }
}

void main() {
  group('CP-190: BusquedaController', () {
    late ServicioBusquedaManual servicio;
    Coordenada? posicion;
    late BusquedaController busqueda;

    setUp(() {
      servicio = ServicioBusquedaManual();
      posicion = null;
      busqueda = BusquedaController(servicio, posicion: () => posicion, espera: const Duration(milliseconds: 50));
    });

    tearDown(() => busqueda.dispose());

    test('con menos de 2 letras no busca', () async {
      busqueda.escribir('a');
      busqueda.escribir('¿? ');
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(servicio.pedidos, isEmpty);
      expect(busqueda.buscando, isFalse);
      expect(BusquedaController.alcanza('ñu'), isTrue);
      expect(BusquedaController.alcanza('a.'), isFalse);
    });

    test('espera a que termine de escribir y busca una sola vez, con la posición', () async {
      posicion = const Coordenada(-21.53, -64.73);
      servicio.respuestas['colon'] = const [
        LugarEncontrado(nombre: 'Calle Colón', categoria: 'CALLE', ubicacion: Coordenada(-21.534, -64.731)),
      ];
      for (final t in ['co', 'col', 'colo', 'colon']) {
        busqueda.escribir(t);
      }
      expect(busqueda.buscando, isTrue);
      await Future<void>.delayed(const Duration(milliseconds: 120));
      expect(servicio.pedidos, ['colon']);
      expect(servicio.cercas.single, posicion);
      expect(busqueda.resultados.single.nombre, 'Calle Colón');
      expect(busqueda.buscando, isFalse);
      expect(busqueda.mensaje, isNull);
    });

    test('una respuesta que llega tarde no pisa la búsqueda nueva', () async {
      servicio.respuestas['sa'] = const [
        LugarEncontrado(nombre: 'Viejo', categoria: 'CALLE', ubicacion: Coordenada(-21.5, -64.7)),
      ];
      servicio.respuestas['san juan'] = const [
        LugarEncontrado(nombre: 'Hospital San Juan de Dios', categoria: 'SALUD', ubicacion: Coordenada(-21.5, -64.7)),
      ];
      busqueda.escribir('sa');
      await Future<void>.delayed(const Duration(milliseconds: 80)); // ya pidió «sa», que tarda
      busqueda.escribir('san juan');
      await Future<void>.delayed(const Duration(milliseconds: 400));
      expect(servicio.pedidos, ['sa', 'san juan']);
      expect(busqueda.resultados.single.nombre, 'Hospital San Juan de Dios');
    });

    test('avisa si no hay resultados o no hay conexión', () async {
      busqueda.escribir('zzzz');
      await Future<void>.delayed(const Duration(milliseconds: 120));
      expect(busqueda.mensaje, BusquedaController.sinResultados);
      servicio.error = const ApiException(codigo: 'SIN_CONEXION', mensaje: 'x');
      busqueda.escribir('zzzzz');
      await Future<void>.delayed(const Duration(milliseconds: 120));
      expect(busqueda.mensaje, BusquedaController.sinConexion);
      busqueda.limpiar();
      expect(busqueda.texto, isEmpty);
      expect(busqueda.mensaje, isNull);
    });
  });

  group('CP-190: ServicioBusqueda', () {
    test('pide GET /lugares con el texto y la posición, y lee la respuesta', () async {
      late Uri pedido;
      final servicio = ServicioBusqueda(ApiClient(client: MockClient((p) async {
        pedido = p.url;
        return respuestaLugares([lugarJson('Mercado Campesino', 'MERCADO', -21.525, -64.733, 2181)]);
      })));
      final lugares = await servicio.buscar('mercado', cerca: const Coordenada(-21.5355, -64.7296));
      expect(pedido.path, '/lugares');
      expect(pedido.queryParameters, {'texto': 'mercado', 'latitud': '-21.535500', 'longitud': '-64.729600'});
      expect(lugares.single.nombre, 'Mercado Campesino');
      expect(lugares.single.nombreCategoria, 'Mercado');
      expect(lugares.single.distanciaM, 2181);
      expect(distanciaCorta(2181), '2,2 km');
      expect(distanciaCorta(350), '350 m');
    });

    test('una respuesta rara es un error, no una lista vacía', () async {
      final servicio = ServicioBusqueda(ApiClient(client: MockClient((_) async => http.Response('{"x":1}', 200))));
      expect(() => servicio.buscar('colon'), throwsA(isA<ApiException>()));
    });
  });

  group('CP-191: la barra de búsqueda en la pantalla principal', () {
    late MapaFalso mapa;
    late List<Uri> pedidos;

    Widget app() {
      mapa = MapaFalso();
      pedidos = [];
      final api = ApiClient(client: MockClient(sinEventos((pedido) async {
        if (pedido.url.path != '/lugares') return http.Response('{}', 200);
        pedidos.add(pedido.url);
        return respuestaLugares([
          lugarJson('Hospital Regional San Juan de Dios', 'SALUD', -21.529, -64.735),
          lugarJson('Las Vegas', 'SURTIDOR', -21.5235, -64.7248),
        ]);
      })));
      return MultiProvider(
        providers: createAppProviders(
          apiClient: api,
          ubicacion: UbicacionFalsa(),
          constructorMapa: mapa.construir,
          almacen: AlmacenFalso.conSesionVigente(),
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

    Future<void> escribir(WidgetTester tester, String texto) async {
      await tester.tap(find.byKey(const Key('campo-busqueda')));
      await tester.enterText(find.byKey(const Key('campo-busqueda')), texto);
      await tester.pump(BusquedaController.esperaPorDefecto);
      await tester.pumpAndSettle();
    }

    testWidgets('al elegir un resultado, el mapa va hasta él y queda marcado con su nombre', (tester) async {
      await tester.pumpWidget(app());
      await tester.pumpAndSettle();
      await tester.tap(find.text('Eventos').last);
      await tester.pumpAndSettle();

      await escribir(tester, 'san juan');
      expect(pedidos.single.queryParameters['texto'], 'san juan');
      expect(find.byKey(const Key('resultados-busqueda')), findsOneWidget);
      expect(find.text('Salud'), findsOneWidget);
      expect(find.text('Surtidor'), findsOneWidget);

      await tester.tap(find.text('Hospital Regional San Juan de Dios'));
      await tester.pumpAndSettle();
      expect(leer<NavigationController>(tester).selectedIndex, AppTab.map.index);
      final mapaController = leer<MapController>(tester);
      expect(mapaController.puntoSeleccionado, const Coordenada(-21.529, -64.735));
      expect(mapa.ultimo!.foco, const Coordenada(-21.529, -64.735));
      expect(mapa.ultimo!.puntoSeleccionado, const Coordenada(-21.529, -64.735));
      expect(find.byKey(const Key('resultados-busqueda')), findsNothing);
      // La hoja del punto lleva el nombre, y desde ahí se puede pedir «Cómo llegar».
      expect(find.text('Hospital Regional San Juan de Dios'), findsOneWidget);
      expect(find.text('Punto elegido'), findsNothing);
      expect(find.text('Cómo llegar'), findsOneWidget);

      // Tocar otro punto del mapa no mueve la cámara y vuelve a «Punto elegido».
      mapa.ultimo!.alTocar(const Coordenada(-21.54, -64.72));
      await tester.pump();
      expect(find.text('Punto elegido'), findsOneWidget);
      expect(mapa.ultimo!.foco, isNull);
    });

    testWidgets('con una letra no busca, y la X borra lo escrito', (tester) async {
      await tester.pumpWidget(app());
      await tester.pumpAndSettle();
      await escribir(tester, 's');
      expect(pedidos, isEmpty);
      expect(find.byKey(const Key('resultados-busqueda')), findsNothing);
      await tester.tap(find.byKey(const Key('borrar-busqueda')));
      await tester.pump();
      expect(leer<BusquedaController>(tester).texto, isEmpty);
      expect(find.byKey(const Key('borrar-busqueda')), findsNothing);
    });

    testWidgets('el gesto de atrás cierra primero la lista de resultados', (tester) async {
      var salidas = 0;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (llamada) async {
        if (llamada.method == 'SystemNavigator.pop') salidas++;
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      await tester.pumpWidget(app());
      await tester.pumpAndSettle();
      await escribir(tester, 'san juan');
      expect(find.byKey(const Key('resultados-busqueda')), findsOneWidget);

      await tester.binding.handlePopRoute();
      await tester.pump();
      expect(find.byKey(const Key('resultados-busqueda')), findsNothing);
      expect(find.text(MainScreen.avisoSalir), findsNothing);
      expect(salidas, 0);
    });
  });
}
