import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/models/sesion.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';
import 'package:app_flutter/services/sesion/servicio_sesion.dart';

import 'dobles/dobles_sesion.dart';

/// Catálogos como los devuelve `GET /catalogos` (T-094).
const String catalogosJson = '''
{"tiposEvento":[
  {"codigo":"ACCIDENTE","nombre":"Accidente","color":"#E53935","vigenciaMinutos":60,"reportable":true},
  {"codigo":"CONTROL_POLICIAL","nombre":"Control policial","color":"#1E88E5","vigenciaMinutos":60,"reportable":true},
  {"codigo":"BLOQUEO","nombre":"Bloqueo","color":"#FB8C00","vigenciaMinutos":60,"reportable":true},
  {"codigo":"OTRO","nombre":"Otro","color":"#757575","vigenciaMinutos":60,"reportable":true},
  {"codigo":"CONGESTION","nombre":"Congestión","color":"#FDD835","vigenciaMinutos":30,"reportable":false}],
 "tiposCombustible":[{"codigo":"GASOLINA","nombre":"Gasolina","seleccionable":true}],
 "tiemposReporte":[
  {"minutos":0,"nombre":"Ahora mismo","porDefecto":false},
  {"minutos":5,"nombre":"Hace 5 minutos","porDefecto":true},
  {"minutos":10,"nombre":"Hace 10 minutos","porDefecto":false},
  {"minutos":30,"nombre":"Hace 30 minutos","porDefecto":false}],
 "valoresFila":[{"codigo":"SIN_FILA","nombre":"Sin fila"}]}
''';

/// Un backend falso que responde las rutas de sesión y de catálogos, y anota lo que recibió.
class BackendFalso {
  final List<http.Request> pedidos = [];
  bool sinRed = false;
  int estadoGoogle = 200;
  int estadoRenovar = 200;
  int jwtEmitidos = 0;
  DateTime ahora = DateTime.utc(2026, 11, 1, 12);

  http.Client get cliente => MockClient((pedido) async {
    pedidos.add(pedido);
    if (sinRed) throw const SocketException('sin red');
    switch (pedido.url.path) {
      case '/sesion/google':
        if (estadoGoogle != 200) {
          return _json({'codigo': 'TOKEN_GOOGLE_INVALIDO', 'mensaje': 'No pudimos verificar tu cuenta de Google.'},
              estadoGoogle);
        }
        return _json({'jwt': 'jwt-${++jwtEmitidos}', 'expiraEn': _hora(1), 'tokenRenovacion': 'renovacion-1'}, 200);
      case '/sesion/renovar':
        if (estadoRenovar != 200) {
          return _json({'codigo': 'SESION_VENCIDA', 'mensaje': 'Tu sesión venció.'}, estadoRenovar);
        }
        return _json({'jwt': 'jwt-${++jwtEmitidos}', 'expiraEn': _hora(1)}, 200);
      case '/sesion/cerrar':
        return http.Response('', 204);
      case '/catalogos':
        return http.Response.bytes(utf8.encode(catalogosJson), 200,
            headers: {'content-type': 'application/json; charset=utf-8'});
      default:
        return _json({'ok': true, 'autorizacion': pedido.headers['Authorization']}, 200);
    }
  });

  String _hora(int horas) => ahora.add(Duration(hours: horas)).toIso8601String();

  static http.Response _json(Object cuerpo, int estado) => http.Response.bytes(
    utf8.encode(jsonEncode(cuerpo)),
    estado,
    headers: {'content-type': 'application/json; charset=utf-8'},
  );

  List<String> rutasPedidas() => [for (final p in pedidos) p.url.path];
}

