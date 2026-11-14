import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/controllers/map_controller.dart';
import 'package:app_flutter/controllers/navegacion_controller.dart';
import 'package:app_flutter/models/coordenada.dart';
import 'package:app_flutter/models/navegacion.dart';
import 'package:app_flutter/models/ruta.dart';

import 'dobles/dobles_mapa.dart';
import 'dobles/rutas_de_navegacion.dart';

void main() {
  group('geometría', () {
    test('distancia y rumbo en Tarija', () {
      expect(distanciaM(esquinaA, esquinaB), closeTo(300, 1));
      expect(rumbo(esquinaA, esquinaB), closeTo(90, 0.5));
      expect(rumbo(esquinaB, esquinaC), closeTo(180, 0.5));
      expect(cambioDeRumbo(90, 180), 90);
      expect(cambioDeRumbo(350, 10), 20);
      expect(cambioDeRumbo(10, 350), -20);
    });

    test('proyecta una posición sobre el trazado', () {
      final t = Trazado(rutaEnL.trazado);
      expect(t.longitudM, closeTo(500, 1));
      final p = t.proyectar(desplazar(esquinaA, esteM: 120, norteM: 8));
      expect(p.recorridoM, closeTo(120, 1));
      expect(p.distanciaM, closeTo(8, 0.5));
      expect(distanciaM(t.puntoEn(400), desplazar(esquinaB, surM: 100)), lessThan(0.5));
    });

    test('D-22: el cambio de calle y el sentido del giro salen del trazado', () {
      final m = planificarManiobras(Trazado(rutaEnL.trazado), rutaEnL.tramos);
      expect(m, hasLength(1));
      expect(m.single.calle, 'Avenida Las Américas');
      expect(m.single.giro, Giro.derecha);
      expect(m.single.recorridoM, closeTo(300, 1));
      expect(m.single.primerTramo, 2);
      expect(textoManiobra(m.single), 'Gira a la derecha en Avenida Las Américas');

      final izquierda = planificarManiobras(Trazado(rutaHaciaElNorte.trazado), rutaHaciaElNorte.tramos);
      expect(izquierda.single.giro, Giro.izquierda);
    });

    test('una calle que cambia de nombre sin girar es seguir derecho; la curva más cerrada es la esquina', () {
      final m = planificarManiobras(Trazado(rutaRecta.trazado), rutaRecta.tramos);
      expect(m.single.giro, Giro.recto);
      expect(textoManiobra(m.single), 'Sigue derecho por Calle Colón');
      expect(planificarManiobras(Trazado(rutaEnL.trazado), const [TramoRuta('a', 'Una sola calle')]), isEmpty);
    });

    test('más calles que esquinas: los cambios se reparten a lo largo de la ruta', () {
      final m = planificarManiobras(Trazado([esquinaA, esquinaB]), const [
        TramoRuta('a', 'Uno'), TramoRuta('b', 'Dos'), TramoRuta('c', 'Tres'),
      ]);
      expect(m.map((x) => x.calle), ['Dos', 'Tres']);
      expect(m.map((x) => x.recorridoM.round()), [100, 200]);
    });
  });

  group('controlador de navegación', () {
    late UbicacionFalsa ubicacion;
    late MapController mapa;

    Future<NavegacionController> iniciar({RutaCalculada? ruta, List<Coordenada> paradas = const []}) async {
      ubicacion = UbicacionFalsa();
      mapa = MapController(ubicacion);
      await mapa.activarUbicacion();
      final r = ruta ?? rutaEnL;
      final nav = NavegacionController(ruta: r, destino: r.trazado.last, paradas: paradas, mapa: mapa);
      addTearDown(() {
        nav.dispose();
        mapa.dispose();
      });
      return nav;
    }

    Future<void> ir(Coordenada p) async {
      ubicacion.emitir(p);
      await Future<void>.delayed(Duration.zero);
    }

    test('CP-107: sigue la posición del conductor y lo que falta', () async {
      final nav = await iniciar();
      expect(nav.activa, isTrue);
      expect(nav.siguienteManiobra!.calle, 'Avenida Las Américas');
      expect(nav.distanciaASiguienteM, closeTo(300, 1));
      expect(nav.calleActual, 'Calle Sucre');
      expect(nav.distanciaRestanteM, closeTo(500, 1));
      expect(nav.tiempoRestanteS, rutaEnL.tiempoS);

      await ir(desplazar(esquinaA, esteM: 250, norteM: 5));
      expect(nav.posicion, isNotNull);
      expect(nav.distanciaASiguienteM, closeTo(50, 1));
      expect(nav.distanciaRestanteM, closeTo(250, 1));
      expect(nav.tiempoRestanteS, (rutaEnL.tiempoS / 2).round());
    });

    test('D-12: guarda los tramos que faltan, desde la calle por la que va', () async {
      final nav = await iniciar();
      expect(nav.tramosQueFaltan, ['osm:1:0:ida', 'osm:1:1:ida', 'osm:2:0:ida']);
      await ir(desplazar(esquinaA, esteM: 200));
      expect(nav.tramosQueFaltan, ['osm:1:0:ida', 'osm:1:1:ida', 'osm:2:0:ida']);
      await ir(desplazar(esquinaB, surM: 60));
      expect(nav.calleActual, 'Avenida Las Américas');
      expect(nav.siguienteManiobra, isNull);
      expect(nav.tramosQueFaltan, ['osm:2:0:ida']);
    });

    test('un salto del GPS hacia atrás no hace retroceder la ruta', () async {
      final nav = await iniciar();
      await ir(desplazar(esquinaA, esteM: 280));
      await ir(desplazar(esquinaA, esteM: 240));
      expect(nav.recorridoM, closeTo(280, 1));
    });

    test('CP-155: a menos de 30 m del destino la navegación termina y la ruta se borra', () async {
      final nav = await iniciar();
      await ir(desplazar(esquinaC, norteM: 35));
      expect(nav.activa, isTrue);
      await ir(desplazar(esquinaC, norteM: 25));
      expect(nav.estado, EstadoNavegacion.terminada);
      expect(nav.fin, FinNavegacion.llegada);
      expect(nav.ruta, isNull);
      expect(nav.tramosQueFaltan, isEmpty);
      // Terminada, ya no sigue posiciones.
      await ir(desplazar(esquinaA, esteM: 10));
      expect(nav.estado, EstadoNavegacion.terminada);
    });

    test('«Cancelar» termina la ruta y la borra', () async {
      final nav = await iniciar();
      nav.cancelar();
      expect(nav.estado, EstadoNavegacion.terminada);
      expect(nav.fin, FinNavegacion.cancelada);
      expect(nav.ruta, isNull);
    });

    test('las paradas que faltan se quitan al pasar a menos de 30 m', () async {
      final parada = desplazar(esquinaA, esteM: 150);
      final nav = await iniciar(paradas: [parada, esquinaB]);
      await ir(desplazar(parada, norteM: 10));
      expect(nav.paradasQueFaltan, [esquinaB]);
    });

    test('si ya había una posición al iniciar, la usa', () async {
      ubicacion = UbicacionFalsa();
      mapa = MapController(ubicacion);
      await mapa.activarUbicacion();
      ubicacion.emitir(desplazar(esquinaA, esteM: 100));
      await Future<void>.delayed(Duration.zero);
      final nav = NavegacionController(ruta: rutaEnL, destino: esquinaC, mapa: mapa);
      addTearDown(() {
        nav.dispose();
        mapa.dispose();
      });
      expect(nav.recorridoM, closeTo(100, 1));
    });
  });
}
