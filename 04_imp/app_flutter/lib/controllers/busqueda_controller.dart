import 'dart:async';

import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../models/lugar.dart';
import '../services/api/api_client.dart';
import '../services/busqueda/servicio_busqueda.dart';

/// La barra de búsqueda del mapa (D-47): busca mientras el conductor escribe, con una pequeña espera para no pedir
/// una búsqueda por cada letra, y descarta las respuestas que llegan tarde.
class BusquedaController extends ChangeNotifier {
  static const String sinResultados = 'No se encontró nada con ese nombre.';
  static const String sinConexion = 'No se pudo buscar. Revisa tu conexión.';

  /// Cuánto se espera después de la última letra para buscar.
  static const Duration esperaPorDefecto = Duration(milliseconds: 350);

  final ServicioBusqueda _servicio;

  /// La posición del conductor, para ordenar por cercanía. Puede ser null.
  final Coordenada? Function() posicion;
  final Duration espera;

  String _texto = '';
  List<LugarEncontrado> _resultados = const [];
  bool _buscando = false;
  String? _mensaje;
  bool _abierta = false;
  Timer? _temporizador;
  int _pedido = 0;
  bool _disposed = false;

  BusquedaController(
    this._servicio, {
    required this.posicion,
    this.espera = esperaPorDefecto,
  });

  String get texto => _texto;
  List<LugarEncontrado> get resultados => _resultados;
  bool get buscando => _buscando;

  /// Sin resultados o sin conexión; null si no hay nada que avisar.
  String? get mensaje => _mensaje;

  /// true mientras el conductor está usando la barra: el gesto de atrás la cierra primero.
  bool get abierta => _abierta;

  /// Si el texto alcanza para buscar: al menos 2 letras o números, como pide el backend.
  static bool alcanza(String texto) =>
      RegExp(r'[\p{L}\p{N}]', unicode: true).allMatches(texto).length >= 2;

  void abrir() {
    if (_abierta) return;
    _abierta = true;
    notifyListeners();
  }

  /// Cierra la lista; el texto se queda, por si vuelve a tocar la barra.
  void cerrar() {
    if (!_abierta) return;
    _abierta = false;
    notifyListeners();
  }

  void escribir(String texto) {
    _texto = texto;
    _abierta = true;
    _temporizador?.cancel();
    _pedido++;
    if (!alcanza(texto)) {
      _resultados = const [];
      _buscando = false;
      _mensaje = null;
      notifyListeners();
      return;
    }
    _buscando = true;
    _mensaje = null;
    notifyListeners();
    _temporizador = Timer(espera, _buscar);
  }

  /// Borra el texto y los resultados.
  void limpiar() {
    _temporizador?.cancel();
    _pedido++;
    _texto = '';
    _resultados = const [];
    _buscando = false;
    _mensaje = null;
    notifyListeners();
  }

  Future<void> _buscar() async {
    final pedido = _pedido;
    final texto = _texto;
    List<LugarEncontrado> resultados = const [];
    String? mensaje;
    try {
      resultados = await _servicio.buscar(texto, cerca: posicion());
      if (resultados.isEmpty) mensaje = sinResultados;
    } on ApiException catch (e) {
      mensaje = e.statusCode == 400 ? e.mensaje : sinConexion;
    } on Object {
      mensaje = sinConexion;
    }
    // Llegó tarde: el conductor ya escribió otra cosa.
    if (_disposed || pedido != _pedido) return;
    _resultados = resultados;
    _mensaje = mensaje;
    _buscando = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _temporizador?.cancel();
    super.dispose();
  }
}
