package bo.edu.ucb.churoviaje.dominio.ubicacion;

/** Estado de validación de un punto para calcular ruta, independiente de HTTP. */
public enum ResultadoUbicacionRuta {
    VALIDA,
    PUNTO_INVALIDO;

    public String mensajeContrato() {
        return this == PUNTO_INVALIDO ? "Origen o destino inválido" : null;
    }
}
