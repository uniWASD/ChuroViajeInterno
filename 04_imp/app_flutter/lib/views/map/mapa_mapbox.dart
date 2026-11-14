import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:mapbox_maps_flutter/mapbox_maps_flutter.dart';

import '../../config/app_config.dart';
import '../../models/coordenada.dart';
import 'mapa_base.dart';

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
  CircleAnnotationManager? _eventos;
  PolylineAnnotationManager? _lineas;
  Cancelable? _toquesEventos;

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
    super.dispose();
  }

  Future<void> _alCrear(MapboxMap mapa) async {
    _mapa = mapa;
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
    // Primero la línea, para que las marcas queden encima.
    _lineas = await mapa.annotations.createPolylineAnnotationManager();
    _eventos = await mapa.annotations.createCircleAnnotationManager();
    _marcas = await mapa.annotations.createCircleAnnotationManager();
    _toquesEventos = _eventos!.tapEvents(onTap: (anotacion) {
      final id = anotacion.customData?['evento'];
      if (id is num) widget.datos.alTocarEvento?.call(id.toInt());
    });
    await _actualizarUbicacion();
    await _dibujarTrazado();
    await _dibujarEventos();
    await _marcarPunto();
  }

  /// RN-02.7 y RN-02.14: cada evento con el color de su tipo; los confirmados, con un borde grueso oscuro.
  Future<void> _dibujarEventos() async {
    final capa = _eventos;
    if (capa == null) return;
    await capa.deleteAll();
    final eventos = widget.datos.eventos;
    if (eventos.isEmpty) return;
    await capa.createMulti([
      for (final e in eventos)
        CircleAnnotationOptions(
          geometry: _punto(e.coordenada),
          circleRadius: 9,
          circleColor: e.color.toARGB32(),
          circleStrokeWidth: e.confirmado ? 4 : 2,
          circleStrokeColor: (e.confirmado ? const Color(0xFF1B1B1B) : Colors.white).toARGB32(),
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
    await lineas.deleteAll();
    final trazado = widget.datos.trazado;
    if (trazado.length < 2) return;
    final puntos = [for (final c in trazado) _punto(c)];
    await lineas.create(
      PolylineAnnotationOptions(
        geometry: LineString(
          coordinates: [for (final p in puntos) p.coordinates],
        ),
        lineColor: const Color(0xFF1565C0).toARGB32(),
        lineWidth: 5,
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
      onMapLoadErrorListener: (error) {
        // Solo el estilo deja el mapa sin cargar; un mosaico que falla se vuelve a pedir solo.
        if (error.type == MapLoadErrorType.STYLE) {
          widget.datos.alFallar(error.message);
        }
      },
    );
  }
}
