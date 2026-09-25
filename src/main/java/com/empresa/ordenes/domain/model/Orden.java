package com.empresa.ordenes.domain.model;

import java.time.LocalDateTime;

public record Orden(
        Long id,
        Long idCliente,
        Long idTipo,
        Long idEstado,
        String canal,
        String llaveIdempotencia,
        String usuarioCreacion,
        LocalDateTime fechaCreacion,
        String usuarioModificacion,
        LocalDateTime fechaModificacion) {
}
