import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nucleo falso para probar el wrapper MotorDeRutas sin depender del C++ (T-095).
 * Se ejecuta con «java NucleoFalso.java [opciones]» y habla como el nucleo real: una linea de JSON por mensaje.
 *
 * Opciones:
 *   --consultas-sin-red=N   las primeras N veces que se consulta el estado dice que la red no esta cargada.
 *
 * Comportamientos que activa cada peticion con un campo propio:
 *   "lento": milisegundos      espera ese tiempo antes de responder.
 *   "colgar": true             no responde nunca.
 *   "morir": true              el proceso termina sin responder.
 *   "id_equivocado": true      antes de responder bien, escribe una respuesta con otro id.
 */
public class NucleoFalso {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern OPERACION = Pattern.compile("\"operacion\"\\s*:\\s*\"([a-z_]+)\"");
    private static final Pattern LENTO = Pattern.compile("\"lento\"\\s*:\\s*(\\d+)");

    public static void main(String[] args) throws Exception {
        int consultasSinRed = 0;
        for (String arg : args) {
            if (arg.startsWith("--consultas-sin-red=")) {
                consultasSinRed = Integer.parseInt(arg.substring(arg.indexOf('=') + 1));
            }
        }
        PrintStream salida = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        System.err.println("nucleo falso listo");
        int eventosRecibidos = 0;
        int consultas = 0;
        BufferedReader entrada = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String linea;
        while ((linea = entrada.readLine()) != null) {
            Matcher id = ID.matcher(linea);
            Matcher operacion = OPERACION.matcher(linea);
            if (!id.find() || !operacion.find()) {
                salida.println("{\"id\":null,\"ok\":false,\"error\":{\"codigo\":\"PETICION_INVALIDA\","
                        + "\"descripcion\":\"falta id u operacion\"}}");
                continue;
            }
            long numero = Long.parseLong(id.group(1));
            if (linea.contains("\"morir\":true")) {
                System.exit(3);
            }
            if (linea.contains("\"colgar\":true")) {
                Thread.sleep(600_000);
            }
            Matcher lento = LENTO.matcher(linea);
            if (lento.find()) {
                Thread.sleep(Long.parseLong(lento.group(1)));
            }
            if (linea.contains("\"id_equivocado\":true")) {
                salida.println("{\"id\":" + (numero + 1000) + ",\"ok\":true,\"intruso\":true}");
            }
            switch (operacion.group(1)) {
                case "consultar_estado" -> {
                    boolean cargada = ++consultas > consultasSinRed;
                    salida.println("{\"id\":" + numero + ",\"ok\":true,\"red_cargada\":" + cargada
                            + ",\"fecha_extracto\":" + (cargada ? "\"2026-10-01\"" : "null")
                            + ",\"nodos\":" + (cargada ? 3 : 0) + ",\"tramos\":" + (cargada ? 4 : 0) + "}");
                }
                case "actualizar_trafico" -> {
                    eventosRecibidos++;
                    salida.println("{\"id\":" + numero + ",\"ok\":true,\"eventos_recibidos\":" + eventosRecibidos + "}");
                }
                case "calcular_ruta", "revisar_ruta", "analizar_telemetria" ->
                    salida.println("{\"id\":" + numero + ",\"ok\":true,\"operacion\":\"" + operacion.group(1) + "\"}");
                default -> salida.println("{\"id\":" + numero + ",\"ok\":false,\"error\":{\"codigo\":"
                        + "\"OPERACION_DESCONOCIDA\",\"descripcion\":\"no existe\"}}");
            }
        }
    }
}
