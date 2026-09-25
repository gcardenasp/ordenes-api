package com.empresa.ordenes.infrastructure.config;

public final class Trazabilidad {

    public static final String HEADER_ID_PETICION = "X-Correlation-Id";

    // Claves del MDC: cada log de la petición las incluye como campos del JSON
    public static final String MDC_ID_PETICION = "idPeticion";
    public static final String MDC_ID_ORDEN = "idOrden";
    public static final String MDC_CANAL = "canal";

    private Trazabilidad() {
    }
}
