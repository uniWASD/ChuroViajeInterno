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
import 'package:app_flutter/views/navegacion/navegacion_activa_screen.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';

/// #191: la hoja del lugar (CP-195) y «Cómo llegar» en el mapa principal (CP-196). Reemplaza a las pruebas de la
/// pantalla Cómo llegar, que ya no existe; CP-105 y CP-106 siguen aquí.
void main() {
  late UbicacionFalsa ubicacion;
  late MapaFalso mapa;
  const posicion = Coordenada(-21.5344253, -64.7339992);
  const destino = Coordenada(-21.541756, -64.725011);

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

  Future<void> conPosicion(WidgetTester tester) async {
    await tester.pumpAndSettle();
    ubicacion.emitir(posicion);
    await tester.pump();
  }

  Future<void> tocar(WidgetTester tester, Coordenada punto, {String? nombre}) async {
    if (nombre == null) {
      mapa.ultimo!.alTocar(punto);
    } else {
      mapa.ultimo!.alTocarLugar!(punto, nombre);
    }
    await tester.pumpAndSettle();
  }

  bool activo(WidgetTester tester, String clave) =>
      tester.widget<ButtonStyleButton>(find.byKey(Key(clave))).onPressed != null;

  testWidgets('CP-195: en el mapa solo queda la campana; ya no están «Reportar evento» ni «Cómo llegar»', (tester) async {
    await tester.pumpWidget(app(ServicioRutasFalso()));
    await conPosicion(tester);
    expect(find.byKey(const Key('boton-notificar')), findsOneWidget);
    expect(find.text('Reportar evento'), findsNothing);
    expect(find.text('Cómo llegar'), findsNothing);
  });

  testWidgets('CP-195 y CP-105: al tocar un punto se abre su hoja con la distancia, el tiempo, «Notificar», «Ir» y '
      '«Cómo llegar»; la ruta sale de la posición del conductor en menos de 3 segundos', (tester) async {
    final servicio = ServicioRutasFalso();
    await tester.pumpWidget(app(servicio));
    await conPosicion(tester);
    final inicio = DateTime.now();
    await tocar(tester, destino);

    expect(DateTime.now().difference(inicio), lessThan(const Duration(seconds: 3)));
    expect(find.byKey(const Key('hoja-lugar')), findsOneWidget);
    expect(find.textContaining('Punto elegido'), findsOneWidget);
    expect(find.textContaining('1,5 km'), findsOneWidget);
    expect(find.text('3 min'), findsOneWidget);
    expect(find.byIcon(Icons.directions_car), findsOneWidget);
    expect(find.text('Notificar'), findsOneWidget);
    expect(activo(tester, 'ir-lugar'), isTrue);
    expect(activo(tester, 'como-llegar-lugar'), isTrue);
    expect(servicio.pedidos.single.$1, posicion);
    expect(servicio.pedidos.single.$2, destino);
    expect(mapa.ultimo!.trazado, isEmpty, reason: 'la ruta se dibuja recién con «Cómo llegar»');
    expect(find.byKey(const Key('boton-notificar')), findsOneWidget);
  });

  testWidgets('CP-195: al tocar un lugar conocido del mapa, la hoja lleva su nombre', (tester) async {
    await tester.pumpWidget(app(ServicioRutasFalso()));
    await conPosicion(tester);
    await tocar(tester, destino, nombre: 'Mercado Negro');
    expect(find.textContaining('Mercado Negro'), findsOneWidget);
    expect(mapa.ultimo!.puntoSeleccionado, destino);
  });

  testWidgets('CP-196: «Cómo llegar» dibuja la ruta en el mismo mapa, con «Iniciar» y «Otra parada»', (tester) async {
    final servicio = ServicioRutasFalso();
    await tester.pumpWidget(app(servicio));
    await conPosicion(tester);
    await tocar(tester, destino);

    await tester.tap(find.byKey(const Key('como-llegar-lugar')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-ruta')), findsOneWidget);
    expect(mapa.ultimo!.trazado, rutaDePrueba.trazado);
    expect(activo(tester, 'iniciar-navegacion'), isTrue);
    expect(find.text('Otra parada'), findsOneWidget);
    expect(servicio.pedidos, hasLength(1), reason: 'usa la ruta que ya calculó la hoja del lugar');
    expect(find.byKey(const Key('boton-notificar')), findsNothing);

    // Mientras se ve la ruta, tocar el mapa no elige otro lugar.
    await tocar(tester, const Coordenada(-21.52, -64.74));
    expect(mapa.ultimo!.puntoSeleccionado, destino);
  });

  testWidgets('CP-196: «Otra parada» agrega la parada tocando el mapa, recalcula y se puede quitar', (tester) async {
    final servicio = ServicioRutasFalso();
    await tester.pumpWidget(app(servicio));
    await conPosicion(tester);
    await tocar(tester, destino);
    await tester.tap(find.byKey(const Key('como-llegar-lugar')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('otra-parada')));
    await tester.pump();
    expect(find.text('Toca el mapa para agregar la parada'), findsOneWidget);
    const parada = Coordenada(-21.525353, -64.723676);
    await tocar(tester, parada);

    expect(find.text('Parada 1'), findsOneWidget);
    expect(servicio.pedidos.last.$3, [parada]);
    expect(mapa.ultimo!.marcas.map((m) => m.tipo), [TipoMarca.parada]);
    expect(mapa.ultimo!.puntoSeleccionado, destino);

    await tester.tap(find.byTooltip('Quitar parada 1'));
    await tester.pumpAndSettle();
    expect(find.text('Parada 1'), findsNothing);
    expect(servicio.pedidos.last.$3, isEmpty);
  });

  testWidgets('CP-196: «Ir» abre directamente la Navegación activa y al volver no queda nada elegido', (tester) async {
    await tester.pumpWidget(app(ServicioRutasFalso()));
    await conPosicion(tester);
    await tocar(tester, destino);

    await tester.tap(find.byKey(const Key('ir-lugar')));
    await tester.pumpAndSettle();
    expect(find.byType(NavegacionActivaScreen), findsOneWidget);
    expect(mapa.navegacion!.trazado, isNotEmpty);

    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-lugar')), findsNothing);
    expect(mapa.ultimo!.puntoSeleccionado, isNull);
  });

  testWidgets('CP-196: cerrar la ruta vuelve a la hoja del lugar y cerrar la hoja quita el lugar', (tester) async {
    await tester.pumpWidget(app(ServicioRutasFalso()));
    await conPosicion(tester);
    await tocar(tester, destino);
    await tester.tap(find.byKey(const Key('como-llegar-lugar')));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Cerrar la ruta'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-lugar')), findsOneWidget);
    expect(mapa.ultimo!.trazado, isEmpty);

    await tester.tap(find.byTooltip('Quitar el lugar'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('hoja-lugar')), findsNothing);
    expect(mapa.ultimo!.puntoSeleccionado, isNull);
  });

  testWidgets('CP-106: un destino fuera de Tarija muestra «Origen o destino inválido» y no deja ir', (tester) async {
    final api = ApiClient(
      client: MockClient(
        (_) async => http.Response(
          jsonEncode({'estado': 'PUNTO_INVALIDO', 'mensaje': 'Origen o destino inválido'}),
          200,
          headers: {'content-type': 'application/json; charset=utf-8'},
        ),
      ),
    );
    await tester.pumpWidget(app(ServicioRutasApi(api)));
    await conPosicion(tester);
    await tocar(tester, const Coordenada(-16.5, -68.15));

    expect(find.text('Origen o destino inválido'), findsOneWidget);
    expect(find.byKey(const Key('tiempo-distancia')), findsNothing);
    expect(activo(tester, 'ir-lugar'), isFalse);
    expect(activo(tester, 'como-llegar-lugar'), isFalse);
  });

  testWidgets('Con 503 informa el fallo y deja reintentar (RN-03.15)', (tester) async {
    final servicio = ServicioRutasFalso()
      ..error = const ApiException(
        codigo: 'MOTOR_NO_DISPONIBLE',
        mensaje: 'El motor no está disponible',
        statusCode: 503,
      );
    await tester.pumpWidget(app(servicio));
    await conPosicion(tester);
    await tocar(tester, destino);
    expect(find.textContaining('no está disponible en este momento'), findsOneWidget);

    servicio.error = null;
    await tester.tap(find.text('Reintentar'));
    await tester.pumpAndSettle();
    expect(find.text('3 min'), findsOneWidget);
  });

  testWidgets('Sin la posición del conductor avisa y calcula la ruta cuando llega', (tester) async {
    final servicio = ServicioRutasFalso();
    await tester.pumpWidget(app(servicio));
    await tester.pumpAndSettle();
    await tocar(tester, destino);
    expect(find.textContaining('No tenemos tu ubicación'), findsOneWidget);
    expect(servicio.pedidos, isEmpty);

    ubicacion.emitir(posicion);
    await tester.pumpAndSettle();
    expect(servicio.pedidos, hasLength(1));
    expect(find.text('3 min'), findsOneWidget);
  });

  testWidgets('Con la ruta de ejemplo lo avisa', (tester) async {
    await tester.pumpWidget(app(ServicioRutasDeEjemplo()));
    await conPosicion(tester);
    mapa.ultimo!.alTocar(destino);
    await tester.pump();
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 100)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('como-llegar-lugar')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Ruta de ejemplo'), findsOneWidget);
    expect(find.byKey(const Key('tiempo-distancia')), findsOneWidget);
  });
}
