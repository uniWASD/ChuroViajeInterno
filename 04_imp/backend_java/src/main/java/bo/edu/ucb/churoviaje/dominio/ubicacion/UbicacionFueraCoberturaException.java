package bo.edu.ucb.churoviaje.dominio.ubicacion;

public final class UbicacionFueraCoberturaException extends RuntimeException {
    private final String codigo;

    private UbicacionFueraCoberturaException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }

    public static UbicacionFueraCoberturaException paraReporte() {
        return new UbicacionFueraCoberturaException(
                "UBICACION_FUERA_COBERTURA", "La ubicación está fuera de la mancha urbana de Tarija");
    }

}
