import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';

import 'package:app_flutter/app_providers.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/views/map/map_screen.dart';
import 'package:app_flutter/views/navegacion/navegacion_activa_screen.dart';
import 'package:app_flutter/views/ruta/como_llegar_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';
import 'dobles/rutas_de_navegacion.dart';

void main() {
  late UbicacionFalsa ubicacion;
  late MapaFalso mapa;

  Widget app() {
    ubicacion = UbicacionFalsa();
    mapa = MapaFalso();
    return MultiProvider(
      providers: createAppProviders(
        apiClient: ApiClient(client: MockClient(sinEventos((_) async => http.Response('{}', 200)))),
        ubicacion: ubicacion,
        servicioRutas: ServicioRutasFalso(respuesta: rutaEnL),
        constructorMapa: mapa.construir,
        almacen: AlmacenFalso.conSesionVigente(),
        google: GoogleFalso(),
        cola: ColaFalsa(),
        conectividad: ConectividadFalsa(),
      ),
      child: const MaterialApp(home: Scaffold(body: MapScreen())),
    );
  }

  Future<void> iniciarNavegacion(WidgetTester tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    ubicacion.emitir(esquinaA);
    await tester.pump();
    await tester.tap(find.text('Cómo llegar'));
    await tester.pumpAndSettle();
    mapa.comoLlegar!.alTocar(esquinaC);
    await tester.pump();
    await tester.tap(find.text('Calcular ruta'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('iniciar-navegacion')));
    await tester.pumpAndSettle();
  }

  /// La posición llega por un stream: un cuadro la entrega y el siguiente la dibuja.
  Future<void> ir(WidgetTester tester, Coordenada p) async {
    ubicacion.emitir(p);
    await tester.pump();
    await tester.pump();
  }

  testWidgets('CP-107: al tocar «Iniciar», la vista Navegación activa sigue la posición del conductor', (tester) async {
    await iniciarNavegacion(tester);
    expect(find.byType(NavegacionActivaScreen), findsOneWidget);
    expect(find.byType(ComoLlegarScreen), findsNothing, reason: 'Navegación activa reemplaza a Cómo llegar');
    expect(mapa.navegacion!.seguirUbicacion, isTrue);
    expect(mapa.navegacion!.mostrarUbicacion, isTrue);
    expect(mapa.navegacion!.trazado, rutaEnL.trazado);
    expect(find.text('2 min · 500 m'), findsOneWidget);

    await ir(tester, desplazar(esquinaA, esteM: 150));
    expect(find.text('2 min · 350 m'), findsOneWidget); // 70 s: se redondea hacia arriba
  });

  testWidgets('CP-155: cerca de la esquina muestra la calle que sigue y el giro; a menos de 30 m del destino termina',
      (tester) async {
    await iniciarNavegacion(tester);
    expect(find.text('Gira a la derecha en Avenida Las Américas'), findsOneWidget);
    expect(find.text('En 300 m'), findsOneWidget);
    expect(find.byIcon(Icons.turn_right), findsOneWidget);

    await ir(tester, desplazar(esquinaA, esteM: 250));
    expect(find.text('En 50 m'), findsOneWidget);

    await ir(tester, desplazar(esquinaB, surM: 50));
    expect(find.text('Sigue hasta el destino'), findsOneWidget);
    expect(find.text('Avenida Las Américas'), findsOneWidget);

    await ir(tester, desplazar(esquinaC, norteM: 20));
    expect(find.text('Llegaste a tu destino'), findsOneWidget);
    expect(mapa.navegacion!.trazado, isEmpty, reason: 'la ruta terminada se borra');

    await tester.tap(find.text('Volver al mapa'));
    await tester.pumpAndSettle();
    expect(find.byType(NavegacionActivaScreen), findsNothing);
    expect(find.byType(MapScreen), findsOneWidget);
  });

  testWidgets('«Cancelar» termina la ruta y vuelve al mapa', (tester) async {
    await iniciarNavegacion(tester);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(find.byType(NavegacionActivaScreen), findsNothing);
    expect(find.byType(MapScreen), findsOneWidget);
  });
}
