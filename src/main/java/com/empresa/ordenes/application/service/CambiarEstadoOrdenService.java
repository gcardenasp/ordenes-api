package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenCommand;
import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenUseCase;
import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.model.Orden;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La regla de transiciones no se valida aquí: la aplica el procedimiento contra TRANSICION_ESTADO.
 */
@Service
public class CambiarEstadoOrdenService implements CambiarEstadoOrdenUseCase {

    private final OrdenRepositorio repositorio;

    public CambiarEstadoOrdenService(OrdenRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional
    public Orden cambiarEstado(CambiarEstadoOrdenCommand comando) {
        repositorio.cambiarEstado(comando.idOrden(), comando.idEstadoNuevo(), comando.usuario(),
                comando.observacion(), comando.idPeticion());

        return repositorio.buscarPorId(comando.idOrden())
                .orElseThrow(() -> new OrdenNoEncontradaException(comando.idOrden()));
    }
}
