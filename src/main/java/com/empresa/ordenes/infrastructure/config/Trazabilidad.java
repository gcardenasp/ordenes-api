package com.empresa.ordenes.infrastructure.config;

public final class Trazabilidad {

    public static final String HEADER_ID_PETICION = "X-Correlation-Id";
    public static final String MDC_ID_PETICION = "idPeticion";

    private Trazabilidad() {
    }
}
