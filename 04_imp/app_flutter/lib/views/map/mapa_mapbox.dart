import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:mapbox_maps_flutter/mapbox_maps_flutter.dart';

import '../../config/app_config.dart';
import '../../models/coordenada.dart';
import '../../models/trafico.dart';
import 'dibujo_en_serie.dart';
import 'estilo_mapa.dart';
import 'mapa_base.dart';
import 'marcadores.dart';

/// El [ConstructorMapa] de la app: el mapa base de Mapbox (D-08: Mapbox solo da el mapa, las rutas las calcula
/// el motor).
Widget construirMapaMapbox(BuildContext context, DatosMapa datos) =>
    MapaMapbox(datos: datos);

class MapaMapbox extends StatefulWidget {
  final DatosMapa datos;

  const MapaMapbox({super.key, required this.datos});

  @override
  State<MapaMapbox> createState() => _MapaMapboxState();
}

class _MapaMapboxState extends State<MapaMapbox> {
  MapboxMap? _mapa;
  CircleAnnotationManager? _marcas;
  PointAnnotationManager? _eventos;
  PointAnnotationManager? _surtidores;
  Cancelable? _toquesSurtidores;
  final Marcadores _dibujos = Marcadores();
  PolylineAnnotationManager? _lineas;
  PolylineAnnotationManager? _trafico;
  PolylineAnnotationManager? _congestiones;
  PointAnnotationManager? _pines;

  /// #191: mueve la cámara a seguir al conductor cuando empieza la navegación.
  final ViewportController _vista = ViewportController();
  PolylineAnnotation? _ruta;
  Cancelable? _toquesEventos;
  Cancelable? _toquesCongestiones;

  /// Cada capa se dibuja de a una vez: así dos cambios seguidos no dejan marcadores ni líneas repetidas.
  final DibujoEnSerie _serieSurtidores = DibujoEnSerie();
  final DibujoEnSerie _serieEventos = DibujoEnSerie();
  final DibujoEnSerie _seriePunto = DibujoEnSerie();
  final DibujoEnSerie _serieTrazado = DibujoEnSerie();
  final DibujoEnSerie _serieTrafico = DibujoEnSerie();

