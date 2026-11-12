import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
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
}
