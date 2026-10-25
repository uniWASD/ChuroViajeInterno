import 'dart:async';
import 'dart:convert';
import 'dart:io';

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
    if (_token != null && _token!.isNotEmpty) 'Authorization': _token!,
  };

  Uri _buildUri(String path) {
    final baseUrl = AppConfig.apiBaseUrl;
    final normalizedBase = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;
    final normalizedPath = path.startsWith('/') ? path : '/$path';

    return Uri.parse('$normalizedBase$normalizedPath');
  }

  Future<dynamic> get(String path) {
    return _send(() => _client.get(_buildUri(path), headers: _headers));
  }

  Future<dynamic> post(String path, {Object? body}) {
    return _send(
      () => _client.post(
        _buildUri(path),
        headers: _headers,
        body: body == null ? null : jsonEncode(body),
      ),
    );
  }

  Future<dynamic> _send(Future<http.Response> Function() peticion) async {
    try {
      final response = await peticion().timeout(timeoutDuration);
      return _handleResponse(response);
    } on ApiException {
      rethrow;
    } on TimeoutException {
      throw const ApiException(
        codigo: 'TIEMPO_ESPERA',
        mensaje:
            'El servidor tardó demasiado en responder. Inténtalo de nuevo.',
      );
    } on SocketException {
      throw const ApiException(
        codigo: 'ERROR_RED',
        mensaje: 'No se pudo conectar con el servidor. Verifica tu conexión.',
      );
    } on http.ClientException {
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
      } on FormatException {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          throw ApiException(
            codigo: 'RESPUESTA_INVALIDA',
            mensaje: 'El servidor devolvió una respuesta inválida.',
            statusCode: response.statusCode,
          );
        }
      }
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return data;
    }

    if (data is Map<String, dynamic>) {
      throw ApiException(
        codigo: data['codigo']?.toString() ?? 'ERROR_SERVIDOR',
        mensaje:
            data['mensaje']?.toString() ??
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
