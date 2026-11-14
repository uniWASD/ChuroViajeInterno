import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/map_controller.dart';
import '../../controllers/ruta_controller.dart';
import '../../models/coordenada.dart';
import '../../models/ruta.dart';
import '../../services/rutas/servicio_rutas.dart';
import '../map/map_screen.dart';
import '../map/mapa_base.dart';
import '../navegacion/navegacion_activa_screen.dart';

/// Vista Cómo llegar (CU-03): origen, destino y paradas; la ruta con su tiempo y su distancia; el botón para
/// iniciar. No hay buscador de lugares: los puntos se eligen tocando el mapa (P-19, D-24).
class ComoLlegarScreen extends StatelessWidget {
  const ComoLlegarScreen({super.key});

  static Route<void> ruta(BuildContext context, {Coordenada? destinoInicial}) {
    final servicio = context.read<ServicioRutas>();
    return MaterialPageRoute(
      builder: (_) => ChangeNotifierProvider(
        create: (_) => RutaController(servicio, destinoInicial: destinoInicial),
        child: const ComoLlegarScreen(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final construirMapa = context.read<ConstructorMapa>();
    final mapa = context.watch<MapController>();
    final ruta = context.watch<RutaController>();
    return Scaffold(
      appBar: AppBar(title: const Text('Cómo llegar')),
      body: Column(
        children: [
          Expanded(
            child: Stack(
              children: [
                Positioned.fill(
                  child: KeyedSubtree(
                    key: ValueKey(mapa.intentoMapa),
                    child: construirMapa(
                      context,
                      DatosMapa(
                        centro: mapa.posicion ?? centroTarija,
                        mostrarUbicacion: mapa.siguiendoUbicacion,
                        marcas: [
                          if (ruta.origenMarcado != null)
                            MarcaMapa(ruta.origenMarcado!, TipoMarca.origen),
                          for (final p in ruta.paradas)
                            MarcaMapa(p, TipoMarca.parada),
                          if (ruta.destino != null)
                            MarcaMapa(ruta.destino!, TipoMarca.destino),
                        ],
                        trazado: ruta.ruta?.trazado ?? const [],
                        alTocar: ruta.tocarMapa,
                        alFallar: mapa.informarErrorMapa,
                      ),
                    ),
                  ),
                ),
                Positioned(
                  top: 8,
                  left: 8,
                  right: 8,
                  child: _Indicacion(eleccion: ruta.eleccion),
                ),
                if (mapa.errorMapa != null)
                  Positioned.fill(child: AvisoErrorMapa(controller: mapa)),
              ],
            ),
          ),
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * 0.5,
            ),
            child: Material(
              elevation: 8,
              child: SafeArea(
                top: false,
                child: SingleChildScrollView(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                  child: _Panel(ruta: ruta, posicion: mapa.posicion),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Indicacion extends StatelessWidget {
  final EleccionPunto eleccion;

  const _Indicacion({required this.eleccion});

  @override
  Widget build(BuildContext context) {
    final texto = switch (eleccion) {
      EleccionPunto.destino => 'Toca el mapa para elegir el destino',
      EleccionPunto.parada => 'Toca el mapa para agregar la parada',
      EleccionPunto.origen => 'Toca el mapa para marcar el origen',
    };
    return Center(
      child: Chip(
        avatar: const Icon(Icons.touch_app, size: 18),
        label: Text(texto),
      ),
    );
  }
}

class _Panel extends StatelessWidget {
  final RutaController ruta;
  final Coordenada? posicion;

  const _Panel({required this.ruta, required this.posicion});

  @override
  Widget build(BuildContext context) {
    final esquema = Theme.of(context).colorScheme;
    final resultado = ruta.ruta;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (ruta.esDeEjemplo)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              'Ruta de ejemplo: hasta que la app inicie sesión, muestra siempre el mismo viaje de prueba.',
              style: TextStyle(color: esquema.tertiary, fontSize: 12),
            ),
          ),
        _Fila(
          icono: Icons.trip_origin,
          color: Colors.green.shade700,
          titulo: 'Origen',
          texto: ruta.origenMarcado != null
              ? _coordenadas(ruta.origenMarcado!)
              : posicion != null
              ? 'Tu ubicación'
              : 'Sin ubicación: marca el origen en el mapa',
          accion: ruta.origenMarcado != null
              ? TextButton(
                  onPressed: ruta.usarPosicionActual,
                  child: const Text('Usar mi ubicación'),
                )
              : TextButton(
                  onPressed: () => ruta.elegir(EleccionPunto.origen),
                  child: const Text('Marcar'),
                ),
        ),
        for (var i = 0; i < ruta.paradas.length; i++)
          _Fila(
            icono: Icons.more_vert,
            color: Colors.orange.shade700,
            titulo: 'Parada ${i + 1}',
            texto: _coordenadas(ruta.paradas[i]),
            accion: IconButton(
              tooltip: 'Quitar parada ${i + 1}',
              icon: const Icon(Icons.close),
              onPressed: () => ruta.quitarParada(i),
            ),
          ),
        _Fila(
          icono: Icons.place,
          color: Colors.red,
          titulo: 'Destino',
          texto: ruta.destino != null
              ? _coordenadas(ruta.destino!)
              : 'Toca el mapa para elegirlo',
          accion: TextButton(
            onPressed: () => ruta.elegir(EleccionPunto.destino),
            child: const Text('Cambiar'),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: ruta.puedeAgregarParada
                    ? () => ruta.elegir(EleccionPunto.parada)
                    : null,
                icon: const Icon(Icons.add_location_alt_outlined),
                label: const Text('Agregar parada'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: FilledButton.icon(
                onPressed: ruta.puedeCalcular
                    ? () => ruta.calcular(posicionActual: posicion)
                    : null,
                icon: ruta.calculando
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.alt_route),
                label: const Text('Calcular ruta'),
              ),
            ),
          ],
        ),
        if (resultado != null) ...[
          const SizedBox(height: 12),
          Text(
            '${textoTiempo(resultado.tiempoS)} · ${textoDistancia(resultado.distanciaM)}',
            key: const Key('tiempo-distancia'),
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          // RN-03.17: «Iniciar» pone la ruta Activa y abre la Navegación activa en lugar de Cómo llegar.
          FilledButton.icon(
            key: const Key('iniciar-navegacion'),
            onPressed: () => Navigator.of(context).pushReplacement(
              NavegacionActivaScreen.ruta(
                context,
                ruta: resultado,
                destino: ruta.destino!,
                paradas: ruta.paradas,
              ),
            ),
            icon: const Icon(Icons.navigation),
            label: const Text('Iniciar'),
          ),
        ],
        if (ruta.aviso != null) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: esquema.secondaryContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              ruta.aviso!,
              style: TextStyle(color: esquema.onSecondaryContainer),
            ),
          ),
        ],
        if (ruta.error != null) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: esquema.errorContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Icon(Icons.error_outline, color: esquema.onErrorContainer),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    ruta.error!,
                    style: TextStyle(color: esquema.onErrorContainer),
                  ),
                ),
                TextButton(
                  onPressed: () => ruta.calcular(posicionActual: posicion),
                  child: const Text('Reintentar'),
                ),
              ],
            ),
          ),
        ],
      ],
    );
  }

  static String _coordenadas(Coordenada c) =>
      '${c.latitud.toStringAsFixed(5)}, ${c.longitud.toStringAsFixed(5)}';
}

class _Fila extends StatelessWidget {
  final IconData icono;
  final Color color;
  final String titulo;
  final String texto;
  final Widget accion;

  const _Fila({
    required this.icono,
    required this.color,
    required this.titulo,
    required this.texto,
    required this.accion,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: Icon(icono, color: color),
      title: Text(titulo),
      subtitle: Text(texto),
      trailing: accion,
    );
  }
}
