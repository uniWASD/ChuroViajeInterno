import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';

import 'controllers/map_controller.dart';
import 'controllers/navigation_controller.dart';
import 'services/api/api_client.dart';
import 'services/ubicacion/servicio_ubicacion.dart';
import 'views/map/mapa_base.dart';
import 'views/map/mapa_mapbox.dart';

List<SingleChildWidget> createAppProviders({
  ApiClient? apiClient,
  ServicioUbicacion? ubicacion,
  ConstructorMapa? constructorMapa,
}) => [
  ChangeNotifierProvider(create: (_) => NavigationController()),
  Provider<ApiClient>(
    create: (_) => apiClient ?? ApiClient(),
    dispose: (_, client) => client.dispose(),
  ),
  Provider<ConstructorMapa>.value(
    value: constructorMapa ?? construirMapaMapbox,
  ),
  ChangeNotifierProvider<MapController>(
    create: (context) => MapController(
      context.read<ApiClient>(),
      ubicacion ?? ServicioUbicacionGeolocator(),
    ),
  ),
];
