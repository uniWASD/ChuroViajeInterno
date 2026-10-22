package bo.edu.ucb.churoviaje.dominio.ubicacion;

import java.util.Objects;

public record Ubicacion(double latitud, double longitud, OrigenUbicacion origen) {
    public Ubicacion {
        new Coordenada(longitud, latitud);
        Objects.requireNonNull(origen, "Debe indicarse cómo se obtuvo la ubicación");
    }

    public boolean estaDentroDeManchaUrbana(CoberturaManchaUrbana cobertura) {
        return Objects.requireNonNull(cobertura, "La cobertura es obligatoria")
                .contiene(latitud, longitud);
    }

    public void validarParaReporte(CoberturaManchaUrbana cobertura) {
        if (!estaDentroDeManchaUrbana(cobertura)) {
            throw UbicacionFueraCoberturaException.paraReporte();
        }
    }

    public void validarParaRuta(CoberturaManchaUrbana cobertura) {
        if (!estaDentroDeManchaUrbana(cobertura)) {
            throw UbicacionFueraCoberturaException.paraRuta();
        }
    }
}
