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
    if (anterior.datos.puntoSeleccionado != widget.datos.puntoSeleccionado) {
      _marcarPunto();
    }
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
    _marcas = await mapa.annotations.createCircleAnnotationManager();
    await _actualizarUbicacion();
    await _marcarPunto();
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
    final punto = widget.datos.puntoSeleccionado;
    if (punto == null) return;
    await marcas.create(
      CircleAnnotationOptions(
        geometry: _punto(punto),
        circleRadius: 8,
        circleColor: Colors.red.toARGB32(),
        circleStrokeWidth: 2,
        circleStrokeColor: Colors.white.toARGB32(),
      ),
    );
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
      viewport: CameraViewportState(
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
