package com.empresa.ordenes.application.port.in;

public interface CrearOrdenUseCase {

    ResultadoCrearOrden crear(CrearOrdenCommand comando);
}
