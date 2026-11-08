import 'package:connectivity_plus/connectivity_plus.dart';

/// Avisa cuando el teléfono vuelve a tener red, para reenviar los reportes guardados (RN-01.13).
abstract class Conectividad {
  /// true cada vez que hay alguna red (wifi, datos móviles, etc.); false cuando no hay ninguna.
  Stream<bool> get cambios;
}

class ConectividadPlus implements Conectividad {
  final Connectivity _conectividad;

  ConectividadPlus([Connectivity? conectividad])
    : _conectividad = conectividad ?? Connectivity();

  @override
  Stream<bool> get cambios => _conectividad.onConnectivityChanged.map(
    (redes) => redes.any((r) => r != ConnectivityResult.none),
  );
}
