import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/controllers/navigation_controller.dart';
import 'package:app_flutter/main.dart';

void main() {
  Widget createTestApp() {
    return ChangeNotifierProvider(
      create: (_) => NavigationController(),
      child: const ChuroViajeApp(),
    );
  }

  testWidgets('Muestra las tres pestañas principales', (tester) async {
    await tester.pumpWidget(createTestApp());

    expect(find.text('ChuroViaje'), findsOneWidget);
    expect(find.text('Mapa'), findsWidgets);
    expect(find.text('Eventos'), findsWidgets);
    expect(find.text('Surtidores'), findsWidgets);
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
  });
}
