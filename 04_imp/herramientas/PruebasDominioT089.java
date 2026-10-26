// Pruebas unitarias autónomas. Las geometrías 0..12 son FORMAS SINTÉTICAS
// para verificar algoritmos; NO son geografía de Tarija ni datos de producción.
import bo.edu.ucb.churoviaje.dominio.catalogo.*;
import bo.edu.ucb.churoviaje.dominio.ubicacion.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class PruebasDominioT089 {
    private static int comprobaciones = 0;
    private static void correcto(boolean condicion, String titulo) {
        comprobaciones++;
        if (!condicion) throw new AssertionError("FALLÓ: " + titulo);
    }
    private static void rechazar(Runnable ejecucion, String titulo) {
        comprobaciones++;
        try { ejecucion.run(); }
        catch (IllegalArgumentException | NullPointerException ex) { return; }
        throw new AssertionError("FALLÓ (se aceptó dato inválido): " + titulo);
    }
    private static Coordenada pos(double lat, double lon) { return new Coordenada(lat,lon); }
    private static Anillo anillo(double[][] puntos) {
        List<Coordenada> coords = new ArrayList<>();
        for (double[] p : puntos) coords.add(pos(p[0],p[1]));
        return new Anillo(coords);
    }
    private static Anillo cuadrado(double minLat, double minLon, double maxLat, double maxLon) {
        return anillo(new double[][]{{minLat,minLon},{minLat,maxLon},{maxLat,maxLon},
                {maxLat,minLon},{minLat,minLon}});
    }
    public static void main(String[] args) {
        Anillo contorno = cuadrado(0,0,4,4);
        Anillo hueco = cuadrado(1,1,3,3);
        Poligono a = new Poligono(contorno, List.of(hueco));
        Poligono b = new Poligono(cuadrado(10,10,12,12), List.of());
        CoberturaManchaUrbana cobertura = new CoberturaManchaUrbana(List.of(a,b));
        correcto(cobertura.cantidadPoligonos()==2,"multipolígono");
        correcto(cobertura.contiene(0.5,0.5),"interior");
        correcto(!cobertura.contiene(2,2),"interior hueco excluido");
        correcto(cobertura.contiene(1,2),"borde hueco incluido");
        correcto(cobertura.contiene(0,2),"borde exterior incluido");
        correcto(cobertura.contiene(12,12),"vértice segundo polígono");
        correcto(cobertura.contiene(11,11),"segundo polígono");
        correcto(!cobertura.contiene(5,5),"punto exterior");
        correcto(!cobertura.contiene(4.001,4.001),"fuera por poco");
        correcto(cobertura.contiene(3,2.01),"borde de hueco incluido");
        correcto(!cobertura.contiene(2.01,2.01),"dentro del hueco excluido");
        Ubicacion desdeGPS = new Ubicacion(0.5,0.5, OrigenUbicacion.GPS);
        Ubicacion fuera = new Ubicacion(5,5, OrigenUbicacion.AJUSTE_MANUAL);
        correcto(desdeGPS.estaDentroDeManchaUrbana(cobertura),"ubicación lat/lon");
        correcto(!fuera.estaDentroDeManchaUrbana(cobertura),"ubicación exterior");
        desdeGPS.validarParaReporte(cobertura);
        correcto(desdeGPS.validarParaRuta(cobertura)==ResultadoUbicacionRuta.VALIDA,"ruta válida");
        correcto(fuera.validarParaRuta(cobertura)==ResultadoUbicacionRuta.PUNTO_INVALIDO,"ruta inválida sin excepción 422");
        correcto("Origen o destino inválido".equals(fuera.validarParaRuta(cobertura).mensajeContrato()),"mensaje contrato");
        correcto(desdeGPS.validarParaRuta(cobertura).mensajeContrato()==null,"ruta válida sin mensaje de error");
        ValidadorPuntosRuta validadorRuta = new ValidadorPuntosRuta(cobertura);
        correcto(validadorRuta.validar(desdeGPS,desdeGPS,List.of())==ResultadoUbicacionRuta.VALIDA,"sin paradas válida");
        correcto(validadorRuta.validar(desdeGPS,desdeGPS,List.of(fuera))==ResultadoUbicacionRuta.PUNTO_INVALIDO,"parada fuera invalida la ruta");
        correcto(validadorRuta.validar(desdeGPS,fuera,List.of())==ResultadoUbicacionRuta.PUNTO_INVALIDO,"destino fuera invalida ruta");
        correcto(validadorRuta.validar(fuera,desdeGPS,List.of())==ResultadoUbicacionRuta.PUNTO_INVALIDO,"origen fuera invalida ruta");
        rechazar(()->validadorRuta.validar(desdeGPS,desdeGPS,java.util.Arrays.asList((Ubicacion)null)),"parada nula no aceptada");
        comprobaciones++;
        try { fuera.validarParaReporte(cobertura); throw new AssertionError("Reporte inválido aceptado"); }
        catch (UbicacionFueraCoberturaException ex) {
            if (!"UBICACION_FUERA_COBERTURA".equals(ex.getCodigo())) throw new AssertionError(ex);
        }
        rechazar(()->pos(91,0),"latitud mayor a 90");
        rechazar(()->pos(0,-181),"longitud menor que -180");
        rechazar(()->pos(Double.NaN,0),"NaN");
        rechazar(()->pos(0,Double.POSITIVE_INFINITY),"Infinity");
        rechazar(()->new Ubicacion(2,2,null),"origen ubicación obligatorio");
        rechazar(()->new CoberturaManchaUrbana(List.of()),"cobertura vacía");
        rechazar(()->anillo(new double[][] {{0,0},{2,2},{0,2},{2,0},{0,0}}),"anillo autointersectado");
        rechazar(()->anillo(new double[][] {{0,0},{2,0},{0,2}}),"anillo abierto");
        rechazar(()->new Poligono(contorno,List.of(cuadrado(3,3,6,6))),"hueco fuera");
        rechazar(()->new Poligono(contorno,List.of(cuadrado(1,1,3,3),cuadrado(2,2,3.5,3.5))),"huecos solapados");
        correcto("moto mendez".equals(ClaveAlias.normalizar(" MÓTO   MÉNDEZ! ")),"normalizar diacríticos y símbolos");
        correcto("el molle".equals(ClaveAlias.normalizar("El Molle")),"clave canónica");
        rechazar(()->ClaveAlias.normalizar("--"),"clave vacía");
        AtomicReference<String> observado = new AtomicReference<>();
        BuscadorSurtidores puerto = clave->{ observado.set(clave);return "san geronimo".equals(clave)?Optional.of(123L):Optional.empty();};
        ServicioBusquedaSurtidores servicio = new ServicioBusquedaSurtidores(puerto);
        correcto(servicio.buscarPorNombreOAlias("SÁN GERÓNIMO").equals(Optional.of(123L)),"nombre normalizado");
        correcto("san geronimo".equals(observado.get()),"repo recibe clave normalizada");
        correcto(servicio.buscarPorNombreOAlias("SIN COINCIDENCIA").isEmpty(),"sin coincidencia sin inventar");
        correcto(!new TipoCombustible("DESCONOCIDO","Desconocido").disponibleParaConductor(),"desconocido no es combustible de recarga");
        correcto(new TipoCombustible("DIESEL","Diesel").disponibleParaConductor(),"diesel apto");
        correcto(new TipoEvento("BLOQUEO","Bloqueo",null,60,true).color()==null,"color P-06 realmente nulo");
        rechazar(()->new TipoEvento("BLOQUEO","Bloqueo","no_aprobado",60,true),"hex inválido");
        correcto(pos(-21.5355,-64.7296).latitud()==-21.5355,"contrato latitud primero");
        correcto(pos(-21.5355,-64.7296).longitud()==-64.7296,"contrato longitud segunda");
        System.out.println("RESULTADO: " + comprobaciones + " comprobaciones de dominio Java: OK");
    }
}
