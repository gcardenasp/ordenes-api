package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.model.Orden;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.empresa.ordenes.application.service.OrdenesDePrueba.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarOrdenServiceTest {

    @Mock
    private OrdenRepositorio repositorio;

    @InjectMocks
    private ConsultarOrdenService servicio;

    @Test
    void retornaLaOrdenCuandoExiste() {
        Orden orden = ordenEnEstado(ID_ESTADO_CREADA);
        when(repositorio.buscarPorId(ID_ORDEN)).thenReturn(Optional.of(orden));

        assertThat(servicio.consultar(ID_ORDEN)).isEqualTo(orden);
    }

    @Test
    void lanzaOrdenNoEncontradaCuandoNoExiste() {
        when(repositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(99L))
                .isInstanceOf(OrdenNoEncontradaException.class)
                .hasMessage("La orden 99 no existe");
    }
}
