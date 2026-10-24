import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/navigation_controller.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/views/main/main_screen.dart';

void main() {
  Widget createTestApp() {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(
          create: (_) => NavigationController(),
        ),
        Provider<ApiClient>(
          create: (_) => ApiClient(),
          dispose: (_, apiClient) => apiClient.dispose(),
        ),
        ChangeNotifierProxyProvider<ApiClient, MapController>(
          create: (context) => MapController(
            context.read<ApiClient>(),
          ),
          update: (_, apiClient, mapController) =>
              mapController ?? MapController(apiClient),
        ),
      ],
      child: const MaterialApp(
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
}