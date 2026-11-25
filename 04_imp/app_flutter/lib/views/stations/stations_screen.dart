import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/combustible_controller.dart';
import '../../controllers/map_controller.dart';
import '../../models/surtidor.dart';
import '../../services/ubicacion/servicio_ubicacion.dart';
import '../events/events_screen.dart';
import '../tema/tema.dart';
import '../ruta/hoja_ruta.dart';
import 'detalle_surtidor.dart';
import 'estilo_surtidor.dart';

/// Pestaña Surtidores (CU-05, HU-07), como en el diseño «08 · Surtidores»: una hoja sobre el mapa con los surtidores
/// del más cercano al más lejano (RN-05.2). Cada fila lleva una barra con su estado general, el nombre, hace cuánto
/// llegó su último dato y de dónde vino (RNF-07). Al tocarla se despliega su estado por combustible y el botón «Ir»;
/// la «i» abre el detalle y lleva el mapa hasta el surtidor. La lista se actualiza sola cada 60 segundos (RN-05.17).
class StationsScreen extends StatefulWidget {
  final DateTime Function() ahora;

  const StationsScreen({super.key, this.ahora = DateTime.now});

  @override
  State<StationsScreen> createState() => _StationsScreenState();
}

class _StationsScreenState extends State<StationsScreen> {
  final DraggableScrollableController _hoja = DraggableScrollableController();

