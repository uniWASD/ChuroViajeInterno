import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../config/app_config.dart';

class ApiException implements Exception {
  final String codigo;
  final String mensaje;
  final int? statusCode;

  const ApiException({
    required this.codigo,
    required this.mensaje,
    this.statusCode,
  });

  @override
  String toString() => mensaje;
}

class ApiClient {
  final http.Client _client;
  final Duration timeoutDuration;
  String? _token;

  ApiClient({
    http.Client? client,
    this.timeoutDuration = const Duration(seconds: 10),
  }) : _client = client ?? http.Client();

  void setToken(String? token) {
    _token = token;
  }

  Map<String, String> get _headers => {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        if (_token != null && _token!.isNotEmpty)
          'Authorization': 'Bearer $_token',
      };

  Uri _buildUri(String path) {
    final baseUrl = AppConfig.apiBaseUrl;
    final normalizedBase = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;
    final normalizedPath = path.startsWith('/') ? path : '/$path';

    return Uri.parse('$normalizedBase$normalizedPath');
  }

  Future<dynamic> get(String path) async {
  try {
    final response = await _client
        .get(
          _buildUri(path),
          headers: _headers,
        )
        .timeout(
          timeoutDuration,
          onTimeout: () {
            throw const ApiException(
              codigo: 'TIEMPO_ESPERA',
              mensaje:
                  'El servidor tardó demasiado en responder. Inténtalo de nuevo.',
            );
          },
        );

    return _handleResponse(response);
  } on ApiException {
    rethrow;
  } catch (_) {
    throw const ApiException(
      codigo: 'ERROR_RED',
      mensaje: 'No se pudo conectar con el servidor. Verifica tu conexión.',
    );
  }
}

  Future<dynamic> post(
  String path, {
  Object? body,
}) async {
  try {
    final response = await _client
        .post(
          _buildUri(path),
          headers: _headers,
          body: body == null ? null : jsonEncode(body),
        )
        .timeout(
          timeoutDuration,
          onTimeout: () {
            throw const ApiException(
              codigo: 'TIEMPO_ESPERA',
              mensaje:
                  'El servidor tardó demasiado en responder. Inténtalo de nuevo.',
            );
          },
        );

    return _handleResponse(response);
  } on ApiException {
    rethrow;
  } catch (_) {
    throw const ApiException(
      codigo: 'ERROR_RED',
      mensaje: 'No se pudo conectar con el servidor. Verifica tu conexión.',
    );
  }
}

  dynamic _handleResponse(http.Response response) {
    dynamic data;

    if (response.body.isNotEmpty) {
      try {
        data = jsonDecode(utf8.decode(response.bodyBytes));
      } catch (_) {
        data = null;
      }
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return data;
    }

    if (data is Map<String, dynamic>) {
      throw ApiException(
        codigo: data['codigo']?.toString() ?? 'ERROR_SERVIDOR',
        mensaje: data['mensaje']?.toString() ??
            'Ocurrió un error al comunicarse con el servidor.',
        statusCode: response.statusCode,
      );
    }

    throw ApiException(
      codigo: 'ERROR_SERVIDOR',
      mensaje: 'Ocurrió un error al comunicarse con el servidor.',
      statusCode: response.statusCode,
    );
  }

  void dispose() {
    _client.close();
  }
}
