import 'package:flutter/foundation.dart';

import '../models/coordenada.dart';
import '../models/ruta.dart';
import '../services/api/api_client.dart';
import '../services/rutas/servicio_rutas.dart';

/// La ruta hacia el lugar elegido en el mapa (CU-03, #191). Al elegir un lugar se calcula la ruta desde la posición
/// del conductor, para que la hoja del lugar muestre su distancia y su tiempo, como en Google Maps. «Cómo llegar»
/// la muestra en el mapa con «Iniciar» y «Otra parada». El origen es siempre la posición del conductor.
class RutaController extends ChangeNotifier {
  /// RN-03.2: hasta 10 paradas intermedias.
  static const int maximoParadas = 10;

  static const String mensajeMotorNoDisponible =
      'El cálculo de rutas no está disponible en este momento. Inténtalo de nuevo.';
  static const String mensajeSinUbicacion =
      'No tenemos tu ubicación. Activa el GPS para calcular la ruta.';

  final ServicioRutas _servicio;

  Coordenada? _destino;
  final List<Coordenada> _paradas = [];
  bool _mostrando = false;
  bool _eligiendoParada = false;

  bool _calculando = false;
  RutaCalculada? _ruta;
  String? _aviso;
  String? _error;
  bool _sinUbicacion = false;

  /// Cada cálculo lleva un número: si el conductor eligió otro lugar mientras tanto, la respuesta vieja se descarta.
  int _pedido = 0;
  bool _disposed = false;

  RutaController(this._servicio);

  bool get esDeEjemplo => _servicio.esDeEjemplo;

  Coordenada? get destino => _destino;
  List<Coordenada> get paradas => List.unmodifiable(_paradas);

  /// true mientras la ruta se ve en el mapa con «Iniciar» y «Otra parada» («Cómo llegar»).
  bool get mostrando => _mostrando;

  /// true después de tocar «Otra parada»: el siguiente toque en el mapa es la parada.
  bool get eligiendoParada => _eligiendoParada;
  bool get puedeAgregarParada => _paradas.length < maximoParadas;

  bool get calculando => _calculando;

  /// La ruta encontrada, con su tiempo y su distancia (RN-03.13).
  RutaCalculada? get ruta => _ruta?.encontrada == true ? _ruta : null;

  /// «No se encontró una ruta válida», «Origen o destino inválido» o que falta la ubicación: es una respuesta, no un
  /// error.
  String? get aviso => _aviso;

  /// El backend no respondió o falló (por ejemplo, 503). Se puede reintentar (RN-03.15).
  String? get error => _error;

  /// true si la ruta no se pudo pedir porque no había posición del conductor.
  bool get sinUbicacion => _sinUbicacion;

  /// El conductor eligió un lugar: se calcula la ruta desde su posición, sin paradas. Si ya se calculó (o se está
  /// calculando) hacia ese mismo lugar, no se vuelve a pedir.
  Future<void> prever(Coordenada destino, Coordenada? posicion) async {
    if (destino == _destino && (_calculando || ruta != null)) return;
    if (destino != _destino) {
      _paradas.clear();
      _mostrando = false;
      _eligiendoParada = false;
    }
    _destino = destino;
    await _calcular(posicion);
  }

  /// «Cómo llegar»: la ruta se dibuja en el mapa con «Iniciar» y «Otra parada».
  void mostrar() {
    if (_destino == null || _mostrando) return;
    _mostrando = true;
    _avisar();
  }

  /// Se cierra «Cómo llegar» y se vuelve a la hoja del lugar. Las paradas se quitan, así que la ruta se recalcula.
  Future<void> ocultar(Coordenada? posicion) async {
    if (!_mostrando) return;
    _mostrando = false;
    _eligiendoParada = false;
    if (_paradas.isEmpty) {
      _avisar();
      return;
    }
    _paradas.clear();
    await _calcular(posicion);
  }

  /// «Otra parada»: el siguiente toque en el mapa la agrega.
  void elegirParada() {
    if (!puedeAgregarParada) return;
    _eligiendoParada = true;
    _avisar();
  }

  void cancelarParada() {
    if (!_eligiendoParada) return;
    _eligiendoParada = false;
    _avisar();
  }

  /// El conductor tocó el mapa después de «Otra parada»: la parada se agrega y la ruta se recalcula.
  Future<void> agregarParada(Coordenada parada, Coordenada? posicion) async {
    if (!puedeAgregarParada) return;
    _paradas.add(parada);
    _eligiendoParada = false;
    await _calcular(posicion);
  }

  Future<void> quitarParada(int indice, Coordenada? posicion) async {
    _paradas.removeAt(indice);
    await _calcular(posicion);
  }

  /// Vuelve a pedir la ruta, por ejemplo después de un error o cuando llega la posición.
  Future<void> reintentar(Coordenada? posicion) => _calcular(posicion);

  /// Se quitó el lugar o empezó la navegación: no queda nada elegido.
  void limpiar() {
    if (_destino == null && !_mostrando) return;
    _pedido++;
    _destino = null;
    _paradas.clear();
    _mostrando = false;
    _eligiendoParada = false;
    _calculando = false;
    _olvidarResultado();
    _avisar();
  }

  Future<void> _calcular(Coordenada? posicion) async {
    final hasta = _destino;
    if (hasta == null) return;
    final numero = ++_pedido;
    _olvidarResultado();
    if (posicion == null) {
      _calculando = false;
      _sinUbicacion = true;
      _aviso = mensajeSinUbicacion;
      _avisar();
      return;
    }
    _calculando = true;
    _avisar();
    try {
      final ruta = await _servicio.calcular(posicion, hasta, List.of(_paradas));
      if (numero != _pedido) return;
      if (ruta.encontrada) {
        _ruta = ruta;
      } else {
        _aviso = ruta.mensaje;
      }
    } on ApiException catch (e) {
      if (numero != _pedido) return;
      _error = e.statusCode == 503 ? mensajeMotorNoDisponible : e.mensaje;
    } finally {
      if (numero == _pedido) {
        _calculando = false;
        _avisar();
      }
    }
  }

  void _olvidarResultado() {
    _ruta = null;
    _aviso = null;
    _error = null;
    _sinUbicacion = false;
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
