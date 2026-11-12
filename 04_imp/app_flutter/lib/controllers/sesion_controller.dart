import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../models/catalogos.dart';
import '../models/sesion.dart';
import '../services/api/api_client.dart';
import '../services/catalogos/servicio_catalogos.dart';
import '../services/sesion/almacen_local.dart';
import '../services/sesion/proveedor_google.dart';
import '../services/sesion/servicio_sesion.dart';

enum EstadoSesion { cargando, sinSesion, conSesion }

/// Controlador de sesión (CU-08): inicia sesión con Google, guarda los tokens, renueva el JWT antes de que venza y
/// cierra la sesión. También es el [GestorToken] del cliente HTTP.
class SesionController extends ChangeNotifier implements GestorToken {
  static const String claveSesion = 'sesion';

  /// El JWT se renueva cuando le quedan menos de 2 minutos (RN-08.12).
  static const Duration margenRenovacion = Duration(minutes: 2);

  static const String mensajeSesionVencida =
      'Tu sesión venció. Inicia sesión de nuevo.';

  final ServicioSesion _servicio;
  final ProveedorGoogle _google;
  final AlmacenLocal _almacen;
  final ServicioCatalogos _catalogos;
  final DateTime Function() _ahora;
  final List<Future<void> Function()> _limpiezas = [];

  EstadoSesion _estado = EstadoSesion.cargando;
  Sesion? _sesion;
  Catalogos? _catalogosActuales;
  String? _error;
  bool _ocupado = false;
  Future<bool>? _renovando;
  Future<void>? _cargandoCatalogos;
  bool _disposed = false;

  SesionController({
    required this._servicio,
    required this._google,
    required this._almacen,
    required this._catalogos,
    DateTime Function()? reloj,
  }) : _ahora = reloj ?? DateTime.now;

  EstadoSesion get estado => _estado;
  bool get conSesion => _estado == EstadoSesion.conSesion;

  /// Los catálogos guardados al iniciar sesión (D-06), o null si todavía no llegaron.
  Catalogos? get catalogos => _catalogosActuales;

  /// Mensaje para el conductor en la vista Inicio de sesión (RN-08.3, RN-08.9).
  String? get error => _error;

  /// true mientras se inicia sesión: el botón se deshabilita.
  bool get ocupado => _ocupado;

  @override
  String? get token => _sesion?.jwt;

  /// Algo que hay que borrar al cerrar sesión, además de los tokens (por ejemplo, la cola de reportes).
  void registrarLimpieza(Future<void> Function() limpieza) =>
      _limpiezas.add(limpieza);

  /// Al abrir la app: con un token de renovación vigente se entra directo, aunque no haya red y el JWT haya
  /// vencido (RN-08.10, D-21). El JWT se renueva en segundo plano cuando haya red.
  Future<void> cargar() async {
    final guardada = await _leerSesion();
    _catalogosActuales = await _catalogos.leerGuardados();
    if (guardada != null && guardada.renovacionVigente(_ahora())) {
      _sesion = guardada;
      _cambiar(EstadoSesion.conSesion);
      unawaited(asegurarTokenVigente());
      if (_catalogosActuales == null) unawaited(_actualizarCatalogos());
    } else {
      if (guardada != null) await _almacen.borrar(claveSesion);
      _cambiar(EstadoSesion.sinSesion);
    }
  }

  /// RN-08.1 a RN-08.9: el selector de cuentas de Google, `POST /sesion/google` y los catálogos.
  Future<void> iniciarConGoogle() async {
    if (_ocupado) return;
    _ocupado = true;
    _error = null;
    _avisar();
    try {
      final tokenGoogle = await _google.pedirTokenDeIdentidad();
      final sesion = await _servicio.iniciarConGoogle(tokenGoogle, _ahora());
      await _guardarSesion(sesion);
      await _actualizarCatalogos();
      _ocupado = false;
      _cambiar(EstadoSesion.conSesion);
    } on InicioCancelado {
      _ocupado = false; // RN-08.8: vuelve a la vista de inicio, sin error
      _avisar();
    } on ApiException catch (e) {
      _ocupado = false;
      _error = e.mensaje; // RN-08.3 y RN-08.9: el mensaje del backend o el de red
      _avisar();
    } on Object catch (e) {
      debugPrint('No se pudo iniciar sesión con Google: $e');
      _ocupado = false;
      _error = 'No se pudo iniciar sesión con Google. Inténtalo de nuevo.';
      _avisar();
    }
  }

