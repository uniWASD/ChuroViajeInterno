package bo.edu.ucb.churoviaje.dominio.catalogo;

import java.util.Objects;

public record TipoEvento(String codigo, String nombre, String color, int vigenciaMinutos,
                         boolean reportable) {
    public TipoEvento {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(nombre);
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("El color debe ser hexadecimal");
        }
        if (vigenciaMinutos <= 0) {
            throw new IllegalArgumentException("La vigencia debe ser positiva");
        }
    }
}
