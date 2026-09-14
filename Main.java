import java.util.*;

public class Main {

    public static void main(String[] args) {
        System.out.println();
        System.out.println("==========================================================");
        System.out.println("   PRUEBAS DE OPTIMIZACION DE RUTAS CON TRAFICO");
        System.out.println("==========================================================");

        caso1_sinTrafico();
        caso2_rutaCortaCongestionada();
        caso3_traficoLeveVsDesvio();
        caso4_coordenadasGPS();

        System.out.println("==========================================================");
        System.out.println("   CONCLUSION: El algoritmo evalua distancia + trafico");
        System.out.println("   y elige la ruta mas rapida, no la mas corta.");
        System.out.println("==========================================================\n");
    }

    // ── CASO 1: Sin trafico, elige la mas corta ──────────────────────────────

    static void caso1_sinTrafico() {
        System.out.println("\n----------------------------------------------------------");
        System.out.println("  CASO 1: Sin trafico (ambas rutas fluidas)");
        System.out.println("----------------------------------------------------------");

        Graph g = new Graph();
        Graph.Node a = new Graph.Node("A", 0, 0, "Origen");
        Graph.Node b = new Graph.Node("B", 0, 0, "Intermedio B");
        Graph.Node c = new Graph.Node("C", 0, 0, "Intermedio C");
        Graph.Node d = new Graph.Node("D", 0, 0, "Destino");

        // Ruta corta: A -> B -> D = 4 km, fluida
        Graph.Edge e1 = g.addEdge(a, b, "Calle Directa", 2000);
        e1.setTraffic(Graph.TrafficLevel.FLUIDO);
        Graph.Edge e2 = g.addEdge(b, d, "Calle Directa", 2000);
        e2.setTraffic(Graph.TrafficLevel.FLUIDO);

        // Ruta larga: A -> C -> D = 10 km, fluida
        Graph.Edge e3 = g.addEdge(a, c, "Circunvalacion", 5000);
        e3.setTraffic(Graph.TrafficLevel.FLUIDO);
        Graph.Edge e4 = g.addEdge(c, d, "Circunvalacion", 5000);
        e4.setTraffic(Graph.TrafficLevel.FLUIDO);

        System.out.println("  Ruta 1: A -> B -> D = 4 km | Trafico: FLUIDO   | Costo: " + (e1.getCost() + e2.getCost()));
        System.out.println("  Ruta 2: A -> C -> D = 10 km | Trafico: FLUIDO   | Costo: " + (e3.getCost() + e4.getCost()));

        DStarLite ds = new DStarLite(g);
        ds.init(a, d);
        List<Graph.Node> path = ds.getPath();

        System.out.println("\n  >> Ruta elegida: " + formatPath(path));
        System.out.println("  >> Motivo: Sin trafico, elige la mas corta (4 km).");
    }

    // ── CASO 2: Ruta corta congestionada vs ruta larga libre ─────────────────
    //    (Escenario principal del diagrama: 2 km vs 4 km)

    static void caso2_rutaCortaCongestionada() {
        System.out.println("\n----------------------------------------------------------");
        System.out.println("  CASO 2: Ruta corta CONGESTIONADA vs ruta larga LIBRE");
        System.out.println("  (Escenario del diagrama: 2 km vs 4 km)");
        System.out.println("----------------------------------------------------------");

        Graph g = new Graph();
        Graph.Node a = new Graph.Node("A", 0, 0, "Punto A");
        Graph.Node z = new Graph.Node("Z", 0, 0, "Zona Z (congestion)");
        Graph.Node b = new Graph.Node("B", 0, 0, "Punto B");

        // Ruta corta: A -> Z -> B = 2 km, MUY CONGESTIONADA
        Graph.Edge e1 = g.addEdge(a, z, "Calle Central", 1000);
        e1.setTraffic(Graph.TrafficLevel.MUY_CONGESTIONADO);
        Graph.Edge e2 = g.addEdge(z, b, "Calle Central", 1000);
        e2.setTraffic(Graph.TrafficLevel.MUY_CONGESTIONADO);

        // Ruta larga: A -> B directo = 4 km, FLUIDA
        Graph.Node r = new Graph.Node("R", 0, 0, "Rodeo");
        Graph.Edge e3 = g.addEdge(a, r, "Ruta Alternativa", 2000);
        e3.setTraffic(Graph.TrafficLevel.FLUIDO);
        Graph.Edge e4 = g.addEdge(r, b, "Ruta Alternativa", 2000);
        e4.setTraffic(Graph.TrafficLevel.FLUIDO);

        double costoRuta1 = e1.getCost() + e2.getCost();
        double costoRuta2 = e3.getCost() + e4.getCost();

        System.out.println("  Ruta 1: A -> Z -> B = 2 km | Trafico: MUY CONGESTIONADO (x4.0) | Costo: " + costoRuta1);
        System.out.println("  Ruta 2: A -> R -> B = 4 km | Trafico: FLUIDO (x1.0)             | Costo: " + costoRuta2);
        System.out.println();
        System.out.println("  Calculo: 2000m x 4.0 = " + costoRuta1 + " vs 4000m x 1.0 = " + costoRuta2);

        DStarLite ds = new DStarLite(g);
        ds.init(a, b);
        List<Graph.Node> path = ds.getPath();

        System.out.println("\n  >> Ruta elegida: " + formatPath(path));
        System.out.println("  >> Motivo: Aunque la ruta de 2 km es mas corta, su costo");
        System.out.println("     con trafico (" + costoRuta1 + ") es mayor que la de 4 km (" + costoRuta2 + ").");
        System.out.println("     El algoritmo evita la congestion.");
    }