  @override
  Future<bool> asegurarTokenVigente() async {
    final sesion = _sesion;
    if (sesion == null) return false;
    if (sesion.jwtVigente(_ahora(), margen: margenRenovacion)) return true;
    return _renovar();
  }

  @override
  Future<bool> renovarTrasRechazo() => _renovar();

  /// Una sola renovación a la vez: si varias peticiones la piden juntas, esperan la misma.
  Future<bool> _renovar() => _renovando ??= _renovarUnaVez().whenComplete(() {
    _renovando = null;
  });

  Future<bool> _renovarUnaVez() async {
    final sesion = _sesion;
    if (sesion == null) return false;
    if (!sesion.renovacionVigente(_ahora())) {
      await _perderSesion();
      return false;
    }
    try {
      final nuevo = await _servicio.renovar(sesion.tokenRenovacion);
      await _guardarSesion(sesion.conJwt(nuevo.jwt, nuevo.expiraEn));
      return true;
    } on ApiException catch (e) {
      if (e.statusCode == 401) {
        await _perderSesion(); // RN-08.11: el backend ya no reconoce la sesión
      }
      return false; // sin red: la sesión sigue y se reintenta después (RN-08.10)
    }
  }

  /// «Cerrar sesión»: avisa al backend y borra del teléfono los tokens, la cola y los datos guardados.
  Future<void> cerrarSesion() async {
    final sesion = _sesion;
    if (sesion != null) {
      try {
        await _servicio.cerrar(sesion.tokenRenovacion);
      } on ApiException catch (e) {
        debugPrint('No se pudo avisar el cierre de sesión al backend: $e');
      }
    }
    try {
      await _google.cerrar();
    } on Object catch (e) {
      debugPrint('No se pudo cerrar la cuenta de Google: $e');
    }
    await _borrarTodo();
    _error = null;
    _cambiar(EstadoSesion.sinSesion);
  }

  /// La sesión ya no se puede renovar (RN-08.11): se borran los tokens, pero no la cola ni los catálogos, para que
  /// lo pendiente se envíe al volver a iniciar sesión (RN-01.16). «Cerrar sesión» sí borra todo.
  Future<void> _perderSesion() async {
    _sesion = null;
    await _almacen.borrar(claveSesion);
    _error = mensajeSesionVencida;
    _cambiar(EstadoSesion.sinSesion);
  }

  Future<void> _borrarTodo() async {
    _sesion = null;
    _catalogosActuales = null;
    await _almacen.borrarTodo();
    for (final limpieza in _limpiezas) {
      try {
        await limpieza();
      } on Object catch (e) {
        debugPrint('No se pudo borrar un dato guardado: $e');
      }
    }
  }

  /// Antes de vaciar la cola es obligatorio conocer la vigencia del tipo.
  /// Si los catálogos no pudieron descargarse al iniciar sesión, se reintentan al volver la red.
  Future<void> asegurarCatalogos() async {
    if (_catalogosActuales != null) return;
    await (_cargandoCatalogos ??= _actualizarCatalogos().whenComplete(() {
      _cargandoCatalogos = null;
    }));
  }

  Future<void> _actualizarCatalogos() async {
    try {
      _catalogosActuales = await _catalogos.descargarYGuardar();
      _avisar();
    } on ApiException catch (e) {
      // Se queda con los que ya tenía; se vuelven a pedir en el próximo inicio.
      debugPrint('No se pudieron traer los catálogos: $e');
    }
  }

  Future<Sesion?> _leerSesion() async {
    final texto = await _almacen.leer(claveSesion);
    if (texto == null) return null;
    try {
      return Sesion.fromJson(jsonDecode(texto) as Map<String, dynamic>);
    } on Object {
      return null;
    }
  }

  Future<void> _guardarSesion(Sesion sesion) async {
    _sesion = sesion;
    await _almacen.guardar(claveSesion, jsonEncode(sesion.toJson()));
  }

  void _cambiar(EstadoSesion nuevo) {
    _estado = nuevo;
    _avisar();
  }

  void _avisar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
