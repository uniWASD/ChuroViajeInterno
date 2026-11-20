import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/eventos_controller.dart';
import '../../models/evento_vial.dart';
import '../tema/tema.dart';
import 'detalle_evento.dart';

/// Pestaña Eventos (CU-02, HU-11, #172): una hoja sobre el mapa con los eventos que reportaron los conductores, su
/// tipo, hace cuánto ocurrieron y si otros conductores los confirmaron. La congestión del tráfico no va en la lista:
/// se ve en el mapa. Abre a media pantalla; se desliza hacia arriba para ver más y
/// hacia abajo para despejar el mapa (queda solo el asa, para volver a subirla). Al tocar un evento la cámara va
/// hasta él y la hoja muestra su detalle, donde se vota si es real o falso.
class EventsScreen extends StatefulWidget {
  const EventsScreen({super.key});

  /// Las alturas donde se acomoda la hoja, como parte de la pantalla: abajo (solo el asa), media y casi entera.
  static const double abajo = 0.045;
  static const double media = 0.5;
  static const double arriba = 0.92;

  @override
  State<EventsScreen> createState() => _EventsScreenState();
}

class _EventsScreenState extends State<EventsScreen> {
  final DraggableScrollableController _hoja = DraggableScrollableController();

  @override
  void dispose() {
    _hoja.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final eventos = context.watch<EventosController>();
    final seleccionado = eventos.seleccionado;
    // El detalle de un evento ocupa solo lo que necesita, abajo, sin tapar el mapa.
    if (seleccionado != null) {
      return Align(
        alignment: Alignment.bottomCenter,
        child: HojaInferior(
          key: const Key('hoja-detalle'),
          child: DetalleEvento(
            evento: seleccionado,
            eventos: eventos,
            conVolver: true,
          ),
        ),
      );
    }
    // La congestión que detecta el tráfico no va en la lista: se ve en el mapa, sobre la calle.
    final lista = [
      for (final e in eventos.eventos)
        if (e.votable) e,
    ]..sort((a, b) => b.momentoOcurrencia.compareTo(a.momentoOcurrencia));
    return DraggableScrollableSheet(
      key: const Key('hoja-eventos'),
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
          boxShadow: [
            BoxShadow(
              color: Color(0x1F000000),
              blurRadius: 14,
              offset: Offset(0, -2),
            ),
          ],
        ),
        child: CustomScrollView(
          controller: desplazamiento,
          slivers: [
            SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: AsaHoja()),
                  if (eventos.sinActualizar)
                    Material(
                      color: Theme.of(context).colorScheme.errorContainer,
                      child: const ListTile(
                        dense: true,
                        leading: Icon(Icons.sync_problem),
                        title: Text(EventosController.mensajeSinActualizar),
                      ),
                    ),
                ],
              ),
            ),
            if (lista.isEmpty)
              const SliverToBoxAdapter(child: _SinEventos())
            else
              SliverPadding(
                padding: const EdgeInsets.only(bottom: 16),
                sliver: SliverList.builder(
                  key: const Key('lista-eventos'),
                  itemCount: lista.length,
                  itemBuilder: (_, i) =>
                      _FilaEvento(evento: lista[i], eventos: eventos),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _SinEventos extends StatelessWidget {
  const _SinEventos();

  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.event_available, size: 64),
          SizedBox(height: 12),
          Text(
            'No hay eventos vigentes por ahora.',
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

/// Una fila como la del diseño: el ícono y el nombre del tipo con hace cuánto ocurrió, la marca de verificación
/// (verde si otros conductores lo confirmaron, gris si no) y el botón de información. Tocar la fila o la «i» abre el
/// detalle y lleva el mapa hasta el evento.
class _FilaEvento extends StatelessWidget {
  final EventoVial evento;
  final EventosController eventos;

  const _FilaEvento({required this.evento, required this.eventos});

  @override
  Widget build(BuildContext context) {
    final tipo = eventos.catalogos?.tipo(evento.tipo);
    void abrir() => eventos.seleccionar(evento.id);
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 3, 4, 3),
      child: Row(
        children: [
          Expanded(
            child: Material(
              color: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
                side: const BorderSide(color: Color(0xFFE4E4E4)),
              ),
              elevation: 2,
              shadowColor: const Color(0x55000000),
              child: InkWell(
                key: Key('evento-${evento.id}'),
                borderRadius: BorderRadius.circular(10),
                onTap: abrir,
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  child: Row(
                    children: [
                      Icon(
                        iconoDeEvento(evento.tipo),
                        color: tipo?.colorFlutter ?? Colors.grey,
                        size: 22,
                      ),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Text(
                          tipo?.nombre ?? evento.tipo,
                          style: const TextStyle(fontSize: 18),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Text(
                        '· ${haceCuantoCorto(evento, DateTime.now())}',
                        style: const TextStyle(
                          fontSize: 13,
                          color: ColoresChuro.textoSecundario,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 4),
          // RN-02.14: verde si está confirmado, gris si todavía no.
          SizedBox(
            width: 26,
            child: MarcaVerificado(confirmado: evento.confirmado),
          ),
          IconButton(
            key: Key('info-${evento.id}'),
            tooltip: 'Ver el detalle',
            visualDensity: VisualDensity.compact,
            icon: const Icon(Icons.info_outline, color: Color(0xFF64A8F0)),
            onPressed: abrir,
          ),
        ],
      ),
    );
  }
}
