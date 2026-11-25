import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/cola_controller.dart';
import '../../controllers/combustible_controller.dart';
import '../../controllers/eventos_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../controllers/recarga_controller.dart';
import '../../controllers/reporte_controller.dart';
import '../../controllers/ruta_controller.dart';
import '../../models/catalogos.dart';
import '../../models/coordenada.dart';
import '../../models/evento_vial.dart';
import '../../models/surtidor.dart';
import '../../models/trafico.dart';
import '../../services/ubicacion/servicio_ubicacion.dart';
import '../busqueda/barra_busqueda.dart';
import '../events/aviso_desactualizados.dart';
import '../events/detalle_evento.dart';
import '../reporte/hoja_notificar.dart';
import '../ruta/hoja_ruta.dart';
import '../stations/detalle_surtidor.dart';
import '../stations/estilo_surtidor.dart';
import '../tema/tema.dart';
import 'mapa_base.dart';

/// Vista Mapa principal: el mapa base de Tarija, la posición del conductor y el punto que toca.
class MapScreen extends StatefulWidget {
  const MapScreen({super.key});

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> with WidgetsBindingObserver {
  /// Lo que se le pasa al mapa se calcula de nuevo solo cuando cambian sus datos, no en cada reconstrucción (por
  /// ejemplo, con cada posición del GPS). Así la lista de surtidores y la de eventos no se traban mientras el mapa
  /// sigue al conductor.
  final _MemoMapa _memo = _MemoMapa();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<MapController>().activarUbicacion();
      context
          .read<EventosController>()
          .activar(); // RN-02.6: consulta cada 5 segundos
      // RN-05.17: con el mapa abierto, los marcadores de surtidores se actualizan cada 60 segundos.
      context.read<CombustibleController>().activar();
      // HU-12: con la app abierta se revisa si el conductor está detenido junto a un surtidor (D-11).
      context.read<RecargaController>().activar();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final controller = context.read<MapController>();
    final eventos = context.read<EventosController>();
    final combustible = context.read<CombustibleController>();
    final recarga = context.read<RecargaController>();
    switch (state) {
      case AppLifecycleState.resumed:
        controller.alVolverAPantalla();
        eventos.activar();
        combustible.activar();
        recarga.activar();
        // Si el conductor fue a los ajustes a dar el permiso o a prender el GPS, se vuelve a revisar.
        if (controller.permiso == PermisoUbicacion.denegadoSiempre ||
            controller.permiso == PermisoUbicacion.servicioApagado) {
          controller.activarUbicacion();
        }
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        controller.alSalirDePantalla();
        eventos.pausar(); // en segundo plano no se consulta
        combustible.pausar();
        recarga.pausar();
      case AppLifecycleState.inactive:
        // También pasa al mostrar el diálogo del permiso: no se deja de seguir por eso.
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    final construirMapa = context.read<ConstructorMapa>();
    // #172: el mapa también se ve detrás de la hoja de Eventos; sus botones y su ficha, solo en la pestaña Mapa.
    final enElMapa =
        context.watch<NavigationController>().selectedIndex == AppTab.map.index;
    final combustible = context.watch<CombustibleController>();
    final ruta = context.watch<RutaController>();
    final reporte = context.watch<ReporteController>();
    return Consumer2<MapController, EventosController>(
      builder: (context, controller, eventos, child) {
        final surtidorAbierto = eventos.seleccionado == null ? combustible.seleccionado : null;
        final punto = controller.puntoSeleccionado;
        // #191: «Cómo llegar» se ve en este mismo mapa, hacia el lugar elegido.
        final enRuta = ruta.mostrando && punto != null && ruta.destino == punto;
        final rutaVisible = enRuta ? ruta.ruta : null;
        final congestiones = eventos.congestiones;
        final notificando = enElMapa && reporte.abierto;
        return Stack(
          children: [
            Positioned.fill(
              child: KeyedSubtree(
                key: ValueKey(controller.intentoMapa),
                child: construirMapa(
                  context,
                  DatosMapa(
                    mostrarUbicacion: controller.siguiendoUbicacion,
                    // Mientras se notifica, el pin marca dónde va el evento (o ninguno, si va en la posición).
                    puntoSeleccionado: notificando ? reporte.puntoManual : punto,
                    marcas: [
                      if (enRuta)
                        for (final p in ruta.paradas) MarcaMapa(p, TipoMarca.parada),
                    ],
                    trazado: rutaVisible?.trazado ?? const [],
                    // #191: la ruta de amarillo o de rojo donde pasa por una congestión.
                    trafico: rutaVisible == null
                        ? const []
                        : _memo.trafico(rutaVisible.trazado, congestiones),
                    eventos: _memo.eventos(eventos, congestiones),
                    alTocarEvento: (id) => _alTocarEvento(context, id),
                    surtidores: _memo.surtidores(combustible),
                    alTocarSurtidor: (id) => _alTocarSurtidor(context, id),
                    // #172: al abrir un evento la cámara va hasta él, por encima del detalle. D-47: también va
                    // hasta el lugar elegido en la búsqueda, y #71, hasta el surtidor abierto.
                    foco: eventos.seleccionado?.ubicacion ??
                        combustible.seleccionado?.ubicacion ??
                        controller.foco,
                    tapadoAbajo: 0.3,
                    alTocar: (c) => _alTocarMapa(context, c),
                    alTocarLugar: (c, nombre) => _alTocarMapa(context, c, nombre: nombre),
                    alFallar: controller.informarErrorMapa,
                  ),
                ),
              ),
            ),
            if (controller.errorMapa != null)
              Positioned.fill(child: AvisoErrorMapa(controller: controller)),
            if (enElMapa && (eventos.desactualizados || eventos.sinActualizar || ruta.eligiendoParada))
              // Debajo de la barra de búsqueda (D-47). Con los eventos guardados (RN-02.9), su marca; si no, el
              // aviso de que no se pudo actualizar (RN-02.8). Mientras se elige una parada, cómo hacerlo.
              Positioned(
                top: MediaQuery.paddingOf(context).top + BarraBusqueda.espacio,
                left: 8,
                right: 8,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (eventos.desactualizados)
                      AvisoDesactualizados(eventos: eventos)
                    else if (eventos.sinActualizar)
                      const _AvisoSinActualizar(),
                    if (ruta.eligiendoParada) const AvisoElegirParada(),
                  ],
                ),
              ),
            if (enElMapa)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(8),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // #191: solo la campana, para notificar en la posición del conductor en 3 toques (RNF-06).
                          if (!notificando && !enRuta)
                            Align(
                              alignment: Alignment.centerRight,
                              child: FloatingActionButton(
                                key: const Key('boton-notificar'),
                                heroTag: 'notificar-evento',
                                tooltip: 'Notificar evento',
                                backgroundColor: ColoresChuro.rojoSuave,
                                shape: const CircleBorder(),
                                onPressed: () {
                                  eventos.cerrarFicha();
                                  combustible.cerrarFicha();
                                  reporte.empezar();
                                },
                                child: const Icon(Icons.notifications_active_outlined),
                              ),
                            ),
                          const SizedBox(height: 8),
                          const _ReportesPendientes(),
                          if (controller.permiso != null &&
                              !controller.tienePermiso)
                            _SinUbicacion(controller: controller),
                        ],
                      ),
                    ),
                    // #172: lo que se abre sobre el mapa va en una hoja inferior con asa, como en el diseño.
                    if (notificando)
                      const HojaInferior(child: HojaNotificar())
                    else if (eventos.seleccionado != null)
                      HojaInferior(
                        child: DetalleEvento(
                          evento: eventos.seleccionado!,
                          eventos: eventos,
                        ),
                      )
                    else if (surtidorAbierto != null)
                      HojaInferior(
                        key: const Key('hoja-surtidor-mapa'),
                        child: DetalleSurtidor(
                          surtidor: surtidorAbierto,
                          combustible: combustible,
                        ),
                      )
                    else if (enRuta)
                      HojaInferior(
                        child: HojaRuta(nombre: controller.nombrePunto, posicion: controller.posicion),
                      )
                    else if (punto != null)
                      HojaInferior(
                        child: HojaLugar(
                          punto: punto,
                          nombre: controller.nombrePunto,
                          posicion: controller.posicion,
                        ),
                      ),
                  ],
                ),
              ),
          ],
        );
      },
    );
  }

  /// #191: un toque en el mapa (o en un lugar conocido, con su [nombre]) depende de lo que esté abierto: con
  /// «Notificar evento» cambia el punto del evento; después de «Otra parada», es la parada; con la ruta a la vista no
  /// hace nada, como en Google Maps; si no, elige el lugar y abre su hoja.
  void _alTocarMapa(BuildContext context, Coordenada punto, {String? nombre}) {
    // En Eventos y en Surtidores el mapa se ve detrás de la hoja, pero no se elige nada en él: la hoja del lugar,
    // «Notificar evento» y las paradas son de la pestaña Mapa, y quedarían cambiadas sin que el conductor lo vea.
    if (context.read<NavigationController>().selectedIndex != AppTab.map.index) return;
    final mapa = context.read<MapController>();
    final reporte = context.read<ReporteController>();
    final ruta = context.read<RutaController>();
    if (reporte.abierto) {
      reporte.usarPunto(punto);
      return;
    }
    if (ruta.eligiendoParada) {
      ruta.agregarParada(punto, mapa.posicion);
      return;
    }
    if (ruta.mostrando && ruta.destino == mapa.puntoSeleccionado) return;
    context.read<EventosController>().cerrarFicha();
    context.read<CombustibleController>().cerrarFicha();
    mapa.seleccionarPunto(punto, nombre: nombre);
  }

  /// Un evento del mapa se abre en la pestaña donde se ve su detalle: en el Mapa y en Eventos, ahí mismo; desde
  /// Surtidores, se pasa a Eventos (antes quedaba elegido sin verse y aparecía al volver al Mapa).
  void _alTocarEvento(BuildContext context, int id) {
    final pestanas = context.read<NavigationController>();
    if (pestanas.selectedIndex == AppTab.stations.index) pestanas.selectTab(AppTab.events.index);
    context.read<CombustibleController>().cerrarFicha();
    context.read<EventosController>().seleccionar(id);
  }

  /// Igual con un surtidor: en el Mapa y en Surtidores se abre ahí; desde Eventos, se pasa a Surtidores.
  void _alTocarSurtidor(BuildContext context, int id) {
    final pestanas = context.read<NavigationController>();
    if (pestanas.selectedIndex == AppTab.events.index) pestanas.selectTab(AppTab.stations.index);
    context.read<EventosController>().cerrarFicha();
    context.read<CombustibleController>().seleccionar(id);
  }
}

