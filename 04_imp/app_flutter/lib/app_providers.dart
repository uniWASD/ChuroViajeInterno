import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';

import 'config/app_config.dart';
import 'controllers/cola_controller.dart';
import 'controllers/eventos_controller.dart';
import 'controllers/map_controller.dart';
import 'controllers/navigation_controller.dart';
import 'controllers/reporte_controller.dart';
import 'controllers/sesion_controller.dart';
import 'controllers/telemetria_controller.dart';
import 'services/api/api_client.dart';
import 'services/catalogos/servicio_catalogos.dart';
import 'services/conectividad/conectividad.dart';
import 'services/eventos/servicio_eventos.dart';
import 'services/rutas/servicio_rutas.dart';
import 'services/sesion/almacen_local.dart';
import 'services/sesion/proveedor_google.dart';
import 'services/sesion/servicio_sesion.dart';
import 'services/telemetria/servicio_telemetria.dart';
import 'services/ubicacion/servicio_ubicacion.dart';
import 'storage/cola_reportes.dart';
import 'views/map/mapa_base.dart';
import 'views/map/mapa_mapbox.dart';

List<SingleChildWidget> createAppProviders({
  ApiClient? apiClient,
  ServicioUbicacion? ubicacion,
  ServicioRutas? servicioRutas,
  ConstructorMapa? constructorMapa,
  AlmacenLocal? almacen,
  ProveedorGoogle? google,
  DateTime Function()? reloj,
  ServicioEventos? servicioEventos,
  ColaReportes? cola,
  Conectividad? conectividad,
}) => [
  ChangeNotifierProvider(create: (_) => NavigationController()),
  Provider<ApiClient>(
    create: (_) => apiClient ?? ApiClient(),
    dispose: (_, client) => client.dispose(),
  ),
  Provider<AlmacenLocal>.value(value: almacen ?? AlmacenSeguro()),
  // La sesión se carga al abrir la app y es la que da el JWT al cliente HTTP (CU-08).
  ChangeNotifierProvider<SesionController>(
    lazy: false,
    create: (context) {
      final api = context.read<ApiClient>();
      final controlador = SesionController(
        servicio: ServicioSesion(api),
        google: google ?? ProveedorGoogleSignIn(),
        almacen: context.read<AlmacenLocal>(),
        catalogos: ServicioCatalogos(api, context.read<AlmacenLocal>()),
        reloj: reloj,
      );
      api.gestor = controlador;
      controlador.cargar();
      return controlador;
    },
  ),
  Provider<ServicioRutas>(
    create: (context) =>
        servicioRutas ??
        (AppConfig.usarRutaDeEjemplo
            ? ServicioRutasDeEjemplo()
            : ServicioRutasApi(context.read<ApiClient>())),
  ),
  Provider<ConstructorMapa>.value(
    value: constructorMapa ?? construirMapaMapbox,
  ),
  Provider<ServicioUbicacion>.value(value: ubicacion ?? ServicioUbicacionGeolocator()),
  ChangeNotifierProvider<MapController>(
    create: (context) => MapController(context.read<ServicioUbicacion>()),
  ),
  // HU-13: la telemetría sigue al mapa (en pantalla y con permiso) y a la sesión; arranca sola.
  Provider<TelemetriaController>(
    lazy: false,
    create: (context) => TelemetriaController(
      context.read<MapController>(),
      context.read<SesionController>(),
      context.read<ServicioUbicacion>(),
      ServicioTelemetria(context.read<ApiClient>()),
    ),
    dispose: (_, telemetria) => telemetria.dispose(),
  ),
  Provider<ServicioEventos>(
    create: (context) => servicioEventos ?? ServicioEventos(context.read<ApiClient>()),
  ),
  // HU-02: la cola sin conexión; se carga al abrir la app y reenvía sola cuando vuelve la red.
  ChangeNotifierProvider<ColaController>(
    lazy: false,
    create: (context) => ColaController(
      cola ?? ColaReportesSqflite(),
      context.read<ServicioEventos>(),
      context.read<SesionController>(),
      reloj: reloj,
      conectividad: conectividad ?? ConectividadPlus(),
    )..cargar(),
  ),
  ChangeNotifierProvider<ReporteController>(
    create: (context) => ReporteController(
      context.read<ServicioEventos>(),
      context.read<SesionController>(),
      context.read<MapController>(),
      cola: context.read<ColaController>(),
      reloj: reloj,
    ),
  ),
  ChangeNotifierProvider<EventosController>(
    create: (context) => EventosController(
      context.read<ServicioEventos>(),
      context.read<SesionController>(),
      reloj: reloj,
    ),
  ),
];
