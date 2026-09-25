package com.empresa.ordenes.application.port.in;

import com.empresa.ordenes.domain.model.Orden;

/**
 * La orden y si se creó en esta petición o ya existía por su llave de idempotencia (201 o 200).
 */
public record ResultadoCrearOrden(Orden orden, boolean nueva) {

    public static ResultadoCrearOrden nueva(Orden orden) {
        return new ResultadoCrearOrden(orden, true);
    }

    public static ResultadoCrearOrden existente(Orden orden) {
        return new ResultadoCrearOrden(orden, false);
    }
}
