package com.empresa.ordenes.application.port.in;

public record CrearOrdenCommand(
        Long idCliente,
        Long idTipo,
        String canal,
        String llaveIdempotencia,
        String usuario,
        String idPeticion) {
}
