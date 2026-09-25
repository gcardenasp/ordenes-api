package com.empresa.ordenes.application.service;

import com.empresa.ordenes.domain.model.Orden;

import java.time.LocalDateTime;

final class OrdenesDePrueba {

    static final Long ID_ORDEN = 10L;
    static final Long ID_CLIENTE = 1L;
    static final Long ID_TIPO = 2L;
    static final Long ID_ESTADO_CREADA = 1L;
    static final String LLAVE = "llave-123";

    private OrdenesDePrueba() {
    }

    static Orden ordenEnEstado(Long idEstado) {
        return new Orden(ID_ORDEN, ID_CLIENTE, ID_TIPO, idEstado, "WEB", LLAVE, "usuario.prueba",
                LocalDateTime.of(2026, 9, 25, 10, 0), null, null);
    }
}
