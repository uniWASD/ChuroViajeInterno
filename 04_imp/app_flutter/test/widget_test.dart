import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/rutas/servicio_rutas.dart';
import 'package:app_flutter/views/main/main_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';

void main() {
  late MapaFalso mapa;
  late UbicacionFalsa ubicacion;

  Widget createTestApp({ApiClient? apiClient}) {
    mapa = MapaFalso();
    ubicacion = UbicacionFalsa();
    final client =
        apiClient ??
        ApiClient(client: MockClient(sinEventos((_) async => http.Response('{}', 200))));

    return MultiProvider(
      providers: createAppProviders(
        apiClient: client,
        ubicacion: ubicacion,
        servicioRutas: ServicioRutasApi(client),
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

  testWidgets('Muestra las tres pestañas principales', (tester) async {
    await tester.pumpWidget(createTestApp());

    // D-47: arriba va la barra de búsqueda, sin el encabezado «ChuroViaje» y sin micrófono.
    expect(find.text('ChuroViaje'), findsNothing);
    expect(find.byKey(const Key('barra-busqueda')), findsOneWidget);
    expect(find.byIcon(Icons.mic), findsNothing);
    expect(find.byIcon(Icons.mic_none), findsNothing);
    expect(find.byKey(const Key('menu-cuenta')), findsOneWidget);
    expect(find.text('Mapa'), findsWidgets);
    expect(find.text('Eventos'), findsWidgets);
    expect(find.text('Surtidores'), findsWidgets);
    // #191: en el mapa solo queda la campana para notificar.
    expect(find.byKey(const Key('boton-notificar')), findsOneWidget);
  });

  testWidgets('Permite navegar entre las pantallas', (tester) async {
    await tester.pumpWidget(createTestApp());

    await tester.tap(find.text('Eventos').last);
    await tester.pumpAndSettle();

    // La pestaña Eventos es la lista de eventos (#64); sin eventos lo dice.
    expect(find.text('No hay eventos vigentes por ahora.'), findsOneWidget);

    await tester.tap(find.text('Surtidores').last);
    await tester.pumpAndSettle();

    // La pestaña Surtidores es una hoja sobre el mapa con la lista (#71).
    expect(find.byKey(const Key('hoja-surtidores')), findsOneWidget);

    await tester.tap(find.text('Mapa').last);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('boton-notificar')), findsOneWidget);
  });

  testWidgets('Configura el idioma español', (tester) async {
    await tester.pumpWidget(createTestApp());

    final materialApp = tester.widget<MaterialApp>(find.byType(MaterialApp));

    expect(materialApp.locale, const Locale('es'));
    expect(materialApp.supportedLocales, contains(const Locale('es')));
  });

  testWidgets('CP-20: muestra el error de red sin cerrar la pantalla', (
    tester,
  ) async {
    final apiClient = ApiClient(
      client: MockClient((_) async {
        throw http.ClientException('Servidor fuera de servicio');
      }),
    );

    await tester.pumpWidget(createTestApp(apiClient: apiClient));

    await tester.pumpAndSettle();
    ubicacion.emitir(const Coordenada(-21.5344253, -64.7339992));
    await tester.pump();
    // #191: al tocar un punto se pide la ruta para la hoja del lugar.
    mapa.ultimo!.alTocar(const Coordenada(-21.541756, -64.725011));
    await tester.pumpAndSettle();

    expect(
      find.text('No se pudo conectar con el servidor. Verifica tu conexión.'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('hoja-lugar')), findsOneWidget);
    expect(find.text('Reintentar'), findsOneWidget);
  });
}
