import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/combustible_controller.dart';
import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/navigation_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/surtidor.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/ubicacion/servicio_ubicacion.dart';
import 'package:app_flutter/views/main/main_screen.dart';
import 'package:app_flutter/views/navegacion/navegacion_activa_screen.dart';
import 'package:app_flutter/views/stations/estilo_surtidor.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';

/// Un surtidor como lo entrega `GET /surtidores`.
Map<String, Object?> surtidorJson(int id, String nombre, double lat, double lon, int distancia,
        List<Map<String, Object?>> combustibles) =>
    {
      'id': id,
      'nombre': nombre,
      'ubicacion': {'latitud': lat, 'longitud': lon},
      'distanciaM': distancia,
      'informacionReciente': combustibles.isNotEmpty,
      'combustibles': combustibles,
    };

Map<String, Object?> datoJson(String tipo, String nombre, bool disponible, String origen, DateTime fecha,
        {String? fila, double? litros, String? estadoAnh}) =>
    {
      'tipo': tipo,
      'nombre': nombre,
      'disponible': disponible,
      'origen': origen,
      'fecha': fecha.toUtc().toIso8601String(),
      'fila': ?fila,
      'litros': ?litros,
      'estadoAnh': ?estadoAnh,
      if (origen == 'ANH') 'consultadoEn': fecha.toUtc().toIso8601String(),
    };

