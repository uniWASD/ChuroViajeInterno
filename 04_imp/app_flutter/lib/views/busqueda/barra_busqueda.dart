import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controllers/busqueda_controller.dart';
import '../../controllers/combustible_controller.dart';
import '../../controllers/eventos_controller.dart';
import '../../controllers/map_controller.dart';
import '../../controllers/navigation_controller.dart';
import '../../controllers/sesion_controller.dart';
import '../../models/lugar.dart';
import '../tema/tema.dart';

/// La barra de búsqueda de arriba (D-47, #172): reemplaza al encabezado «ChuroViaje». Busca calles, lugares conocidos
/// y surtidores; al elegir uno, el mapa va hasta él y queda marcado para pedir «Cómo llegar». A la derecha queda el
/// menú de la cuenta. No lleva micrófono: la búsqueda por voz no se implementa.
class BarraBusqueda extends StatefulWidget {
  const BarraBusqueda({super.key});

  /// Alto de la barra, sin los márgenes. Lo usan las vistas que deben quedar debajo.
  static const double alto = 48;

  /// Lo que ocupa la barra desde el borde de abajo de la barra de estado, con sus márgenes.
  static const double espacio = alto + 16;

  static const String pista = 'Buscar calles y lugares';

  @override
  State<BarraBusqueda> createState() => _BarraBusquedaState();
}

class _BarraBusquedaState extends State<BarraBusqueda> {
  final TextEditingController _campo = TextEditingController();
  final FocusNode _foco = FocusNode();
  BusquedaController? _busqueda;

  @override
  void initState() {
    super.initState();
    _foco.addListener(() {
      if (_foco.hasFocus) _busqueda?.abrir();
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final busqueda = context.read<BusquedaController>();
    if (!identical(busqueda, _busqueda)) {
      _busqueda?.removeListener(_alCambiar);
      _busqueda = busqueda..addListener(_alCambiar);
    }
  }

  /// Si la búsqueda se cerró desde afuera (el gesto de atrás), se esconde el teclado.
  void _alCambiar() {
    if (!_busqueda!.abierta && _foco.hasFocus) _foco.unfocus();
    if (_busqueda!.texto.isEmpty && _campo.text.isNotEmpty) _campo.clear();
  }

  @override
  void dispose() {
    _busqueda?.removeListener(_alCambiar);
    _campo.dispose();
    _foco.dispose();
    super.dispose();
  }

  void _elegir(LugarEncontrado lugar) {
    _foco.unfocus();
    context.read<BusquedaController>()
      ..limpiar()
      ..cerrar();
    context.read<EventosController>().cerrarFicha();
    // Si había un surtidor abierto en el Mapa, su hoja tapaba la del lugar elegido.
    context.read<CombustibleController>().cerrarFicha();
    context.read<NavigationController>().selectTab(AppTab.map.index);
    context.read<MapController>().elegirLugar(lugar.ubicacion, lugar.nombre);
  }

  @override
  Widget build(BuildContext context) {
    final busqueda = context.watch<BusquedaController>();
    final conLista =
        busqueda.abierta && BusquedaController.alcanza(busqueda.texto);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Material(
          key: const Key('barra-busqueda'),
          color: Colors.white,
          elevation: 3,
          shadowColor: Colors.black38,
          borderRadius: BorderRadius.circular(21),
          child: SizedBox(
            height: BarraBusqueda.alto,
            child: Row(
              children: [
                const SizedBox(width: 14),
                const Icon(Icons.search, color: ColoresChuro.textoSecundario),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    key: const Key('campo-busqueda'),
                    controller: _campo,
                    focusNode: _foco,
                    textInputAction: TextInputAction.search,
                    onChanged: busqueda.escribir,
                    onSubmitted: (_) {
                      if (busqueda.resultados.isNotEmpty) {
                        _elegir(busqueda.resultados.first);
                      }
                    },
                    onTapOutside: (_) => _foco.unfocus(),
                    decoration: const InputDecoration(
                      hintText: BarraBusqueda.pista,
                      border: InputBorder.none,
                      enabledBorder: InputBorder.none,
                      focusedBorder: InputBorder.none,
                      filled: false,
                      isDense: true,
                      contentPadding: EdgeInsets.zero,
                    ),
                  ),
                ),
                if (busqueda.texto.isNotEmpty)
                  IconButton(
                    key: const Key('borrar-busqueda'),
                    tooltip: 'Borrar',
                    icon: const Icon(
                      Icons.close,
                      color: ColoresChuro.textoSecundario,
                    ),
                    onPressed: () {
                      _campo.clear();
                      busqueda.limpiar();
                    },
                  ),
                const MenuCuenta(),
                const SizedBox(width: 4),
              ],
            ),
          ),
        ),
        if (conLista) ...[
          const SizedBox(height: 6),
          _Resultados(busqueda: busqueda, alElegir: _elegir),
        ],
      ],
    );
  }
}

