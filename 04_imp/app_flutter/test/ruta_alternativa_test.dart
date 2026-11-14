import 'dart:convert';

import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/navegacion_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/evento_vial.dart';
import 'package:app_flutter/models/revision_ruta.dart';
import 'package:app_flutter/models/ruta.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/rutas/servicio_rutas.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/rutas_de_navegacion.dart';

final EventoVial accidenteAdelante = EventoVial(
  id: 153,
  tipo: 'ACCIDENTE',
  ubicacion: desplazar(esquinaB, surM: 100),
  momentoOcurrencia: DateTime.utc(2026, 11, 14, 14),
  estado: 'ACTIVO',
  confirmado: false,
  origen: 'CONDUCTOR',
);
final EventoVial controlAtras = EventoVial(
  id: 154,
  tipo: 'CONTROL_POLICIAL',
  ubicacion: desplazar(esquinaA, esteM: 50),
  momentoOcurrencia: DateTime.utc(2026, 11, 14, 14),
  estado: 'ACTIVO',
  confirmado: false,
  origen: 'CONDUCTOR',
);

final RutaCalculada alternativa = RutaCalculada(
  estado: EstadoRuta.encontrada,
  distanciaM: 420,
  tiempoS: 90,
  trazado: [desplazar(esquinaA, esteM: 100), desplazar(esquinaA, esteM: 100, surM: 200), esquinaC],
  tramos: const [TramoRuta('osm:9:0:ida', 'Calle Colón'), TramoRuta('osm:9:1:ida', 'Calle Ingavi')],
);

/// El controlador con la ubicación, el servicio de rutas y el reloj falsos.
class Escenario {
  final FakeAsync tiempo;
  final UbicacionFalsa ubicacion = UbicacionFalsa();
  final ServicioRutasFalso rutas = ServicioRutasFalso();
  late final MapController mapa = MapController(ubicacion);
  late final NavegacionController nav;
  List<EventoVial> eventos = [accidenteAdelante, controlAtras];

  Escenario(this.tiempo) {
    mapa.activarUbicacion();
    tiempo.flushMicrotasks();
    nav = NavegacionController(ruta: rutaEnL, destino: esquinaC, mapa: mapa, rutas: rutas, eventos: () => eventos);
    ir(desplazar(esquinaA, esteM: 100));
  }

  void ir(Coordenada c) {
    ubicacion.emitir(c);
    tiempo.flushMicrotasks();
  }

  /// Pasa un ciclo de revisión (5 s).
  void ciclo() => tiempo.elapse(NavegacionController.cicloRevision);

  void cerrar() {
    nav.dispose();
    mapa.dispose();
  }
}

void enEscenario(void Function(Escenario e) prueba) {
  fakeAsync((tiempo) {
    final e = Escenario(tiempo);
    prueba(e);
    e.cerrar();
  });
}

