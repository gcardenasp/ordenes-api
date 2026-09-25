package com.empresa.ordenes.application.port.in;

import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;

public interface ListarOrdenesUseCase {

    Pagina<Orden> listar(FiltroOrdenes filtro);
}
