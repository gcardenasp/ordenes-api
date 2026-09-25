package com.empresa.ordenes.infrastructure.adapter.in.rest;

import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.ErrorDto;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.OrdenDto;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.PaginaOrdenDto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

final class OrdenRestMapper {

    // Las columnas TIMESTAMP guardan la hora de Bogotá sin zona; el contrato exige el offset
    static final ZoneId ZONA_HORARIA = ZoneId.of("America/Bogota");

    private OrdenRestMapper() {
    }

    static OrdenDto aDto(Orden orden) {
        return new OrdenDto()
                .id(orden.id())
                .idCliente(orden.idCliente())
                .idTipo(orden.idTipo())
                .idEstado(orden.idEstado())
                .canal(orden.canal())
                .llaveIdempotencia(orden.llaveIdempotencia())
                .usuarioCreacion(orden.usuarioCreacion())
                .fechaCreacion(conZona(orden.fechaCreacion()))
                .usuarioModificacion(orden.usuarioModificacion())
                .fechaModificacion(conZona(orden.fechaModificacion()));
    }

    static PaginaOrdenDto aDto(Pagina<Orden> pagina) {
        return new PaginaOrdenDto()
                .contenido(pagina.contenido().stream().map(OrdenRestMapper::aDto).toList())
                .pagina(pagina.pagina())
                .tamano(pagina.tamano())
                .totalElementos(pagina.totalElementos())
                .totalPaginas(pagina.totalPaginas());
    }

    static ErrorDto error(String codigo, String mensaje) {
        return new ErrorDto().codigo(codigo).mensaje(mensaje).fecha(OffsetDateTime.now(ZONA_HORARIA));
    }

    private static OffsetDateTime conZona(LocalDateTime fecha) {
        return fecha == null ? null : fecha.atZone(ZONA_HORARIA).toOffsetDateTime();
    }
}
