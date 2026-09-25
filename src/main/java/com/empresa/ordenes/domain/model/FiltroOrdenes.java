package com.empresa.ordenes.domain.model;

import java.time.LocalDate;

/**
 * Filtros opcionales del listado (nulo = sin filtro) y la página solicitada, que inicia en 0.
 */
public record FiltroOrdenes(
        Long idEstado,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        int pagina,
        int tamano) {
}
