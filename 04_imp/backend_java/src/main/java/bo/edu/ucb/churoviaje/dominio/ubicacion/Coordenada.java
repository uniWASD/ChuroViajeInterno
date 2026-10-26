package bo.edu.ucb.churoviaje.dominio.ubicacion;

/** El dominio recibe primero latitud, después longitud (contrato de la API). */
public record Coordenada(double latitud, double longitud) {
    public Coordenada {
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new IllegalArgumentException("Latitud inválida");
        }
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("Longitud inválida");
        }
    }
}