class _Resultados extends StatelessWidget {
  final BusquedaController busqueda;
  final ValueChanged<LugarEncontrado> alElegir;

  const _Resultados({required this.busqueda, required this.alElegir});

  @override
  Widget build(BuildContext context) {
    final Widget contenido;
    if (busqueda.resultados.isEmpty && busqueda.buscando) {
      contenido = const Padding(
        padding: EdgeInsets.all(16),
        child: Center(
          child: SizedBox.square(
            dimension: 24,
            child: CircularProgressIndicator(strokeWidth: 2.5),
          ),
        ),
      );
    } else if (busqueda.resultados.isEmpty) {
      contenido = ListTile(
        leading: const Icon(Icons.search_off),
        title: Text(busqueda.mensaje ?? BusquedaController.sinResultados),
      );
    } else {
      contenido = ListView.separated(
        shrinkWrap: true,
        padding: const EdgeInsets.symmetric(vertical: 4),
        itemCount: busqueda.resultados.length,
        separatorBuilder: (_, _) => const Divider(height: 1, indent: 56),
        itemBuilder: (context, i) {
          final lugar = busqueda.resultados[i];
          final distancia = lugar.distanciaM;
          return ListTile(
            key: Key('resultado-$i'),
            leading: Icon(
              iconoDeLugar(lugar.categoria),
              color: ColoresChuro.azul,
            ),
            title: Text(
              lugar.nombre,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              distancia == null
                  ? lugar.nombreCategoria
                  : '${lugar.nombreCategoria} · ${distanciaCorta(distancia)}',
              style: const TextStyle(color: ColoresChuro.textoSecundario),
            ),
            onTap: () => alElegir(lugar),
          );
        },
      );
    }
    return Material(
      key: const Key('resultados-busqueda'),
      color: Colors.white,
      elevation: 3,
      shadowColor: Colors.black38,
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.45,
        ),
        child: contenido,
      ),
    );
  }
}

/// El ícono de cada categoría de la búsqueda.
IconData iconoDeLugar(String categoria) => switch (categoria) {
  'CALLE' => Icons.signpost_outlined,
  'PLAZA' => Icons.location_city_outlined,
  'PARQUE' => Icons.park_outlined,
  'SALUD' => Icons.local_hospital_outlined,
  'EDUCACION' => Icons.school_outlined,
  'MERCADO' => Icons.storefront_outlined,
  'GOBIERNO' => Icons.account_balance_outlined,
  'TRANSPORTE' => Icons.directions_bus_outlined,
  'TURISMO' => Icons.photo_camera_outlined,
  'HOTEL' => Icons.hotel_outlined,
  'BANCO' => Icons.payments_outlined,
  'COMIDA' => Icons.restaurant_outlined,
  'DEPORTE' => Icons.sports_soccer_outlined,
  'RELIGION' => Icons.church_outlined,
  'COMERCIO' => Icons.shopping_bag_outlined,
  'SURTIDOR' => Icons.local_gas_station_outlined,
  _ => Icons.place_outlined,
};

/// Menú de la cuenta, a la derecha de la barra de búsqueda: «Cerrar sesión».
class MenuCuenta extends StatelessWidget {
  const MenuCuenta({super.key});

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      key: const Key('menu-cuenta'),
      tooltip: 'Cuenta',
      icon: const Icon(
        Icons.account_circle_outlined,
        color: ColoresChuro.textoSecundario,
        size: 28,
      ),
      onSelected: (opcion) {
        if (opcion == 'cerrar') context.read<SesionController>().cerrarSesion();
      },
      itemBuilder: (_) => const [
        PopupMenuItem(
          value: 'cerrar',
          child: ListTile(
            leading: Icon(Icons.logout),
            title: Text('Cerrar sesión'),
            contentPadding: EdgeInsets.zero,
          ),
        ),
      ],
    );
  }
}
