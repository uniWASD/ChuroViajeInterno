import 'package:flutter/foundation.dart';

import '../models/catalogos.dart';
import '../models/coordenada.dart';
import '../models/evento_vial.dart';
import '../models/navegacion.dart';
import '../models/reporte_evento.dart';
import '../services/api/api_client.dart';
import '../services/eventos/servicio_eventos.dart';
import 'cola_controller.dart';
import 'map_controller.dart';
import 'sesion_controller.dart';

enum EstadoReporte {
  /// El conductor elige el tipo y el tiempo.
  eligiendo,

  /// El GPS no dio la ubicación: el conductor toca el mapa donde ocurrió (RN-01.5).
  necesitaPunto,

  /// El punto elegido está a más de 1 km del conductor: no se envía (RN-01.5, P-39).
  fueraDeAlcance,
  enviando,
  enviado,

  /// El backend no lo aceptó: duplicado, límite, fuera de Tarija o vencido. Se muestra su mensaje.
  rechazado,

  /// No hay red y no hay cola: se conserva lo elegido.
  sinRed,

  /// No hay red: el reporte quedó en la cola y se enviará solo (HU-02, RN-01.13).
  guardadoEnCola,

  /// La sesión venció y no se pudo renovar: al volver a iniciar sesión, el reporte se envía solo (RN-01.16).
  esperandoSesion,
}

/// Controlador de reporte (CU-01): lo que el conductor eligió, la ubicación y el envío al backend. Desde #191 la
/// vista es la hoja «Notificar evento» sobre el mapa: se abre en la posición del conductor (la campana) o en el
/// lugar que eligió (a 1 km o menos, RN-01.5), y tocar el mapa mientras está abierta cambia el punto.
class ReporteController extends ChangeNotifier {
  static const String mensajeEnviado = 'Gracias, tu reporte se envió.';
  static const String mensajeSinUbicacion =
      'No pudimos obtener tu ubicación. Toca el mapa donde ocurrió el evento y envía.';
  static const String mensajeFueraDeAlcance = 'Solo puedes notificar eventos a 1 km de donde estás.';
  static const String mensajeEsperandoSesion =
      'Tu sesión venció. Inicia sesión y tu reporte se enviará solo.';

  final ServicioEventos _servicio;
  final SesionController _sesion;
  final MapController _mapa;
  final ColaController? _cola;
  final DateTime Function() _ahora;

  EstadoReporte _estado = EstadoReporte.eligiendo;
  String? _tipo;
  int? _minutos;
  Coordenada? _puntoManual;
  String? _mensaje;
  EventoVial? _ultimoEnviado;
  ReporteEvento? _pendienteTrasSesion;
  bool _abierto = false;
  final List<String> _avisos = [];
  bool _disposed = false;

  ReporteController(
    this._servicio,
    this._sesion,
    this._mapa, {
    this._cola,
    DateTime Function()? reloj,
  }) : _ahora = reloj ?? DateTime.now {
    _sesion.addListener(_alCambiarSesion);
  }

  EstadoReporte get estado => _estado;
  String? get tipo => _tipo;
  Coordenada? get puntoManual => _puntoManual;
  String? get mensaje => _mensaje;
  EventoVial? get ultimoEnviado => _ultimoEnviado;
  bool get enviando => _estado == EstadoReporte.enviando;

  /// La hoja «Notificar evento» está abierta. Si la sesión vence mientras el conductor la llena, la hoja no se
  /// cierra: al enviar, el reporte queda guardado y se envía al volver a iniciar sesión (RN-01.16). Al enviarse (o
  /// quedar guardado) se cierra sola y deja su mensaje en [tomarAviso].
  bool get abierto => _abierto;

  /// RN-01.5 y P-39: hasta dónde puede reportar el conductor, según el catálogo. Sin el dato, la app no lo revisa.
  int? get radioMetros => _sesion.catalogos?.radioReporteMetros;

