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
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/ruta.dart';
import 'package:app_flutter/models/trafico.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/views/main/main_screen.dart';
import 'package:app_flutter/views/map/mapa_base.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

void main() {
  late MapaFalso mapa;
  late bool backendCaido;
  late bool conCongestiones;
  late bool conCalleLenta;
  late UbicacionFalsa ubicacion;

  // #191: una calle de oeste a este con dos cuadras congestionadas seguidas (ids 5 y 6).
  const lat = -21.5355;
  Coordenada esquina(int n) => Coordenada(lat, -64.7350 + n * 0.00096);
  Map<String, double> json(Coordenada c) => {'latitud': c.latitud, 'longitud': c.longitud};

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
          if (conCongestiones) ...[
            {'id': 3, 'tipo': 'CONGESTION', 'ubicacion': {'latitud': -21.54, 'longitud': -64.735},
             'momentoOcurrencia': hace5, 'estado': 'ACTIVO', 'confirmado': false, 'origen': 'TELEMETRIA',
             'nivel': 'MUY_CONGESTIONADO',
             'trazado': [{'latitud': -21.54, 'longitud': -64.74}, {'latitud': -21.54, 'longitud': -64.73}]},
            // Guardada antes de V9: sin trazado.
            {'id': 4, 'tipo': 'CONGESTION', 'ubicacion': {'latitud': -21.53, 'longitud': -64.72},
             'momentoOcurrencia': hace5, 'estado': 'ACTIVO', 'confirmado': false, 'origen': 'TELEMETRIA',
             'nivel': 'CONGESTIONADO'},
          ],
          if (conCalleLenta)
            for (final (id, desde) in [(5, 1), (6, 2)])
              {'id': id, 'tipo': 'CONGESTION', 'ubicacion': json(esquina(desde)),
               'momentoOcurrencia': hace5, 'estado': 'ACTIVO', 'confirmado': false, 'origen': 'TELEMETRIA',
               'nivel': 'CONGESTIONADO', 'trazado': [json(esquina(desde)), json(esquina(desde + 1))]},
        ],
      })), 200);
    }));
    return MultiProvider(
      providers: createAppProviders(
        apiClient: api,
        ubicacion: ubicacion = UbicacionFalsa(),
        // La ruta va por la misma calle, de la esquina 0 a la 4.
        servicioRutas: ServicioRutasFalso(
          respuesta: RutaCalculada(
            estado: EstadoRuta.encontrada,
            distanciaM: 400,
            tiempoS: 90,
            trazado: [for (var n = 0; n <= 4; n++) esquina(n)],
          ),
        ),
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
    backendCaido = false;
    conCongestiones = false;
    conCalleLenta = false;
  });

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
    expect(find.text('5 min'), findsOneWidget);

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

  testWidgets('CP-177: la congestión con trazado se dibuja como línea con el color de su nivel; sin trazado, como punto', (tester) async {
    conCongestiones = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    final marcas = {for (final m in mapa.ultimo!.eventos) m.id: m};
    expect(marcas[3]!.esLinea, isTrue);
    expect(marcas[3]!.trazado, hasLength(2));
    expect(colorDeNivel(marcas[3]!.nivel), colorMuyCongestionado, reason: 'muy congestionado: rojo');
    expect(colorDeNivel('CONGESTIONADO'), colorCongestionado, reason: 'congestionado: naranja');
    expect(marcas[4]!.esLinea, isFalse, reason: 'una congestión vieja, sin trazado, sigue siendo un punto');
    expect(marcas[1]!.esLinea, isFalse, reason: 'los eventos de conductores siguen siendo puntos');

    // Al tocar la línea se abre la ficha, igual que con un punto.
    mapa.ultimo!.alTocarEvento!(3);
    await tester.pump();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);
    expect(find.text('Congestión'), findsOneWidget);
  });

  testWidgets('CP-198: dos cuadras congestionadas seguidas se ven como una sola línea y al tocarla se abre la primera',
      (tester) async {
    conCalleLenta = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    final lineas = mapa.ultimo!.eventos.where((m) => m.esLinea).toList();
    expect(lineas, hasLength(1));
    expect(lineas.single.id, 5);
    expect(lineas.single.trazado, [esquina(1), esquina(2), esquina(3)]);
    expect(colorDeNivel(lineas.single.nivel), colorCongestionado, reason: 'congestionado: amarillo');

    mapa.ultimo!.alTocarEvento!(lineas.single.id);
    await tester.pump();
    expect(find.text('Congestión'), findsOneWidget);
  });

  testWidgets('CP-197: con «Cómo llegar», la ruta se pinta de amarillo donde pasa por la calle congestionada',
      (tester) async {
    conCalleLenta = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    ubicacion.emitir(esquina(0));
    await tester.pump();
    mapa.ultimo!.alTocar(esquina(4));
    await tester.pumpAndSettle();
    expect(mapa.ultimo!.trafico, isEmpty, reason: 'sin «Cómo llegar» la ruta no se dibuja');

    await tester.tap(find.byKey(const Key('como-llegar-lugar')));
    await tester.pumpAndSettle();
    expect(mapa.ultimo!.trafico, [
      LineaTrafico(puntos: [esquina(1), esquina(2), esquina(3)], nivel: nivelCongestionado),
    ]);
  });

  testWidgets('CP-194: «Notificar» desde un lugar usa ese punto; tocar el mapa lo cambia y a más de 1 km avisa sin '
      'dejar enviar', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    ubicacion.emitir(esquina(0));
    await tester.pump();
    final cerca = Coordenada(lat + 0.004, esquina(0).longitud); // unos 445 m
    mapa.ultimo!.alTocar(cerca);
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('notificar-lugar')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-notificar')), findsOneWidget);
    expect(mapa.ultimo!.puntoSeleccionado, cerca, reason: 'el pin marca dónde va el evento');
    expect(find.textContaining('En el punto marcado'), findsOneWidget);

    final lejos = Coordenada(lat + 0.012, esquina(0).longitud); // unos 1335 m
    mapa.ultimo!.alTocar(lejos);
    await tester.pump();
    expect(mapa.ultimo!.puntoSeleccionado, lejos);
    await tester.tap(find.text('Accidente'));
    await tester.pump();
    expect(find.text('Solo puedes notificar eventos a 1 km de donde estás.'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byKey(const Key('confirmar-reporte'))).onPressed, isNull);

    mapa.ultimo!.alTocar(cerca);
    await tester.pump();
    expect(find.text('Solo puedes notificar eventos a 1 km de donde estás.'), findsNothing);
    expect(tester.widget<FilledButton>(find.byKey(const Key('confirmar-reporte'))).onPressed, isNotNull);
  });
}
