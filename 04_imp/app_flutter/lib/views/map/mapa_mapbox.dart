import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:mapbox_maps_flutter/mapbox_maps_flutter.dart';

import '../../config/app_config.dart';
import '../../models/coordenada.dart';
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
  final Marcadores _dibujos = Marcadores();
  PolylineAnnotationManager? _lineas;
  PolylineAnnotationManager? _congestiones;
  PolylineAnnotation? _ruta;
  Cancelable? _toquesEventos;
  Cancelable? _toquesCongestiones;

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
    if (!listEquals(anterior.datos.eventos, widget.datos.eventos)) {
      _dibujarEventos();
    }
  }

  @override
  void dispose() {
    _toquesEventos?.cancel();
    _toquesCongestiones?.cancel();
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
    mapa.addInteraction(
      TapInteraction.onMap((contexto) {
        final c = contexto.point.coordinates;
        widget.datos.alTocar(Coordenada(c.lat.toDouble(), c.lng.toDouble()));
      }),
    );
    // Primero la ruta, después las congestiones sobre la calle y al final los puntos, para que queden encima.
    _lineas = await mapa.annotations.createPolylineAnnotationManager();
    _congestiones = await mapa.annotations.createPolylineAnnotationManager();
    _eventos = await mapa.annotations.createPointAnnotationManager();
    _marcas = await mapa.annotations.createCircleAnnotationManager();
    _toquesEventos = _eventos!.tapEvents(onTap: (anotacion) => _tocarEvento(anotacion.customData));
    // Al tocar la línea de una congestión se abre su ficha, igual que con un punto.
    _toquesCongestiones = _congestiones!.tapEvents(onTap: (anotacion) => _tocarEvento(anotacion.customData));
    await _actualizarUbicacion();
    await _dibujarTrazado();
    await _dibujarEventos();
    await _marcarPunto();
  }

  void _tocarEvento(Map<String, Object?>? datos) {
    final id = datos?['evento'];
    if (id is num) widget.datos.alTocarEvento?.call(id.toInt());
  }

  /// RN-02.7 y RN-02.14: cada evento con el color de su tipo; los confirmados, con un borde grueso oscuro. Una
  /// congestión con trazado se dibuja a lo largo de su calle (RN-04.25, D-45).
  Future<void> _dibujarEventos() async {
    final capa = _eventos;
    final lineas = _congestiones;
    if (capa == null || lineas == null) return;
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
    // #172: cada evento con el ícono de su tipo, en una gota del color del tipo. La imagen se dibuja con la
    // densidad de la pantalla: el SDK la muestra en píxeles físicos.
    _dibujos.usarEscala(MediaQuery.devicePixelRatioOf(context));
    await capa.createMulti([
      for (final e in enPunto)
        PointAnnotationOptions(
          geometry: _punto(e.coordenada),
          image: await _dibujos.de(tipo: e.tipo, color: e.color, confirmado: e.confirmado),
          iconAnchor: IconAnchor.BOTTOM,
          customData: {'evento': e.id},
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

  Future<void> _marcarPunto() async {
    final marcas = _marcas;
    if (marcas == null) return;
    await marcas.deleteAll();
    final todas = [
      ...widget.datos.marcas,
      if (widget.datos.puntoSeleccionado != null)
        MarcaMapa(widget.datos.puntoSeleccionado!, TipoMarca.punto),
    ];
    if (todas.isEmpty) return;
    await marcas.createMulti([
      for (final m in todas)
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

  Future<void> _dibujarTrazado() async {
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

  static Point _punto(Coordenada c) =>
      Point(coordinates: Position(c.longitud, c.latitud));

  @override
  Widget build(BuildContext context) {
    if (!AppConfig.tieneTokenMapbox) {
      return const ColoredBox(color: Color(0xFFE0E0E0));
    }
    return MapWidget(
      styleUri: MapboxStyles.STANDARD,
      viewport: _siguiendo
          ? const FollowPuckViewportState(zoom: 16.5, pitch: 40)
          : CameraViewportState(
              center: _punto(widget.datos.centro),
              zoom: 13.5,
            ),
      onMapCreated: _alCrear,
      onStyleLoadedListener: (_) => _aplicarEstilo(),
      onMapLoadErrorListener: (error) {
        // Solo el estilo deja el mapa sin cargar; un mosaico que falla se vuelve a pedir solo.
        if (error.type == MapLoadErrorType.STYLE) {
          widget.datos.alFallar(error.message);
        }
      },
    );
  }
}
