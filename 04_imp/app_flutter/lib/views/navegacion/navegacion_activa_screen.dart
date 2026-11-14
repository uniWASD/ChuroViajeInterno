import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/map_controller.dart';
import '../../controllers/navegacion_controller.dart';
import '../../models/coordenada.dart';
import '../../models/navegacion.dart';
import '../../models/ruta.dart';
import '../map/map_screen.dart';
import '../map/mapa_base.dart';

/// Vista Navegación activa (CU-03): sigue la posición del conductor y muestra la calle que sigue con el sentido del
/// giro, y la distancia y el tiempo que faltan (RN-03.17, D-22). Se abre al tocar «Iniciar» en Cómo llegar.
class NavegacionActivaScreen extends StatelessWidget {
  const NavegacionActivaScreen({super.key});

  static Route<void> ruta(
    BuildContext context, {
    required RutaCalculada ruta,
    required Coordenada destino,
    List<Coordenada> paradas = const [],
  }) {
    final mapa = context.read<MapController>();
    return MaterialPageRoute(
      builder: (_) => ChangeNotifierProvider(
        create: (_) => NavegacionController(ruta: ruta, destino: destino, paradas: paradas, mapa: mapa),
        child: const NavegacionActivaScreen(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final construirMapa = context.read<ConstructorMapa>();
    final mapa = context.watch<MapController>();
    final nav = context.watch<NavegacionController>();
    return PopScope(
      // Volver atrás también termina la ruta: una ruta Activa sin su vista no se sigue (D-05).
      onPopInvokedWithResult: (salio, _) {
        if (salio) nav.cancelar();
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Navegación')),
        body: Stack(
          children: [
            Positioned.fill(
              child: KeyedSubtree(
                key: ValueKey(mapa.intentoMapa),
                child: construirMapa(
                  context,
                  DatosMapa(
                    centro: mapa.posicion ?? nav.destino,
                    mostrarUbicacion: mapa.siguiendoUbicacion,
                    seguirUbicacion: true,
                    marcas: [
                      for (final p in nav.paradasQueFaltan) MarcaMapa(p, TipoMarca.parada),
                      MarcaMapa(nav.destino, TipoMarca.destino),
                    ],
                    trazado: nav.ruta?.trazado ?? const [],
                    alTocar: (_) {},
                    alFallar: mapa.informarErrorMapa,
                  ),
                ),
              ),
            ),
            if (mapa.errorMapa != null) Positioned.fill(child: AvisoErrorMapa(controller: mapa)),
            Positioned(
              top: 8,
              left: 8,
              right: 8,
              child: nav.activa ? _Indicacion(nav: nav) : const SizedBox.shrink(),
            ),
            Positioned(
              left: 8,
              right: 8,
              bottom: 8,
              child: SafeArea(
                top: false,
                child: nav.activa ? _Resumen(nav: nav, sinUbicacion: mapa.posicion == null) : _Fin(nav: nav),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// La calle que sigue y el sentido del giro; si no hay más cambios, la calle hasta el destino.
class _Indicacion extends StatelessWidget {
  final NavegacionController nav;

  const _Indicacion({required this.nav});

  @override
  Widget build(BuildContext context) {
    final esquema = Theme.of(context).colorScheme;
    final maniobra = nav.siguienteManiobra;
    final distancia = nav.distanciaASiguienteM;
    final (icono, titulo, detalle) = maniobra == null
        ? (Icons.flag, 'Sigue hasta el destino', nav.calleActual)
        : (
            switch (maniobra.giro) {
              Giro.derecha => Icons.turn_right,
              Giro.izquierda => Icons.turn_left,
              Giro.recto => Icons.straight,
            },
            textoManiobra(maniobra),
            'En ${textoDistanciaCorta(distancia!)}',
          );
    return Card(
      key: const Key('indicacion-navegacion'),
      color: esquema.primaryContainer,
      child: ListTile(
        leading: Icon(icono, size: 40, color: esquema.onPrimaryContainer),
        title: Text(
          titulo,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(color: esquema.onPrimaryContainer),
        ),
        subtitle: detalle == null ? null : Text(detalle, style: TextStyle(color: esquema.onPrimaryContainer)),
      ),
    );
  }
}

/// Lo que falta y el botón «Cancelar».
class _Resumen extends StatelessWidget {
  final NavegacionController nav;
  final bool sinUbicacion;

  const _Resumen({required this.nav, required this.sinUbicacion});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${textoTiempo(nav.tiempoRestanteS)} · ${textoDistancia(nav.distanciaRestanteM.round())}',
                    key: const Key('falta-navegacion'),
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                OutlinedButton(
                  onPressed: () {
                    nav.cancelar();
                    Navigator.of(context).maybePop();
                  },
                  child: const Text('Cancelar'),
                ),
              ],
            ),
            if (sinUbicacion)
              const Padding(
                padding: EdgeInsets.only(top: 4),
                child: Text('Esperando tu ubicación…'),
              ),
          ],
        ),
      ),
    );
  }
}

/// La ruta terminó al llegar: el conductor vuelve al mapa.
class _Fin extends StatelessWidget {
  final NavegacionController nav;

  const _Fin({required this.nav});

  @override
  Widget build(BuildContext context) {
    return Card(
      key: const Key('fin-navegacion'),
      child: ListTile(
        leading: const Icon(Icons.where_to_vote, color: Colors.green, size: 36),
        title: Text(nav.fin == FinNavegacion.llegada ? 'Llegaste a tu destino' : 'Navegación terminada'),
        trailing: FilledButton(
          onPressed: () => Navigator.of(context).maybePop(),
          child: const Text('Volver al mapa'),
        ),
      ),
    );
  }
}
