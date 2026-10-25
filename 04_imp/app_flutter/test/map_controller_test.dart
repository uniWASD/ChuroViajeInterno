import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/services/api/api_client.dart';

void main() {
  group('MapController', () {
    test('Captura el error cuando el servidor no está disponible', () async {
      final mockClient = MockClient((request) async {
        throw http.ClientException('Servidor fuera de servicio');
      });

      final apiClient = ApiClient(client: mockClient);
      final controller = MapController(apiClient);

      await controller.checkConnection();

      expect(controller.isLoading, isFalse);
      expect(controller.isConnected, isFalse);
      expect(
        controller.errorMessage,
        'No se pudo conectar con el servidor. Verifica tu conexión.',
      );

      controller.dispose();
      apiClient.dispose();
    });

    test(
      'Actualiza el estado cuando recibe una respuesta del servidor',
      () async {
        final mockClient = MockClient((request) async {
          return http.Response('{}', 200);
        });

        final apiClient = ApiClient(client: mockClient);
        final controller = MapController(apiClient);

        await controller.checkConnection();

        expect(controller.isLoading, isFalse);
        expect(controller.isConnected, isTrue);
        expect(controller.errorMessage, isNull);

        controller.dispose();
        apiClient.dispose();
      },
    );

    test('Informa el error del servidor aunque haya respondido', () async {
      final mockClient = MockClient((request) async {
        expect(request.url.path, '/catalogos');
        return http.Response(
          jsonEncode({
            'codigo': 'NO_AUTORIZADO',
            'mensaje': 'Debes iniciar sesión.',
          }),
          401,
          headers: {'content-type': 'application/json'},
        );
      });

      final apiClient = ApiClient(client: mockClient);
      final controller = MapController(apiClient);

      await controller.checkConnection();

      expect(controller.isConnected, isTrue);
      expect(controller.errorMessage, 'Debes iniciar sesión.');

      controller.dispose();
      apiClient.dispose();
    });
  });
}
