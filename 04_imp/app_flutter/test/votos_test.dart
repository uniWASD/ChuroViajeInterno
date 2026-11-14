import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

/// Backend falso de eventos y votos: la primera consulta trae tres eventos; las siguientes, lo que la prueba deje en
/// [cambios]. Los votos responden lo que diga [votar].
class BackendVotos {
  final String hace5 = DateTime.now().toUtc().subtract(const Duration(minutes: 5)).toIso8601String();
  final String hace12 = DateTime.now().toUtc().subtract(const Duration(minutes: 12)).toIso8601String();
  Map<String, dynamic>? cambios;
  final List<(int, String)> votos = [];
  http.Response Function(int id, String valor) votar = (id, _) =>
      http.Response(jsonEncode({'eventoId': id, 'estado': 'ACTIVO', 'confirmado': false}), 201);
  bool primera = true;

  Map<String, dynamic> _evento(int id, String tipo, String momento, {String origen = 'CONDUCTOR', String? nivel,
          bool confirmado = false}) =>
      {
        'id': id, 'tipo': tipo, 'ubicacion': {'latitud': -21.53, 'longitud': -64.73}, 'momentoOcurrencia': momento,
        'estado': confirmado ? 'CONFIRMADO' : 'ACTIVO', 'confirmado': confirmado, 'origen': origen,
        'nivel': ?nivel,
      };

  http.Client get cliente => MockClient((pedido) async {
    final path = pedido.url.path;
    final voto = RegExp(r'^/eventos/(\d+)/votos$').firstMatch(path);
    if (voto != null && pedido.method == 'POST') {
      final id = int.parse(voto.group(1)!);
      final valor = (jsonDecode(pedido.body) as Map<String, dynamic>)['valor'] as String;
      votos.add((id, valor));
      return votar(id, valor);
    }
    if (path != '/eventos') return http.Response('{}', 200);
    final Map<String, dynamic> cuerpo;
    if (primera) {
      primera = false;
      cuerpo = {'marca': 'm1', 'completa': true, 'quitados': [], 'eventos': [
        _evento(1, 'CONTROL_POLICIAL', hace5),
        _evento(2, 'ACCIDENTE', hace12, confirmado: true),
        _evento(3, 'CONGESTION', hace5, origen: 'TELEMETRIA', nivel: 'MUY_CONGESTIONADO'),
      ]};
    } else {
      cuerpo = cambios ?? {'marca': 'm2', 'completa': false, 'quitados': [], 'eventos': []};
      cambios = null;
    }
    return http.Response.bytes(utf8.encode(jsonEncode(cuerpo)), 200);
  });
}

