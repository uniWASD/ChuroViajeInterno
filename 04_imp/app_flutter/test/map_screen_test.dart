import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/ubicacion/servicio_ubicacion.dart';
import 'package:app_flutter/views/map/map_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';

void main() {
  late UbicacionFalsa ubicacion;
  late MapaFalso mapa;

  Widget app({PermisoUbicacion permiso = PermisoUbicacion.concedido}) {
    ubicacion = UbicacionFalsa(permiso: permiso);
    mapa = MapaFalso();
    return MultiProvider(
      providers: createAppProviders(
        apiClient: ApiClient(
          client: MockClient(sinEventos((_) async => http.Response('{}', 200))),
        ),
        ubicacion: ubicacion,
        constructorMapa: mapa.construir,
        almacen: AlmacenFalso.conSesionVigente(),
        google: GoogleFalso(),
      ),
      child: const MaterialApp(home: Scaffold(body: MapScreen())),
    );
  }

  testWidgets('Abre el mapa en Tarija y pide el permiso de ubicación', (
    tester,
  ) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('mapa-falso')), findsOneWidget);
    expect(mapa.ultimo!.centro, centroTarija);
    expect(ubicacion.vecesPedido, 1);
    expect(mapa.ultimo!.mostrarUbicacion, isTrue);
  });

  testWidgets('Sin permiso, el mapa sigue y avisa que no hay posición', (
    tester,
  ) async {
    await tester.pumpWidget(app(permiso: PermisoUbicacion.denegado));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('mapa-falso')), findsOneWidget);
    expect(mapa.ultimo!.mostrarUbicacion, isFalse);
    expect(find.textContaining('Sin permiso de ubicación'), findsOneWidget);

    ubicacion.permiso = PermisoUbicacion.concedido;
    await tester.tap(find.text('Permitir'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Sin permiso de ubicación'), findsNothing);
    expect(mapa.ultimo!.mostrarUbicacion, isTrue);
  });

  testWidgets('Si el permiso se negó para siempre, lleva a los ajustes', (
    tester,
  ) async {
    await tester.pumpWidget(app(permiso: PermisoUbicacion.denegadoSiempre));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Ajustes'));
    expect(ubicacion.ajustesAbiertos, 1);
  });

  testWidgets('Tocar el mapa devuelve las coordenadas del punto', (
    tester,
  ) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();

    mapa.ultimo!.alTocar(const Coordenada(-21.541756, -64.725011));
    await tester.pumpAndSettle();

    expect(find.text('Punto elegido'), findsOneWidget);
    expect(find.text('-21.541756, -64.725011'), findsOneWidget);
    expect(
      mapa.ultimo!.puntoSeleccionado,
      const Coordenada(-21.541756, -64.725011),
    );

    await tester.tap(find.byTooltip('Quitar el punto'));
    await tester.pumpAndSettle();
    expect(find.text('Punto elegido'), findsNothing);
  });

  testWidgets(
    'CP-29: si el mapa base no carga, muestra el error y deja reintentar',
    (tester) async {
      await tester.pumpWidget(app());
      await tester.pumpAndSettle();
      final antes = mapa.construcciones;

      mapa.ultimo!.alFallar('Failed to load style');
      await tester.pumpAndSettle();
      expect(find.text(MapController.mensajeErrorMapa), findsOneWidget);

      await tester.tap(find.text('Reintentar'));
      await tester.pumpAndSettle();
      expect(find.text(MapController.mensajeErrorMapa), findsNothing);
      expect(mapa.construcciones, greaterThan(antes));
    },
  );

  testWidgets('CP-30: al pasar a segundo plano deja de pedir la ubicación', (
    tester,
  ) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(ubicacion.escuchando, 1);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    // En segundo plano no se dibujan cuadros: basta con que se cancele la suscripción.
    expect(ubicacion.escuchando, 0);

    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(ubicacion.escuchando, 1);
    expect(mapa.ultimo!.mostrarUbicacion, isTrue);
  });
}
