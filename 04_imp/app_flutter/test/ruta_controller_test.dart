import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/controllers/ruta_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/revision_ruta.dart';
import 'package:app_flutter/models/ruta.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/rutas/servicio_rutas.dart';

import 'dobles/dobles_mapa.dart';

const _posicion = Coordenada(-21.5344, -64.7340);
const _destino = Coordenada(-21.5417, -64.7250);

/// Un servicio que responde cuando la prueba lo decide, para ver qué pasa con las respuestas que llegan tarde.
class ServicioRutasLento implements ServicioRutas {
  final List<Completer<RutaCalculada>> pendientes = [];
  final List<Coordenada> destinos = [];

  @override
  bool get esDeEjemplo => false;

  @override
  Future<RutaCalculada> calcular(Coordenada origen, Coordenada destino, List<Coordenada> paradas) {
    destinos.add(destino);
    final c = Completer<RutaCalculada>();
    pendientes.add(c);
    return c.future;
  }

  @override
  Future<RevisionRuta> revisar(PedidoRevision pedido) async => const RevisionRuta(estado: EstadoRevision.sinCambios);
}

void main() {
  group('RutaController (#191: la ruta hacia el lugar elegido)', () {
    test('Al elegir un lugar se calcula la ruta desde la posición del conductor', () async {
      final servicio = ServicioRutasFalso();
      final c = RutaController(servicio);

      await c.prever(_destino, _posicion);

      expect(c.destino, _destino);
      expect(servicio.pedidos.single.$1, _posicion);
      expect(servicio.pedidos.single.$2, _destino);
      expect(servicio.pedidos.single.$3, isEmpty);
      expect(c.ruta!.distanciaM, 1524);
      expect(c.mostrando, isFalse, reason: 'la ruta se calcula, pero se muestra recién con «Cómo llegar»');
    });

    test('El mismo lugar no se vuelve a pedir', () async {
      final servicio = ServicioRutasFalso();
      final c = RutaController(servicio);
      await c.prever(_destino, _posicion);
      await c.prever(_destino, _posicion);
      expect(servicio.pedidos, hasLength(1));
    });

    test('Si el conductor eligió otro lugar mientras tanto, la respuesta vieja se descarta', () async {
      final servicio = ServicioRutasLento();
      final c = RutaController(servicio);
      const otro = Coordenada(-21.55, -64.71);

      unawaited(c.prever(_destino, _posicion));
      unawaited(c.prever(otro, _posicion));
      servicio.pendientes[1].complete(rutaDePrueba);
      await Future<void>.delayed(Duration.zero);
      servicio.pendientes[0].complete(
        const RutaCalculada(estado: EstadoRuta.sinRuta, mensaje: RutaCalculada.mensajeSinRuta),
      );
      await Future<void>.delayed(Duration.zero);

      expect(c.destino, otro);
      expect(c.ruta, isNotNull);
      expect(c.aviso, isNull);
      expect(c.calculando, isFalse);
    });

    test('CP-196: «Otra parada» agrega la parada que se toca y recalcula, en el orden elegido (RN-03.2)', () async {
      final servicio = ServicioRutasFalso();
      final c = RutaController(servicio);
      const a = Coordenada(-21.53, -64.73);
      const b = Coordenada(-21.54, -64.72);
      await c.prever(_destino, _posicion);
      c.mostrar();

      c.elegirParada();
      expect(c.eligiendoParada, isTrue);
      await c.agregarParada(a, _posicion);
      expect(c.eligiendoParada, isFalse);
      c.elegirParada();
      await c.agregarParada(b, _posicion);

      expect(servicio.pedidos.last.$3, [a, b]);
      expect(servicio.pedidos.last.$1, _posicion, reason: 'el origen es siempre la posición del conductor');

      await c.quitarParada(0, _posicion);
      expect(servicio.pedidos.last.$3, [b]);
    });

    test('Cerrar «Cómo llegar» quita las paradas y vuelve a la ruta directa', () async {
      final servicio = ServicioRutasFalso();
      final c = RutaController(servicio);
      await c.prever(_destino, _posicion);
      c.mostrar();
      await c.agregarParada(const Coordenada(-21.53, -64.73), _posicion);

      await c.ocultar(_posicion);
      expect(c.mostrando, isFalse);
      expect(c.paradas, isEmpty);
      expect(servicio.pedidos.last.$3, isEmpty);
      expect(c.ruta, isNotNull);
    });

    test('No deja agregar más de 10 paradas', () async {
      final c = RutaController(ServicioRutasFalso());
      await c.prever(_destino, _posicion);
      for (var i = 0; i < 12; i++) {
        c.elegirParada();
        await c.agregarParada(Coordenada(-21.53 - i / 1000, -64.73), _posicion);
      }
      expect(c.paradas, hasLength(RutaController.maximoParadas));
      expect(c.puedeAgregarParada, isFalse);
    });

    test('Sin la posición del conductor avisa sin llamar al backend, y la pide cuando llega', () async {
      final servicio = ServicioRutasFalso();
      final c = RutaController(servicio);

      await c.prever(_destino, null);
      expect(servicio.pedidos, isEmpty);
      expect(c.aviso, RutaController.mensajeSinUbicacion);
      expect(c.sinUbicacion, isTrue);

      await c.reintentar(_posicion);
      expect(c.ruta, isNotNull);
      expect(c.sinUbicacion, isFalse);
    });

    test('«No se encontró una ruta válida» es un aviso, no un error (RN-03.12)', () async {
      final c = RutaController(
        ServicioRutasFalso(
          respuesta: const RutaCalculada(estado: EstadoRuta.sinRuta, mensaje: RutaCalculada.mensajeSinRuta),
        ),
      );
      await c.prever(_destino, _posicion);
      expect(c.aviso, RutaCalculada.mensajeSinRuta);
      expect(c.error, isNull);
      expect(c.ruta, isNull);
    });

    test('Un 503 deja reintentar y el reintento muestra la ruta (RN-03.15)', () async {
      final servicio = ServicioRutasFalso()
        ..error = const ApiException(
          codigo: 'MOTOR_NO_DISPONIBLE',
          mensaje: 'El motor no está disponible',
          statusCode: 503,
        );
      final c = RutaController(servicio);

      await c.prever(_destino, _posicion);
      expect(c.error, RutaController.mensajeMotorNoDisponible);
      expect(c.calculando, isFalse);

      servicio.error = null;
      await c.reintentar(_posicion);
      expect(c.error, isNull);
      expect(c.ruta, isNotNull);
    });

    test('Elegir otro lugar cierra «Cómo llegar» y borra las paradas', () async {
      final c = RutaController(ServicioRutasFalso());
      await c.prever(_destino, _posicion);
      c.mostrar();
      await c.agregarParada(const Coordenada(-21.53, -64.73), _posicion);

      await c.prever(const Coordenada(-21.55, -64.71), _posicion);
      expect(c.mostrando, isFalse);
      expect(c.paradas, isEmpty);
    });

    test('Limpiar deja todo sin elegir', () async {
      final c = RutaController(ServicioRutasFalso());
      await c.prever(_destino, _posicion);
      c.mostrar();
      c.limpiar();
      expect(c.destino, isNull);
      expect(c.ruta, isNull);
      expect(c.mostrando, isFalse);
    });
  });
}