  @override
  void initState() {
    super.initState();
    if (!AppConfig.tieneTokenMapbox) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => widget.datos.alFallar(
          'Falta el token de Mapbox (--dart-define=MAPBOX_ACCESS_TOKEN=...)',
        ),
      );
    }
  }

  @override
  void didUpdateWidget(MapaMapbox anterior) {
    super.didUpdateWidget(anterior);
    if (anterior.datos.mostrarUbicacion != widget.datos.mostrarUbicacion) {
      _actualizarUbicacion();
    }
    if (anterior.datos.puntoSeleccionado != widget.datos.puntoSeleccionado ||
        !listEquals(anterior.datos.marcas, widget.datos.marcas)) {
      _marcarPunto();
    }
    if (!listEquals(anterior.datos.trazado, widget.datos.trazado)) {
      _dibujarTrazado();
    }
    if (!listEquals(anterior.datos.trafico, widget.datos.trafico)) {
      _dibujarTrafico();
    }
    if (_siguiendo && !(anterior.datos.seguirUbicacion && anterior.datos.mostrarUbicacion)) {
      _seguirAlConductor();
    }
    if (!listEquals(anterior.datos.eventos, widget.datos.eventos)) {
      _dibujarEventos();
    }
    if (!listEquals(anterior.datos.surtidores, widget.datos.surtidores)) {
      _dibujarSurtidores();
    }
    final foco = widget.datos.foco;
    if (foco != null && foco != anterior.datos.foco) {
      _irAlFoco(foco);
    }
  }

  /// #172: al abrir un evento, la cámara va hasta él y lo deja en la parte del mapa que no tapa la hoja.
  Future<void> _irAlFoco(Coordenada foco) async {
    final mapa = _mapa;
    if (mapa == null || _siguiendo) return;
    final alto = MediaQuery.sizeOf(context).height;
    final camara = await mapa.getCameraState();
    if (!mounted) return;
    await mapa.flyTo(
      CameraOptions(
        center: _punto(foco),
        zoom: camara.zoom < 16 ? 16 : camara.zoom,
        padding: MbxEdgeInsets(top: 0, left: 0, bottom: alto * widget.datos.tapadoAbajo, right: 0),
      ),
      MapAnimationOptions(duration: 600),
    );
  }

  @override
  void dispose() {
    _vista.dispose();
    _toquesEventos?.cancel();
    _toquesCongestiones?.cancel();
    _toquesSurtidores?.cancel();
    super.dispose();
  }

  Future<void> _alCrear(MapboxMap mapa) async {
    _mapa = mapa;
    // Por si el estilo terminó de cargar antes de que el mapa avisara que está creado.
    unawaited(_aplicarEstilo());
    // RNF-19: el logo y la atribución (© Mapbox © OpenStreetMap) quedan visibles.
    await mapa.logo.updateSettings(LogoSettings(enabled: true));
    await mapa.attribution.updateSettings(
      AttributionSettings(enabled: true, clickable: true),
    );
    // D-47: la brújula, que aparece al girar el mapa, va debajo de la barra de búsqueda.
    if (mounted) {
      final arriba = MediaQuery.paddingOf(context).top + 72;
      await mapa.compass.updateSettings(CompassSettings(marginTop: arriba, marginRight: 12));
    }
    mapa.addInteraction(
      TapInteraction.onMap((contexto) {
        final c = contexto.point.coordinates;
        widget.datos.alTocar(Coordenada(c.lat.toDouble(), c.lng.toDouble()));
      }),
    );
    // #191: tocar un lugar conocido del mapa base lo elige con su nombre, como en Google Maps. Mapbox atiende las
    // interacciones sobre lugares antes que la del mapa y esta detiene el toque, así que no llega también como un
    // punto suelto.
    mapa.addInteraction(
      TapInteraction(StandardPOIs(), (lugar, contexto) {
        final c = lugar.coordinate?.coordinates ?? contexto.point.coordinates;
        final punto = Coordenada(c.lat.toDouble(), c.lng.toDouble());
        final nombre = lugar.name;
        final alTocarLugar = widget.datos.alTocarLugar;
        if (alTocarLugar != null && nombre != null && nombre.isNotEmpty) {
          alTocarLugar(punto, nombre);
        } else {
          widget.datos.alTocar(punto);
        }
      }),
    );
    // Primero la ruta y su tráfico, después las congestiones sobre la calle y al final los puntos, para que queden
    // encima.
    _lineas = await mapa.annotations.createPolylineAnnotationManager();
    _trafico = await mapa.annotations.createPolylineAnnotationManager();
    _congestiones = await mapa.annotations.createPolylineAnnotationManager();
    _surtidores = await mapa.annotations.createPointAnnotationManager();
    _eventos = await mapa.annotations.createPointAnnotationManager();
    _marcas = await mapa.annotations.createCircleAnnotationManager();
    _pines = await mapa.annotations.createPointAnnotationManager();
    _toquesSurtidores = _surtidores!.tapEvents(onTap: (anotacion) {
      final id = anotacion.customData?['surtidor'];
      if (id is num) widget.datos.alTocarSurtidor?.call(id.toInt());
    });
    _toquesEventos = _eventos!.tapEvents(onTap: (anotacion) => _tocarEvento(anotacion.customData));
    // Al tocar la línea de una congestión se abre su ficha, igual que con un punto.
    _toquesCongestiones = _congestiones!.tapEvents(onTap: (anotacion) => _tocarEvento(anotacion.customData));
    await _actualizarUbicacion();
    if (_siguiendo) _seguirAlConductor();
    await _dibujarTrazado();
    await _dibujarTrafico();
    await _dibujarEventos();
    await _dibujarSurtidores();
    await _marcarPunto();
  }

  /// D-25 y RN-05.17: cada surtidor con el ícono del surtidor, del color de su estado.
  Future<void> _dibujarSurtidores() => _serieSurtidores.pedir(_hacerSurtidores);

  Future<void> _hacerSurtidores() async {
    if (!mounted) return;
    final capa = _surtidores;
    if (capa == null) return;
    _dibujos.usarEscala(MediaQuery.devicePixelRatioOf(context));
    await capa.deleteAll();
    final surtidores = widget.datos.surtidores;
    if (surtidores.isEmpty) return;
    final imagenes = _ImagenesDelDibujo(this);
    await capa.createMulti([
      for (final s in surtidores)
        await imagenes.en(
          PointAnnotationOptions(
            geometry: _punto(s.coordenada),
            iconAnchor: IconAnchor.BOTTOM,
            iconSize: 0.85,
            customData: {'surtidor': s.id},
          ),
          _dibujos.idSurtidor(s.color),
          () => _dibujos.deSurtidor(s.color),
        ),
    ]);
  }

  void _tocarEvento(Map<String, Object?>? datos) {
    final id = datos?['evento'];
    if (id is num) widget.datos.alTocarEvento?.call(id.toInt());
  }

  /// RN-02.7 y RN-02.14: cada evento con el color de su tipo; los confirmados, con un borde grueso oscuro. Una
  /// congestión con trazado se dibuja a lo largo de su calle (RN-04.25, D-45).
  Future<void> _dibujarEventos() => _serieEventos.pedir(_hacerEventos);

  Future<void> _hacerEventos() async {
    if (!mounted) return;
    final capa = _eventos;
    final lineas = _congestiones;
    if (capa == null || lineas == null) return;
    // La imagen de los marcadores se dibuja con la densidad de la pantalla: el SDK la muestra en píxeles físicos.
    _dibujos.usarEscala(MediaQuery.devicePixelRatioOf(context));
    await capa.deleteAll();
    await lineas.deleteAll();
    final eventos = widget.datos.eventos;
    final enLinea = [for (final e in eventos) if (e.esLinea) e];
    final enPunto = [for (final e in eventos) if (!e.esLinea) e];
    if (enLinea.isNotEmpty) {
      await lineas.createMulti([
        for (final e in enLinea)
          PolylineAnnotationOptions(
            geometry: LineString(coordinates: [for (final c in e.trazado) _punto(c).coordinates]),
            lineColor: colorDeNivel(e.nivel).toARGB32(),
            lineWidth: 6,
            // El trazado va en el sentido del tramo: corrida a su derecha, cada sentido de una calle de doble
            // sentido se ve aparte (D-18), como el tráfico de Google Maps.
            lineOffset: 4,
            lineJoin: LineJoin.ROUND,
            customData: {'evento': e.id},
          ),
      ]);
    }
    if (enPunto.isEmpty) return;
    // #172: cada evento con el ícono de su tipo, en una gota del color del tipo.
    final imagenes = _ImagenesDelDibujo(this);
    await capa.createMulti([
      for (final e in enPunto)
        await imagenes.en(
          PointAnnotationOptions(
            geometry: _punto(e.coordenada),
            iconAnchor: IconAnchor.BOTTOM,
            customData: {'evento': e.id},
          ),
          _dibujos.idEvento(tipo: e.tipo, color: e.color, confirmado: e.confirmado),
          () => _dibujos.de(tipo: e.tipo, color: e.color, confirmado: e.confirmado),
        ),
    ]);
  }

  Future<void> _actualizarUbicacion() async {
    await _mapa?.location.updateSettings(
      LocationComponentSettings(
        enabled: widget.datos.mostrarUbicacion,
        pulsingEnabled: true,
      ),
    );
  }

  /// El origen y las paradas van como círculos; el punto elegido y el destino, como el pin rojo del diseño (#191).
  Future<void> _marcarPunto() => _seriePunto.pedir(_hacerPunto);

  Future<void> _hacerPunto() async {
    if (!mounted) return;
    final marcas = _marcas;
    final pines = _pines;
    if (marcas == null || pines == null) return;
    _dibujos.usarEscala(MediaQuery.devicePixelRatioOf(context));
    await marcas.deleteAll();
    await pines.deleteAll();
    final todas = [
      ...widget.datos.marcas,
      if (widget.datos.puntoSeleccionado != null)
        MarcaMapa(widget.datos.puntoSeleccionado!, TipoMarca.punto),
    ];
    final conPin = {
      for (final m in todas)
        if (m.tipo == TipoMarca.punto || m.tipo == TipoMarca.destino) m.coordenada,
    };
    if (conPin.isNotEmpty) {
      final color = Colors.red.shade700;
      final imagenes = _ImagenesDelDibujo(this);
      await pines.createMulti([
        for (final c in conPin)
          await imagenes.en(
            PointAnnotationOptions(geometry: _punto(c), iconAnchor: IconAnchor.BOTTOM, iconSize: 0.9),
            _dibujos.idPin(color),
            () => _dibujos.dePin(color),
          ),
      ]);
    }
    final circulos = [
      for (final m in todas)
        if (m.tipo == TipoMarca.origen || m.tipo == TipoMarca.parada) m,
    ];
    if (circulos.isEmpty) return;
    await marcas.createMulti([
      for (final m in circulos)
        CircleAnnotationOptions(
          geometry: _punto(m.coordenada),
          circleRadius: m.tipo == TipoMarca.parada ? 6 : 8,
          circleColor: _color(m.tipo).toARGB32(),
          circleStrokeWidth: 2,
          circleStrokeColor: Colors.white.toARGB32(),
        ),
    ]);
  }

  static Color _color(TipoMarca tipo) => switch (tipo) {
    TipoMarca.origen => Colors.green.shade700,
    TipoMarca.parada => Colors.orange.shade700,
    TipoMarca.destino || TipoMarca.punto => Colors.red,
  };

  Future<void> _dibujarTrazado() => _serieTrazado.pedir(_hacerTrazado);

  Future<void> _hacerTrazado() async {
    if (!mounted) return;
    final lineas = _lineas;
    final mapa = _mapa;
    if (lineas == null || mapa == null) return;
    final trazado = widget.datos.trazado;
    if (trazado.length < 2) {
      _ruta = null;
      await lineas.deleteAll();
      return;
    }
    final puntos = [for (final c in trazado) _punto(c)];
    final linea = LineString(coordinates: [for (final p in puntos) p.coordinates]);
    final ruta = _ruta;
    if (ruta != null && _siguiendo) {
      // #172: en la Navegación activa la línea se acorta a cada posición; se mueve la misma, sin parpadear.
      ruta.geometry = linea;
      await lineas.update(ruta);
      return;
    }
    await lineas.deleteAll();
    _ruta = await lineas.create(
      PolylineAnnotationOptions(
        geometry: linea,
        lineColor: colorRuta.toARGB32(),
        lineBorderColor: colorBordeRuta.toARGB32(),
        lineBorderWidth: 1.5,
        lineWidth: 7,
        lineJoin: LineJoin.ROUND,
      ),
    );
    // En la Navegación activa la cámara sigue al conductor: no se encuadra la ruta.
    if (_siguiendo) return;
    final camara = await mapa.cameraForCoordinatesPadding(
      puntos,
      CameraOptions(),
      MbxEdgeInsets(top: 80, left: 40, bottom: 280, right: 40),
      16,
      null,
    );
    await mapa.flyTo(camara, MapAnimationOptions(duration: 600));
  }

  /// #191: las partes de la ruta que pasan por una congestión, de amarillo o de rojo encima de la línea azul.
  Future<void> _dibujarTrafico() => _serieTrafico.pedir(_hacerTrafico);

  Future<void> _hacerTrafico() async {
    if (!mounted) return;
    final capa = _trafico;
    if (capa == null) return;
    await capa.deleteAll();
    final lineas = [
      for (final l in widget.datos.trafico)
        if (l.puntos.length >= 2) l,
    ];
    if (lineas.isEmpty) return;
    await capa.createMulti([
      for (final l in lineas)
        PolylineAnnotationOptions(
          geometry: LineString(coordinates: [for (final c in l.puntos) _punto(c).coordinates]),
          lineColor: (l.nivel == nivelMuyCongestionado ? colorMuyCongestionado : colorCongestionado).toARGB32(),
          lineBorderColor: const Color(0x33000000).toARGB32(),
          lineBorderWidth: 1,
          lineWidth: 7,
          lineJoin: LineJoin.ROUND,
        ),
    ]);
  }

  /// #191 (punto 7): al empezar la navegación la cámara va hasta el conductor y lo sigue, inclinada como en Google
  /// Maps. Si el conductor mueve el mapa, la cámara deja de seguirlo hasta la siguiente navegación.
  void _seguirAlConductor() {
    _vista.moveTo(
      const FollowPuckViewportState(zoom: 16.5, pitch: 40, bearing: FollowPuckViewportStateBearingCourse()),
      transition: const DefaultViewportTransition(maxDuration: Duration(milliseconds: 1200)),
    );
  }

  /// El azul de la ruta y su borde, como en Google Maps.
  static const Color colorRuta = Color(0xFF1A73E8);
  static const Color colorBordeRuta = Color(0xFF1557B0);

  /// #172: colores, fuente y capas parecidos a Google Maps. Si el SDK no acepta alguna opción, el mapa sigue con
  /// el estilo Standard sin cambios: no es motivo para dejarlo sin cargar.
  Future<void> _aplicarEstilo() async {
    try {
      await _mapa?.setStyleImportConfigProperties(importacionBasemap, configuracionEstiloGoogle);
    } catch (e) {
      debugPrint('No se pudo aplicar el estilo parecido a Google Maps: $e');
    }
  }

  bool get _siguiendo => widget.datos.seguirUbicacion && widget.datos.mostrarUbicacion;

  /// Registra un dibujo en el estilo del mapa, una sola vez. Devuelve false si no se pudo: entonces el marcador lleva
  /// su propia imagen, como antes, y el mapa se ve igual.
  Future<bool> _registrarImagen(String id, Uint8List png) async {
    final mapa = _mapa;
    if (mapa == null) return false;
    try {
      if (await mapa.hasStyleImage(id)) return true;
      // La imagen está dibujada con la densidad de la pantalla: con esa escala se ve del mismo tamaño que antes.
      await mapa.addImage(id, _dibujos.escala, StyleImage.bytes(png));
      return true;
    } on Object catch (e) {
      debugPrint('No se pudo registrar la imagen $id en el mapa: $e');
      return false;
    }
  }

  /// Si el estilo se vuelve a cargar, sus imágenes se pierden: los marcadores se dibujan de nuevo y las registran.
  void _alCargarEstilo() {
    _aplicarEstilo();
    if (_surtidores != null) _dibujarSurtidores();
    if (_eventos != null) _dibujarEventos();
    if (_pines != null) _marcarPunto();
  }

  static Point _punto(Coordenada c) =>
      Point(coordinates: Position(c.longitud, c.latitud));

  @override
  Widget build(BuildContext context) {
    if (!AppConfig.tieneTokenMapbox) {
      return const ColoredBox(color: Color(0xFFE0E0E0));
    }
    return MapWidget(
      styleUri: MapboxStyles.STANDARD,
      // Opcional, para probar en el teléfono: con --dart-define=MAPA_TEXTURA=true el mapa de Android va como textura
      // (TLHC) y las hojas que se desplazan encima suelen ir más fluidas. Sin la opción, queda como siempre (HC).
      // El SDK marca el modo como experimental; sin la opción se pasa el mismo valor que usa por defecto (HC).
      androidHostingMode: AppConfig.mapaEnTextura
          // ignore: experimental_member_use
          ? AndroidPlatformViewHostingMode.TLHC_HC
          // ignore: experimental_member_use
          : AndroidPlatformViewHostingMode.HC,
      textureView: AppConfig.mapaEnTextura ? true : null,
      // Abre donde está el conductor (o en el centro pedido) y, al navegar, [_seguirAlConductor] lo sigue.
      viewport: CameraViewportState(
        center: _punto(widget.datos.centro),
        zoom: widget.datos.seguirUbicacion ? 16.5 : 13.5,
        pitch: widget.datos.seguirUbicacion ? 40 : 0,
      ),
      viewportController: _vista,
      onMapCreated: _alCrear,
      onStyleLoadedListener: (_) => _alCargarEstilo(),
      onMapLoadErrorListener: (error) {
        // Solo el estilo deja el mapa sin cargar; un mosaico que falla se vuelve a pedir solo.
        if (error.type == MapLoadErrorType.STYLE) {
          widget.datos.alFallar(error.message);
        }
      },
    );
  }
}

/// Las imágenes de un dibujo de marcadores: cada dibujo distinto se registra en el estilo una sola vez y los
/// marcadores solo lo nombran. Si registrarlo falla, el marcador lleva su imagen como antes.
class _ImagenesDelDibujo {
  final _MapaMapboxState _estado;
  final Map<String, bool> _registradas = {};

  _ImagenesDelDibujo(this._estado);

  Future<PointAnnotationOptions> en(
    PointAnnotationOptions opciones,
    String id,
    Future<Uint8List> Function() dibujo,
  ) async {
    final png = await dibujo();
    final registrada = _registradas[id] ??= await _estado._registrarImagen(id, png);
    if (registrada) {
      opciones.iconImage = id;
    } else {
      opciones.image = png;
    }
    return opciones;
  }
}
