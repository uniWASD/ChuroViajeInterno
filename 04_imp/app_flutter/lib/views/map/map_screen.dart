import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/eventos_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/reporte_controller.dart';
import '../../models/evento_vial.dart';
import '../../services/ubicacion/servicio_ubicacion.dart';
import '../reporte/reportar_evento_screen.dart';
import '../ruta/como_llegar_screen.dart';
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
      context.read<EventosController>().activar(); // RN-02.6: consulta cada 5 segundos
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
    switch (state) {
      case AppLifecycleState.resumed:
        controller.alVolverAPantalla();
        eventos.activar();
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
      case AppLifecycleState.inactive:
        // También pasa al mostrar el diálogo del permiso: no se deja de seguir por eso.
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    final construirMapa = context.read<ConstructorMapa>();
    return Consumer2<MapController, EventosController>(
      builder: (context, controller, eventos, child) {
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
                    eventos: _marcas(eventos),
                    alTocarEvento: eventos.seleccionar,
                    alTocar: controller.seleccionarPunto,
                    alFallar: controller.informarErrorMapa,
                  ),
                ),
              ),
            ),
            if (controller.errorMapa != null)
              Positioned.fill(child: AvisoErrorMapa(controller: controller)),
            if (eventos.sinActualizar)
              const Positioned(
                top: 8,
                left: 8,
                right: 8,
                child: _AvisoSinActualizar(),
              ),
            Positioned(
              left: 8,
              right: 8,
              bottom: 8,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Align(
                    alignment: Alignment.centerRight,
                    child: FloatingActionButton.extended(
                      heroTag: 'reportar-evento',
                      onPressed: () {
                        context.read<ReporteController>().empezar();
                        Navigator.of(context).push(ReportarEventoScreen.ruta());
                      },
                      icon: const Icon(Icons.add_alert_outlined),
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
                  if (eventos.seleccionado != null)
                    FichaEvento(evento: eventos.seleccionado!, controller: eventos),
                  if (controller.puntoSeleccionado != null)
                    _PuntoElegido(controller: controller),
                  if (controller.permiso != null && !controller.tienePermiso)
                    _SinUbicacion(controller: controller),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

/// Los eventos vigentes con el color de su tipo, tomado de los catálogos guardados (RN-02.7).
List<MarcaEvento> _marcas(EventosController eventos) {
  final catalogos = eventos.catalogos;
  return [
    for (final e in eventos.eventos)
      MarcaEvento(
        id: e.id,
        coordenada: e.ubicacion,
        color: catalogos?.tipo(e.tipo)?.colorFlutter ?? Colors.grey,
        confirmado: e.confirmado,
      ),
  ];
}

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

/// Ficha de un evento: su tipo y hace cuánto ocurrió (RN-02.7); si está confirmado, lo dice (RN-02.14).
class FichaEvento extends StatelessWidget {
  final EventoVial evento;
  final EventosController controller;

  const FichaEvento({super.key, required this.evento, required this.controller});

  @override
  Widget build(BuildContext context) {
    final tipo = controller.catalogos?.tipo(evento.tipo);
    return Card(
      key: const Key('ficha-evento'),
      child: ListTile(
        leading: Icon(Icons.warning_amber_rounded, color: tipo?.colorFlutter ?? Colors.grey),
        title: Text(tipo?.nombre ?? evento.tipo),
        subtitle: Text(
          [
            evento.haceCuanto(DateTime.now()),
            if (evento.confirmado) 'Confirmado por otros conductores',
          ].join(' · '),
        ),
        trailing: IconButton(
          tooltip: 'Cerrar',
          icon: const Icon(Icons.close),
          onPressed: controller.cerrarFicha,
        ),
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
    return Card(
      child: ListTile(
        leading: const Icon(Icons.place, color: Colors.red),
        title: const Text('Punto elegido'),
        subtitle: Text(
          '${punto.latitud.toStringAsFixed(6)}, ${punto.longitud.toStringAsFixed(6)}',
        ),
        trailing: IconButton(
          tooltip: 'Quitar el punto',
          icon: const Icon(Icons.close),
          onPressed: controller.limpiarPunto,
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
