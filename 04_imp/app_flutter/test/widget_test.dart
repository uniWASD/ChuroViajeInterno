import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/views/main/main_screen.dart';

void main() {
  Widget createTestApp({ApiClient? apiClient}) {
    final client =
        apiClient ??
        ApiClient(client: MockClient((_) async => http.Response('{}', 200)));

    return MultiProvider(
      providers: createAppProviders(apiClient: client),
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

    expect(find.text('ChuroViaje'), findsOneWidget);
    expect(find.text('Mapa'), findsWidgets);
    expect(find.text('Eventos'), findsWidgets);
    expect(find.text('Surtidores'), findsWidgets);
    expect(find.text('Probar conexión'), findsOneWidget);
  });

  testWidgets('Permite navegar entre las pantallas', (tester) async {
    await tester.pumpWidget(createTestApp());

    await tester.tap(find.text('Eventos').last);
    await tester.pumpAndSettle();

    expect(
      find.text('Aquí se mostrarán los eventos de ChuroViaje.'),
      findsOneWidget,
    );

    await tester.tap(find.text('Surtidores').last);
    await tester.pumpAndSettle();

    expect(
      find.text('Aquí se mostrarán los surtidores disponibles.'),
      findsOneWidget,
    );

    await tester.tap(find.text('Mapa').last);
    await tester.pumpAndSettle();

    expect(find.text('Probar conexión'), findsOneWidget);
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

    await tester.tap(find.text('Probar conexión'));
    await tester.pump();
    await tester.pumpAndSettle();

    expect(
      find.text('No se pudo conectar con el servidor. Verifica tu conexión.'),
      findsOneWidget,
    );
    expect(find.text('ChuroViaje'), findsOneWidget);
    expect(find.text('Probar conexión'), findsOneWidget);
  });
}
