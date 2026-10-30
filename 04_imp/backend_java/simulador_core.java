import java.util.Scanner;

public class simulador_core {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String linea = scanner.nextLine().trim();
            if (linea.isEmpty()) continue;

            String id = extraerValor(linea, "id");
            String operacion = extraerValor(linea, "operacion");

            if ("consultarEstado".equals(operacion)) {
                System.out.println("{\"id\":\"" + id + "\",\"estado\":\"cargada\"}");
            } else if ("simular_demora".equals(operacion)) {
                Thread.sleep(4000);
                System.out.println("{\"id\":\"" + id + "\",\"estado\":\"completado_con_demora\"}");
            } else if ("simular_caida".equals(operacion)) {
                System.err.println("Error fatal simulado en el nucleo C++");
                System.exit(1);
            } else {
                System.out.println("{\"id\":\"" + id + "\",\"resultado\":\"ok\"}");
            }
            System.out.flush();
        }
    }

    private static String extraerValor(String json, String clave) {
        String busqueda = "\"" + clave + "\":\"";
        int inicio = json.indexOf(busqueda);
        if (inicio == -1) return "";
        inicio += busqueda.length();
        int fin = json.indexOf("\"", inicio);
        return fin == -1 ? "" : json.substring(inicio, fin);
    }
}