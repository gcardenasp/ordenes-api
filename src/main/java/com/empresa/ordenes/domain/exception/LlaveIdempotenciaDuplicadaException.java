package com.empresa.ordenes.domain.exception;

/**
 * Otra petición con la misma llave de idempotencia insertó su orden primero.
 * El caso de uso de creación la atrapa y retorna la orden existente: nunca llega al cliente.
 */
public class LlaveIdempotenciaDuplicadaException extends RuntimeException {

    public LlaveIdempotenciaDuplicadaException(String llaveIdempotencia) {
        super("Ya existe una orden con la llave de idempotencia " + llaveIdempotencia);
    }
}