void main() {
  late BackendFalso backend;
  late AlmacenFalso almacen;
  late GoogleFalso google;
  late ApiClient api;
  late SesionController sesion;

  SesionController crear() {
    api = ApiClient(client: backend.cliente);
    final controlador = SesionController(
      servicio: ServicioSesion(api),
      google: google,
      almacen: almacen,
      catalogos: ServicioCatalogos(api, almacen),
      reloj: () => backend.ahora,
    );
    api.gestor = controlador;
    return controlador;
  }

  void guardarSesion({required DateTime expiraEn, required DateTime iniciadaEn}) {
    almacen.datos[SesionController.claveSesion] = jsonEncode(Sesion(
      jwt: 'jwt-guardado',
      expiraEn: expiraEn,
      tokenRenovacion: 'renovacion-guardada',
      iniciadaEn: iniciadaEn,
    ).toJson());
  }

  setUp(() {
    backend = BackendFalso();
    almacen = AlmacenFalso();
    google = GoogleFalso();
    sesion = crear();
  });

  test('Sin sesión guardada, queda en la vista de inicio (RN-08.2)', () async {
    await sesion.cargar();
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(backend.pedidos, isEmpty);
  });

  test('Al elegir la cuenta, envía el token a POST /sesion/google, guarda los tokens y los catálogos', () async {
    await sesion.cargar();
    await sesion.iniciarConGoogle();

    expect(sesion.estado, EstadoSesion.conSesion);
    expect(sesion.token, 'jwt-1');
    expect(backend.rutasPedidas(), ['/sesion/google', '/catalogos']);
    expect(jsonDecode(backend.pedidos.first.body), {'tokenGoogle': 'token-de-google'});
    expect(backend.pedidos.first.headers.containsKey('Authorization'), isFalse);
    expect(backend.pedidos.last.headers['Authorization'], 'Bearer jwt-1');
    // RN-08.7: los tokens y los catálogos quedan guardados en el teléfono.
    expect(almacen.datos[SesionController.claveSesion], contains('renovacion-1'));
    expect(almacen.datos[ServicioCatalogos.clave], contains('CONTROL_POLICIAL'));
    expect(sesion.catalogos!.reportables.map((t) => t.codigo),
        ['ACCIDENTE', 'CONTROL_POLICIAL', 'BLOQUEO', 'OTRO']);
  });

  test('CP-38: cancelar el selector de cuentas deja al conductor en la vista de inicio, sin error', () async {
    google.token = null;
    await sesion.cargar();
    await sesion.iniciarConGoogle();

    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(sesion.error, isNull);
    expect(sesion.ocupado, isFalse);
    expect(backend.pedidos, isEmpty);
  });

  test('RN-08.3: si el backend rechaza el token, muestra el error y deja reintentar', () async {
    backend.estadoGoogle = 401;
    await sesion.cargar();
    await sesion.iniciarConGoogle();
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(sesion.error, 'No pudimos verificar tu cuenta de Google.');

    backend.estadoGoogle = 200;
    await sesion.iniciarConGoogle();
    expect(sesion.estado, EstadoSesion.conSesion);
    expect(sesion.error, isNull);
  });

  test('RN-08.9: sin red durante el inicio, avisa del error de red', () async {
    backend.sinRed = true;
    await sesion.cargar();
    await sesion.iniciarConGoogle();
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(sesion.error, contains('conexión'));
  });

  test('CP-39: con sesión vigente y sin red, entra directo al mapa aunque el JWT haya vencido', () async {
    guardarSesion(
      expiraEn: backend.ahora.subtract(const Duration(hours: 3)),
      iniciadaEn: backend.ahora.subtract(const Duration(days: 2)),
    );
    backend.sinRed = true;
    await sesion.cargar();
    await Future<void>.delayed(Duration.zero);

    expect(sesion.estado, EstadoSesion.conSesion);
    expect(almacen.datos[SesionController.claveSesion], isNotNull, reason: 'sin red no se pierde la sesión');

    // RN-08.10: al volver la red, renueva el JWT.
    backend.sinRed = false;
    expect(await sesion.asegurarTokenVigente(), isTrue);
    expect(sesion.token, 'jwt-1');
    expect(backend.rutasPedidas().last, '/sesion/renovar');
  });

  test('Con el token de renovación de más de 30 días, vuelve a la vista de inicio', () async {
    guardarSesion(
      expiraEn: backend.ahora.subtract(const Duration(days: 31)),
      iniciadaEn: backend.ahora.subtract(const Duration(days: 31)),
    );
    await sesion.cargar();
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(almacen.datos, isEmpty);
  });

  test('RN-08.12: antes de que venza el JWT, el cliente HTTP lo renueva', () async {
    await sesion.cargar();
    await sesion.iniciarConGoogle();
    backend.ahora = backend.ahora.add(const Duration(minutes: 59));

    final respuesta = await api.get('/eventos');
    expect(backend.rutasPedidas().sublist(2), ['/sesion/renovar', '/eventos']);
    expect(respuesta['autorizacion'], 'Bearer jwt-2');
  });

  test('RN-08.11: si el backend responde 401, renueva una vez y repite la petición', () async {
    await sesion.cargar();
    await sesion.iniciarConGoogle();
    var primera = true;
    final cliente = MockClient((pedido) async {
      if (primera) {
        primera = false;
        return http.Response('{"codigo":"NO_AUTENTICADO","mensaje":"Inicia sesión para continuar."}', 401);
      }
      return http.Response(jsonEncode({'autorizacion': pedido.headers['Authorization']}), 200);
    });
    final otraApi = ApiClient(client: cliente)..gestor = sesion;
    // El servicio de sesión sigue usando el backend falso para renovar.
    final respuesta = await otraApi.get('/eventos');
    expect(respuesta['autorizacion'], 'Bearer jwt-2');
    expect(backend.rutasPedidas(), contains('/sesion/renovar'));
  });

  test('RN-08.11: si la renovación falla con 401, lleva a la vista de inicio', () async {
    await sesion.cargar();
    await sesion.iniciarConGoogle();
    backend.ahora = backend.ahora.add(const Duration(hours: 2));
    backend.estadoRenovar = 401;

    await expectLater(api.get('/eventos'),
        throwsA(isA<ApiException>().having((e) => e.codigo, 'codigo', 'SESION_VENCIDA')));
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(sesion.error, SesionController.mensajeSesionVencida);
    // Solo se borran los tokens: los catálogos (y la cola) se quedan para cuando vuelva a iniciar sesión.
    expect(almacen.datos.containsKey(SesionController.claveSesion), isFalse);
    expect(almacen.datos[ServicioCatalogos.clave], isNotNull);
    expect(sesion.catalogos, isNotNull);
  });

  test('CP-40: después de cerrar sesión, vuelve a la vista de inicio y no queda ningún token guardado', () async {
    var limpiezas = 0;
    sesion.registrarLimpieza(() async => limpiezas++);
    await sesion.cargar();
    await sesion.iniciarConGoogle();

    await sesion.cerrarSesion();
    expect(sesion.estado, EstadoSesion.sinSesion);
    expect(sesion.token, isNull);
    expect(sesion.catalogos, isNull);
    expect(almacen.datos, isEmpty);
    expect(limpiezas, 1, reason: 'también se borran la cola de reportes y los datos guardados');
    expect(google.cierres, 1);
    expect(backend.rutasPedidas().last, '/sesion/cerrar');
    expect(jsonDecode(backend.pedidos.last.body), {'tokenRenovacion': 'renovacion-1'});
  });
}