  /// Comodidad para el conductor (02_mvc.md, regla 2): el punto elegido está más lejos de lo permitido. El backend lo
  /// vuelve a revisar.
  bool get fueraDeAlcance {
    final punto = _puntoManual, posicion = _mapa.posicion, radio = radioMetros;
    if (punto == null || posicion == null || radio == null) return false;
    return distanciaM(punto, posicion) > radio;
  }

  /// Los tipos reportables del catálogo guardado; la congestión no aparece (RN-01.3).
  List<TipoEventoCatalogo> get tipos => _sesion.catalogos?.reportables ?? const [];

  /// Los tiempos del catálogo: ahora mismo, hace 5 (marcado), hace 10 y hace 30 minutos (RN-01.4, D-09).
  List<TiempoReporte> get tiempos => _sesion.catalogos?.tiemposReporte ?? const [];

  int get minutos => _minutos ?? _sesion.catalogos?.tiempoPorDefecto?.minutos ?? 5;

  /// Se puede confirmar con un tipo elegido, sin un envío en curso y con el punto dentro del alcance.
  bool get puedeConfirmar => _tipo != null && !enviando && !fueraDeAlcance;

  /// Empieza un reporte nuevo: «5 min» marcado y nada elegido. Sin [punto], el evento va en la posición del conductor;
  /// con él, en el lugar que eligió en el mapa (#191).
  void empezar({Coordenada? punto}) {
    _abierto = true;
    if (_estado == EstadoReporte.esperandoSesion) {
      _avisar();
      return; // el anterior todavía espera la sesión
    }
    _estado = EstadoReporte.eligiendo;
    _tipo = null;
    _minutos = null;
    _puntoManual = punto;
    _mensaje = null;
    _revisarAlcance();
    _avisar();
  }

  /// Se cerró la hoja «Notificar evento».
  void cerrar() {
    if (!_abierto) return;
    _abierto = false;
    _avisar();
  }

  /// El mensaje que la pantalla principal debe mostrar después de cerrar la hoja, o null si no hay.
  String? tomarAviso() => _avisos.isEmpty ? null : _avisos.removeAt(0);

  void elegirTipo(String codigo) {
    _tipo = codigo;
    _avisar();
  }

  void elegirTiempo(int minutos) {
    _minutos = minutos;
    _avisar();
  }

  /// El conductor tocó el mapa: ese punto reemplaza al del GPS (RN-01.5), si está a 1 km o menos.
  void usarPunto(Coordenada punto) {
    _puntoManual = punto;
    if (_estado == EstadoReporte.fueraDeAlcance || _estado == EstadoReporte.necesitaPunto) {
      _estado = EstadoReporte.eligiendo;
      _mensaje = null;
    }
    _revisarAlcance();
    _avisar();
  }

  void _revisarAlcance() {
    if (fueraDeAlcance) {
      _estado = EstadoReporte.fueraDeAlcance;
      _mensaje = mensajeFueraDeAlcance;
    } else if (_estado == EstadoReporte.fueraDeAlcance) {
      _estado = EstadoReporte.eligiendo;
      _mensaje = null;
    }
  }

  /// Toma la ubicación del GPS, o la que marcó el conductor si el GPS no respondió, y envía el reporte.
  Future<void> confirmar() async {
    final tipo = _tipo;
    if (tipo == null || enviando) return;
    final posicion = _mapa.posicion;
    final ubicacion = _puntoManual ?? posicion;
    if (ubicacion == null) {
      _estado = EstadoReporte.necesitaPunto;
      _mensaje = mensajeSinUbicacion;
      _avisar();
      return;
    }
    _revisarAlcance();
    if (_estado == EstadoReporte.fueraDeAlcance) {
      _avisar();
      return;
    }
    await _enviar(ReporteEvento(
      tipo: tipo,
      ubicacion: ubicacion,
      horaConfirmacion: _ahora(),
      minutosElegidos: minutos,
      // Con un punto elegido va también la posición, para que el backend revise el 1 km (RN-01.5).
      posicionConductor: _puntoManual != null ? posicion : null,
    ));
  }

