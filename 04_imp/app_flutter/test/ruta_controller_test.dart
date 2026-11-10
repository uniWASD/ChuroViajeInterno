import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/controllers/ruta_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/ruta.dart';
import 'package:app_flutter/services/api/api_client.dart';

import 'dobles/dobles_mapa.dart';

const _posicion = Coordenada(-21.5344, -64.7340);
const _destino = Coordenada(-21.5417, -64.7250);

void main() {
  group('RutaController', () {
    test(
      'El primer toque elige el destino y el origen es la posición actual',
      () async {
        final servicio = ServicioRutasFalso();
        final c = RutaController(servicio);

        c.tocarMapa(_destino);
        await c.calcular(posicionActual: _posicion);

        expect(c.destino, _destino);
        expect(servicio.pedidos.single.$1, _posicion);
        expect(servicio.pedidos.single.$2, _destino);
        expect(c.ruta, isNotNull);
        expect(c.ruta!.distanciaM, 1524);
      },
    );

    test(
      'Las paradas se mandan en el orden en que se agregaron (RN-03.2)',
      () async {
        final servicio = ServicioRutasFalso();
        final c = RutaController(servicio, destinoInicial: _destino);
        const a = Coordenada(-21.53, -64.73);
        const b = Coordenada(-21.54, -64.72);

        c.elegir(EleccionPunto.parada);
        c.tocarMapa(a);
        c.elegir(EleccionPunto.parada);
        c.tocarMapa(b);
        await c.calcular(posicionActual: _posicion);

        expect(servicio.pedidos.single.$3, [a, b]);
        expect(c.eleccion, EleccionPunto.destino);
      },
    );

    test('No deja agregar más de 10 paradas', () {
      final c = RutaController(ServicioRutasFalso());
      for (var i = 0; i < 12; i++) {
        c.elegir(EleccionPunto.parada);
        c.tocarMapa(Coordenada(-21.53 - i / 1000, -64.73));
      }
      expect(c.paradas, hasLength(RutaController.maximoParadas));
      expect(c.puedeAgregarParada, isFalse);
    });

    test('Un origen marcado reemplaza a la posición actual', () async {
      final servicio = ServicioRutasFalso();
      final c = RutaController(servicio, destinoInicial: _destino);
      const marcado = Coordenada(-21.52, -64.74);

      c.elegir(EleccionPunto.origen);
      c.tocarMapa(marcado);
      await c.calcular(posicionActual: _posicion);
      expect(servicio.pedidos.last.$1, marcado);

      c.usarPosicionActual();
      await c.calcular(posicionActual: _posicion);
      expect(servicio.pedidos.last.$1, _posicion);
    });

    test('Sin ubicación ni origen marcado, pide marcar el origen sin llamar al backend', () async {
      final servicio = ServicioRutasFalso();
      final c = RutaController(servicio, destinoInicial: _destino);

      await c.calcular();

      expect(servicio.pedidos, isEmpty);
      expect(c.aviso, RutaController.mensajeSinUbicacion);
      expect(c.eleccion, EleccionPunto.origen);
    });

    test(
      '«No se encontró una ruta válida» es un aviso, no un error (RN-03.12)',
      () async {
        final c = RutaController(
          ServicioRutasFalso(
            respuesta: const RutaCalculada(
              estado: EstadoRuta.sinRuta,
              mensaje: RutaCalculada.mensajeSinRuta,
            ),
          ),
          destinoInicial: _destino,
        );

        await c.calcular(posicionActual: _posicion);

        expect(c.aviso, RutaCalculada.mensajeSinRuta);
        expect(c.error, isNull);
        expect(c.ruta, isNull);
      },
    );

    test(
      'Un 503 deja reintentar y el reintento muestra la ruta (RN-03.15)',
      () async {
        final servicio = ServicioRutasFalso()
          ..error = const ApiException(
            codigo: 'MOTOR_NO_DISPONIBLE',
            mensaje: 'El motor no está disponible',
            statusCode: 503,
          );
        final c = RutaController(servicio, destinoInicial: _destino);

        await c.calcular(posicionActual: _posicion);
        expect(c.error, RutaController.mensajeMotorNoDisponible);
        expect(c.calculando, isFalse);
        expect(c.puedeCalcular, isTrue);

        servicio.error = null;
        await c.calcular(posicionActual: _posicion);
        expect(c.error, isNull);
        expect(c.ruta, isNotNull);
      },
    );

    test('Cambiar un punto borra la ruta anterior', () async {
      final c = RutaController(ServicioRutasFalso(), destinoInicial: _destino);
      await c.calcular(posicionActual: _posicion);
      expect(c.ruta, isNotNull);

      c.tocarMapa(const Coordenada(-21.55, -64.71));
      expect(c.ruta, isNull);
    });
  });
}
