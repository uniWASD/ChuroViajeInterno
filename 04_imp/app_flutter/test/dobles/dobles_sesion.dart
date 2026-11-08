import 'dart:convert';

import 'package:app_flutter/controllers/sesion_controller.dart';
import 'package:app_flutter/models/sesion.dart';
import 'package:app_flutter/services/sesion/almacen_local.dart';
import 'package:app_flutter/services/sesion/proveedor_google.dart';

/// Almacenamiento en memoria, en lugar del almacenamiento seguro del teléfono.
class AlmacenFalso implements AlmacenLocal {
  final Map<String, String> datos = {};

  AlmacenFalso();

  /// Con una sesión guardada que todavía vale: la app abre directo en el mapa.
  AlmacenFalso.conSesionVigente() {
    final ahora = DateTime.now();
    datos[SesionController.claveSesion] = jsonEncode(Sesion(
      jwt: 'jwt-de-prueba',
      expiraEn: ahora.add(const Duration(days: 1)),
      tokenRenovacion: 'renovacion-de-prueba',
      iniciadaEn: ahora,
    ).toJson());
  }

  @override
  Future<String?> leer(String clave) async => datos[clave];

  @override
  Future<void> guardar(String clave, String valor) async => datos[clave] = valor;

  @override
  Future<void> borrar(String clave) async => datos.remove(clave);

  @override
  Future<void> borrarTodo() async => datos.clear();
}

/// Selector de cuentas de Google falso: devuelve un token, o simula que el conductor lo cerró.
class GoogleFalso implements ProveedorGoogle {
  String? token;
  int pedidos = 0;
  int cierres = 0;

  GoogleFalso({this.token = 'token-de-google'});

  @override
  Future<String> pedirTokenDeIdentidad() async {
    pedidos++;
    final t = token;
    if (t == null) throw const InicioCancelado();
    return t;
  }

  @override
  Future<void> cerrar() async => cierres++;
}
