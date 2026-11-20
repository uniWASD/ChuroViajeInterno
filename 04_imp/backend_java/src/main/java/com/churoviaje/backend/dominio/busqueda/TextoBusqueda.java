package com.churoviaje.backend.dominio.busqueda;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cómo se compara un texto en la búsqueda (D-47): minúsculas, sin tildes ni signos y con las abreviaturas de calles
 * escritas completas. Así "Av. Las Américas", "avenida las americas" y "av las americas" son lo mismo.
 */
public final class TextoBusqueda {

    private static final Map<String, String> ABREVIATURAS = Map.of(
            "av", "avenida", "avda", "avenida", "pje", "pasaje", "psje", "pasaje", "pza", "plaza", "cll", "calle");

    private TextoBusqueda() {
    }

    /** El texto normalizado; vacío si no queda ninguna letra ni número. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (limpio.isEmpty()) {
            return "";
        }
        return Arrays.stream(limpio.split(" "))
                .map(palabra -> ABREVIATURAS.getOrDefault(palabra, palabra))
                .collect(Collectors.joining(" "));
    }

    /** Las palabras del texto normalizado. */
    public static List<String> palabras(String texto) {
        String normal = normalizar(texto);
        return normal.isEmpty() ? List.of() : List.of(normal.split(" "));
    }

    /** Cada palabra buscada es el comienzo de alguna palabra del nombre ("hosp san" encuentra "hospital san juan"). */
    public static boolean coincide(String nombreNormalizado, List<String> palabras) {
        String conEspacio = " " + nombreNormalizado;
        return palabras.stream().allMatch(palabra -> conEspacio.contains(" " + palabra));
    }

    /**
     * Qué tan bien coincide, de mejor a peor: 0 si el nombre empieza con lo buscado, 1 si empieza así después de la
     * primera palabra ("calle sucre" para "sucre"), 2 en otro caso.
     */
    public static int cercania(String nombreNormalizado, String buscado) {
        if (nombreNormalizado.startsWith(buscado)) {
            return 0;
        }
        int espacio = nombreNormalizado.indexOf(' ');
        return espacio > 0 && nombreNormalizado.substring(espacio + 1).startsWith(buscado) ? 1 : 2;
    }
}