    // ── CASO 3: Trafico leve no justifica un desvio enorme ───────────────────

    static void caso3_traficoLeveVsDesvio() {
        System.out.println("\n----------------------------------------------------------");
        System.out.println("  CASO 3: Trafico MODERADO vs desvio exagerado");
        System.out.println("----------------------------------------------------------");

        Graph g = new Graph();
        Graph.Node a = new Graph.Node("A", 0, 0, "Casa");
        Graph.Node b = new Graph.Node("B", 0, 0, "Esquina");
        Graph.Node c = new Graph.Node("C", 0, 0, "Autopista lejana");
        Graph.Node d = new Graph.Node("D", 0, 0, "Supermercado");

        // Ruta directa: 500 metros, trafico moderado
        Graph.Edge e1 = g.addEdge(a, b, "Calle Local", 250);
        e1.setTraffic(Graph.TrafficLevel.MODERADO);
        Graph.Edge e2 = g.addEdge(b, d, "Calle Local", 250);
        e2.setTraffic(Graph.TrafficLevel.MODERADO);

        // Desvio: 5 km, fluido
        Graph.Edge e3 = g.addEdge(a, c, "Desvio Norte", 2500);
        e3.setTraffic(Graph.TrafficLevel.FLUIDO);
        Graph.Edge e4 = g.addEdge(c, d, "Desvio Sur", 2500);
        e4.setTraffic(Graph.TrafficLevel.FLUIDO);

        double costoRuta1 = e1.getCost() + e2.getCost();
        double costoRuta2 = e3.getCost() + e4.getCost();

        System.out.println("  Ruta 1: A -> B -> D = 0.5 km | Trafico: MODERADO (x1.5) | Costo: " + costoRuta1);
        System.out.println("  Ruta 2: A -> C -> D = 5.0 km | Trafico: FLUIDO (x1.0)   | Costo: " + costoRuta2);
        System.out.println();
        System.out.println("  Calculo: 500m x 1.5 = " + costoRuta1 + " vs 5000m x 1.0 = " + costoRuta2);

        DStarLite ds = new DStarLite(g);
        ds.init(a, d);
        List<Graph.Node> path = ds.getPath();

        System.out.println("\n  >> Ruta elegida: " + formatPath(path));
        System.out.println("  >> Motivo: El trafico moderado no justifica manejar 5 km");
        System.out.println("     de mas. Es mas rapido aguantar un poco de trafico.");
    }

    // ── CASO 4: Coordenadas GPS reales (Camiri) ─────────────────────────────

    static void caso4_coordenadasGPS() {
        System.out.println("\n----------------------------------------------------------");
        System.out.println("  CASO 4: Coordenadas GPS reales (Camiri)");
        System.out.println("----------------------------------------------------------");

        Graph g = new Graph();
        Graph.Node origen  = new Graph.Node("O",  -21.5334, -64.7373, "Inicio GPS");
        Graph.Node inter1  = new Graph.Node("I1", -21.5343, -64.7344, "Calle 25 de Mayo");
        Graph.Node inter2  = new Graph.Node("I2", -21.5335, -64.7253, "Avenida Comercio");
        Graph.Node destino = new Graph.Node("D",  -21.5384, -64.7442, "Destino GPS");

        double distO_I1 = Graph.haversineMeters(-21.5334, -64.7373, -21.5343, -64.7344);
        double distI1_D = Graph.haversineMeters(-21.5343, -64.7344, -21.5384, -64.7442);
        double distO_I2 = Graph.haversineMeters(-21.5334, -64.7373, -21.5335, -64.7253);
        double distI2_D = Graph.haversineMeters(-21.5335, -64.7253, -21.5384, -64.7442);

        Graph.Edge e1 = g.addEdge(origen, inter1, "25 de Mayo", distO_I1);
        e1.setTraffic(Graph.TrafficLevel.MUY_CONGESTIONADO);
        Graph.Edge e2 = g.addEdge(inter1, destino, "25 de Mayo Sur", distI1_D);
        e2.setTraffic(Graph.TrafficLevel.MUY_CONGESTIONADO);

        Graph.Edge e3 = g.addEdge(origen, inter2, "Av. Comercio", distO_I2);
        e3.setTraffic(Graph.TrafficLevel.FLUIDO);
        Graph.Edge e4 = g.addEdge(inter2, destino, "Av. Comercio Sur", distI2_D);
        e4.setTraffic(Graph.TrafficLevel.MODERADO);

        double costoRuta1 = e1.getCost() + e2.getCost();
        double costoRuta2 = e3.getCost() + e4.getCost();
        double distRuta1 = distO_I1 + distI1_D;
        double distRuta2 = distO_I2 + distI2_D;

        System.out.println("  Ruta 1 (Centro): " + String.format("%.1f", distRuta1/1000) + " km | MUY CONGESTIONADO | Costo: " + String.format("%.0f", costoRuta1));
        System.out.println("  Ruta 2 (Borde):  " + String.format("%.1f", distRuta2/1000) + " km | FLUIDO/MODERADO   | Costo: " + String.format("%.0f", costoRuta2));

        DStarLite ds = new DStarLite(g);
        ds.init(origen, destino);
        List<Graph.Node> path = ds.getPath();

        System.out.println("\n  >> Ruta elegida: " + formatPath(path));
        System.out.println("  >> Motivo: Las distancias GPS reales + trafico simulado");
        System.out.println("     funcionan correctamente en conjunto.");
    }

    // ── Utilidad ─────────────────────────────────────────────────────────────

    static String formatPath(List<Graph.Node> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(path.get(i).getName());
        }
        return sb.toString();
    }
}
