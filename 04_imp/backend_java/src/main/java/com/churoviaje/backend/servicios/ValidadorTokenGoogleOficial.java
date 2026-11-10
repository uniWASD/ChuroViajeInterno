package com.churoviaje.backend.servicios;

import com.churoviaje.backend.config.PropiedadesSeguridad;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Validacion con la libreria oficial de Google: firma, emisor, vencimiento y, ademas, que el token
 * se emitio para el Client ID de nuestra app (RN-08.3). Las claves publicas de Google las guarda
 * en cache la propia libreria.
 */
@Component
public class ValidadorTokenGoogleOficial implements ValidadorTokenGoogle {

    private final GoogleIdTokenVerifier verificador;

    public ValidadorTokenGoogleOficial(PropiedadesSeguridad propiedades) {
        this.verificador = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(propiedades.google().clientId()))
                .build();
    }

    @Override
    public Optional<String> identificarCuenta(String tokenIdentidad) {
        try {
            GoogleIdToken token = verificador.verify(tokenIdentidad);
            return token == null ? Optional.empty() : Optional.ofNullable(token.getPayload().getSubject());
        } catch (IllegalArgumentException e) {
            // Un texto que ni siquiera tiene forma de token no es un problema de Google.
            return Optional.empty();
        } catch (GeneralSecurityException | IOException e) {
            throw new GoogleNoDisponibleException(e);
        }
    }
}
