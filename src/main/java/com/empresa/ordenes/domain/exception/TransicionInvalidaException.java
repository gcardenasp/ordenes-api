package com.empresa.ordenes.domain.exception;

public class TransicionInvalidaException extends RuntimeException {

    public TransicionInvalidaException(Long idOrden, Long idEstadoNuevo) {
        super("La orden " + idOrden + " no puede pasar al estado " + idEstadoNuevo + " desde su estado actual");
    }
}
