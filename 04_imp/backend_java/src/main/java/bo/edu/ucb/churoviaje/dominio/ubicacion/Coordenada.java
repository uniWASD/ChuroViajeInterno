package bo.edu.ucb.churoviaje.dominio.ubicacion;

public record Coordenada(double longitud, double latitud) {
    public Coordenada {
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("Longitud inválida");
        }
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new IllegalArgumentException("Latitud inválida");
        }
    }
}
