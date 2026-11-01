package com.churoviaje.backend.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class TokensRenovacionTest {

    private final TokensRenovacion tokens = new TokensRenovacion();

    @Test
    void generaTokensDistintosYLargos() {
        var vistos = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            String token = tokens.generar();
            assertTrue(token.length() >= 43, "256 bits en base64 son 43 caracteres");
            assertTrue(vistos.add(token), "no se repiten");
        }
    }

    @Test
    void laHuellaEsSha256EnHexadecimalYNoEsElToken() {
        String huella = tokens.huella("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", huella);
        assertNotEquals("abc", huella);
        assertEquals(huella, tokens.huella("abc"));
    }
}
