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

/// Quien lleva la sesión (el controlador de sesión). El cliente HTTP le pide el JWT antes de cada petición y le
/// avisa cuando el backend responde 401, para renovar la sesión sin que el conductor intervenga (RN-08.11).
abstract class GestorToken {
  /// El JWT vigente, o null si no hay sesión.
  String? get token;

  /// Renueva el JWT si está por vencer (RN-08.12). Devuelve false si no hay sesión.
  Future<bool> asegurarTokenVigente();

  /// El backend rechazó el JWT: lo renueva. Devuelve false si no pudo y hay que volver a iniciar sesión.
  Future<bool> renovarTrasRechazo();
}

class ApiClient {
  final http.Client _client;
  final Duration timeoutDuration;
  String? _token;

  /// Si está puesto, el JWT sale de aquí y un 401 se reintenta una vez con la sesión renovada. Las rutas
  /// `/sesion/*` no lo usan: son las que renuevan.
  GestorToken? gestor;

  ApiClient({
    http.Client? client,
    this.timeoutDuration = const Duration(seconds: 10),
  }) : _client = client ?? http.Client();

  void setToken(String? token) {
    _token = token;
  }

  Map<String, String> _headers(bool conSesion) {
    final token = conSesion ? (gestor?.token ?? _token) : null;
    return {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
    };
  }

  static const ApiException _sesionVencida = ApiException(
    codigo: 'SESION_VENCIDA',
    mensaje: 'Tu sesión venció. Inicia sesión de nuevo.',
    statusCode: 401,
  );

  static bool _esDeSesion(String path) =>
      path.startsWith('/sesion/') || path.startsWith('sesion/');

  Uri _buildUri(String path) {
    final baseUrl = AppConfig.apiBaseUrl;
    final normalizedBase = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;
    final normalizedPath = path.startsWith('/') ? path : '/$path';

    return Uri.parse('$normalizedBase$normalizedPath');
  }

  Future<dynamic> get(String path, {Map<String, String>? query}) {
    final conSesion = !_esDeSesion(path);
    return _send(
      conSesion,
      () => _client.get(
        _buildUri(path).replace(queryParameters: query),
        headers: _headers(conSesion),
      ),
    );
  }

  Future<dynamic> post(String path, {Object? body}) {
    final conSesion = !_esDeSesion(path);
    return _send(
      conSesion,
      () => _client.post(
        _buildUri(path),
        headers: _headers(conSesion),
        body: body == null ? null : jsonEncode(body),
      ),
    );
  }

  Future<dynamic> _send(
    bool conSesion,
    Future<http.Response> Function() peticion,
  ) async {
    try {
      final gestor = conSesion ? this.gestor : null;
      if (gestor != null && !await gestor.asegurarTokenVigente()) {
        if (gestor.token == null) {
          throw _sesionVencida; // renovación realmente vencida o sesión inexistente
        }
        // Hay sesión local pero no se pudo renovar el JWT (p. ej. backend sin red).
        // No lo confundimos con una sesión cerrada ni enviamos con un JWT ya caduco.
        throw const ApiException(
          codigo: 'ERROR_RED',
          mensaje: 'No se pudo renovar la sesión. Comprueba tu conexión e inténtalo de nuevo.',
        );
      }
      var response = await peticion().timeout(timeoutDuration);
      if (response.statusCode == 401 && gestor != null) {
        // RN-08.11: el JWT venció o lo rechazaron. Se renueva y se reintenta una sola vez.
        if (!await gestor.renovarTrasRechazo()) {
          throw _sesionVencida;
        }
        response = await peticion().timeout(timeoutDuration);
      }
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
    } on TlsException {
      throw const ApiException(
        codigo: 'ERROR_RED',
        mensaje: 'No se pudo establecer una conexión segura con el servidor.',
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