  /// Envía un reporte ya armado. Lo usan la confirmación y el reintento tras volver a iniciar sesión.
  Future<void> _enviar(ReporteEvento reporte) async {
    _estado = EstadoReporte.enviando;
    _mensaje = null;
    _avisar();
    try {
      _ultimoEnviado = await _servicio.reportar(reporte);
      _pendienteTrasSesion = null;
      _estado = EstadoReporte.enviado;
      _mensaje = mensajeEnviado;
    } on ApiException catch (e) {
      await _alFallarEnvio(reporte, e);
    }
    _cerrarSiTermino();
    _avisar();
  }

  /// Enviado, guardado en la cola o esperando la sesión: la hoja se cierra y su mensaje queda para la pantalla.
  void _cerrarSiTermino() {
    if (!_abierto) return;
    if (_estado == EstadoReporte.enviado ||
        _estado == EstadoReporte.guardadoEnCola ||
        _estado == EstadoReporte.esperandoSesion) {
      _abierto = false;
      _avisos.add(_mensaje ?? mensajeEnviado);
    }
  }

  /// Decide qué hacer cuando el envío falla.
  Future<void> _alFallarEnvio(ReporteEvento reporte, ApiException error) async {
    if (error.codigo == 'SESION_VENCIDA' && _cola != null) {
      if (await _guardarEnCola(reporte)) {
        _estado = EstadoReporte.esperandoSesion;
        _mensaje = mensajeEsperandoSesion;
      }
    } else if (error.codigo == 'SESION_VENCIDA') {
      _pendienteTrasSesion = reporte; // RN-01.16: se conserva y se envía al volver a iniciar sesión
      _estado = EstadoReporte.esperandoSesion;
      _mensaje = mensajeEsperandoSesion;
    } else if (esErrorTemporal(error) && _cola != null) {
      if (await _guardarEnCola(reporte)) {
        _estado = EstadoReporte.guardadoEnCola;
        _mensaje = ColaController.mensajeGuardado;
      }
    } else if (esErrorTemporal(error)) {
      _estado = EstadoReporte.sinRed;
      _mensaje = error.mensaje;
    } else {
      _estado = EstadoReporte.rechazado;
      _mensaje = error.mensaje;
    }
  }

  /// Un servidor que responde 5xx también es indisponibilidad temporal (CP-83).
  static bool esErrorTemporal(ApiException e) =>
      e.codigo == 'ERROR_RED' ||
      e.codigo == 'TIEMPO_ESPERA' ||
      (e.statusCode != null && e.statusCode! >= 500);

  /// Si SQLite falla, no se cierra la pantalla ni se afirma que el reporte quedó guardado.
  Future<bool> _guardarEnCola(ReporteEvento reporte) async {
    try {
      await _cola!.guardar(reporte);
      return true;
    } on Object catch (e) {
      debugPrint('No se pudo guardar el reporte pendiente: $e');
      _estado = EstadoReporte.sinRed;
      _mensaje = 'No se pudo guardar el reporte en el teléfono. '
          'Mantén esta pantalla abierta e inténtalo de nuevo.';
      return false;
    }
  }

  void _alCambiarSesion() {
    if (!_sesion.conSesion || _estado != EstadoReporte.esperandoSesion) return;
    final pendiente = _pendienteTrasSesion;
    if (pendiente != null) {
      _enviar(pendiente);
    } else {
      // El reporte quedó en la cola y la cola lo envía al volver la sesión: se puede empezar otro.
      _estado = EstadoReporte.eligiendo;
      _mensaje = null;
    }
  }

  void _avisar() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _sesion.removeListener(_alCambiarSesion);
    super.dispose();
  }
}