void main() {
  late BackendVotos backend;
  late MapaFalso mapa;

  Widget app() {
    backend = BackendVotos();
    mapa = MapaFalso();
    return MultiProvider(
      providers: createAppProviders(
        apiClient: ApiClient(client: backend.cliente),
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

  Future<void> abrirEventos(WidgetTester tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Eventos'));
    await tester.pumpAndSettle();
  }

  bool habilitado(WidgetTester tester, String clave) =>
      tester.widget<IconButton>(find.byKey(Key(clave))).onPressed != null;

  testWidgets('La pestaña Eventos muestra los vigentes con su tipo y hace cuánto, como el mapa', (tester) async {
    await abrirEventos(tester);
    expect(find.byKey(const Key('lista-eventos')), findsOneWidget);
    expect(find.text('Control policial'), findsOneWidget);
    expect(find.text('Hace 5 minutos'), findsOneWidget);
    expect(find.text('Accidente'), findsOneWidget);
    expect(find.text('Hace 12 minutos · Confirmado por otros conductores'), findsOneWidget);
    expect(find.byIcon(Icons.verified), findsOneWidget, reason: 'RN-02.14: la marca de confirmado');
    expect(mapa.ultimo!.eventos.map((e) => e.id), unorderedEquals([1, 2, 3]));
  });

  testWidgets('D-20: la congestión de la telemetría aparece sin botones de voto', (tester) async {
    await abrirEventos(tester);
    expect(find.text('Congestión'), findsOneWidget);
    expect(find.text('Hace 5 minutos · Muy congestionado · Detectado por el tráfico'), findsOneWidget);
    expect(find.byKey(const Key('real-3')), findsNothing);
    expect(find.byKey(const Key('falso-3')), findsNothing);
    expect(find.byKey(const Key('real-1')), findsOneWidget);
  });

  testWidgets('Votar toma un solo toque y deja desactivados los botones de ese evento', (tester) async {
    await abrirEventos(tester);
    await tester.tap(find.byKey(const Key('falso-1')));
    await tester.pumpAndSettle();
    expect(backend.votos, [(1, 'falso')]);
    expect(habilitado(tester, 'real-1'), isFalse);
    expect(habilitado(tester, 'falso-1'), isFalse);
    expect(habilitado(tester, 'real-2'), isTrue, reason: 'los demás eventos se siguen votando');
    expect(find.text('Gracias: votaste que es falso.'), findsOneWidget);
  });

  testWidgets('CP-91: un segundo voto sobre el mismo evento muestra el mensaje del backend y no cambia nada',
      (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => http.Response.bytes(
        utf8.encode(jsonEncode({'codigo': 'VOTO_REPETIDO', 'mensaje': 'Ya votaste este evento.'})), 409);
    await tester.tap(find.byKey(const Key('real-2')));
    await tester.pumpAndSettle();
    expect(find.text('Ya votaste este evento.'), findsOneWidget);
    expect(find.text('Accidente'), findsOneWidget);
    expect(habilitado(tester, 'real-2'), isFalse);
    expect(backend.votos, hasLength(1));
  });

  testWidgets('RN-02.12: votar el propio evento muestra el mensaje del backend', (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => http.Response.bytes(
        utf8.encode(jsonEncode({'codigo': 'VOTO_PROPIO', 'mensaje': 'No puedes votar un evento que reportaste.'})), 422);
    await tester.tap(find.byKey(const Key('real-1')));
    await tester.pumpAndSettle();
    expect(find.text('No puedes votar un evento que reportaste.'), findsOneWidget);
    expect(habilitado(tester, 'real-1'), isFalse);
  });

  testWidgets('Sin red el voto no se pierde: se puede volver a tocar', (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => throw http.ClientException('sin red');
    await tester.tap(find.byKey(const Key('real-1')));
    await tester.pumpAndSettle();
    expect(habilitado(tester, 'real-1'), isTrue);
  });

  testWidgets('CP-90: un evento que otros retiraron desaparece de la lista y del mapa en la siguiente actualización',
      (tester) async {
    await abrirEventos(tester);
    // Tres teléfonos votaron «falso» (RN-02.13): la siguiente consulta lo trae entre los quitados.
    backend.cambios = {'marca': 'm2', 'completa': false, 'eventos': [], 'quitados': [1]};
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
    expect(find.text('Control policial'), findsNothing);
    expect(mapa.ultimo!.eventos.map((e) => e.id), unorderedEquals([2, 3]));
  });

  testWidgets('El tercer «falso» de este conductor lo quita de la lista sin esperar a la actualización', (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => http.Response(jsonEncode({'eventoId': id, 'estado': 'RETIRADO', 'confirmado': false}), 201);
    await tester.tap(find.byKey(const Key('falso-1')));
    await tester.pumpAndSettle();
    expect(find.text('Control policial'), findsNothing);
  });

  testWidgets('Un voto que confirma el evento muestra su marca', (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => http.Response(jsonEncode({'eventoId': id, 'estado': 'CONFIRMADO', 'confirmado': true}), 201);
    await tester.tap(find.byKey(const Key('real-1')));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.verified), findsNWidgets(2));
  });
}
