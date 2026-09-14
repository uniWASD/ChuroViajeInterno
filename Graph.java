import java.util.*;

/**
 * Modelo de datos del grafo de calles para optimización de rutas.
 * Contiene: Node, Edge, TrafficLevel y cálculo geográfico Haversine.
 */
public class Graph {

    // ══════════════════════════════════════════════════════════════════════════
    //  NODO (Intersección / Punto en el mapa)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Representa un punto/intersección en el mapa con coordenadas GPS.
     */
    public static class Node {
        private final String id;
        private final double lat, lon;
        private final String name;

        public Node(String id, double lat, double lon, String name) {
            this.id = id; this.lat = lat; this.lon = lon;
            this.name = (name != null) ? name : id;
        }

        public String getId()   { return id;   }
        public double getLat()  { return lat;  }
        public double getLon()  { return lon;  }
        public String getName() { return name; }

        /**
         * Distancia real en metros hacia otro nodo (Haversine).
         */
        public double distanceTo(Node o) {
            return haversineMeters(lat, lon, o.lat, o.lon);
        }

        @Override public boolean equals(Object o) {
            return (o instanceof Node) && id.equals(((Node) o).id);
        }
        @Override public int hashCode() { return id.hashCode(); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NIVEL DE TRÁFICO
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Clasificación del estado de tráfico de una vía.
     * El multiplicador penaliza el costo del tramo proporcionalmente.
     */
    public enum TrafficLevel {
        FLUIDO          ("Tráfico Fluido",           1.0),
        MODERADO        ("Tráfico Moderado",         1.5),
        CONGESTIONADO   ("Tráfico Congestionado",    2.5),
        MUY_CONGESTIONADO("Tráfico Muy Congestionado", 4.0);

        public final String label;
        public final double multiplier;

        TrafficLevel(String label, double multiplier) {
            this.label = label; this.multiplier = multiplier;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ARISTA (Tramo de calle)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Representa un tramo de calle entre dos nodos.
     * Costo efectivo = distancia base × multiplicador de tráfico.
     */
    public static class Edge {
        private final Node from, to;
        private final String streetName;
        private final double baseWeight;
        private TrafficLevel trafficLevel = TrafficLevel.FLUIDO;
        private double trafficMultiplier = 1.0;

        public Edge(Node from, Node to, String name, double weight) {
            this.from = from; this.to = to;
            this.streetName = name;
            this.baseWeight = weight;
        }

        public Node getTo() { return to; }
        public Node getFrom() { return from; }
        public String getStreetName() { return streetName; }
        public double getBaseWeight() { return baseWeight; }

        public TrafficLevel getTrafficLevel() { return trafficLevel; }

        public void setTraffic(TrafficLevel lvl) {
            this.trafficLevel = lvl;
            this.trafficMultiplier = lvl.multiplier;
        }

        /**
         * Costo efectivo = distancia × multiplicador de tráfico.
         * Métrica que usa DStarLite para decidir la ruta óptima.
         */
        public double getCost() {
            return baseWeight * trafficMultiplier;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GRAFO (Estructura de datos)
    // ══════════════════════════════════════════════════════════════════════════

    final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<Edge>> edgesFrom = new HashMap<>();
    private final Map<String, List<Edge>> edgesTo = new HashMap<>();

    public void addNode(Node n) { nodes.put(n.getId(), n); }
    public Node getNode(String id) { return nodes.get(id); }

    public Edge addEdge(Node from, Node to, String name, double weight) {
        nodes.putIfAbsent(from.getId(), from);
        nodes.putIfAbsent(to.getId(), to);
        Edge e = new Edge(from, to, name, weight);
        edgesFrom.computeIfAbsent(from.getId(), k -> new ArrayList<>()).add(e);
        edgesTo.computeIfAbsent(to.getId(), k -> new ArrayList<>()).add(e);
        return e;
    }

    public List<Edge> getEdgesFrom(Node n) { return edgesFrom.getOrDefault(n.getId(), Collections.emptyList()); }
    public List<Edge> getEdgesTo(Node n) { return edgesTo.getOrDefault(n.getId(), Collections.emptyList()); }

    // ══════════════════════════════════════════════════════════════════════════
    //  UTILIDAD GEOGRÁFICA (Haversine)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Distancia en metros entre dos coordenadas GPS (fórmula de Haversine).
     * Radio de la Tierra = 6,371,000 metros.
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2)) * Math.sin(dLon/2)*Math.sin(dLon/2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
