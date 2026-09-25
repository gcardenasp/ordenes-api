package com.empresa.ordenes.application.port.in;

public record CambiarEstadoOrdenCommand(
        Long idOrden,
        Long idEstadoNuevo,
        String usuario,
        String observacion,
        String idPeticion) {
}
