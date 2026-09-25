package com.empresa.ordenes.domain.exception;

public class OrdenBloqueadaException extends RuntimeException {

    public OrdenBloqueadaException(Long idOrden) {
        super("La orden " + idOrden + " está siendo actualizada por otra solicitud, intente nuevamente");
    }
}
