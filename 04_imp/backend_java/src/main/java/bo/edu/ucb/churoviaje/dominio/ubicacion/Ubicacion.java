package bo.edu.ucb.churoviaje.dominio.ubicacion;

import java.util.Objects;

public record Ubicacion(double latitud, double longitud, OrigenUbicacion origen) {
    public Ubicacion {
        new Coordenada(latitud, longitud);
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

    /**
     * RN-03.3: en una ruta el punto fuera de cobertura es un resultado del proceso,
     * no un rechazo HTTP 422. El servicio deberá verificar origen, destino y CADA
     * parada antes de llamar al motor, que además puede devolver PUNTO_INVALIDO
     * cuando no encuentra una intersección adecuada (RN-03.4).
     */
    public ResultadoUbicacionRuta validarParaRuta(CoberturaManchaUrbana cobertura) {
        return estaDentroDeManchaUrbana(cobertura)
                ? ResultadoUbicacionRuta.VALIDA : ResultadoUbicacionRuta.PUNTO_INVALIDO;
    }
}
