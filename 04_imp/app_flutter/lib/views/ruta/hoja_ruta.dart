import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/combustible_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../controllers/reporte_controller.dart';
import '../../controllers/ruta_controller.dart';
import '../../models/coordenada.dart';
import '../../models/ruta.dart';
import '../events/detalle_evento.dart';
import '../navegacion/navegacion_activa_screen.dart';
import '../tema/tema.dart';

/// La hoja del lugar elegido (#191, diseño «Destino seleccionado»): su nombre, la distancia y el tiempo de la ruta
/// desde la posición del conductor, y los botones «Notificar», «Ir» y «Cómo llegar». La ruta se pide al abrirla, así
/// que tocar otro lugar la vuelve a pedir.
class HojaLugar extends StatefulWidget {
  final Coordenada punto;
  final String? nombre;
  final Coordenada? posicion;

  const HojaLugar({super.key, required this.punto, this.nombre, this.posicion});

  @override
  State<HojaLugar> createState() => _HojaLugarState();
}

class _HojaLugarState extends State<HojaLugar> {
  @override
  void initState() {
    super.initState();
    _prever();
  }

  @override
  void didUpdateWidget(HojaLugar anterior) {
    super.didUpdateWidget(anterior);
    if (anterior.punto != widget.punto) {
      _prever();
    } else if (anterior.posicion == null && widget.posicion != null) {
      // Llegó la posición del conductor: si la ruta esperaba por ella, se pide ahora.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        final ruta = context.read<RutaController>();
        if (ruta.sinUbicacion) ruta.reintentar(widget.posicion);
      });
    }
  }

  void _prever() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<RutaController>().prever(widget.punto, widget.posicion);
    });
  }

  @override
  Widget build(BuildContext context) {
    final ruta = context.watch<RutaController>();
    final mapa = context.read<MapController>();
    final calculada = ruta.destino == widget.punto ? ruta.ruta : null;
    return Column(
      key: const Key('hoja-lugar'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _Encabezado(
          nombre: widget.nombre ?? 'Punto elegido',
          ruta: calculada,
          calculando: ruta.calculando,
          alCerrar: () {
            ruta.limpiar();
            mapa.limpiarPunto();
          },
          tooltipCerrar: 'Quitar el lugar',
        ),
        _AvisoRuta(ruta: ruta, posicion: widget.posicion),
        Align(
          alignment: Alignment.centerRight,
          child: FilledButton.icon(
            key: const Key('notificar-lugar'),
            style: FilledButton.styleFrom(
              backgroundColor: ColoresChuro.rojoSuave,
              minimumSize: const Size(0, 32),
              padding: const EdgeInsets.symmetric(horizontal: 12),
              textStyle: const TextStyle(fontFamily: fuenteChuro, fontSize: 14),
            ),
            // RN-01.5 (#191): el evento va en este lugar, si está a 1 km o menos del conductor.
            onPressed: () => context.read<ReporteController>().empezar(punto: widget.punto),
            icon: const Icon(Icons.notifications, size: 18),
            label: const Text('Notificar'),
          ),
        ),
        const SizedBox(height: 8),
        _BarraBotones(
          izquierda: FilledButton(
            key: const Key('ir-lugar'),
            style: _estiloBoton,
            onPressed: calculada == null ? null : () => iniciarNavegacion(context),
            child: const Text('Ir'),
          ),
          derecha: FilledButton.icon(
            key: const Key('como-llegar-lugar'),
            style: _estiloBoton,
            onPressed: calculada == null ? null : ruta.mostrar,
            icon: const Icon(Icons.directions_outlined, size: 20),
            label: const Text('Cómo llegar'),
          ),
        ),
      ],
    );
  }
}

/// «Cómo llegar» en el mapa principal (#191, diseño «Ruta calculada»): la ruta se ve en el mapa y la hoja trae su
/// distancia, su tiempo, las paradas, «Iniciar» y «Otra parada». El origen es siempre la posición del conductor.
class HojaRuta extends StatelessWidget {
  final String? nombre;
  final Coordenada? posicion;

  const HojaRuta({super.key, this.nombre, this.posicion});

