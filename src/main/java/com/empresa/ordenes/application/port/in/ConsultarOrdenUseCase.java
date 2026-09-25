package com.empresa.ordenes.application.port.in;

import com.empresa.ordenes.domain.model.Orden;

public interface ConsultarOrdenUseCase {

    Orden consultar(Long idOrden);
}