/// Guarda lo último que se calculó para el mapa y lo devuelve igual mientras sus datos sean los mismos (la misma
/// lista de eventos, de surtidores o el mismo trazado). Unir las congestiones y pintar la ruta por tráfico recorren
/// todos los puntos; no hace falta repetirlo con cada posición del conductor.
class _MemoMapa {
  List<CongestionUnida>? _congestionesEventos;
  Catalogos? _catalogos;
  List<EventoVial>? _baseEventos;
  List<MarcaEvento> _eventos = const [];

  List<Surtidor>? _baseSurtidores;
  List<MarcaSurtidor> _surtidores = const [];

  List<Coordenada>? _baseTrazado;
  List<CongestionUnida>? _congestionesTrafico;
  List<LineaTrafico> _trafico = const [];

  List<MarcaEvento> eventos(EventosController controller, List<CongestionUnida> congestiones) {
    final lista = controller.eventos;
    final catalogos = controller.catalogos;
    if (!identical(lista, _baseEventos) ||
        !identical(congestiones, _congestionesEventos) ||
        !identical(catalogos, _catalogos)) {
      _baseEventos = lista;
      _congestionesEventos = congestiones;
      _catalogos = catalogos;
      _eventos = marcasDeEventos(controller, congestiones: congestiones);
    }
    return _eventos;
  }

