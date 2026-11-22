import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/combustible_controller.dart';
import 'package:app_flutter/controllers/recarga_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/surtidor.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'surtidores_test.dart' show datoJson, surtidorJson;

/// Las Vegas, del catálogo (V2), y puntos a su alrededor. Un grado de latitud en Tarija mide unos 110 724 m.
const Coordenada lasVegas = Coordenada(-21.5234844, -64.724772);
Coordenada alNorte(double metros) => Coordenada(lasVegas.latitud + metros / 110724, lasVegas.longitud);

void main() {
  late MapaFalso mapa;
  late UbicacionFalsa ubicacion;
  late DateTime ahora;
  late List<http.Request> confirmaciones;
  late bool dieselConfirmado;
  late int respuestaConfirmacion;

  Widget app() {
    mapa = MapaFalso();
    final api = ApiClient(client: MockClient(sinEventos((pedido) async {
      if (pedido.method == 'POST' && pedido.url.path.endsWith('/confirmaciones')) {
        confirmaciones.add(pedido);
        if (respuestaConfirmacion == 409) {
          return http.Response.bytes(utf8.encode(jsonEncode({'codigo': 'CONFIRMACION_REPETIDA',
              'mensaje': 'Ya confirmaste este combustible en este surtidor hace poco.'})), 409);
        }
        dieselConfirmado = true;
        return http.Response(jsonEncode({'surtidorId': 1, 'tipo': 'DIESEL', 'disponible': true,
            'origen': 'CONDUCTORES', 'fecha': ahora.toUtc().toIso8601String()}), 201);
      }
      if (pedido.url.path != '/surtidores') return http.Response('{}', 200);
      return http.Response.bytes(utf8.encode(jsonEncode({'surtidores': [
        surtidorJson(1, 'Las Vegas', lasVegas.latitud, lasVegas.longitud, 10, [
          if (dieselConfirmado) datoJson('DIESEL', 'Diesel', true, 'CONDUCTORES', ahora),
        ]),
        surtidorJson(2, 'Don Daniel', -21.5133312, -64.7382423, 1500, []),
      ]})), 200);
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
        reloj: () => ahora,
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
    ahora = DateTime.now();
    confirmaciones = [];
    dieselConfirmado = false;
    respuestaConfirmacion = 201;
  });

  /// Pasa [tiempo] en el reloj de la app y deja correr la revisión de cada 10 segundos.
  Future<void> pasar(WidgetTester tester, Duration tiempo) async {
    ahora = ahora.add(tiempo);
    await tester.pump(RecargaController.revision + const Duration(seconds: 1));
    await tester.pump();
  }

  Future<void> abrir(WidgetTester tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
  }

  /// El conductor se detiene a [metros] del surtidor y pasa [tiempo] ahí.
  Future<void> detenerse(WidgetTester tester, Duration tiempo, {double metros = 15}) async {
    ubicacion.emitir(alNorte(metros));
    await tester.pump();
    await pasar(tester, tiempo);
  }

  final pregunta = find.byKey(const Key('pregunta-recarga'));

  testWidgets('CP-130: detenido 3 minutos junto a un surtidor aparece la pregunta; al elegir Diesel queda Diesel disponible, origen Conductores', (tester) async {
    await abrir(tester);
    await detenerse(tester, const Duration(minutes: 2));
    expect(pregunta, findsNothing, reason: 'todavía no pasaron los 3 minutos');
    await pasar(tester, const Duration(minutes: 1, seconds: 5));
    expect(pregunta, findsOneWidget);
    expect(find.text('¿Acabas de recargar combustible?'), findsOneWidget);
    expect(find.descendant(of: pregunta, matching: find.text('Las Vegas')), findsOneWidget);
    // RN-05.9: los cuatro tipos, sin Desconocido.
    for (final t in ['Gasolina', 'Gasolina Premium', 'Diesel', 'Diesel ULS']) {
      expect(find.descendant(of: pregunta, matching: find.text(t)), findsOneWidget);
    }
    expect(find.text('Desconocido'), findsNothing);

    await tester.tap(find.byKey(const Key('recarga-DIESEL'))); // RN-05.16: un solo toque
    await tester.pumpAndSettle();

    expect(confirmaciones, hasLength(1));
    expect(confirmaciones.single.url.path, '/surtidores/1/confirmaciones');
    final cuerpo = jsonDecode(confirmaciones.single.body) as Map<String, dynamic>;
    expect(cuerpo['tipo'], 'DIESEL');
    expect((cuerpo['ubicacion'] as Map)['latitud'], closeTo(alNorte(15).latitud, 1e-9));
    expect(pregunta, findsNothing);
    expect(find.text('Gracias: marcaste Diesel disponible en Las Vegas.'), findsOneWidget);

    final contexto = tester.element(find.byType(MainScreen));
    final diesel = contexto.read<CombustibleController>().porId(1)!.combustibles.single;
    expect(diesel.disponible, isTrue);
    expect(diesel.origen, OrigenDato.conductores);
  });

  testWidgets('CP-131: al responder «No» no se envía nada y la pregunta no reaparece mientras siga detenido ahí', (tester) async {
    await abrir(tester);
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    expect(pregunta, findsOneWidget);

    await tester.tap(find.byKey(const Key('recarga-no')));
    await tester.pumpAndSettle();
    expect(pregunta, findsNothing);
    await pasar(tester, const Duration(minutes: 10));
    await pasar(tester, const Duration(minutes: 10));
    expect(pregunta, findsNothing);
    expect(confirmaciones, isEmpty);

    // Si se va y vuelve a detenerse, sí se puede preguntar de nuevo.
    ubicacion.emitir(alNorte(400));
    await pasar(tester, const Duration(minutes: 5));
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    expect(pregunta, findsOneWidget);
  });

  testWidgets('RN-05.10: cerrar la pregunta con la X tampoco envía nada', (tester) async {
    await abrir(tester);
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    await tester.tap(find.byKey(const Key('cerrar-pregunta-recarga')));
    await tester.pumpAndSettle();
    expect(pregunta, findsNothing);
    expect(confirmaciones, isEmpty);
  });

  testWidgets('CP-132: después de confirmar, 10 minutos después en el mismo surtidor la pregunta no vuelve; pasada la hora, sí', (tester) async {
    await abrir(tester);
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    await tester.tap(find.byKey(const Key('recarga-DIESEL')));
    await tester.pumpAndSettle();

    ubicacion.emitir(alNorte(400)); // se va
    await pasar(tester, const Duration(minutes: 6));
    await detenerse(tester, const Duration(minutes: 4)); // vuelve 10 minutos después y se detiene
    expect(pregunta, findsNothing);

    ubicacion.emitir(alNorte(400));
    await pasar(tester, const Duration(minutes: 50));
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    expect(pregunta, findsOneWidget, reason: 'pasaron más de 60 minutos');
  });

  testWidgets('RN-05.14: si el backend responde que es repetida, muestra su mensaje y no vuelve a preguntar en 60 minutos', (tester) async {
    respuestaConfirmacion = 409;
    await abrir(tester);
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    await tester.tap(find.byKey(const Key('recarga-GASOLINA')));
    await tester.pumpAndSettle();
    expect(find.text('Ya confirmaste este combustible en este surtidor hace poco.'), findsOneWidget);

    ubicacion.emitir(alNorte(400));
    await pasar(tester, const Duration(minutes: 1));
    await detenerse(tester, const Duration(minutes: 5));
    expect(pregunta, findsNothing);
  });

  testWidgets('La confirmación se envía con la ubicación de la parada aunque el conductor ya haya arrancado', (tester) async {
    await abrir(tester);
    await detenerse(tester, const Duration(minutes: 3, seconds: 5), metros: 20);
    ubicacion.emitir(alNorte(600));
    await pasar(tester, const Duration(minutes: 1));
    expect(pregunta, findsOneWidget, reason: 'la pregunta sigue después de arrancar');

    await tester.tap(find.byKey(const Key('recarga-GASOLINA_PREMIUM')));
    await tester.pumpAndSettle();
    final cuerpo = jsonDecode(confirmaciones.single.body) as Map<String, dynamic>;
    expect((cuerpo['ubicacion'] as Map)['latitud'], closeTo(alNorte(20).latitud, 1e-9));
  });

  testWidgets('RN-05.8: pasar por al lado sin detenerse, o detenerse a más de 50 m, no pregunta', (tester) async {
    await abrir(tester);
    await detenerse(tester, const Duration(seconds: 30));
    ubicacion.emitir(alNorte(300));
    await pasar(tester, const Duration(minutes: 5));
    expect(pregunta, findsNothing);

    await detenerse(tester, const Duration(minutes: 5), metros: 70);
    expect(pregunta, findsNothing);
  });

  testWidgets('Una pregunta sin responder se cierra sola a los 15 minutos sin enviar nada', (tester) async {
    await abrir(tester);
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    expect(pregunta, findsOneWidget);
    ubicacion.emitir(alNorte(500));
    await pasar(tester, const Duration(minutes: 16));
    expect(pregunta, findsNothing);
    expect(confirmaciones, isEmpty);
  });

  testWidgets('RN-05.16: la pregunta no tapa la lista de surtidores', (tester) async {
    await abrir(tester);
    await tester.tap(find.text('Surtidores').last);
    await tester.pumpAndSettle();
    await detenerse(tester, const Duration(minutes: 3, seconds: 5));
    expect(pregunta, findsOneWidget);

    final abajoDeLaPregunta = tester.getBottomLeft(pregunta).dy;
    final arribaDeLaLista = tester.getTopLeft(find.byKey(const Key('surtidor-1'))).dy;
    expect(abajoDeLaPregunta, lessThan(arribaDeLaLista));
    expect(find.byKey(const Key('hoja-surtidores')), findsOneWidget);
  });

  test('distanciaEnMetros mide bien unas decenas de metros', () {
    expect(distanciaEnMetros(lasVegas, alNorte(50)), closeTo(50, 0.5));
  });
}
