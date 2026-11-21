import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/combustible_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../models/surtidor.dart';
import '../events/detalle_evento.dart';
import '../ruta/como_llegar_screen.dart';
import '../tema/tema.dart';
import 'estilo_surtidor.dart';

/// El detalle de un surtidor, como en el diseño «12 · Detalle de surtidor»: su nombre y su distancia y, en la
/// tarjeta «Información», el estado de cada combustible con su fila, su origen y su hora (RN-05.4). Desde aquí se
/// abre «Cómo llegar» con el surtidor como destino.
class DetalleSurtidor extends StatelessWidget {
  final Surtidor surtidor;
  final CombustibleController combustible;
  final DateTime Function() ahora;

  const DetalleSurtidor({
    super.key,
    required this.surtidor,
    required this.combustible,
    this.ahora = DateTime.now,
  });

  @override
  Widget build(BuildContext context) {
    final instante = ahora();
    final distancia = combustible.ordenadosPorCercania ? surtidor.distanciaParaMostrar : null;
    return Column(
      key: const Key('ficha-surtidor'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Icon(Icons.local_gas_station_outlined, size: 40, color: colorDeEstado(surtidor.estadoGeneral)),
            const SizedBox(width: 6),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    surtidor.nombre,
                    style: const TextStyle(fontSize: 22),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (distancia != null)
                    Text(distancia, style: const TextStyle(fontSize: 13, color: ColoresChuro.textoSecundario)),
                ],
              ),
            ),
            BotonCerrarHoja(alTocar: combustible.cerrarFicha),
          ],
        ),
        const SizedBox(height: 8),
        Card(
          margin: EdgeInsets.zero,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 6, 12, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text('Información:', style: TextStyle(fontSize: 17, color: ColoresChuro.etiqueta)),
                const SizedBox(height: 4),
                if (!surtidor.informacionReciente || surtidor.combustibles.isEmpty)
                  const SinInformacionReciente()
                else
                  for (final dato in surtidor.combustibles) LineaCombustible(dato: dato, ahora: instante),
              ],
            ),
          ),
        ),
        const SizedBox(height: 10),
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            key: const Key('como-llegar-surtidor'),
            onPressed: () => abrirComoLlegar(context, surtidor),
            icon: const Icon(Icons.directions),
            label: const Text('Cómo llegar'),
          ),
        ),
      ],
    );
  }
}

/// RN-05.5: un surtidor sin datos vigentes.
class SinInformacionReciente extends StatelessWidget {
  const SinInformacionReciente({super.key});

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(vertical: 6),
    child: Row(
      children: [
        BarraEstado(color: ColoresSurtidor.sinInformacion),
        SizedBox(width: 8),
        Text('Sin información reciente', style: TextStyle(fontSize: 15, color: ColoresChuro.textoSecundario)),
      ],
    ),
  );
}

/// D-24 y D-47: el surtidor queda como el punto elegido en el mapa, con su nombre, y se abre «Cómo llegar» con él
/// como destino. Al volver, el Mapa lo sigue mostrando.
void abrirComoLlegar(BuildContext context, Surtidor surtidor) {
  context.read<MapController>().elegirLugar(surtidor.ubicacion, surtidor.nombre);
  context.read<CombustibleController>().cerrarFicha();
  context.read<NavigationController>().selectTab(AppTab.map.index);
  Navigator.of(context).push(ComoLlegarScreen.ruta(context, destinoInicial: surtidor.ubicacion));
}