  List<MarcaSurtidor> surtidores(CombustibleController controller) {
    final lista = controller.surtidores;
    if (!identical(lista, _baseSurtidores)) {
      _baseSurtidores = lista;
      _surtidores = marcasDeSurtidores(controller);
    }
    return _surtidores;
  }

  List<LineaTrafico> trafico(List<Coordenada> trazado, List<CongestionUnida> congestiones) {
    if (!identical(trazado, _baseTrazado) || !identical(congestiones, _congestionesTrafico)) {
      _baseTrazado = trazado;
      _congestionesTrafico = congestiones;
      _trafico = pintarRutaPorTrafico(trazado, congestiones);
    }
    return _trafico;
  }
}

/// RN-01.15: los reportes guardados sin conexión se ven; los que fallaron varias veces, como pendientes.
class _ReportesPendientes extends StatelessWidget {
  const _ReportesPendientes();

  @override
  Widget build(BuildContext context) {
    final cola = context.watch<ColaController>();
    final cantidad = cola.pendientes.length;
    if (cantidad == 0) return const SizedBox.shrink();
    final atascados = cola.atascados.length;
    return Card(
      key: const Key('reportes-pendientes'),
      child: ListTile(
        leading: const Icon(Icons.cloud_upload_outlined),
        title: Text(
          cantidad == 1
              ? '1 reporte pendiente de envío'
              : '$cantidad reportes pendientes de envío',
        ),
        subtitle: Text(
          atascados > 0
              ? 'No se pudo enviar después de varios intentos. Se seguirá intentando.'
              : 'Se enviará solo cuando vuelva la red.',
        ),
        trailing: TextButton(
          onPressed: cola.reenviar,
          child: const Text('Reintentar'),
        ),
      ),
    );
  }
}

