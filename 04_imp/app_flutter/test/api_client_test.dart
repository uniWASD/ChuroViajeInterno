import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/services/api/api_client.dart';

void main() {
  group('ApiClient', () {
    test('Incluye el JWT en la petición', () async {
      final mockClient = MockClient((request) async {
        expect(request.headers['Authorization'], 'Bearer token-prueba');

        return http.Response(
          jsonEncode({'ok': true}),
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      final api = ApiClient(client: mockClient);
      api.setToken('token-prueba');

      final result = await api.get('/prueba');

      expect(result['ok'], isTrue);
      api.dispose();
    });

    test('Interpreta los errores del backend', () async {
      final mockClient = MockClient((request) async {
        return http.Response(
          jsonEncode({
            'codigo': 'NO_AUTORIZADO',
            'mensaje': 'Debes iniciar sesión.',
          }),
          401,
          headers: {'content-type': 'application/json'},
        );
      });

      final api = ApiClient(client: mockClient);

      await expectLater(
        api.get('/prueba'),
        throwsA(
          isA<ApiException>()
              .having((error) => error.codigo, 'codigo', 'NO_AUTORIZADO')
              .having(
                (error) => error.mensaje,
                'mensaje',
                'Debes iniciar sesión.',
              )
              .having((error) => error.statusCode, 'statusCode', 401),
        ),
      );

      api.dispose();
    });

    test('Controla los fallos de conexión', () async {
      final mockClient = MockClient((request) async {
        throw http.ClientException('Servidor fuera de servicio');
      });

      final api = ApiClient(client: mockClient);

      await expectLater(
        api.get('/prueba'),
        throwsA(
          isA<ApiException>().having(
            (error) => error.codigo,
            'codigo',
            'ERROR_RED',
          ),
        ),
      );

      api.dispose();
    });

    test('GET identifica cuando se agota el tiempo de espera', () async {
      final mockClient = MockClient((request) async {
        await Future<void>.delayed(const Duration(seconds: 1));
        return http.Response('{}', 200);
      });

      final api = ApiClient(
        client: mockClient,
        timeoutDuration: const Duration(milliseconds: 100),
      );

      await expectLater(
        api.get('/prueba'),
        throwsA(
          isA<ApiException>().having(
            (error) => error.codigo,
            'codigo',
            'TIEMPO_ESPERA',
          ),
        ),
      );

      api.dispose();
    });

    test('POST identifica cuando se agota el tiempo de espera', () async {
      final mockClient = MockClient((request) async {
        await Future<void>.delayed(const Duration(seconds: 1));
        return http.Response('{}', 200);
      });

      final api = ApiClient(
        client: mockClient,
        timeoutDuration: const Duration(milliseconds: 100),
      );

      await expectLater(
        api.post('/prueba', body: {'dato': 'prueba'}),
        throwsA(
          isA<ApiException>().having(
            (error) => error.codigo,
            'codigo',
            'TIEMPO_ESPERA',
          ),
        ),
      );

      api.dispose();
    });
  });
}