  @override
  void dispose() {
    _hoja.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final combustible = context.watch<CombustibleController>();
    // Del mapa solo importa el permiso: la lista no se reconstruye con cada posición del GPS (antes se trababa).
    final permiso = context.select<MapController, PermisoUbicacion?>((m) => m.permiso);
    final seleccionado = combustible.seleccionado;
    if (seleccionado != null) {
      return Align(
        alignment: Alignment.bottomCenter,
        child: HojaInferior(
          key: const Key('hoja-detalle-surtidor'),
          child: DetalleSurtidor(surtidor: seleccionado, combustible: combustible, ahora: widget.ahora),
        ),
      );
    }
    final ahora = widget.ahora();
    final lista = combustible.surtidores;
    return DraggableScrollableSheet(
      key: const Key('hoja-surtidores'),
      controller: _hoja,
      initialChildSize: EventsScreen.media,
      minChildSize: EventsScreen.abajo,
      maxChildSize: EventsScreen.arriba,
      snap: true,
      snapSizes: const [EventsScreen.media],
      builder: (context, desplazamiento) => DecoratedBox(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          boxShadow: [BoxShadow(color: Color(0x1F000000), blurRadius: 14, offset: Offset(0, -2))],
        ),
        child: CustomScrollView(
          controller: desplazamiento,
          slivers: [
            SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: AsaHoja()),
                  if (permiso != null && permiso != PermisoUbicacion.concedido)
                    _PedirUbicacion(mapa: context.read<MapController>()),
                  if (combustible.sinActualizar)
                    Material(
                      color: Theme.of(context).colorScheme.errorContainer,
                      child: const ListTile(
                        dense: true,
                        leading: Icon(Icons.sync_problem),
                        title: Text(CombustibleController.mensajeSinActualizar),
                      ),
                    ),
                ],
              ),
            ),
            if (lista.isEmpty)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: combustible.cargando
                      ? const Center(child: CircularProgressIndicator())
                      : const Text('No hay surtidores para mostrar.', textAlign: TextAlign.center),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.only(bottom: 16),
                sliver: SliverList.builder(
                  key: const Key('lista-surtidores'),
                  itemCount: lista.length,
                  itemBuilder: (_, i) => _FilaSurtidor(
                    surtidor: lista[i],
                    combustible: combustible,
                    expandido: combustible.expandido == lista[i].id,
                    ahora: ahora,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Sin permiso de ubicación: la lista no se puede ordenar por cercanía, y se deja concederlo.
class _PedirUbicacion extends StatelessWidget {
  final MapController mapa;

  const _PedirUbicacion({required this.mapa});

  @override
  Widget build(BuildContext context) {
    final alAjustes = mapa.permiso == PermisoUbicacion.denegadoSiempre ||
        mapa.permiso == PermisoUbicacion.servicioApagado;
    return Card(
      key: const Key('pedir-ubicacion-surtidores'),
      margin: const EdgeInsets.fromLTRB(8, 0, 8, 6),
      child: ListTile(
        leading: const Icon(Icons.location_off_outlined),
        title: const Text('Necesitamos tu ubicación para ordenar los surtidores del más cercano al más lejano.'),
        trailing: TextButton(
          onPressed: alAjustes ? mapa.abrirAjustesDeUbicacion : mapa.activarUbicacion,
          child: Text(alAjustes ? 'Ajustes' : 'Permitir'),
        ),
      ),
    );
  }
}

class _FilaSurtidor extends StatelessWidget {
  final Surtidor surtidor;
  final CombustibleController combustible;
  final bool expandido;
  final DateTime ahora;

  const _FilaSurtidor({
    required this.surtidor,
    required this.combustible,
    required this.expandido,
    required this.ahora,
  });

  @override
  Widget build(BuildContext context) {
    final ultimo = surtidor.ultimoDato;
    final sinDatos = surtidor.estadoGeneral == EstadoGeneral.sinInformacion;
    void abrirDetalle() => combustible.seleccionar(surtidor.id);
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 3, 4, 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Material(
              color: expandido ? const Color(0xFFE4E4E4) : Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
                side: const BorderSide(color: Color(0xFFE4E4E4)),
              ),
              elevation: 2,
              shadowColor: const Color(0x55000000),
              child: InkWell(
                key: Key('surtidor-${surtidor.id}'),
                borderRadius: BorderRadius.circular(10),
                onTap: () => combustible.alternar(surtidor.id),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(8, 6, 10, 6),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          BarraEstado(color: colorDeEstado(surtidor.estadoGeneral)),
                          const SizedBox(width: 6),
                          const Icon(Icons.local_gas_station, size: 22),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              surtidor.nombre,
                              style: const TextStyle(fontSize: 18),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          Text(
                            sinDatos || ultimo == null ? 'sin información reciente' : '· ${haceCuanto(ultimo.fecha, ahora)}',
                            style: const TextStyle(fontSize: 13, color: ColoresChuro.textoSecundario),
                          ),
                        ],
                      ),
                      if (expandido) ...[
                        const SizedBox(height: 6),
                        if (sinDatos)
                          const SinInformacionReciente()
                        else
                          for (final dato in surtidor.combustibles) LineaCombustible(dato: dato, ahora: ahora),
                        Row(
                          children: [
                            // #191: la distancia con el auto a la izquierda.
                            if (combustible.ordenadosPorCercania && surtidor.distanciaParaMostrar != null)
                              DistanciaEnAuto(distancia: surtidor.distanciaParaMostrar!),
                            const Spacer(),
                            FilledButton(
                              key: Key('ir-${surtidor.id}'),
                              style: FilledButton.styleFrom(
                                backgroundColor: const Color(0xFF64A8F0),
                                minimumSize: const Size(44, 30),
                                padding: const EdgeInsets.symmetric(horizontal: 14),
                              ),
                              // #191: «Ir» abre directamente la navegación hasta el surtidor.
                              onPressed: () => irA(context, surtidor.ubicacion, surtidor.nombre),
                              child: const Text('Ir'),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
          IconButton(
            key: Key('info-surtidor-${surtidor.id}'),
            tooltip: 'Ver el detalle',
            visualDensity: VisualDensity.compact,
            icon: const Icon(Icons.info_outline, color: Color(0xFF64A8F0)),
            onPressed: abrirDetalle,
          ),
        ],
      ),
    );
  }
}
