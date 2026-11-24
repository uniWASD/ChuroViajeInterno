import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../controllers/reporte_controller.dart';
import '../controllers/sesion_controller.dart';
import 'main/main_screen.dart';
import 'sesion/inicio_sesion_screen.dart';

/// Sin sesión solo se ve Inicio de sesión; con sesión, el mapa (RN-08.2).
class Inicio extends StatelessWidget {
  const Inicio({super.key});

  @override
  Widget build(BuildContext context) {
    final estado = context.select<SesionController, EstadoSesion>((s) => s.estado);
    final reporteAbierto = _reporteAbierto(context);
    if (estado == EstadoSesion.sinSesion && !reporteAbierto) {
      // Si la sesión se perdió con otra vista abierta encima (por ejemplo, la Navegación), se cierra.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted) Navigator.of(context).popUntil((ruta) => ruta.isFirst);
      });
    }
    return switch (estado) {
      EstadoSesion.cargando => const CargandoSesion(),
      // La hoja «Notificar evento» queda abierta para no perder lo que el conductor eligió: al enviar, se cierra sola
      // y recién entonces se pide iniciar sesión (RN-01.16).
      EstadoSesion.sinSesion when reporteAbierto => const MainScreen(),
      EstadoSesion.sinSesion => const InicioSesionScreen(),
      EstadoSesion.conSesion => const MainScreen(),
    };
  }

  static bool _reporteAbierto(BuildContext context) {
    try {
      return context.select<ReporteController, bool>((r) => r.abierto);
    } on ProviderNotFoundException {
      return false;
    }
  }
}
