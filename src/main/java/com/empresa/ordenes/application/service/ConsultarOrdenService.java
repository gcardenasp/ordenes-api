package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.in.ConsultarOrdenUseCase;
import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.model.Orden;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarOrdenService implements ConsultarOrdenUseCase {

    private final OrdenRepositorio repositorio;

    public ConsultarOrdenService(OrdenRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public Orden consultar(Long idOrden) {
        return repositorio.buscarPorId(idOrden)
                .orElseThrow(() -> new OrdenNoEncontradaException(idOrden));
    }
}
