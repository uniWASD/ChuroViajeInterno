import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/ruta.dart';
import 'package:app_flutter/services/api/api_client.dart';
import 'package:app_flutter/services/rutas/servicio_rutas.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('RutaCalculada', () {
    test('Lee la respuesta ENCONTRADA del contrato', () {
      final r = RutaCalculada.fromJson({
        'estado': 'ENCONTRADA',
        'distanciaM': 755,
        'tiempoS': 136,
        'trazado': [
          {'latitud': -21.5355, 'longitud': -64.7296},
          {'latitud': -21.53, 'longitud': -64.726},
        ],
        'tramos': [
          {'id': 'osm:5001:1:ida', 'calle': 'Calle Comercio'},
        ],
      });

      expect(r.encontrada, isTrue);
      expect(r.distanciaM, 755);
      expect(r.tiempoS, 136);
      expect(r.trazado.first, const Coordenada(-21.5355, -64.7296));
      expect(r.tramos.single.calle, 'Calle Comercio');
    });

    test('SIN_RUTA y PUNTO_INVALIDO traen el mensaje para el conductor', () {
      final sin = RutaCalculada.fromJson({
        'estado': 'SIN_RUTA',
        'mensaje': 'No se encontró una ruta válida',
      });
      final invalido = RutaCalculada.fromJson({'estado': 'PUNTO_INVALIDO'});

      expect(sin.estado, EstadoRuta.sinRuta);
      expect(sin.mensaje, 'No se encontró una ruta válida');
      expect(invalido.mensaje, RutaCalculada.mensajePuntoInvalido);
      expect(invalido.trazado, isEmpty);
    });

    test('La ruta de ejemplo es la del primer viaje de referencia', () async {
      final texto = File(ServicioRutasDeEjemplo.archivo).readAsStringSync();
      final r = RutaCalculada.fromJson(
        jsonDecode(texto) as Map<String, dynamic>,
      );

      expect(r.encontrada, isTrue);
      expect(r.trazado.length, greaterThan(2));
      expect(r.tramos.map((t) => t.calle), contains('Avenida Las Américas'));

      final servicio = ServicioRutasDeEjemplo(rootBundle);
      final desdeAsset = await servicio.calcular(
        centroTarija,
        centroTarija,
        const [],
      );
      expect(desdeAsset.distanciaM, r.distanciaM);
      expect(servicio.esDeEjemplo, isTrue);
    });
  });

  group('Textos de la ruta', () {
    test('Distancia', () {
      expect(textoDistancia(747), '750 m');
      expect(textoDistancia(1524), '1,5 km');
      expect(textoDistancia(6000), '6 km');
    });

    test('Tiempo, redondeado hacia arriba', () {
      expect(textoTiempo(20), '1 min');
      expect(textoTiempo(163), '3 min');
      expect(textoTiempo(3600), '1 h');
      expect(textoTiempo(3900), '1 h 5 min');
    });
  });

  group('ServicioRutasApi', () {
    test('Manda origen, destino y paradas a POST /rutas', () async {
      late Map<String, dynamic> enviado;
      final api = ApiClient(
        client: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/rutas');
          enviado = jsonDecode(request.body) as Map<String, dynamic>;
          return http.Response(
            jsonEncode({
              'estado': 'SIN_RUTA',
              'mensaje': 'No se encontró una ruta válida',
            }),
            200,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }),
      );

      final r = await ServicioRutasApi(api).calcular(
        const Coordenada(-21.5355, -64.7296),
        const Coordenada(-21.53, -64.726),
        const [Coordenada(-21.532, -64.728)],
      );

      expect(enviado['origen'], {'latitud': -21.5355, 'longitud': -64.7296});
      expect(enviado['destino'], {'latitud': -21.53, 'longitud': -64.726});
      expect(enviado['paradas'], [
        {'latitud': -21.532, 'longitud': -64.728},
      ]);
      expect(r.estado, EstadoRuta.sinRuta);
      api.dispose();
    });

    test('Un 503 llega como ApiException con su código de estado', () async {
      final api = ApiClient(
        client: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'codigo': 'MOTOR_NO_DISPONIBLE',
              'mensaje': 'El motor no está disponible',
            }),
            503,
          ),
        ),
      );

      await expectLater(
        ServicioRutasApi(api).calcular(centroTarija, centroTarija, const []),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'statusCode', 503),
        ),
      );
      api.dispose();
    });
  });
}
