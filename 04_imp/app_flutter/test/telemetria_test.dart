import 'dart:convert';
import 'dart:io';

import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/controllers/telemetria_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/services/sesion/servicio_sesion.dart';
import 'package:app_flutter/services/telemetria/servicio_telemetria.dart';
import 'package:app_flutter/services/ubicacion/servicio_ubicacion.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/dobles_sesion.dart';

/// El backend falso: anota cada `POST /telemetria`.
class Backend {
  final List<List<dynamic>> envios = [];
  final List<String?> autorizaciones = [];
  bool caido = false;

  http.Client get cliente => MockClient((pedido) async {
    if (pedido.url.path != '/telemetria') return http.Response('{}', 200);
    if (caido) throw const SocketException('sin red');
    autorizaciones.add(pedido.headers['Authorization']);
    envios.add((jsonDecode(pedido.body) as Map<String, dynamic>)['lecturas'] as List<dynamic>);
    return http.Response('{"estado":"RECIBIDO","lecturas":5}', 202);
  });
}

class Escenario {
  final FakeAsync tiempo;
  final Backend backend = Backend();
  final UbicacionFalsa ubicacion;
  late final MapController mapa;
  late final SesionController sesion;
  late final TelemetriaController telemetria;
  late final DateTime inicio;

  Escenario(this.tiempo, {PermisoUbicacion permiso = PermisoUbicacion.concedido, bool conSesion = true})
    : ubicacion = UbicacionFalsa(permiso: permiso) {
    inicio = DateTime.utc(2026, 11, 14, 14);
    ubicacion.reloj = () => inicio.add(tiempo.elapsed);
    final api = ApiClient(client: backend.cliente);
    final almacen = conSesion ? AlmacenFalso.conSesionVigente() : AlmacenFalso();
    sesion = SesionController(
      servicio: ServicioSesion(api),
      google: GoogleFalso(),
      almacen: almacen,
      catalogos: ServicioCatalogos(api, almacen),
      reloj: () => inicio.add(tiempo.elapsed),
    );
    api.gestor = sesion;
    sesion.cargar();
    mapa = MapController(ubicacion);
    telemetria = TelemetriaController(mapa, sesion, ubicacion, ServicioTelemetria(api));
    mapa.activarUbicacion();
    tiempo.flushMicrotasks();
  }

  /// El GPS da una lectura por segundo durante [segundos], avanzando 10 m al este cada vez.
  void moverse(int segundos) {
    for (var i = 0; i < segundos; i++) {
      final k = tiempo.elapsed.inSeconds;
      ubicacion.emitir(Coordenada(-21.5355, -64.7296 + k * 0.0001), precisionM: 6);
      tiempo.elapse(const Duration(seconds: 1));
    }
  }

  void cerrar() {
    telemetria.dispose();
    mapa.dispose();
    sesion.dispose();
  }
}

void enEscenario(void Function(Escenario e) prueba,
    {PermisoUbicacion permiso = PermisoUbicacion.concedido, bool conSesion = true}) {
  fakeAsync((tiempo) {
    final e = Escenario(tiempo, permiso: permiso, conSesion: conSesion);
    prueba(e);
    e.cerrar();
  });
}

void main() {
  test('CP-122: con la app abierta 1 minuto llegan 4 envíos; con la app minimizada, ninguno', () {
    enEscenario((e) {
      expect(e.telemetria.activa, isTrue);
      e.moverse(60);
      expect(e.backend.envios, hasLength(4));
      // P-13: una lectura cada 3 segundos, en grupos de 15 segundos.
      expect(e.backend.envios.map((g) => g.length), [5, 5, 5, 5]);
      final lectura = e.backend.envios.first.first as Map<String, dynamic>;
      expect(lectura.keys, unorderedEquals(['ubicacion', 'precisionM', 'momento']));
      expect(lectura['precisionM'], 6);
      expect(DateTime.parse(lectura['momento'] as String).isUtc, isTrue);
      expect(e.backend.autorizaciones.every((a) => a != null && a.startsWith('Bearer ')), isTrue);

      // RN-04.1 y RNF-13: en segundo plano deja de leer y de enviar.
      e.mapa.alSalirDePantalla();
      expect(e.telemetria.activa, isFalse);
      expect(e.ubicacion.escuchandoLecturas, 0);
      e.moverse(60);
      expect(e.backend.envios, hasLength(4));

      // Al volver, empieza de cero.
      e.mapa.alVolverAPantalla();
      e.moverse(15);
      expect(e.backend.envios, hasLength(5));
      final momento = DateTime.parse((e.backend.envios.last.first as Map)['momento'] as String);
      expect(momento.isAfter(e.inicio.add(const Duration(seconds: 120))), isTrue, reason: 'nada de lecturas viejas');
    });
  });

  test('CP-123: al revocar el permiso, la telemetría se detiene y el mapa sigue funcionando', () {
    enEscenario((e) {
      e.moverse(15);
      expect(e.backend.envios, hasLength(1));
      e.ubicacion.fallar(StateError('permiso revocado'));
      e.tiempo.flushMicrotasks();
      expect(e.telemetria.activa, isFalse);
      expect(e.mapa.errorMapa, isNull, reason: 'el mapa base no depende de la ubicación');
      e.mapa.seleccionarPunto(const Coordenada(-21.53, -64.73));
      expect(e.mapa.puntoSeleccionado, isNotNull);
      e.moverse(60);
      expect(e.backend.envios, hasLength(1));
    });
  });

  test('RN-04.3: sin permiso de ubicación no envía nada', () {
    enEscenario((e) {
      expect(e.telemetria.activa, isFalse);
      e.moverse(60);
      expect(e.backend.envios, isEmpty);
    }, permiso: PermisoUbicacion.denegado);
  });

  test('RN-04.4: sin sesión no envía nada', () {
    enEscenario((e) {
      e.moverse(60);
      expect(e.backend.envios, isEmpty);
      expect(e.telemetria.activa, isFalse);
    }, conSesion: false);
  });

  test('Un grupo que no se pudo enviar se descarta: no se reintenta ni se suma al siguiente', () {
    enEscenario((e) {
      e.backend.caido = true;
      e.moverse(15);
      expect(e.telemetria.descartados, 1);
      e.backend.caido = false;
      e.moverse(15);
      expect(e.backend.envios.single, hasLength(5));
      expect(e.telemetria.enviados, 1);
    });
  });

  test('Detenido sin lecturas nuevas del GPS no repite la última', () {
    enEscenario((e) {
      e.moverse(1);
      e.tiempo.elapse(const Duration(seconds: 14));
      expect(e.backend.envios.single, hasLength(1));
      e.tiempo.elapse(const Duration(seconds: 15));
      expect(e.backend.envios, hasLength(1));
    });
  });

  test('Al cerrar la sesión deja de enviar', () {
    enEscenario((e) {
      e.moverse(15);
      e.sesion.cerrarSesion();
      e.tiempo.flushMicrotasks();
      e.moverse(30);
      expect(e.backend.envios, hasLength(1));
      expect(e.telemetria.activa, isFalse);
    });
  });
}
