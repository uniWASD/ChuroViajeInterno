import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/catalogos/servicio_catalogos.dart';

import 'dobles/dobles_sesion.dart';
import 'sesion_controller_test.dart' show catalogosJson;

void main() {
  test('D-06/RN-01.14: un catálogo vacío no reemplaza las vigencias guardadas', () async {
    final almacen = AlmacenFalso()
      ..datos[ServicioCatalogos.clave] = catalogosJson;
    final api = ApiClient(client: MockClient((_) async => http.Response('{}', 200)));
    addTearDown(api.dispose);
    final servicio = ServicioCatalogos(api, almacen);

    await expectLater(
      servicio.descargarYGuardar(),
      throwsA(isA<ApiException>().having(
        (e) => e.codigo, 'codigo', 'RESPUESTA_INVALIDA',
      )),
    );
    final vigente = await servicio.leerGuardados();
    expect(vigente?.tipo('BLOQUEO')?.vigenciaMinutos, 60);
    expect(almacen.datos[ServicioCatalogos.clave], catalogosJson);
  });

  test('D-06: un catálogo mal formado no sobreescribe el válido', () async {
    final almacen = AlmacenFalso()
      ..datos[ServicioCatalogos.clave] = catalogosJson;
    final invalido = jsonEncode({
      'tiposEvento': [
        {'codigo': 'BLOQUEO'}, // incompleto: faltan campos obligatorios
      ],
      'tiemposReporte': [
        {'minutos': 5, 'nombre': 'Hace 5 minutos', 'porDefecto': true},
      ],
    });
    final api = ApiClient(client: MockClient((_) async => http.Response(invalido, 200)));
    addTearDown(api.dispose);
    final servicio = ServicioCatalogos(api, almacen);

    await expectLater(
      servicio.descargarYGuardar(),
      throwsA(isA<ApiException>().having(
        (e) => e.codigo, 'codigo', 'RESPUESTA_INVALIDA',
      )),
    );
    final vigente = await servicio.leerGuardados();
    expect(vigente?.tipo('BLOQUEO')?.vigenciaMinutos, 60);
    expect(almacen.datos[ServicioCatalogos.clave], catalogosJson);
  });
}
