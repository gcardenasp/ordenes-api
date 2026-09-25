package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenCommand;
import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.TransicionInvalidaException;
import com.empresa.ordenes.domain.model.Orden;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.empresa.ordenes.application.service.OrdenesDePrueba.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CambiarEstadoOrdenServiceTest {

    private static final Long ID_ESTADO_ASIGNADA = 2L;
    private static final CambiarEstadoOrdenCommand COMANDO = new CambiarEstadoOrdenCommand(
            ID_ORDEN, ID_ESTADO_ASIGNADA, "usuario.prueba", "Asignada a cuadrilla", "peticion-1");

    @Mock
    private OrdenRepositorio repositorio;

    @InjectMocks
    private CambiarEstadoOrdenService servicio;

    @Test
    void delegaElCambioAlPuertoYRetornaLaOrdenReleida() {
        Orden actualizada = ordenEnEstado(ID_ESTADO_ASIGNADA);
        when(repositorio.buscarPorId(ID_ORDEN)).thenReturn(Optional.of(actualizada));

        Orden resultado = servicio.cambiarEstado(COMANDO);

        assertThat(resultado).isEqualTo(actualizada);
        InOrder orden = inOrder(repositorio);
        orden.verify(repositorio).cambiarEstado(ID_ORDEN, ID_ESTADO_ASIGNADA,
                "usuario.prueba", "Asignada a cuadrilla", "peticion-1");
        orden.verify(repositorio).buscarPorId(ID_ORDEN);
    }

    @Test
    void propagaLaExcepcionDelPuertoSinReleerLaOrden() {
        doThrow(new TransicionInvalidaException(ID_ORDEN, ID_ESTADO_ASIGNADA))
                .when(repositorio).cambiarEstado(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> servicio.cambiarEstado(COMANDO))
                .isInstanceOf(TransicionInvalidaException.class);
        verify(repositorio, never()).buscarPorId(any());
    }
}
