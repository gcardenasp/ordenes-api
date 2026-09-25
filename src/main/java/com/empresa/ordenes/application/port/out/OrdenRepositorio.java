package com.empresa.ordenes.application.port.out;

import com.empresa.ordenes.domain.exception.LlaveIdempotenciaDuplicadaException;
import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.domain.model.NuevaOrden;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;

import java.util.Optional;

public interface OrdenRepositorio {

    boolean existeCliente(Long idCliente);

    boolean existeTipo(Long idTipo);

    Optional<Long> buscarIdEstadoPorCodigo(String codigo);

    Optional<Orden> buscarPorId(Long idOrden);

    Optional<Orden> buscarPorLlaveIdempotencia(String llaveIdempotencia);

    /**
     * Inserta la orden y su primer registro de histórico (estado anterior nulo).
     *
     * @throws LlaveIdempotenciaDuplicadaException si otra transacción ya insertó una orden con la misma llave
     */
    Orden crear(NuevaOrden nuevaOrden);

    /**
     * Cambia el estado con el procedimiento prc_cambio_estado_orden, que valida la transición,
     * bloquea la orden y registra el histórico.
     */
    void cambiarEstado(Long idOrden, Long idEstadoNuevo, String usuario, String observacion, String idPeticion);

    Pagina<Orden> listar(FiltroOrdenes filtro);
}
