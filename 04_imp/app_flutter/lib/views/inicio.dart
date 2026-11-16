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
    if (estado == EstadoSesion.sinSesion && !_reporteAbierto(context)) {
      // Si la sesión se perdió con otra vista abierta encima (por ejemplo, Cómo llegar), se cierra. Reportar evento
      // queda abierta para no perder lo que el conductor eligió: al confirmar, se cierra sola (RN-01.16).
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted) Navigator.of(context).popUntil((ruta) => ruta.isFirst);
      });
    }
    return switch (estado) {
      EstadoSesion.cargando => const CargandoSesion(),
      EstadoSesion.sinSesion => const InicioSesionScreen(),
      EstadoSesion.conSesion => const MainScreen(),
    };
  }

  static bool _reporteAbierto(BuildContext context) {
    try {
      return context.read<ReporteController>().abierto;
    } on ProviderNotFoundException {
      return false;
    }
  }
}