/// Los eventos vigentes con el color de su tipo, tomado de los catálogos guardados (RN-02.7). Las congestiones con
/// trazado se dibujan como línea (RN-04.25) y, desde #191, las cuadras seguidas del mismo sentido y nivel se unen en
/// una sola línea de donde empieza a donde acaba. Al tocarla se abre la primera.
List<MarcaEvento> marcasDeEventos(EventosController eventos, {List<CongestionUnida>? congestiones}) {
  final catalogos = eventos.catalogos;
  final lista = eventos.eventos;
  final unidas = congestiones ?? unirCongestiones(lista);
  final enLinea = {for (final u in unidas) ...u.ids};
  final colorCongestion = catalogos?.tipo('CONGESTION')?.colorFlutter ?? Colors.grey;
  final porId = {for (final e in lista) e.id: e};
  return [
    for (final e in lista)
      if (!enLinea.contains(e.id))
        MarcaEvento(
          id: e.id,
          coordenada: e.ubicacion,
          color: catalogos?.tipo(e.tipo)?.colorFlutter ?? Colors.grey,
          confirmado: e.confirmado,
          tipo: e.tipo,
          nivel: e.nivel,
          trazado: e.trazado,
        ),
    for (final u in unidas)
      MarcaEvento(
        id: u.id,
        coordenada: porId[u.id]?.ubicacion ?? u.trazado.first,
        color: colorCongestion,
        confirmado: false,
        tipo: 'CONGESTION',
        nivel: u.nivel,
        trazado: u.trazado,
      ),
  ];
}

/// D-25: los surtidores con el color de su estado general.
List<MarcaSurtidor> marcasDeSurtidores(CombustibleController combustible) => [
  for (final s in combustible.surtidores)
    MarcaSurtidor(id: s.id, coordenada: s.ubicacion, color: colorDeEstado(s.estadoGeneral)),
];

/// RN-02.8: el backend no respondió; el mapa conserva lo último y reintenta en el siguiente ciclo.
class _AvisoSinActualizar extends StatelessWidget {
  const _AvisoSinActualizar();

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.errorContainer,
      child: const ListTile(
        dense: true,
        leading: Icon(Icons.sync_problem),
        title: Text(EventosController.mensajeSinActualizar),
      ),
    );
  }
}

/// El mapa base no cargó: mensaje de servicio externo y botón para reintentar (RN-03.18).
class AvisoErrorMapa extends StatelessWidget {
  final MapController controller;

  const AvisoErrorMapa({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: Theme.of(context).colorScheme.surface,
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.cloud_off_outlined, size: 56),
              const SizedBox(height: 12),
              Text(controller.errorMapa!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: controller.reintentarMapa,
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SinUbicacion extends StatelessWidget {
  final MapController controller;

  const _SinUbicacion({required this.controller});

  @override
  Widget build(BuildContext context) {
    final String texto;
    final String accion;
    final VoidCallback alTocar;
    switch (controller.permiso) {
      case PermisoUbicacion.servicioApagado:
        texto = 'El GPS está apagado. El mapa funciona, pero sin tu posición.';
        accion = 'Ajustes';
        alTocar = controller.abrirAjustesDeUbicacion;
      case PermisoUbicacion.denegadoSiempre:
        texto =
            'Sin permiso de ubicación. El mapa funciona, pero sin tu posición.';
        accion = 'Ajustes';
        alTocar = controller.abrirAjustesDeUbicacion;
      default:
        texto =
            'Sin permiso de ubicación. El mapa funciona, pero sin tu posición.';
        accion = 'Permitir';
        alTocar = controller.activarUbicacion;
    }
    return Card(
      child: ListTile(
        leading: const Icon(Icons.location_off_outlined),
        title: Text(texto),
        trailing: TextButton(onPressed: alTocar, child: Text(accion)),
      ),
    );
  }
}
