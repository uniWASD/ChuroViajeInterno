import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Lo que la app guarda en el teléfono: la sesión y los catálogos (RN-08.7, D-06). Las vistas y los controladores
/// usan esta interfaz, y así se prueban sin el almacenamiento del teléfono.
abstract class AlmacenLocal {
  Future<String?> leer(String clave);
  Future<void> guardar(String clave, String valor);
  Future<void> borrar(String clave);

  /// Al cerrar sesión no queda nada guardado.
  Future<void> borrarTodo();
}

/// El almacenamiento seguro del teléfono: Keystore en Android y Keychain en iOS (RN-08.7).
class AlmacenSeguro implements AlmacenLocal {
  final FlutterSecureStorage _almacen;

  AlmacenSeguro([FlutterSecureStorage? almacen])
    : _almacen = almacen ?? const FlutterSecureStorage();

  @override
  Future<String?> leer(String clave) => _almacen.read(key: clave);

  @override
  Future<void> guardar(String clave, String valor) =>
      _almacen.write(key: clave, value: valor);

  @override
  Future<void> borrar(String clave) => _almacen.delete(key: clave);

  @override
  Future<void> borrarTodo() => _almacen.deleteAll();
}