void main() {
  test('RN-04.13 y D-12: cada 5 segundos manda la posición, los tramos y paradas que faltan, el destino y los avisados', () {
    enEscenario((e) {
      expect(e.rutas.revisiones, isEmpty);
      e.ciclo();
      final pedido = e.rutas.revisiones.single;
      expect(pedido.posicion, desplazar(esquinaA, esteM: 100));
      expect(pedido.tramos, ['osm:1:0:ida', 'osm:1:1:ida', 'osm:2:0:ida']);
      expect(pedido.destino, esquinaC);
      expect(pedido.eventosAvisados, isEmpty);
      e.ciclo();
      expect(e.rutas.revisiones, hasLength(2));
    });
  });

  test('CP-138: una alternativa se muestra sin cortar la navegación, con el evento y su ruta', () {
    enEscenario((e) {
      e.rutas.revision = RevisionRuta(estado: EstadoRevision.alternativa, eventoId: 153, alternativa: alternativa);
      e.ciclo();
      final aviso = e.nav.aviso!;
      expect(aviso.conAlternativa, isTrue);
      expect(aviso.evento!.tipo, 'ACCIDENTE');
      expect(aviso.segundosRestantes, 15);
      expect(e.nav.activa, isTrue);
      expect(e.nav.ruta, same(rutaEnL), reason: 'la navegación sigue con su ruta mientras el conductor decide');
      // Con la alternativa a la vista no se revisa más.
      e.ciclo();
      expect(e.rutas.revisiones, hasLength(1));
    });
  });

  test('RN-04.18: al aceptar, la alternativa pasa a ser la ruta Activa', () {
    enEscenario((e) {
      e.rutas.revision = RevisionRuta(estado: EstadoRevision.alternativa, eventoId: 153, alternativa: alternativa);
      e.ciclo();
      e.nav.aceptarAlternativa();
      expect(e.nav.aviso, isNull);
      expect(e.nav.ruta, same(alternativa));
      expect(e.nav.tramosQueFaltan, ['osm:9:0:ida', 'osm:9:1:ida']);
      expect(e.nav.calleActual, 'Calle Colón');
      e.rutas.revision = const RevisionRuta(estado: EstadoRevision.sinCambios);
      e.ciclo();
      expect(e.rutas.revisiones.last.eventosAvisados, [153]);
    });
  });

  test('CP-139: al rechazar, la ruta sigue igual y el aviso no vuelve', () {
    enEscenario((e) {
      e.rutas.revision = RevisionRuta(estado: EstadoRevision.alternativa, eventoId: 153, alternativa: alternativa);
      e.ciclo();
      e.nav.rechazarAlternativa();
      expect(e.nav.aviso, isNull);
      expect(e.nav.ruta, same(rutaEnL));
      // El backend ya no lo debería mandar (va en los avisados), y si lo manda no se muestra.
      e.ciclo();
      expect(e.rutas.revisiones.last.eventosAvisados, [153]);
      expect(e.nav.aviso, isNull);
    });
  });

  test('CP-140: sin respuesta, a los 15 segundos el aviso se cierra solo, la ruta sigue y no vuelve', () {
    enEscenario((e) {
      e.rutas.revision = RevisionRuta(estado: EstadoRevision.alternativa, eventoId: 153, alternativa: alternativa);
      e.ciclo();
      e.tiempo.elapse(const Duration(seconds: 14));
      expect(e.nav.aviso!.segundosRestantes, 1);
      e.tiempo.elapse(const Duration(seconds: 1));
      expect(e.nav.aviso, isNull);
      expect(e.nav.ruta, same(rutaEnL));
      expect(e.nav.eventosAvisados, {153});
      e.ciclo();
      expect(e.nav.aviso, isNull);
    });
  });

  test('RN-04.16: sin alternativa avisa del evento, mantiene la ruta y lo agrega a los avisados', () {
    enEscenario((e) {
      e.rutas.revision = const RevisionRuta(estado: EstadoRevision.sinAlternativa, eventoId: 153);
      e.ciclo();
      expect(e.nav.aviso!.conAlternativa, isFalse);
      expect(e.nav.ruta, same(rutaEnL));
      expect(e.nav.eventosAvisados, {153});
      e.nav.cerrarAviso();
      expect(e.nav.aviso, isNull);
    });
  });

  test('RN-04.14: un evento que ya quedó atrás no se avisa, pero cuenta como avisado', () {
    enEscenario((e) {
      e.rutas.revision = const RevisionRuta(estado: EstadoRevision.sinAlternativa, eventoId: 154);
      e.ciclo();
      expect(e.nav.aviso, isNull);
      expect(e.nav.eventosAvisados, {154});
    });
  });

  test('«Ruta desactualizada»: pide la ruta de nuevo desde la posición, con el mismo destino y las paradas que faltan', () {
    enEscenario((e) {
      e.rutas.revision = const RevisionRuta(estado: EstadoRevision.rutaDesactualizada);
      e.rutas.respuesta = alternativa;
      e.ciclo();
      final (origen, destino, paradas) = e.rutas.pedidos.single;
      expect(origen, desplazar(esquinaA, esteM: 100));
      expect(destino, esquinaC);
      expect(paradas, isEmpty);
      expect(e.nav.ruta, same(alternativa));
      expect(e.nav.aviso, isNull);
    });
  });

  test('Si la revisión falla (sin red o 503) sigue con la ruta sin mostrar nada y reintenta en el ciclo siguiente', () {
    enEscenario((e) {
      e.rutas.errorRevision = const ApiException(codigo: 'MOTOR_NO_DISPONIBLE', mensaje: 'x', statusCode: 503);
      e.ciclo();
      expect(e.nav.aviso, isNull);
      expect(e.nav.ruta, same(rutaEnL));
      e.rutas.errorRevision = null;
      e.ciclo();
      expect(e.rutas.revisiones, hasLength(2));
    });
  });

  test('Al terminar la ruta deja de revisar', () {
    enEscenario((e) {
      e.nav.cancelar();
      e.ciclo();
      expect(e.rutas.revisiones, isEmpty);
    });
  });

  test('POST /rutas/revision: el cuerpo del contrato y las cuatro respuestas', () async {
    late Map<String, dynamic> enviado;
    var respuesta = <String, dynamic>{};
    final api = ApiClient(client: MockClient((pedido) async {
      expect(pedido.url.path, '/rutas/revision');
      enviado = jsonDecode(pedido.body) as Map<String, dynamic>;
      return http.Response(jsonEncode(respuesta), 200, headers: {'content-type': 'application/json'});
    }));
    final servicio = ServicioRutasApi(api);
    final pedido = PedidoRevision(
      posicion: esquinaA,
      tramos: const ['osm:1:0:ida'],
      destino: esquinaC,
      paradas: [esquinaB],
      eventosAvisados: const [150],
    );

    respuesta = {'estado': 'SIN_CAMBIOS'};
    expect((await servicio.revisar(pedido)).estado, EstadoRevision.sinCambios);
    expect(enviado.keys, containsAll(['posicion', 'tramos', 'destino', 'paradas', 'eventosAvisados']));
    expect(enviado['posicion'], {'latitud': esquinaA.latitud, 'longitud': esquinaA.longitud});
    expect(enviado['eventosAvisados'], [150]);

    respuesta = {'estado': 'SIN_ALTERNATIVA', 'eventoId': 154};
    expect((await servicio.revisar(pedido)).eventoId, 154);

    respuesta = {
      'estado': 'ALTERNATIVA', 'eventoId': 153, 'costo': 1700, 'costoActual': 3244, 'distanciaM': 1700, 'tiempoS': 306,
      'trazado': [{'latitud': -21.5355, 'longitud': -64.7296}, {'latitud': -21.533, 'longitud': -64.731}],
      'tramos': [{'id': 'osm:6001:1:ida', 'calle': 'Av. Circunvalación'}],
    };
    final r = await servicio.revisar(pedido);
    expect(r.alternativa!.tiempoS, 306);
    expect(r.alternativa!.tramos.single.id, 'osm:6001:1:ida');
    expect(r.costoActual, 3244);

    respuesta = {'estado': 'RUTA_DESACTUALIZADA'};
    expect((await servicio.revisar(pedido)).estado, EstadoRevision.rutaDesactualizada);

    respuesta = {'estado': 'ALTERNATIVA'}; // sin evento
    await expectLater(servicio.revisar(pedido), throwsA(isA<ApiException>()));
  });
}
