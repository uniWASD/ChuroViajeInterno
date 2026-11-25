import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/map_controller.dart';
import '../../controllers/reporte_controller.dart';
import '../../models/catalogos.dart';
import '../../models/navegacion.dart';
import '../../models/ruta.dart';
import '../events/detalle_evento.dart';
import '../tema/tema.dart';

/// La hoja «Notificar evento» sobre el mapa (CU-01, #191, diseño «Notificar evento»): el tipo, hace cuánto ocurrió y
/// «Enviar». Con la campana va en la posición del conductor: son 3 toques (RNF-06). Desde un lugar elegido va en ese
/// lugar, y mientras está abierta se puede tocar el mapa para cambiar el punto, a 1 km o menos (RN-01.5). No lleva
/// imagen ni comentario.
class HojaNotificar extends StatelessWidget {
  const HojaNotificar({super.key});

  @override
  Widget build(BuildContext context) {
    final reporte = context.watch<ReporteController>();
    final posicion = context.watch<MapController>().posicion;
    return Column(
      key: const Key('hoja-notificar'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            const Icon(Icons.notifications, size: 22),
            const SizedBox(width: 6),
            const Expanded(child: Text('Notificar evento', style: TextStyle(fontSize: 20))),
            BotonCerrarHoja(alTocar: reporte.cerrar, tooltip: 'Cerrar'),
          ],
        ),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
          decoration: BoxDecoration(
            border: Border.all(color: ColoresChuro.borde),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const _Etiqueta('Tipo de evento:'),
              if (reporte.tipos.isEmpty)
                const Text('Cargando los tipos de evento…')
              else
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final tipo in reporte.tipos)
                      ChoiceChip(
                        key: Key('tipo-${tipo.codigo}'),
                        avatar: Icon(
                          iconoDeEvento(tipo.codigo),
                          color: reporte.tipo == tipo.codigo ? Colors.white : ColoresChuro.textoChip,
                        ),
                        label: Text(nombreCortoDeTipo(tipo)),
                        selected: reporte.tipo == tipo.codigo,
                        visualDensity: VisualDensity.compact,
                        onSelected: (_) => reporte.elegirTipo(tipo.codigo),
                      ),
                  ],
                ),
              const SizedBox(height: 8),
              const _Etiqueta('Hace:'),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  for (final tiempo in reporte.tiempos)
                    ChoiceChip(
                      key: Key('hace-${tiempo.minutos}'),
                      label: Text(nombreCortoDeTiempo(tiempo)),
                      selected: reporte.minutos == tiempo.minutos,
                      visualDensity: VisualDensity.compact,
                      onSelected: (_) => reporte.elegirTiempo(tiempo.minutos),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              _DondeOcurrio(reporte: reporte, posicion: posicion != null),
              if (reporte.mensaje != null && reporte.estado != EstadoReporte.enviado) _Mensaje(reporte: reporte),
              const SizedBox(height: 6),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  key: const Key('confirmar-reporte'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size(0, 36),
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                  ),
                  onPressed: reporte.puedeConfirmar ? reporte.confirmar : null,
                  child: reporte.enviando
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Text('Enviar'),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// El nombre corto del diseño para cada tipo: «Policía» en vez de «Control policial». Los demás, como el catálogo.
String nombreCortoDeTipo(TipoEventoCatalogo tipo) => switch (tipo.codigo) {
  'CONTROL_POLICIAL' => 'Policía',
  _ => tipo.nombre,
};

/// «Un momento», «5 min», «10 min», «30 min» (RN-01.4, D-09).
String nombreCortoDeTiempo(TiempoReporte tiempo) =>
    tiempo.minutos == 0 ? 'Un momento' : '${tiempo.minutos} min';

/// Dónde va el evento: en la posición del conductor o en el punto marcado, con su distancia.
class _DondeOcurrio extends StatelessWidget {
  final ReporteController reporte;
  final bool posicion;

  const _DondeOcurrio({required this.reporte, required this.posicion});

  @override
  Widget build(BuildContext context) {
    final punto = reporte.puntoManual;
    final mapa = context.read<MapController>().posicion;
    final String texto;
    if (punto == null) {
      texto = posicion
          ? 'En tu ubicación. Toca el mapa para marcar otro punto cercano.'
          : 'Toca el mapa donde ocurrió el evento.';
    } else if (mapa != null) {
      texto = 'En el punto marcado, a ${textoDistancia(distanciaM(punto, mapa).round())} de ti.';
    } else {
      texto = 'En el punto marcado.';
    }
    return Row(
      children: [
        Icon(
          punto == null ? Icons.my_location : Icons.place,
          size: 16,
          color: punto == null ? ColoresChuro.azul : ColoresChuro.rojo,
        ),
        const SizedBox(width: 6),
        Expanded(
          child: Text(
            texto,
            key: const Key('donde-ocurrio'),
            style: const TextStyle(fontSize: 12.5, color: ColoresChuro.textoSecundario),
          ),
        ),
      ],
    );
  }
}

class _Etiqueta extends StatelessWidget {
  final String texto;

  const _Etiqueta(this.texto);

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 4),
    child: Text(texto, style: const TextStyle(fontSize: 13, color: ColoresChuro.etiqueta)),
  );
}

class _Mensaje extends StatelessWidget {
  final ReporteController reporte;

  const _Mensaje({required this.reporte});

  @override
  Widget build(BuildContext context) {
    final esError = reporte.estado == EstadoReporte.rechazado ||
        reporte.estado == EstadoReporte.sinRed ||
        reporte.estado == EstadoReporte.fueraDeAlcance;
    final color = esError ? Theme.of(context).colorScheme.error : Theme.of(context).colorScheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          Icon(esError ? Icons.error_outline : Icons.info_outline, color: color, size: 18),
          const SizedBox(width: 6),
          Expanded(
            child: Text(reporte.mensaje!, key: const Key('mensaje-reporte'), style: TextStyle(color: color)),
          ),
        ],
      ),
    );
  }
}
