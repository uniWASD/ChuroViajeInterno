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
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/navegacion.dart' show distanciaM;
import 'package:app_flutter/views/events/detalle_evento.dart';
import 'package:app_flutter/views/main/main_screen.dart';
import 'package:app_flutter/views/tema/tema.dart';

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
  http.Response Function(int id, String valor) votar = (id, valor) => http.Response(
      jsonEncode({'eventoId': id, 'estado': 'ACTIVO', 'confirmado': false,
        'votosReal': valor == 'real' ? 2 : 1, 'votosFalso': valor == 'falso' ? 2 : 1}), 201);
  bool primera = true;

  Map<String, dynamic> _evento(int id, String tipo, String momento, {String origen = 'CONDUCTOR', String? nivel,
          bool confirmado = false, int? real, int? falso}) =>
      {
        'id': id, 'tipo': tipo, 'ubicacion': {'latitud': -21.53 - id / 1000, 'longitud': -64.73}, 'momentoOcurrencia': momento,
        'estado': confirmado ? 'CONFIRMADO' : 'ACTIVO', 'confirmado': confirmado, 'origen': origen,
        'nivel': ?nivel, 'votosReal': ?real, 'votosFalso': ?falso,
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
        _evento(1, 'CONTROL_POLICIAL', hace5, real: 1, falso: 1),
        _evento(2, 'ACCIDENTE', hace12, confirmado: true, real: 3, falso: 0),
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

  /// Toca un evento de la lista: se abre su detalle en la hoja.
  Future<void> abrirDetalle(WidgetTester tester, int id) async {
    await tester.tap(find.byKey(Key('evento-$id')));
    await tester.pumpAndSettle();
  }

  bool habilitado(WidgetTester tester, String clave) =>
      tester.widget<FilledButton>(find.byKey(Key(clave))).onPressed != null;

  testWidgets('La pestaña Eventos muestra los vigentes con su tipo, hace cuánto y la marca de verificación', (tester) async {
    await abrirEventos(tester);
    expect(find.byKey(const Key('lista-eventos')), findsOneWidget);
    expect(find.text('Control policial'), findsOneWidget);
    expect(find.text('· 5 min'), findsNWidgets(2));
    expect(find.text('Accidente'), findsOneWidget);
    expect(find.text('· 12 min'), findsOneWidget);
    // #172: verde si otros lo confirmaron (RN-02.14), gris si todavía no.
    final marcas = tester.widgetList<Icon>(find.byIcon(Icons.verified)).toList();
    expect(marcas, hasLength(2));
    expect(marcas.map((m) => m.semanticLabel), containsAll(['Confirmado', 'Sin confirmar']));
    expect(marcas.firstWhere((m) => m.semanticLabel == 'Confirmado').color, ColoresChuro.verde);
    expect(marcas.firstWhere((m) => m.semanticLabel == 'Sin confirmar').color, MarcaVerificado.gris);
  });

  testWidgets('CP-183: Eventos abre a media pantalla sobre el mapa y se desliza hacia abajo para despejarlo',
      (tester) async {
    await abrirEventos(tester);
    expect(mapa.ultimo, isNotNull, reason: 'el mapa sigue detrás de la hoja');
    final alto = tester.getSize(find.byType(MainScreen)).height;
    final antes = tester.getRect(find.byKey(const Key('hoja-eventos')));
    final hoja = tester.getTopLeft(find.text('Control policial')).dy;
    expect(hoja, greaterThan(alto * 0.4), reason: 'ocupa más o menos la mitad de abajo');
    expect(antes.height, greaterThan(0));

    // Se desliza hacia abajo: queda solo el asa y el mapa se ve entero.
    await tester.drag(find.byType(AsaHoja), const Offset(0, 500));
    await tester.pumpAndSettle();
    expect(find.text('Control policial').hitTestable(), findsNothing);
    expect(find.byType(AsaHoja).hitTestable(), findsOneWidget, reason: 'el asa queda para volver a subirla');

    // Se vuelve a subir.
    await tester.drag(find.byType(AsaHoja), const Offset(0, -300));
    await tester.pumpAndSettle();
    expect(find.text('Control policial').hitTestable(), findsOneWidget);
  });

  testWidgets('CP-184: tocar un evento lleva el mapa hasta él y abre su detalle con los votos', (tester) async {
    await abrirEventos(tester);
    await abrirDetalle(tester, 1);
    expect(distanciaM(mapa.ultimo!.foco!, const Coordenada(-21.531, -64.73)), lessThan(0.01),
        reason: 'la cámara va hasta el evento');
    expect(mapa.ultimo!.tapadoAbajo, 0.5, reason: 'y lo deja en la mitad que no tapa la hoja');
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);
    expect(find.byKey(const Key('lista-eventos')), findsNothing);
    expect(find.text('Real'), findsOneWidget);
    expect(find.text('Falso'), findsOneWidget);
    // D-46: cuántos votaron cada cosa, sobre los 3 que deciden.
    expect(tester.widget<Text>(find.byKey(const Key('votos-real-1'))).data, '1/3');
    expect(tester.widget<Text>(find.byKey(const Key('votos-falso-1'))).data, '1/3');

    // La flecha vuelve a la lista.
    await tester.tap(find.byTooltip('Volver a la lista'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('lista-eventos')), findsOneWidget);
  });

  testWidgets('El botón de información también abre el detalle', (tester) async {
    await abrirEventos(tester);
    await tester.tap(find.byKey(const Key('info-2')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);
    expect(tester.widget<Text>(find.byKey(const Key('votos-real-2'))).data, '3/3');
  });

  testWidgets('Tocar un evento en el mapa, con la hoja de Eventos abierta, abre su detalle en la hoja', (tester) async {
    await abrirEventos(tester);
    mapa.ultimo!.alTocarEvento!(2);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);
    expect(find.byTooltip('Volver a la lista'), findsOneWidget);
  });

  testWidgets('D-20: la congestión de la telemetría aparece sin botones de voto', (tester) async {
    await abrirEventos(tester);
    expect(find.text('Congestión'), findsOneWidget);
    await abrirDetalle(tester, 3);
    expect(find.text('Hace 5 minutos · Muy congestionado · Detectado por el tráfico'), findsOneWidget);
    expect(find.byKey(const Key('real-3')), findsNothing);
    expect(find.byKey(const Key('falso-3')), findsNothing);
    expect(find.text('La congestión que detecta el tráfico no se vota.'), findsOneWidget);
  });

  testWidgets('Votar toma un solo toque, actualiza el conteo y deja desactivados los botones de ese evento',
      (tester) async {
    await abrirEventos(tester);
    await abrirDetalle(tester, 1);
    await tester.tap(find.byKey(const Key('falso-1')));
    await tester.pumpAndSettle();
    expect(backend.votos, [(1, 'falso')]);
    expect(habilitado(tester, 'real-1'), isFalse);
    expect(habilitado(tester, 'falso-1'), isFalse);
    expect(tester.widget<Text>(find.byKey(const Key('votos-falso-1'))).data, '2/3', reason: 'D-46: el conteo nuevo');
    expect(find.text('Gracias: votaste que es falso.'), findsOneWidget);
    await tester.tap(find.byTooltip('Volver a la lista'));
    await tester.pumpAndSettle();
    await abrirDetalle(tester, 2);
    expect(habilitado(tester, 'real-2'), isTrue, reason: 'los demás eventos se siguen votando');
  });

  testWidgets('CP-91: un segundo voto sobre el mismo evento muestra el mensaje del backend y no cambia nada',
      (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => http.Response.bytes(
        utf8.encode(jsonEncode({'codigo': 'VOTO_REPETIDO', 'mensaje': 'Ya votaste este evento.'})), 409);
    await abrirDetalle(tester, 2);
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
    await abrirDetalle(tester, 1);
    await tester.tap(find.byKey(const Key('real-1')));
    await tester.pumpAndSettle();
    expect(find.text('No puedes votar un evento que reportaste.'), findsOneWidget);
    expect(habilitado(tester, 'real-1'), isFalse);
  });

  testWidgets('Sin red el voto no se pierde: se puede volver a tocar', (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => throw http.ClientException('sin red');
    await abrirDetalle(tester, 1);
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

  testWidgets('El tercer «falso» de este conductor lo quita sin esperar a la actualización y vuelve a la lista',
      (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => http.Response(jsonEncode({'eventoId': id, 'estado': 'RETIRADO', 'confirmado': false}), 201);
    await abrirDetalle(tester, 1);
    await tester.tap(find.byKey(const Key('falso-1')));
    await tester.pumpAndSettle();
    expect(find.text('Control policial'), findsNothing);
    expect(find.byKey(const Key('lista-eventos')), findsOneWidget);
  });

  testWidgets('Un voto que confirma el evento pone su marca en verde', (tester) async {
    await abrirEventos(tester);
    backend.votar = (id, _) => http.Response(
        jsonEncode({'eventoId': id, 'estado': 'CONFIRMADO', 'confirmado': true, 'votosReal': 3, 'votosFalso': 1}), 201);
    await abrirDetalle(tester, 1);
    await tester.tap(find.byKey(const Key('real-1')));
    await tester.pumpAndSettle();
    expect(tester.widget<Text>(find.byKey(const Key('votos-real-1'))).data, '3/3');
    final marca = tester.widget<Icon>(find.byWidgetPredicate((w) => w is Icon && w.semanticLabel == 'Confirmado'));
    expect(marca.color, ColoresChuro.verde);
  });

  testWidgets('En la pestaña Mapa, tocar un evento abre el mismo detalle, con la X para cerrarlo', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    mapa.ultimo!.alTocarEvento!(1);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-evento')), findsOneWidget);
    expect(find.text('Real'), findsOneWidget);
    expect(distanciaM(mapa.ultimo!.foco!, const Coordenada(-21.531, -64.73)), lessThan(0.01));
    await tester.tap(find.byTooltip('Cerrar'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('ficha-evento')), findsNothing);
  });
}