  @override
  Widget build(BuildContext context) {
    final ruta = context.watch<RutaController>();
    final calculada = ruta.ruta;
    final esquema = Theme.of(context).colorScheme;
    return Column(
      key: const Key('hoja-ruta'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _Encabezado(
          nombre: nombre ?? 'Punto elegido',
          ruta: calculada,
          calculando: ruta.calculando,
          // Vuelve a la hoja del lugar.
          alCerrar: () => ruta.ocultar(posicion),
          tooltipCerrar: 'Cerrar la ruta',
        ),
        if (ruta.esDeEjemplo)
          Text(
            'Ruta de ejemplo: muestra siempre el mismo viaje de prueba.',
            style: TextStyle(color: esquema.tertiary, fontSize: 12),
          ),
        if (ruta.paradas.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Wrap(
              spacing: 6,
              runSpacing: 4,
              children: [
                for (var i = 0; i < ruta.paradas.length; i++)
                  InputChip(
                    key: Key('parada-$i'),
                    avatar: Icon(Icons.more_vert, color: Colors.orange.shade700),
                    label: Text('Parada ${i + 1}'),
                    deleteButtonTooltipMessage: 'Quitar parada ${i + 1}',
                    onDeleted: () => ruta.quitarParada(i, posicion),
                  ),
              ],
            ),
          ),
        _AvisoRuta(ruta: ruta, posicion: posicion),
        const SizedBox(height: 10),
        _BarraBotones(
          izquierda: FilledButton(
            key: const Key('iniciar-navegacion'),
            style: _estiloBoton,
            // RN-03.17: «Iniciar» pone la ruta Activa y abre la Navegación activa.
            onPressed: calculada == null ? null : () => iniciarNavegacion(context),
            child: const Text('Iniciar'),
          ),
          derecha: FilledButton.icon(
            key: const Key('otra-parada'),
            style: _estiloBoton,
            onPressed: ruta.puedeAgregarParada && !ruta.calculando ? ruta.elegirParada : null,
            icon: const Icon(Icons.add_circle_outline, size: 20),
            label: const Text('Otra parada'),
          ),
        ),
      ],
    );
  }
}

/// «Toca el mapa para agregar la parada», arriba del mapa mientras se elige.
class AvisoElegirParada extends StatelessWidget {
  const AvisoElegirParada({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      key: const Key('aviso-elegir-parada'),
      child: ListTile(
        dense: true,
        leading: const Icon(Icons.touch_app),
        title: const Text('Toca el mapa para agregar la parada'),
        trailing: TextButton(
          onPressed: context.read<RutaController>().cancelarParada,
          child: const Text('Cancelar'),
        ),
      ),
    );
  }
}

/// RN-03.17 (#191): la ruta pasa a Activa y se abre la Navegación. Al volver no queda nada elegido en el mapa.
Future<void> iniciarNavegacion(BuildContext context) async {
  final ruta = context.read<RutaController>();
  final mapa = context.read<MapController>();
  final calculada = ruta.ruta;
  final destino = ruta.destino;
  if (calculada == null || destino == null) return;
  final navegador = Navigator.of(context);
  await navegador.push(
    NavegacionActivaScreen.ruta(navegador.context, ruta: calculada, destino: destino, paradas: ruta.paradas),
  );
  ruta.limpiar();
  mapa.limpiarPunto();
}

/// D-24 y D-47: el lugar queda elegido en el mapa, con su nombre, y se muestra su ruta («Cómo llegar»).
void abrirComoLlegar(BuildContext context, Coordenada destino, String nombre) {
  final mapa = _elegirEnElMapa(context, destino, nombre);
  final ruta = context.read<RutaController>();
  ruta.prever(destino, mapa.posicion);
  ruta.mostrar();
}

