package com.empresa.ordenes.infrastructure.adapter.in.rest;

import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenCommand;
import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenUseCase;
import com.empresa.ordenes.application.port.in.ConsultarOrdenUseCase;
import com.empresa.ordenes.application.port.in.CrearOrdenCommand;
import com.empresa.ordenes.application.port.in.CrearOrdenUseCase;
import com.empresa.ordenes.application.port.in.ListarOrdenesUseCase;
import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.infrastructure.adapter.in.rest.api.OrdenApi;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.ActualizarEstadoRequestDto;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.CrearOrdenRequestDto;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.OrdenDto;
import com.empresa.ordenes.infrastructure.adapter.in.rest.dto.PaginaOrdenDto;
import com.empresa.ordenes.infrastructure.config.Trazabilidad;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Implementa la interfaz generada desde openapi.yaml. El parámetro X-Correlation-Id se ignora aquí:
 * CorrelationIdFilter lo lee (o lo genera) y lo deja en el MDC para toda la petición. El controller
 * agrega al MDC el id de orden y el canal, que el filtro incluye en el log de fin de petición.
 */
@RestController
public class OrdenController implements OrdenApi {

    private final CrearOrdenUseCase crearOrden;
    private final ConsultarOrdenUseCase consultarOrden;
    private final CambiarEstadoOrdenUseCase cambiarEstadoOrden;
    private final ListarOrdenesUseCase listarOrdenes;
    private final UsuarioActualProvider usuarioActual;

    public OrdenController(CrearOrdenUseCase crearOrden, ConsultarOrdenUseCase consultarOrden,
                           CambiarEstadoOrdenUseCase cambiarEstadoOrden, ListarOrdenesUseCase listarOrdenes,
                           UsuarioActualProvider usuarioActual) {
        this.crearOrden = crearOrden;
        this.consultarOrden = consultarOrden;
        this.cambiarEstadoOrden = cambiarEstadoOrden;
        this.listarOrdenes = listarOrdenes;
        this.usuarioActual = usuarioActual;
    }

    @Override
    public ResponseEntity<OrdenDto> crearOrden(CrearOrdenRequestDto solicitud, String xCorrelationId) {
        MDC.put(Trazabilidad.MDC_CANAL, solicitud.getCanal());
        var resultado = crearOrden.crear(new CrearOrdenCommand(solicitud.getIdCliente(), solicitud.getIdTipo(),
                solicitud.getCanal(), solicitud.getLlaveIdempotencia(), usuarioActual.obtener(), idPeticion()));

        MDC.put(Trazabilidad.MDC_ID_ORDEN, String.valueOf(resultado.orden().id()));

        // 201 si se creó en esta petición; 200 si la llave de idempotencia ya existía
        return ResponseEntity.status(resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(OrdenRestMapper.aDto(resultado.orden()));
    }

    @Override
    public ResponseEntity<OrdenDto> consultarOrden(Long id) {
        MDC.put(Trazabilidad.MDC_ID_ORDEN, String.valueOf(id));
        return ResponseEntity.ok(OrdenRestMapper.aDto(consultarOrden.consultar(id)));
    }

    @Override
    public ResponseEntity<OrdenDto> actualizarEstado(Long id, ActualizarEstadoRequestDto solicitud, String xCorrelationId) {
        MDC.put(Trazabilidad.MDC_ID_ORDEN, String.valueOf(id));
        var orden = cambiarEstadoOrden.cambiarEstado(new CambiarEstadoOrdenCommand(id, solicitud.getIdEstadoNuevo(),
                usuarioActual.obtener(), solicitud.getObservacion(), idPeticion()));
        return ResponseEntity.ok(OrdenRestMapper.aDto(orden));
    }

    @Override
    public ResponseEntity<PaginaOrdenDto> listarOrdenes(Long estado, LocalDate fechaInicio, LocalDate fechaFin,
                                                        Integer pagina, Integer tamano) {
        var filtro = new FiltroOrdenes(estado, fechaInicio, fechaFin, pagina, tamano);
        return ResponseEntity.ok(OrdenRestMapper.aDto(listarOrdenes.listar(filtro)));
    }

    private static String idPeticion() {
        return MDC.get(Trazabilidad.MDC_ID_PETICION);
    }
}
