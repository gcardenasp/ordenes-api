package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.in.ListarOrdenesUseCase;
import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.DatosInvalidosException;
import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarOrdenesService implements ListarOrdenesUseCase {

    private final OrdenRepositorio repositorio;

    public ListarOrdenesService(OrdenRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Orden> listar(FiltroOrdenes filtro) {
        if (filtro.fechaInicio() != null && filtro.fechaFin() != null
                && filtro.fechaInicio().isAfter(filtro.fechaFin())) {
            throw new DatosInvalidosException("La fecha inicial no puede ser mayor que la fecha final");
        }
        return repositorio.listar(filtro);
    }
}
