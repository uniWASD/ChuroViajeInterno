import java.util.*;

public class DStarLite {
    private final Graph graph;
    private Graph.Node start, goal;
    private final Map<String, Double> g = new HashMap<>();

    public DStarLite(Graph graph) {
        this.graph = graph;
    }

    public void init(Graph.Node start, Graph.Node goal) {
        this.start = start;
        this.goal = goal;
        PriorityQueue<NodeCost> pq = new PriorityQueue<>(Comparator.comparingDouble(nc -> nc.cost));
        g.clear();

        for (Graph.Node n : graph.nodes.values())
            g.put(n.getId(), Double.POSITIVE_INFINITY);
        g.put(start.getId(), 0.0);

        pq.add(new NodeCost(start, 0.0));

        while (!pq.isEmpty()) {
            NodeCost current = pq.poll();
            if (current.cost > g.get(current.node.getId()))
                continue;

            for (Graph.Edge e : graph.getEdgesFrom(current.node)) {
                double newCost = current.cost + e.getCost();
                if (newCost < g.get(e.getTo().getId())) {
                    g.put(e.getTo().getId(), newCost);
                    pq.add(new NodeCost(e.getTo(), newCost));
                }
            }
        }
    }

    public List<Graph.Node> getPath() {
        List<Graph.Node> path = new ArrayList<>();
        if (g.get(goal.getId()) == Double.POSITIVE_INFINITY)
            return path;

        Graph.Node current = goal;
        path.add(current);
        while (!current.equals(start)) {
            Graph.Node bestPrev = null;
            double bestCost = Double.POSITIVE_INFINITY;

            for (Graph.Edge e : graph.getEdgesTo(current)) {
                double eval = g.get(e.getFrom().getId()) + e.getCost();
                if (eval < bestCost + 1e-5) {
                    bestCost = eval;
                    bestPrev = e.getFrom();
                }
            }
            if (bestPrev == null)
                break;
            current = bestPrev;
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    public static class NodeCost {
        public Graph.Node node;
        public double cost;

        public NodeCost(Graph.Node n, double c) {
            this.node = n;
            this.cost = c;
        }
    }
}
