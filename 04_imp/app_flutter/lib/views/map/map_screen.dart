import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/cola_controller.dart';
import '../../controllers/combustible_controller.dart';
import '../../controllers/eventos_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../controllers/recarga_controller.dart';
import '../../controllers/reporte_controller.dart';
import '../../services/ubicacion/servicio_ubicacion.dart';
import '../busqueda/barra_busqueda.dart';
import '../events/aviso_desactualizados.dart';
import '../events/detalle_evento.dart';
import '../reporte/reportar_evento_screen.dart';
import '../ruta/como_llegar_screen.dart';
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
    return Consumer2<MapController, EventosController>(
      builder: (context, controller, eventos, child) {
        final surtidorAbierto = eventos.seleccionado == null ? combustible.seleccionado : null;
        return Stack(
          children: [
            Positioned.fill(
              child: KeyedSubtree(
                key: ValueKey(controller.intentoMapa),
                child: construirMapa(
                  context,
                  DatosMapa(
                    mostrarUbicacion: controller.siguiendoUbicacion,
                    puntoSeleccionado: controller.puntoSeleccionado,
                    eventos: marcasDeEventos(eventos),
                    alTocarEvento: (id) {
                      combustible.cerrarFicha();
                      eventos.seleccionar(id);
                    },
                    surtidores: marcasDeSurtidores(combustible),
                    alTocarSurtidor: (id) {
                      eventos.cerrarFicha();
                      combustible.seleccionar(id);
                    },
                    // #172: al abrir un evento la cámara va hasta él, por encima del detalle. D-47: también va
                    // hasta el lugar elegido en la búsqueda, y #71, hasta el surtidor abierto.
                    foco: eventos.seleccionado?.ubicacion ??
                        combustible.seleccionado?.ubicacion ??
                        controller.foco,
                    tapadoAbajo: 0.3,
                    alTocar: controller.seleccionarPunto,
                    alFallar: controller.informarErrorMapa,
                  ),
                ),
              ),
            ),
            if (controller.errorMapa != null)
              Positioned.fill(child: AvisoErrorMapa(controller: controller)),
            if (enElMapa && (eventos.desactualizados || eventos.sinActualizar))
              // Debajo de la barra de búsqueda (D-47). Con los eventos guardados (RN-02.9), su marca; si no, el
              // aviso de que no se pudo actualizar (RN-02.8).
              Positioned(
                top: MediaQuery.paddingOf(context).top + BarraBusqueda.espacio,
                left: 8,
                right: 8,
                child: eventos.desactualizados
                    ? AvisoDesactualizados(eventos: eventos)
                    : const _AvisoSinActualizar(),
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
                          Align(
                            alignment: Alignment.centerRight,
                            child: FloatingActionButton.extended(
                              heroTag: 'reportar-evento',
                              // #172: «Notificar» va en el rojo suave del diseño.
                              backgroundColor: ColoresChuro.rojoSuave,
                              onPressed: () {
                                context.read<ReporteController>().empezar();
                                Navigator.of(context)
                                    .push(ReportarEventoScreen.ruta());
                              },
                              icon: const Icon(
                                Icons.notifications_active_outlined,
                              ),
                              label: const Text('Reportar evento'),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Align(
                            alignment: Alignment.centerRight,
                            child: FloatingActionButton.extended(
                              heroTag: 'como-llegar',
                              onPressed: () => Navigator.of(context).push(
                                ComoLlegarScreen.ruta(
                                  context,
                                  destinoInicial: controller.puntoSeleccionado,
                                ),
                              ),
                              icon: const Icon(Icons.directions),
                              label: const Text('Cómo llegar'),
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
                    if (eventos.seleccionado != null)
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
                    else if (controller.puntoSeleccionado != null)
                      HojaInferior(
                        child: _PuntoElegido(controller: controller),
                      ),
                  ],
                ),
              ),
          ],
        );
      },
    );
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
/// trazado llevan su nivel y su trazado, para dibujarse como línea (RN-04.25).
List<MarcaEvento> marcasDeEventos(EventosController eventos) {
  final catalogos = eventos.catalogos;
  return [
    for (final e in eventos.eventos)
      MarcaEvento(
        id: e.id,
        coordenada: e.ubicacion,
        color: catalogos?.tipo(e.tipo)?.colorFlutter ?? Colors.grey,
        confirmado: e.confirmado,
        tipo: e.tipo,
        nivel: e.nivel,
        trazado: e.trazado,
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

class _PuntoElegido extends StatelessWidget {
  final MapController controller;

  const _PuntoElegido({required this.controller});

  @override
  Widget build(BuildContext context) {
    final punto = controller.puntoSeleccionado!;
    return Row(
      children: [
        const Icon(Icons.place, color: ColoresChuro.rojo),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // D-47: si vino de la búsqueda, su nombre.
              Text(
                controller.nombrePunto ?? 'Punto elegido',
                style: const TextStyle(fontSize: 20),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              Text(
                '${punto.latitud.toStringAsFixed(6)}, ${punto.longitud.toStringAsFixed(6)}',
                style: const TextStyle(
                  fontSize: 13,
                  color: ColoresChuro.textoSecundario,
                ),
              ),
            ],
          ),
        ),
        BotonCerrarHoja(
          alTocar: controller.limpiarPunto,
          tooltip: 'Quitar el punto',
        ),
      ],
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