void main() {
  late MapaFalso mapa;
  late UbicacionFalsa ubicacion;
  late List<Uri> pedidos;
  late List<Map<String, Object?>> respuesta;

  final hace10 = DateTime.now().subtract(const Duration(minutes: 10));

  List<Map<String, Object?>> catalogoNormal() => [
    surtidorJson(1, 'Las Vegas', -21.5235, -64.7248, 850, [
      datoJson('GASOLINA', 'Gasolina', true, 'WHATSAPP', hace10, fila: 'CORTA'),
      datoJson('DIESEL', 'Diesel', false, 'CONDUCTORES', hace10.subtract(const Duration(minutes: 20))),
    ]),
    surtidorJson(2, 'Don Daniel', -21.5133, -64.7382, 2623, []),
    surtidorJson(3, 'Ex Terminal', -21.5419, -64.7241, 3100, [
      datoJson('DIESEL', 'Diesel', true, 'ANH', hace10, litros: 1250.5, estadoAnh: 'ALTO'),
    ]),
  ];

  Widget app() {
    mapa = MapaFalso();
    final api = ApiClient(client: MockClient(sinEventos((pedido) async {
      if (pedido.url.path != '/surtidores') return http.Response('{}', 200);
      pedidos.add(pedido.url);
      return http.Response.bytes(utf8.encode(jsonEncode({'surtidores': respuesta})), 200,
          headers: {'content-type': 'application/json'});
    })));
    return MultiProvider(
      providers: createAppProviders(
        apiClient: api,
        ubicacion: ubicacion,
        constructorMapa: mapa.construir,
        servicioRutas: ServicioRutasFalso(),
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

  setUp(() {
    ubicacion = UbicacionFalsa();
    pedidos = [];
    respuesta = catalogoNormal();
  });

  Future<void> abrirSurtidores(WidgetTester tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    ubicacion.emitir(const Coordenada(-21.5355, -64.7296));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Surtidores').last);
    await tester.pumpAndSettle();
  }

  group('Modelo', () {
    test('El estado general sale de los combustibles vigentes', () {
      Surtidor s(List<Map<String, Object?>> c) => Surtidor.fromJson(surtidorJson(1, 'X', 0, 0, 1, c));
      final ahora = DateTime.now();
      expect(s([]).estadoGeneral, EstadoGeneral.sinInformacion);
      expect(s([datoJson('GASOLINA', 'Gasolina', true, 'WHATSAPP', ahora)]).estadoGeneral, EstadoGeneral.disponible);
      expect(s([datoJson('GASOLINA', 'Gasolina', false, 'WHATSAPP', ahora)]).estadoGeneral, EstadoGeneral.agotado);
      expect(
        s([
          datoJson('GASOLINA', 'Gasolina', true, 'WHATSAPP', ahora),
          datoJson('DIESEL', 'Diesel', false, 'ANH', ahora),
        ]).estadoGeneral,
        EstadoGeneral.mixto,
      );
    });

    test('RN-05.6: un dato sin tipo se muestra como Desconocido; la distancia, en m o km', () {
      final d = EstadoCombustible.fromJson(datoJson('DESCONOCIDO', 'Desconocido', true, 'WHATSAPP', DateTime.now()));
      expect(d.nombreParaMostrar, 'Desconocido');
      expect(Surtidor.fromJson(surtidorJson(1, 'X', 0, 0, 850, [])).distanciaParaMostrar, '850 m');
      expect(Surtidor.fromJson(surtidorJson(1, 'X', 0, 0, 2450, [])).distanciaParaMostrar, '2,5 km');
    });
  });

  testWidgets('RN-05.2: la pestaña pide GET /surtidores con la ubicación actual y los lista del más cercano al más lejano', (tester) async {
    await abrirSurtidores(tester);

    final ultimo = pedidos.last;
    expect(ultimo.queryParameters['latitud'], '-21.5355');
    expect(ultimo.queryParameters['longitud'], '-64.7296');
    final nombres = tester.widgetList<Text>(find.descendant(
      of: find.byKey(const Key('lista-surtidores')),
      matching: find.byType(Text),
    )).map((t) => t.data).where((t) => ['Las Vegas', 'Don Daniel', 'Ex Terminal'].contains(t)).toList();
    expect(nombres, ['Las Vegas', 'Don Daniel', 'Ex Terminal']);
  });

  testWidgets('CP-108: un surtidor con un aviso de WhatsApp de hace 10 minutos muestra el estado, el origen WhatsApp y hace cuánto llegó', (tester) async {
    await abrirSurtidores(tester);
    expect(find.descendant(of: find.byKey(const Key('surtidor-1')), matching: find.text('· 10 min')), findsOneWidget);

    await tester.tap(find.byKey(const Key('info-surtidor-1')));
    await tester.pumpAndSettle();

    final ficha = find.byKey(const Key('ficha-surtidor'));
    expect(find.descendant(of: ficha, matching: find.text('Las Vegas')), findsOneWidget);
    final gasolina = find.byKey(const Key('combustible-GASOLINA'));
    expect(find.descendant(of: gasolina, matching: find.text('Disponible · Fila corta')), findsOneWidget);
    expect(find.descendant(of: gasolina, matching: find.byKey(const Key('origen-whatsapp'))), findsOneWidget);
    // #191: sin la hora exacta, solo hace cuánto.
    expect(find.descendant(of: gasolina, matching: find.text('hace 10 min')), findsOneWidget);
    final diesel = find.byKey(const Key('combustible-DIESEL'));
    expect(find.descendant(of: diesel, matching: find.text('Agotado')), findsOneWidget);
    expect(find.descendant(of: diesel, matching: find.byKey(const Key('origen-conductores'))), findsOneWidget);
  });

  testWidgets('CP-109: un surtidor sin datos muestra «sin información reciente»', (tester) async {
    await abrirSurtidores(tester);
    expect(find.text('sin información reciente'), findsOneWidget);

    await tester.tap(find.byKey(const Key('info-surtidor-2')));
    await tester.pumpAndSettle();
    expect(find.text('Sin información reciente'), findsOneWidget);
  });

  testWidgets('CP-200 y RN-05.4: un dato de ANH muestra los litros junto al combustible, sin el texto «estado ANH»', (tester) async {
    await abrirSurtidores(tester);
    await tester.tap(find.byKey(const Key('surtidor-3')));
    await tester.pumpAndSettle();

    final diesel = find.byKey(const Key('combustible-DIESEL'));
    expect(find.descendant(of: diesel, matching: find.text('Diesel (1250,5 L)')), findsOneWidget);
    expect(find.descendant(of: diesel, matching: find.text('Disponible')), findsOneWidget);
    expect(find.descendant(of: diesel, matching: find.textContaining('estado ANH')), findsNothing);
    expect(find.descendant(of: diesel, matching: find.byKey(const Key('origen-anh'))), findsOneWidget);
  });

  testWidgets('CP-200: junto a (i) ya no va el ícono del origen, y la distancia lleva el auto a la izquierda', (tester) async {
    await abrirSurtidores(tester);
    final fila = find.byKey(const Key('surtidor-1')).first;
    expect(find.byKey(const Key('info-surtidor-1')), findsOneWidget);
    // El ícono de origen solo aparece dentro de cada combustible, al desplegar.
    expect(find.byTooltip('Dato de WhatsApp'), findsNothing);

    await tester.tap(fila);
    await tester.pumpAndSettle();
    final distancia = find.byKey(const Key('distancia-en-auto'));
    expect(distancia, findsOneWidget);
    expect(find.descendant(of: distancia, matching: find.byIcon(Icons.directions_car)), findsOneWidget);
    expect(find.textContaining(RegExp(r'^\d{2}:\d{2}')), findsNothing, reason: 'sin la hora exacta');
  });

  testWidgets('D-25: los surtidores son marcadores del mapa con el color de su estado, y al tocar uno se ve su estado', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    final marcas = mapa.ultimo!.surtidores;
    expect(marcas.map((m) => m.id), [1, 2, 3]);
    expect(marcas[0].color, ColoresSurtidor.mixto);
    expect(marcas[1].color, ColoresSurtidor.sinInformacion);
    expect(marcas[2].color, ColoresSurtidor.disponible);

    mapa.ultimo!.alTocarSurtidor!(1);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-surtidor-mapa')), findsOneWidget);
    expect(find.byKey(const Key('combustible-GASOLINA')), findsOneWidget);
    expect(mapa.ultimo!.foco, const Coordenada(-21.5235, -64.7248), reason: 'la cámara va hasta el surtidor');
  });

  testWidgets('CP-110 y RN-05.17: con el mapa abierto, un dato nuevo cambia el marcador en el siguiente ciclo de 60 s, sin tocar nada', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(mapa.ultimo!.surtidores[1].color, ColoresSurtidor.sinInformacion);
    final antes = pedidos.length;

    respuesta = catalogoNormal()
      ..[1] = surtidorJson(2, 'Don Daniel', -21.5133, -64.7382, 2623, [
        datoJson('GASOLINA', 'Gasolina', true, 'WHATSAPP', DateTime.now()),
      ]);
    await tester.pump(const Duration(seconds: 59));
    expect(pedidos.length, antes, reason: 'todavía no pasó el minuto');
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(pedidos.length, antes + 1);
    expect(mapa.ultimo!.surtidores[1].color, ColoresSurtidor.disponible);
  });

  testWidgets('#191: desde un surtidor de la lista, «Ir» abre directamente la navegación hasta el surtidor', (tester) async {
    await abrirSurtidores(tester);
    await tester.tap(find.byKey(const Key('surtidor-1')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('ir-1')));
    await tester.pumpAndSettle();

    expect(find.byType(NavegacionActivaScreen), findsOneWidget);
    expect(mapa.navegacion!.marcas.last.coordenada, const Coordenada(-21.5235, -64.7248));
    expect(tester.element(find.byType(MainScreen, skipOffstage: false)).read<NavigationController>().selectedIndex, AppTab.map.index);
  });

  testWidgets('D-24: desde el detalle de un surtidor del mapa, «Cómo llegar» muestra la ruta en el mapa', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    ubicacion.emitir(const Coordenada(-21.5355, -64.7296));
    await tester.pump();
    mapa.ultimo!.alTocarSurtidor!(3);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('como-llegar-surtidor')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('hoja-ruta')), findsOneWidget);
    expect(mapa.ultimo!.puntoSeleccionado, const Coordenada(-21.5419, -64.7241));
    expect(mapa.ultimo!.trazado, isNotEmpty);
    expect(tester.element(find.byType(MainScreen)).read<MapController>().nombrePunto, 'Ex Terminal');
  });

  testWidgets('Sin permiso de ubicación, explica que lo necesita, deja concederlo y pide desde el centro de Tarija', (tester) async {
    ubicacion = UbicacionFalsa(permiso: PermisoUbicacion.denegado);
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Surtidores').last);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('pedir-ubicacion-surtidores')), findsOneWidget);
    expect(pedidos.last.queryParameters['latitud'], '${centroTarija.latitud}');
    final pedidosAntes = ubicacion.vecesPedido;
    await tester.tap(find.descendant(
      of: find.byKey(const Key('pedir-ubicacion-surtidores')),
      matching: find.text('Permitir'),
    ));
    await tester.pumpAndSettle();
    expect(ubicacion.vecesPedido, pedidosAntes + 1);
  });

  testWidgets('Al llegar la primera posición se vuelve a pedir la lista, ya ordenada por cercanía', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(pedidos.last.queryParameters['latitud'], '${centroTarija.latitud}');
    final contexto = tester.element(find.byType(MainScreen));
    expect(contexto.read<CombustibleController>().ordenadosPorCercania, isFalse);

    ubicacion.emitir(const Coordenada(-21.52, -64.73));
    await tester.pumpAndSettle();
    expect(pedidos.last.queryParameters['latitud'], '-21.52');
    expect(contexto.read<CombustibleController>().ordenadosPorCercania, isTrue);
  });

  testWidgets('#172: el gesto de atrás cierra primero el detalle del surtidor', (tester) async {
    await abrirSurtidores(tester);
    await tester.tap(find.byKey(const Key('info-surtidor-1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-surtidor')), findsOneWidget);

    final navegador = tester.state<NavigatorState>(find.byType(Navigator).first);
    await navegador.maybePop();
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('ficha-surtidor')), findsNothing);
    expect(find.byKey(const Key('hoja-surtidores')), findsOneWidget, reason: 'sigue en la pestaña Surtidores');
  });
}
