package com.empresa.ordenes.domain.exception;

public class ReferenciaInexistenteException extends RuntimeException {

    public ReferenciaInexistenteException(String mensaje) {
        super(mensaje);
    }
}
