import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/navegacion.dart';
import 'package:app_flutter/models/revision_ruta.dart';

import 'dobles/rutas_de_navegacion.dart';
import 'ruta_alternativa_test.dart' show alternativa, enEscenario;

/// #172 (CP-178): mientras se navega, lo ya recorrido de la ruta desaparece desde la posición proyectada.
void main() {
  final trazado = Trazado(rutaEnL.trazado); // A → +150 m → B (300 m) → +100 m al sur → C (500 m)

  void cerca(Coordenada real, Coordenada esperada) =>
      expect(distanciaM(real, esperada), lessThan(0.5), reason: '$real debería estar en $esperada');

  group('recortarRecorrido', () {
    test('sin recorrer, queda la ruta entera', () {
      expect(recortarRecorrido(trazado, 0), rutaEnL.trazado);
      expect(recortarRecorrido(trazado, -5), rutaEnL.trazado);
    });

    test('a mitad de un segmento empieza en el punto proyectado y sigue con los puntos que faltan', () {
      final falta = recortarRecorrido(trazado, 100); // antes del punto de los 150 m
      expect(falta, hasLength(5));
      cerca(falta.first, desplazar(esquinaA, esteM: 100));
      expect(falta.skip(1), rutaEnL.trazado.skip(1));
    });

    test('justo en un punto del trazado no lo repite', () {
      final falta = recortarRecorrido(trazado, trazado.recorridoHasta(2)); // en la esquina B
      cerca(falta.first, esquinaB);
      expect(falta, hasLength(3));
      expect(falta.skip(1), [desplazar(esquinaB, surM: 100), esquinaC]);
    });

    test('en el último segmento queda solo lo que falta hasta el destino', () {
      final falta = recortarRecorrido(trazado, 450);
      expect(falta, hasLength(2));
      cerca(falta.first, desplazar(esquinaB, surM: 150));
      expect(falta.last, esquinaC);
    });

    test('al final, o pasado el final, no queda nada', () {
      expect(recortarRecorrido(trazado, trazado.longitudM), isEmpty);
      expect(recortarRecorrido(trazado, 9999), isEmpty);
    });

    test('un trazado de un solo punto queda igual', () {
      expect(recortarRecorrido(Trazado([esquinaA]), 10), [esquinaA]);
    });
  });

  group('en la navegación', () {
    test('a cada posición el trazado que se dibuja empieza donde está el conductor', () {
      enEscenario((e) {
        cerca(e.nav.trazadoQueFalta.first, desplazar(esquinaA, esteM: 100));
        e.ir(desplazar(esquinaA, esteM: 250, norteM: 6)); // el GPS lo pone a 6 m de la calle
        cerca(e.nav.trazadoQueFalta.first, desplazar(esquinaA, esteM: 250));
        expect(e.nav.trazadoQueFalta.last, esquinaC);
      });
    });

    test('al aceptar una alternativa se recorta la ruta nueva desde donde está el conductor', () {
      enEscenario((e) {
        e.rutas.revision = RevisionRuta(estado: EstadoRevision.alternativa, eventoId: 153, alternativa: alternativa);
        e.ciclo();
        e.nav.aceptarAlternativa();
        expect(e.nav.trazadoQueFalta.first, alternativa.trazado.first);
        e.ir(desplazar(esquinaA, esteM: 100, surM: 80));
        cerca(e.nav.trazadoQueFalta.first, desplazar(esquinaA, esteM: 100, surM: 80));
        expect(e.nav.trazadoQueFalta.last, esquinaC);
      });
    });

    test('al recalcular por «ruta desactualizada» también se recorta la ruta nueva', () {
      enEscenario((e) {
        e.rutas.revision = const RevisionRuta(estado: EstadoRevision.rutaDesactualizada);
        e.rutas.respuesta = alternativa;
        e.ciclo();
        expect(e.nav.ruta, same(alternativa));
        e.ir(desplazar(esquinaA, esteM: 100, surM: 120));
        cerca(e.nav.trazadoQueFalta.first, desplazar(esquinaA, esteM: 100, surM: 120));
        expect(e.nav.trazadoQueFalta, hasLength(3));
      });
    });
  });
}
