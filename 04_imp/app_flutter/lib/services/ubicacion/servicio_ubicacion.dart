import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';

import '../../models/coordenada.dart';
import '../../models/lectura_ubicacion.dart';
import '../../models/navegacion.dart' show distanciaM;

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

  /// Lecturas para la telemetría (RN-04.1), aunque el conductor esté detenido: con posición, precisión y momento.
  Stream<LecturaUbicacion> lecturas();

  Future<void> abrirAjustes();
}

/// La ubicación con el GPS del teléfono. El mapa y la telemetría comparten una sola lectura del GPS, pedida con los
/// ajustes de la telemetría (cada 3 segundos y sin filtro de distancia, P-13).
///
/// Antes cada uno pedía la suya con ajustes distintos, pero el paquete geolocator devuelve el mismo flujo a la segunda
/// llamada y descarta sus ajustes: como el mapa empieza primero, la telemetría recibía las lecturas con el filtro de
/// 5 metros del mapa, y un auto detenido en la congestión dejaba de mandarlas. Ahora el filtro de 5 metros del mapa se
/// aplica aquí, sobre las mismas lecturas.
class ServicioUbicacionGeolocator implements ServicioUbicacion {
  /// El mapa solo se entera de una posición nueva cuando el conductor se movió esta distancia (como antes).
  static const double filtroMapaM = 5;

  final Stream<LecturaUbicacion> Function() _gps;
  Stream<LecturaUbicacion>? _compartido;

  /// [gps] reemplaza al GPS del teléfono en las pruebas.
  ServicioUbicacionGeolocator({@visibleForTesting Stream<LecturaUbicacion> Function()? gps})
    : _gps = gps ?? lecturasDelGps;

  /// La única lectura del GPS: empieza con el primero que escucha y se cierra cuando ya nadie escucha.
  Stream<LecturaUbicacion> get _lecturasCompartidas => _compartido ??= _gps().asBroadcastStream(
    onCancel: (suscripcion) {
      suscripcion.cancel();
      _compartido = null;
    },
  );

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
  Stream<Coordenada> posiciones() =>
      soloAlMoverse(_lecturasCompartidas.map((l) => l.posicion), filtroMapaM);

  @override
  Stream<LecturaUbicacion> lecturas() => _lecturasCompartidas;

  @override
  Future<void> abrirAjustes() async {
    await Geolocator.openAppSettings();
  }
}

/// Las lecturas del GPS del teléfono, con los ajustes de la telemetría: sin filtro de distancia, porque un auto
/// detenido en la congestión también tiene que mandar lecturas (P-13), y en Android una cada 3 segundos.
Stream<LecturaUbicacion> lecturasDelGps() {
  final LocationSettings ajustes = defaultTargetPlatform == TargetPlatform.android
      ? AndroidSettings(accuracy: LocationAccuracy.high, distanceFilter: 0, intervalDuration: const Duration(seconds: 3))
      : const LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: 0);
  return Geolocator.getPositionStream(locationSettings: ajustes)
      .map((p) => LecturaUbicacion(Coordenada(p.latitude, p.longitude), p.accuracy, p.timestamp));
}

/// Deja pasar una posición solo si está a [metros] o más de la última que dejó pasar, como el filtro de distancia
/// del GPS. Los errores pasan igual.
Stream<Coordenada> soloAlMoverse(Stream<Coordenada> origen, double metros) {
  Coordenada? ultima;
  return origen.where((c) {
    final antes = ultima;
    if (antes != null && distanciaM(antes, c) < metros) return false;
    ultima = c;
    return true;
  });
}
