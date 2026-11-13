package com.churoviaje.backend.motor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Punto de enganche de D-14: cada vez que el wrapper arranca el nucleo le envia los eventos vigentes, para que
 * nunca empiece con el trafico en blanco. Cada elemento es el cuerpo de un mensaje {@code actualizar_trafico}
 * (sin {@code id} ni {@code operacion}, que pone el wrapper). Lo implementa {@link EventosVigentesDelMotor} (T-099).
 */
@FunctionalInterface
public interface EventosVigentesParaMotor {

    List<ObjectNode> eventosVigentes();
}
