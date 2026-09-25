import java.util.Scanner;

public class Analisis_Trafico {

    private static final double RADIO_TIERRA = 6371000; // En metros

    // 2.1 Formula de Haversine (Distancia)
    public static double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA * Math.asin(Math.sqrt(a));
    }

    // 2.2 Formula de Velocidad Real
    public static double calcularVelocidad(double distanciaMts, double segundos) {
        if (segundos <= 0) return 0;
        return (distanciaMts / segundos) * 3.6; // Convierte m/s a km/h
    }

    // 2.3 Formula de Azimut (Angulo)
    public static double calcularAzimut(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double l1 = Math.toRadians(lat1);
        double l2 = Math.toRadians(lat2);
        double y = Math.sin(dLon) * Math.cos(l2);
        double x = Math.cos(l1) * Math.sin(l2) - Math.sin(l1) * Math.cos(l2) * Math.cos(dLon);
        double angulo = Math.toDegrees(Math.atan2(y, x));
        return (angulo + 360) % 360; // Normaliza de 0 a 360 grados
    }

    // 2.4 Formula de Retraso Vehicular (Minutos perdidos)
    public static double calcularRetraso(double distanciaMts, double velActualKmh, double velIdealKmh) {
        if (velActualKmh <= 0) velActualKmh = 0.1; // Evita division por cero
        double distKm = distanciaMts / 1000.0;
        
        double tiempoRealHoras = distKm / velActualKmh;
        double tiempoIdealHoras = distKm / velIdealKmh;
        
        double retrasoMinutos = (tiempoRealHoras - tiempoIdealHoras) * 60;
        return Math.max(retrasoMinutos, 0); // Si va mas rapido que lo ideal, el retraso es 0
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("SIMULADOR MATEMATICO - CHUROVIAJE");
        System.out.println("Punto de calibracion inicial:");
        System.out.print("Latitud inicial: ");
        double latAnterior = Double.parseDouble(scanner.nextLine());
        System.out.print("Longitud inicial: ");
        double lonAnterior = Double.parseDouble(scanner.nextLine());
        
        double rumboAnterior = -1;
        int lectura = 1;

        while (true) {
            try {
                System.out.println("\n--- LECTURA " + lectura + " ---");
                
                System.out.print("Nueva Latitud: ");
                double latActual = Double.parseDouble(scanner.nextLine());
                
                System.out.print("Nueva Longitud: ");
                double lonActual = Double.parseDouble(scanner.nextLine());
                
                System.out.print("Precision del GPS (metros): ");
                double precision = Double.parseDouble(scanner.nextLine());
                
                System.out.print("Tiempo transcurrido (segundos): ");
                double tiempo = Double.parseDouble(scanner.nextLine());
                
                System.out.print("Velocidad ideal de la via (km/h): ");
                double velIdeal = Double.parseDouble(scanner.nextLine());

                System.out.println("\n--- RESULTADOS DE FORMULAS ---");

                // Regla 1: Filtro de hardware
                if (precision > 50) {
                    System.out.println("DESCARTADO: Precision GPS muy baja (> 50m).");
                    continue; // Salta al siguiente ciclo sin procesar
                }

                // Ejecucion de formulas
                double distancia = calcularDistancia(latAnterior, lonAnterior, latActual, lonActual);
                double velocidad = calcularVelocidad(distancia, tiempo);
                double rumboActual = calcularAzimut(latAnterior, lonAnterior, latActual, lonActual);
                double retraso = calcularRetraso(distancia, velocidad, velIdeal);

                // Calculo de diferencia de giro
                double cambioAngulo = 0;
                if (rumboAnterior != -1) {
                    cambioAngulo = Math.abs(rumboActual - rumboAnterior);
                    if (cambioAngulo > 180) cambioAngulo = 360 - cambioAngulo;
                }

                // Salidas
                System.out.printf("2.1 Distancia recorrida: %.2f metros\n", distancia);
                System.out.printf("2.2 Velocidad actual: %.2f km/h\n", velocidad);
                System.out.printf("2.3 Cambio de angulo (Giro): %.2f grados\n", cambioAngulo);
                System.out.printf("2.4 Tiempo de retraso: %.2f minutos\n", retraso);

                System.out.println("\n--- CLASIFICACION FINAL ---");
                if (velocidad > 150) {
                    System.out.println("DESCARTADO: Velocidad irreal (Fallo de hardware).");
                } else if (cambioAngulo > 40 && velocidad <= 25) {
                    System.out.println("ESTADO: GIRO EN ESQUINA (No es trafico).");
                } else if (velocidad < 10) {
                    System.out.println("ESTADO: MUY CONGESTIONADO (Trancadera).");
                } else if (velocidad < 25) {
                    System.out.println("ESTADO: CONGESTIONADO.");
                } else {
                    System.out.println("ESTADO: VELOCIDAD NORMAL.");
                }

                // Actualizar estado para el siguiente ciclo
                latAnterior = latActual;
                lonAnterior = lonActual;
                rumboAnterior = rumboActual;
                lectura++;

                System.out.print("\n¿Procesar siguiente punto? (s/n): ");
                String continuar = scanner.nextLine().toLowerCase();
                if (continuar.equals("n")) break;

            } catch (Exception e) {
                System.out.println("Error: Ingrese un formato de numero valido.");
            }
        }
        scanner.close();
    }
}
