import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/lectura_ubicacion.dart';
import 'package:app_flutter/services/ubicacion/servicio_ubicacion.dart';

import 'dobles/dobles_mapa.dart';

void main() {
  group('Ubicación en MapController', () {
    test('Con permiso sigue la posición del conductor', () async {
      final ubicacion = UbicacionFalsa();
      final controller = MapController(ubicacion);

      await controller.activarUbicacion();
      ubicacion.emitir(const Coordenada(-21.53, -64.73));
      await Future<void>.delayed(Duration.zero);

      expect(controller.tienePermiso, isTrue);
      expect(controller.siguiendoUbicacion, isTrue);
      expect(controller.posicion, const Coordenada(-21.53, -64.73));
      controller.dispose();
      expect(ubicacion.escuchando, 0);
    });

    test(
      'Sin permiso no pide posiciones y el resto sigue igual (RN-04.3)',
      () async {
        final ubicacion = UbicacionFalsa(permiso: PermisoUbicacion.denegado);
        final controller = MapController(ubicacion);

        await controller.activarUbicacion();
        controller.seleccionarPunto(const Coordenada(-21.54, -64.72));

        expect(controller.tienePermiso, isFalse);
        expect(controller.siguiendoUbicacion, isFalse);
        expect(ubicacion.escuchando, 0);
        expect(controller.puntoSeleccionado, const Coordenada(-21.54, -64.72));
        controller.dispose();
      },
    );

    test('CP-58: si el GPS se desactiva, no se reutiliza la última ubicación', () async {
      final ubicacion = UbicacionFalsa();
      final controller = MapController(ubicacion);
      await controller.activarUbicacion();
      ubicacion.emitir(const Coordenada(-21.53, -64.73));
      await Future<void>.delayed(Duration.zero);
      expect(controller.posicion, isNotNull);

      ubicacion.permiso = PermisoUbicacion.servicioApagado;
      await controller.activarUbicacion();
      expect(controller.posicion, isNull);
      expect(controller.siguiendoUbicacion, isFalse);
      controller.dispose();
    });

    test('CP-30: en segundo plano deja de pedir la ubicación y la retoma al volver (RNF-13)', () async {
      final ubicacion = UbicacionFalsa();
      final controller = MapController(ubicacion);
      await controller.activarUbicacion();
      expect(ubicacion.escuchando, 1);

      controller.alSalirDePantalla();
      expect(ubicacion.escuchando, 0);
      expect(controller.siguiendoUbicacion, isFalse);
      expect(controller.posicion, isNull, reason: 'al volver no se reutiliza un GPS obsoleto');

      controller.alVolverAPantalla();
      expect(ubicacion.escuchando, 1);
      expect(controller.siguiendoUbicacion, isTrue);
      controller.dispose();
    });

    test('Si el permiso llega con la app en segundo plano, no empieza a seguir hasta volver', () async {
      final ubicacion = UbicacionFalsa();
      final controller = MapController(ubicacion);
      controller.alSalirDePantalla();

      await controller.activarUbicacion();
      expect(ubicacion.escuchando, 0);

      controller.alVolverAPantalla();
      expect(ubicacion.escuchando, 1);
      controller.dispose();
    });
  });

  group('Mapa base en MapController', () {
    test('CP-29: si el mapa no carga, muestra el error de servicio externo y deja reintentar', () {
      final controller = MapController(UbicacionFalsa());
      final intento = controller.intentoMapa;

      controller.informarErrorMapa('Failed to load style');
      expect(controller.errorMapa, MapController.mensajeErrorMapa);

      controller.reintentarMapa();
      expect(controller.errorMapa, isNull);
      expect(controller.intentoMapa, intento + 1);
      controller.dispose();
    });

    test('Guarda y quita el punto tocado', () {
      final controller = MapController(UbicacionFalsa());
      controller.seleccionarPunto(const Coordenada(-21.5, -64.7));
      expect(controller.puntoSeleccionado, const Coordenada(-21.5, -64.7));
      controller.limpiarPunto();
      expect(controller.puntoSeleccionado, isNull);
      controller.dispose();
    });
  });

  group('Una sola lectura del GPS para el mapa y la telemetría', () {
    late StreamController<LecturaUbicacion> gps;
    late int pedidosAlGps;
    late int escuchasAlGps;
    late int cierresDelGps;
    late ServicioUbicacionGeolocator servicio;

    setUp(() {
      pedidosAlGps = 0;
      escuchasAlGps = 0;
      cierresDelGps = 0;
      servicio = ServicioUbicacionGeolocator(gps: () {
        pedidosAlGps++;
        gps = StreamController<LecturaUbicacion>(
          onListen: () => escuchasAlGps++,
          onCancel: () => cierresDelGps++,
        );
        return gps.stream;
      });
    });

    final momento = DateTime.utc(2026, 11, 25, 12);
    LecturaUbicacion lectura(double lat, double lon, int segundos) =>
        LecturaUbicacion(Coordenada(lat, lon), 5, momento.add(Duration(seconds: segundos)));

    test('el mapa y la telemetría escuchan la misma lectura del GPS', () async {
      final posiciones = servicio.posiciones().listen((_) {});
      final lecturas = servicio.lecturas().listen((_) {});
      await Future<void>.delayed(Duration.zero);

      expect(pedidosAlGps, 1, reason: 'se pide al GPS una sola vez');
      expect(escuchasAlGps, 1);

      await posiciones.cancel();
      expect(cierresDelGps, 0, reason: 'la telemetría todavía escucha');
      await lecturas.cancel();
      expect(cierresDelGps, 1, reason: 'sin nadie que escuche, se deja de pedir la ubicación (RNF-13)');

      // Al volver a pantalla se pide de nuevo.
      final otra = servicio.posiciones().listen((_) {});
      await Future<void>.delayed(Duration.zero);
      expect(pedidosAlGps, 2);
      await otra.cancel();
    });

    test('la telemetría recibe todas las lecturas, también detenido; el mapa, solo al moverse 5 m', () async {
      final alMapa = <Coordenada>[];
      final aTelemetria = <LecturaUbicacion>[];
      final posiciones = servicio.posiciones().listen(alMapa.add);
      final lecturas = servicio.lecturas().listen(aTelemetria.add);
      await Future<void>.delayed(Duration.zero);

      gps.add(lectura(-21.53500, -64.73000, 0));
      gps.add(lectura(-21.53500, -64.73000, 3)); // detenido
      gps.add(lectura(-21.53502, -64.73000, 6)); // unos 2 m
      gps.add(lectura(-21.53510, -64.73000, 9)); // unos 11 m desde la primera
      await Future<void>.delayed(Duration.zero);

      expect(aTelemetria, hasLength(4), reason: 'P-13: un auto detenido también manda lecturas');
      expect(alMapa, const [Coordenada(-21.53500, -64.73000), Coordenada(-21.53510, -64.73000)]);
      await posiciones.cancel();
      await lecturas.cancel();
    });

    test('un error del GPS (por ejemplo, se apagó) llega al mapa y a la telemetría', () async {
      Object? errorMapa;
      Object? errorTelemetria;
      final posiciones = servicio.posiciones().listen((_) {}, onError: (Object e) => errorMapa = e);
      final lecturas = servicio.lecturas().listen((_) {}, onError: (Object e) => errorTelemetria = e);
      await Future<void>.delayed(Duration.zero);

      gps.addError(StateError('GPS apagado'));
      await Future<void>.delayed(Duration.zero);

      expect(errorMapa, isA<StateError>());
      expect(errorTelemetria, isA<StateError>());
      await posiciones.cancel();
      await lecturas.cancel();
    });
  });

  test('soloAlMoverse deja pasar la primera posición y después solo las que se alejan 5 m o más', () async {
    final salida = await soloAlMoverse(
      Stream.fromIterable(const [
        Coordenada(-21.5350, -64.7300),
        Coordenada(-21.53503, -64.7300), // unos 3 m
        Coordenada(-21.53506, -64.7300), // unos 7 m desde la primera
        Coordenada(-21.53507, -64.7300), // 1 m desde la anterior que pasó
      ]),
      5,
    ).toList();
    expect(salida, const [Coordenada(-21.5350, -64.7300), Coordenada(-21.53506, -64.7300)]);
  });
}
