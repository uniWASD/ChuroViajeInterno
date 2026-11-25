import 'package:flutter/foundation.dart';

/// Hace de a uno los dibujos de una capa del mapa. Cada dibujo borra la capa y la vuelve a llenar esperando al SDK
/// de Mapbox; si llegaba otro cambio en medio (por ejemplo, dos consultas de eventos seguidas o una posición nueva
/// en la Navegación), los dos borraban y los dos creaban, y quedaban marcadores o líneas repetidas que no se iban.
///
/// Con esta clase, mientras un dibujo está en curso los pedidos nuevos no empiezan otro: se espera a que termine y
/// se dibuja una sola vez más, con los datos de ese momento. Los pedidos intermedios se saltan porque el último ya
/// los incluye.
class DibujoEnSerie {
  Future<void>? _enCurso;
  Future<void> Function()? _siguiente;

  /// true mientras hay un dibujo en curso.
  bool get ocupado => _enCurso != null;

  /// Pide un dibujo. El futuro termina cuando la capa quedó con lo último que se pidió.
  Future<void> pedir(Future<void> Function() dibujar) {
    final enCurso = _enCurso;
    if (enCurso != null) {
      _siguiente = dibujar;
      return enCurso;
    }
    return _enCurso = _correr(dibujar);
  }

  Future<void> _correr(Future<void> Function() primero) async {
    Future<void> Function()? actual = primero;
    try {
      while (actual != null) {
        _siguiente = null;
        try {
          await actual();
        } on Object catch (e) {
          // Un dibujo que falla no debe trabar los siguientes.
          debugPrint('No se pudo dibujar en el mapa: $e');
        }
        actual = _siguiente;
      }
    } finally {
      _enCurso = null;
    }
  }
}
