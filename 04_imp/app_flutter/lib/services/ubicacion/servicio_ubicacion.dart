import 'dart:async';

import 'package:geolocator/geolocator.dart';

import '../../models/coordenada.dart';

enum PermisoUbicacion {
  concedido,
  denegado,

  /// El conductor lo negó y el sistema ya no vuelve a preguntar: solo se cambia en los ajustes.
  denegadoSiempre,

  /// El GPS del teléfono está apagado.
  servicioApagado,
}

/// La ubicación del conductor. Se pide solo con la app abierta en pantalla (RNF-13).
abstract class ServicioUbicacion {
  Future<PermisoUbicacion> pedirPermiso();

  /// Posiciones mientras alguien escuche; al cancelar la suscripción se deja de pedir la ubicación.
  Stream<Coordenada> posiciones();

  Future<void> abrirAjustes();
}

class ServicioUbicacionGeolocator implements ServicioUbicacion {
  @override
  Future<PermisoUbicacion> pedirPermiso() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      return PermisoUbicacion.servicioApagado;
    }
    var permiso = await Geolocator.checkPermission();
    if (permiso == LocationPermission.denied) {
      permiso = await Geolocator.requestPermission();
    }
    switch (permiso) {
      case LocationPermission.always:
      case LocationPermission.whileInUse:
        return PermisoUbicacion.concedido;
      case LocationPermission.deniedForever:
        return PermisoUbicacion.denegadoSiempre;
      case LocationPermission.denied:
      case LocationPermission.unableToDetermine:
        return PermisoUbicacion.denegado;
    }
  }

  @override
  Stream<Coordenada> posiciones() {
    return Geolocator.getPositionStream(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 5,
      ),
    ).map((p) => Coordenada(p.latitude, p.longitude));
  }

  @override
  Future<void> abrirAjustes() async {
    await Geolocator.openAppSettings();
  }
}
