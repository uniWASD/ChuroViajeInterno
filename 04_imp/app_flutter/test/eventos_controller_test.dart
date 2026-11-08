import 'dart:convert';
import 'dart:io';

import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/controllers/eventos_controller.dart';
import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/services/eventos/servicio_eventos.dart';
import 'package:app_flutter/services/sesion/servicio_sesion.dart';

import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

Map<String, dynamic> evento(int id, String tipo, String momento, {bool confirmado = false}) => {
  'id': id,
  'tipo': tipo,
  'ubicacion': {'latitud': -21.5355, 'longitud': -64.7296},
  'momentoOcurrencia': momento,
  'estado': confirmado ? 'CONFIRMADO' : 'ACTIVO',
  'confirmado': confirmado,
  'origen': 'CONDUCTOR',
};

/// `GET /eventos` falso: responde lo que la prueba ponga en [respuestas], en orden, y anota las marcas recibidas.
class BackendEventos {
  final List<Map<String, dynamic>> respuestas = [];
  final List<String?> marcas = [];
  bool caido = false;

  http.Client get cliente => MockClient((pedido) async {
    if (pedido.url.path != '/eventos') return http.Response('{}', 200);
    marcas.add(pedido.url.queryParameters['desde']);
    if (caido) throw const SocketException('backend apagado');
    final respuesta = respuestas.isEmpty
        ? {'marca': 'm-${marcas.length}', 'completa': false, 'eventos': [], 'quitados': []}
        : respuestas.removeAt(0);
    return http.Response.bytes(utf8.encode(jsonEncode(respuesta)), 200);
  });
}

void main() {
  late BackendEventos backend;
  late DateTime ahora;
  late SesionController sesion;
  late EventosController eventos;

  Future<void> preparar() async {
    backend = BackendEventos();
    ahora = DateTime.parse('2026-11-01T12:00:00Z');
    final almacen = AlmacenFalso.conSesionVigente()..datos[ServicioCatalogos.clave] = catalogosJson;
    final api = ApiClient(client: backend.cliente);
    sesion = SesionController(
      servicio: ServicioSesion(api),
      google: GoogleFalso(),
      almacen: almacen,
      catalogos: ServicioCatalogos(api, almacen),
    );
    api.gestor = sesion;
    await sesion.cargar();
    eventos = EventosController(ServicioEventos(api), sesion, reloj: () => ahora);
  }

  tearDown(() => eventos.dispose());

  test('La primera consulta trae todos los vigentes; las siguientes mandan la marca y aplican solo los cambios', () async {
    await preparar();
    backend.respuestas.addAll([
      {'marca': 'm1', 'completa': true, 'quitados': [], 'eventos': [
        evento(1, 'ACCIDENTE', '2026-11-01T11:50:00Z'),
        evento(2, 'BLOQUEO', '2026-11-01T11:55:00Z'),
      ]},
      {'marca': 'm2', 'completa': false, 'quitados': [1], 'eventos': [
        evento(2, 'BLOQUEO', '2026-11-01T11:55:00Z', confirmado: true),
        evento(3, 'OTRO', '2026-11-01T11:59:00Z'),
      ]},
    ]);

    await eventos.actualizar();
    expect(eventos.eventos.map((e) => e.id), [1, 2]);
    await eventos.actualizar();

    expect(backend.marcas, [null, 'm1']);
    expect(eventos.eventos.map((e) => e.id), [2, 3], reason: 'el 1 se retiró o venció');
    expect(eventos.eventos.first.confirmado, isTrue, reason: 'el 2 llega confirmado entre los modificados');
  });

  test('CP-66: con el backend apagado, conserva los eventos y avisa «No se pudo actualizar eventos»', () async {
    await preparar();
    backend.respuestas.add({'marca': 'm1', 'completa': true, 'quitados': [], 'eventos': [
      evento(1, 'ACCIDENTE', '2026-11-01T11:50:00Z'),
    ]});
    await eventos.actualizar();

    backend.caido = true;
    await eventos.actualizar();
    expect(eventos.sinActualizar, isTrue);
    expect(eventos.eventos.map((e) => e.id), [1]);

    backend.caido = false; // en el siguiente ciclo se recupera
    await eventos.actualizar();
    expect(eventos.sinActualizar, isFalse);
    expect(backend.marcas.last, 'm1', reason: 'la consulta fallida no cambió la marca');
  });

  test('CP-67: un evento que vence mientras se mira desaparece en la siguiente actualización', () async {
    await preparar();
    backend.respuestas.add({'marca': 'm1', 'completa': true, 'quitados': [], 'eventos': [
      evento(1, 'CONTROL_POLICIAL', '2026-11-01T11:00:30Z'), // vence a las 12:00:30
    ]});
    await eventos.actualizar();
    expect(eventos.eventos, hasLength(1));

    ahora = ahora.add(const Duration(seconds: 35));
    backend.respuestas.add({'marca': 'm2', 'completa': false, 'quitados': [1], 'eventos': []});
    await eventos.actualizar();
    expect(eventos.eventos, isEmpty);
  });

  test('Con los tiempos del catálogo, un evento ya vencido no se dibuja aunque el aviso no haya llegado', () async {
    await preparar();
    backend.respuestas.add({'marca': 'm1', 'completa': true, 'quitados': [], 'eventos': [
      evento(1, 'CONTROL_POLICIAL', '2026-11-01T11:00:30Z'),
    ]});
    await eventos.actualizar();
    ahora = ahora.add(const Duration(minutes: 1));
    expect(eventos.eventos, isEmpty);
  });

  test('RN-02.6: con el mapa abierto consulta cada 5 segundos; en segundo plano se detiene', () async {
    await preparar();
    fakeAsync((tiempo) {
      eventos.activar();
      tiempo.flushMicrotasks();
      expect(backend.marcas, hasLength(1));
      tiempo.elapse(const Duration(seconds: 15));
      expect(backend.marcas, hasLength(4));

      eventos.pausar();
      tiempo.elapse(const Duration(seconds: 30));
      expect(backend.marcas, hasLength(4));
      expect(eventos.activo, isFalse);
    });
  });

  test('Al cerrar sesión deja de consultar y olvida los eventos', () async {
    await preparar();
    backend.respuestas.add({'marca': 'm1', 'completa': true, 'quitados': [], 'eventos': [
      evento(1, 'ACCIDENTE', '2026-11-01T11:50:00Z'),
    ]});
    eventos.activar();
    await Future<void>.delayed(Duration.zero);
    await sesion.cerrarSesion();
    expect(eventos.activo, isFalse);
    expect(eventos.eventos, isEmpty);
  });
}
