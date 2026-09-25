package com.empresa.ordenes.domain.exception;

public class OrdenNoEncontradaException extends RuntimeException {

    public OrdenNoEncontradaException(Long idOrden) {
        super("La orden " + idOrden + " no existe");
    }
}
