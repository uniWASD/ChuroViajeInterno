package bo.edu.ucb.churoviaje.dominio.catalogo;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

public final class ClaveAlias {
    private ClaveAlias() {
    }

    public static String normalizar(String texto) {
        Objects.requireNonNull(texto, "El alias es obligatorio");
        String clave = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (clave.isBlank()) {
            throw new IllegalArgumentException("El alias no puede estar vacío");
        }
        return clave;
    }
}
