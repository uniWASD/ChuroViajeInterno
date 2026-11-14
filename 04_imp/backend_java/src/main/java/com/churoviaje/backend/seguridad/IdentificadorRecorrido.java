package com.churoviaje.backend.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Identificador temporal de recorrido para la telemetría (#74, RN-04.12, RNF-10): un HMAC-SHA256 del conductor con
 * una clave aleatoria que solo vive en la memoria del backend y cambia cada día (a la medianoche de Bolivia).
 *
 * <p>Mientras dura la clave, el mismo conductor tiene siempre el mismo recorrido, aunque renueve su JWT, y el motor
 * puede seguir su velocidad. La relación con el conductor no se guarda en ningún lado: al cambiar la clave (o al
 * reiniciar el backend) se pierde, y del identificador no se puede volver al conductor.
 */
@Component
public class IdentificadorRecorrido {

    private static final ZoneId ZONA = ZoneId.of("America/La_Paz");
    private static final String ALGORITMO = "HmacSHA256";
    /** 128 bits del HMAC alcanzan para que dos conductores nunca compartan recorrido. */
    private static final int BYTES_DEL_IDENTIFICADOR = 16;

    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();
    private LocalDate dia;
    private SecretKeySpec clave;

    public IdentificadorRecorrido(Clock reloj) {
        this.reloj = reloj;
    }

    public synchronized String de(long conductorId) {
        LocalDate hoy = reloj.instant().atZone(ZONA).toLocalDate();
        if (!hoy.equals(dia)) {
            byte[] bytes = new byte[32];
            azar.nextBytes(bytes);
            clave = new SecretKeySpec(bytes, ALGORITMO);
            dia = hoy;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(clave);
            byte[] huella = mac.doFinal(Long.toString(conductorId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(huella, 0, BYTES_DEL_IDENTIFICADOR);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("La JVM no tiene HmacSHA256", e);
        }
    }
}
