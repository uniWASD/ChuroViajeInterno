package com.churoviaje.backend.dominio.anh;

import com.churoviaje.backend.dominio.EstadoCombustible;
import com.churoviaje.backend.dominio.OrigenCombustible;
import com.churoviaje.backend.dominio.Ubicacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Lo que ANH dice de una estación para el combustible pedido, ya validado. Solo existe durante el ciclo: de aquí se
 * guarda lo que cabe en {@code estado_combustible} y nada más (RN-09.18).
 *
 * @param anhId       identificador de la estación en ANH
 * @param nombre      nombre de la estación; solo para anotar las candidatas en el registro (RN-09.9)
 * @param ubicacion   dónde está, o null si ANH no la dio o no se entiende
 * @param litros      el saldo, o null si es «sin saldo»: no llegó, no es un número válido o es negativo (RN-09.7)
 * @param estadoAnh   {@code ALTO}, {@code MEDIO} o {@code BAJO} tal como llega, o null (RN-09.12)
 * @param actualizado el {@code updated_at} de ANH, o null si falta o no se entiende (RN-09.13)
 */
public record LecturaAnh(int anhId, String nombre, Ubicacion ubicacion, BigDecimal litros, String estadoAnh,
        Instant actualizado) {

    public LecturaAnh {
        if (anhId <= 0) {
            throw new IllegalArgumentException("El id de ANH es un entero positivo");
        }
        if (litros != null && litros.signum() < 0) {
            throw new IllegalArgumentException("Un saldo negativo es «sin saldo» (RN-09.7)");
        }
    }

    /**
     * RN-09.11 a RN-09.13: el {@link EstadoCombustible} de origen ANH de esta lectura, con el {@code updated_at} como
     * fecha. Al guardarlo, esa fecha reemplaza a la del dato anterior solo si ANH cambió lo que dice (RN-09.14). Sin
     * saldo o sin fecha no hay estado: no se crea ni se cambia nada, y nunca se toma como cero litros.
     *
     * @param leidoEn cuándo leyó el backend la respuesta; de ahí se cuenta la vigencia (RN-09.17)
     */
    public Optional<EstadoCombustible> comoEstado(long surtidorId, String tipoCodigo, Instant leidoEn) {
        Objects.requireNonNull(leidoEn, "leidoEn");
        if (litros == null || actualizado == null) {
            return Optional.empty();
        }
        return Optional.of(new EstadoCombustible(surtidorId, tipoCodigo, OrigenCombustible.ANH, litros.signum() > 0,
                null, actualizado, litros, estadoAnh, leidoEn));
    }
}
