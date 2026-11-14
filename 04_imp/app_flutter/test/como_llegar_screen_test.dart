import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/rutas/servicio_rutas.dart';
import 'package:app_flutter/views/map/map_screen.dart';
import 'package:app_flutter/views/map/mapa_base.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';

void main() {
  late UbicacionFalsa ubicacion;
  late MapaFalso mapa;

  Widget app(ServicioRutas servicio) {
    ubicacion = UbicacionFalsa();
    mapa = MapaFalso();
    return MultiProvider(
      providers: createAppProviders(
        apiClient: ApiClient(
          client: MockClient(sinEventos((_) async => http.Response('{}', 200))),
        ),
        ubicacion: ubicacion,
        servicioRutas: servicio,
        constructorMapa: mapa.construir,
        almacen: AlmacenFalso.conSesionVigente(),
        google: GoogleFalso(),
        cola: ColaFalsa(),
        conectividad: ConectividadFalsa(),
      ),
      child: const MaterialApp(home: Scaffold(body: MapScreen())),
    );
  }

  Future<void> abrirComoLlegar(WidgetTester tester) async {
    await tester.pumpAndSettle();
    ubicacion.emitir(const Coordenada(-21.5344253, -64.7339992));
    await tester.pump();
    await tester.tap(find.text('Cómo llegar'));
    await tester.pumpAndSettle();
  }

  testWidgets(
    'CP-105: de la posición actual a un punto del mapa, la ruta aparece con tiempo y distancia',
    (tester) async {
      final servicio = ServicioRutasFalso();
      await tester.pumpWidget(app(servicio));
      await abrirComoLlegar(tester);
      expect(find.text('Tu ubicación'), findsOneWidget);

      mapa.comoLlegar!.alTocar(const Coordenada(-21.541756, -64.725011));
      await tester.pump();
      final inicio = DateTime.now();
      await tester.tap(find.text('Calcular ruta'));
      await tester.pumpAndSettle();

      expect(
        DateTime.now().difference(inicio),
        lessThan(const Duration(seconds: 3)),
      );
      expect(find.text('3 min · 1,5 km'), findsOneWidget);
      expect(mapa.comoLlegar!.trazado, rutaDePrueba.trazado);
      expect(
        servicio.pedidos.single.$1,
        const Coordenada(-21.5344253, -64.7339992),
      );
      // «Iniciar» abre la Navegación activa (#94).
      final iniciar = tester.widget<ButtonStyleButton>(find.byKey(const Key('iniciar-navegacion')));
      expect(iniciar.onPressed, isNotNull);
    },
  );

  testWidgets(
    'CP-106: un destino fuera de Tarija muestra «Origen o destino inválido»',
    (tester) async {
      final api = ApiClient(
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'estado': 'PUNTO_INVALIDO',
              'mensaje': 'Origen o destino inválido',
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          ),
        ),
      );
      await tester.pumpWidget(app(ServicioRutasApi(api)));
      await abrirComoLlegar(tester);

      mapa.comoLlegar!.alTocar(const Coordenada(-16.5, -68.15));
      await tester.pump();
      await tester.tap(find.text('Calcular ruta'));
      await tester.pumpAndSettle();

      expect(find.text('Origen o destino inválido'), findsOneWidget);
      expect(find.byKey(const Key('tiempo-distancia')), findsNothing);
      // Puede elegir otro destino y volver a calcular.
      expect(find.text('Toca el mapa para elegir el destino'), findsOneWidget);
    },
  );

  testWidgets('Con 503 informa el fallo y deja reintentar', (tester) async {
    final servicio = ServicioRutasFalso()
      ..error = const ApiException(
        codigo: 'MOTOR_NO_DISPONIBLE',
        mensaje: 'El motor no está disponible',
        statusCode: 503,
      );
    await tester.pumpWidget(app(servicio));
    await abrirComoLlegar(tester);
    mapa.comoLlegar!.alTocar(const Coordenada(-21.541756, -64.725011));
    await tester.pump();

    await tester.tap(find.text('Calcular ruta'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('no está disponible en este momento'),
      findsOneWidget,
    );

    servicio.error = null;
    await tester.tap(find.text('Reintentar'));
    await tester.pumpAndSettle();
    expect(find.text('3 min · 1,5 km'), findsOneWidget);
  });

  testWidgets('Las paradas se agregan tocando el mapa y se pueden quitar', (
    tester,
  ) async {
    final servicio = ServicioRutasFalso();
    await tester.pumpWidget(app(servicio));
    await abrirComoLlegar(tester);
    mapa.comoLlegar!.alTocar(const Coordenada(-21.525353, -64.723676));
    await tester.pump();

    await tester.tap(find.text('Agregar parada'));
    await tester.pump();
    expect(find.text('Toca el mapa para agregar la parada'), findsOneWidget);
    mapa.comoLlegar!.alTocar(const Coordenada(-21.541756, -64.725011));
    await tester.pump();

    expect(find.text('Parada 1'), findsOneWidget);
    expect(mapa.comoLlegar!.marcas.map((m) => m.tipo), [
      TipoMarca.parada,
      TipoMarca.destino,
    ]);

    await tester.tap(find.byTooltip('Quitar parada 1'));
    await tester.pump();
    expect(find.text('Parada 1'), findsNothing);
  });

  testWidgets('El punto elegido en el mapa principal pasa a ser el destino', (
    tester,
  ) async {
    await tester.pumpWidget(app(ServicioRutasFalso()));
    await tester.pumpAndSettle();
    mapa.ultimo!.alTocar(const Coordenada(-21.541756, -64.725011));
    await tester.pump();

    await tester.tap(find.text('Cómo llegar'));
    await tester.pumpAndSettle();
    expect(find.text('-21.54176, -64.72501'), findsOneWidget);
  });

  testWidgets('Con la ruta de ejemplo lo avisa', (tester) async {
    await tester.pumpWidget(app(ServicioRutasDeEjemplo()));
    await abrirComoLlegar(tester);
    expect(find.textContaining('Ruta de ejemplo'), findsOneWidget);

    mapa.comoLlegar!.alTocar(const Coordenada(-21.541756, -64.725011));
    await tester.pump();
    await tester.tap(find.text('Calcular ruta'));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('tiempo-distancia')), findsOneWidget);
  });
}
