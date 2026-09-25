package com.empresa.ordenes.domain.model;

/**
 * Datos para insertar una orden en su estado inicial junto con su primer registro de histórico.
 */
public record NuevaOrden(
        Long idCliente,
        Long idTipo,
        Long idEstadoInicial,
        String canal,
        String llaveIdempotencia,
        String usuarioCreacion,
        String idPeticion) {
}
