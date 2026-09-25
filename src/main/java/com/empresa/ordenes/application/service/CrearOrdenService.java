package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.in.CrearOrdenCommand;
import com.empresa.ordenes.application.port.in.CrearOrdenUseCase;
import com.empresa.ordenes.application.port.in.ResultadoCrearOrden;
import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.LlaveIdempotenciaDuplicadaException;
import com.empresa.ordenes.domain.exception.ReferenciaInexistenteException;
import com.empresa.ordenes.domain.model.EstadoOrden;
import com.empresa.ordenes.domain.model.NuevaOrden;
import com.empresa.ordenes.domain.model.Orden;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrearOrdenService implements CrearOrdenUseCase {

    private final OrdenRepositorio repositorio;

    public CrearOrdenService(OrdenRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public ResultadoCrearOrden crear(CrearOrdenCommand comando) {
        validarReferencias(comando);

        var existente = repositorio.buscarPorLlaveIdempotencia(comando.llaveIdempotencia());
        if (existente.isPresent()) {
            return ResultadoCrearOrden.existente(existente.get());
        }

        Long idEstadoInicial = repositorio.buscarIdEstadoPorCodigo(EstadoOrden.CODIGO_INICIAL)
                .orElseThrow(() -> new IllegalStateException(
                        "El estado inicial " + EstadoOrden.CODIGO_INICIAL + " no existe en el catálogo"));

        var nuevaOrden = new NuevaOrden(comando.idCliente(), comando.idTipo(), idEstadoInicial,
                comando.canal(), comando.llaveIdempotencia(), comando.usuario(), comando.idPeticion());
        try {
            return ResultadoCrearOrden.nueva(repositorio.crear(nuevaOrden));
        } catch (LlaveIdempotenciaDuplicadaException e) {
            // Carrera: otra petición con la misma llave confirmó su orden entre la búsqueda y el insert
            return ResultadoCrearOrden.existente(buscarPorLlave(comando.llaveIdempotencia()));
        }
    }

    private void validarReferencias(CrearOrdenCommand comando) {
        if (!repositorio.existeCliente(comando.idCliente())) {
            throw new ReferenciaInexistenteException("El cliente " + comando.idCliente() + " no existe");
        }
        if (!repositorio.existeTipo(comando.idTipo())) {
            throw new ReferenciaInexistenteException("El tipo de orden " + comando.idTipo() + " no existe");
        }
    }

    private Orden buscarPorLlave(String llaveIdempotencia) {
        return repositorio.buscarPorLlaveIdempotencia(llaveIdempotencia)
                .orElseThrow(() -> new IllegalStateException(
                        "La llave de idempotencia está duplicada pero no se encontró la orden existente"));
    }
}