/// «Ir» desde otra pantalla (por ejemplo, la lista de surtidores): el lugar queda elegido, se calcula la ruta y, si
/// hay, se abre directamente la Navegación. Si no hay, queda la hoja del lugar con el aviso.
Future<void> irA(BuildContext context, Coordenada destino, String nombre) async {
  final navegador = Navigator.of(context);
  final mapa = _elegirEnElMapa(context, destino, nombre);
  final ruta = context.read<RutaController>();
  await ruta.prever(destino, mapa.posicion);
  if (!navegador.mounted || ruta.destino != destino || ruta.ruta == null) return;
  if (!navegador.context.mounted) return;
  await iniciarNavegacion(navegador.context);
}

MapController _elegirEnElMapa(BuildContext context, Coordenada destino, String nombre) {
  final mapa = context.read<MapController>()..elegirLugar(destino, nombre);
  context.read<CombustibleController>().cerrarFicha();
  context.read<NavigationController>().selectTab(AppTab.map.index);
  return mapa;
}

final ButtonStyle _estiloBoton = FilledButton.styleFrom(
  minimumSize: const Size(0, 36),
  padding: const EdgeInsets.symmetric(horizontal: 18),
);

/// El nombre del lugar con la distancia al lado y, debajo, el auto con el tiempo, como en el diseño.
class _Encabezado extends StatelessWidget {
  final String nombre;
  final RutaCalculada? ruta;
  final bool calculando;
  final VoidCallback alCerrar;
  final String tooltipCerrar;

  const _Encabezado({
    required this.nombre,
    required this.ruta,
    required this.calculando,
    required this.alCerrar,
    required this.tooltipCerrar,
  });

  @override
  Widget build(BuildContext context) {
    const gris = TextStyle(fontSize: 13, color: ColoresChuro.textoSecundario);
    final r = ruta;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text.rich(
                TextSpan(
                  children: [
                    TextSpan(text: nombre, style: const TextStyle(fontSize: 20, color: Colors.black)),
                    if (r != null) TextSpan(text: '  ${textoDistancia(r.distanciaM)}', style: gris),
                  ],
                ),
                key: const Key('nombre-lugar'),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 2),
              if (r != null)
                Row(
                  key: const Key('tiempo-distancia'),
                  children: [
                    const Icon(Icons.directions_car, size: 16, color: ColoresChuro.textoSecundario),
                    const SizedBox(width: 4),
                    Text(textoTiempo(r.tiempoS), style: gris),
                  ],
                )
              else if (calculando)
                const Row(
                  children: [
                    SizedBox.square(dimension: 12, child: CircularProgressIndicator(strokeWidth: 1.5)),
                    SizedBox(width: 6),
                    Text('Calculando la ruta…', style: gris),
                  ],
                ),
            ],
          ),
        ),
        BotonCerrarHoja(alTocar: alCerrar, tooltip: tooltipCerrar),
      ],
    );
  }
}

/// El aviso (sin ruta, punto inválido, sin ubicación) o el error con «Reintentar» (RN-03.15).
class _AvisoRuta extends StatelessWidget {
  final RutaController ruta;
  final Coordenada? posicion;

  const _AvisoRuta({required this.ruta, required this.posicion});

  @override
  Widget build(BuildContext context) {
    final esquema = Theme.of(context).colorScheme;
    final error = ruta.error;
    final aviso = ruta.aviso;
    if (error == null && aviso == null) return const SizedBox.shrink();
    final esError = error != null;
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          Icon(
            esError ? Icons.error_outline : Icons.info_outline,
            size: 18,
            color: esError ? esquema.error : ColoresChuro.textoSecundario,
          ),
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              error ?? aviso!,
              key: const Key('aviso-ruta'),
              style: TextStyle(fontSize: 13, color: esError ? esquema.error : ColoresChuro.textoSecundario),
            ),
          ),
          if (esError || ruta.sinUbicacion)
            TextButton(onPressed: () => ruta.reintentar(posicion), child: const Text('Reintentar')),
        ],
      ),
    );
  }
}

/// La franja con borde de abajo de la hoja, con un botón a cada lado.
class _BarraBotones extends StatelessWidget {
  final Widget izquierda;
  final Widget derecha;

  const _BarraBotones({required this.izquierda, required this.derecha});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      decoration: BoxDecoration(
        border: Border.all(color: ColoresChuro.borde),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(children: [izquierda, const Spacer(), derecha]),
    );
  }
}
