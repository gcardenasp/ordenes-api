package com.empresa.ordenes.domain.model;

/**
 * Códigos de estado que la aplicación necesita conocer. Las transiciones entre estados
 * viven solo en la base de datos (TRANSICION_ESTADO), no aquí.
 */
public final class EstadoOrden {

    public static final String CODIGO_INICIAL = "CREADA";

    private EstadoOrden() {
    }
}
