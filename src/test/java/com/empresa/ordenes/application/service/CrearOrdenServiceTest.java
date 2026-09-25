package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.in.CrearOrdenCommand;
import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.LlaveIdempotenciaDuplicadaException;
import com.empresa.ordenes.domain.exception.ReferenciaInexistenteException;
import com.empresa.ordenes.domain.model.EstadoOrden;
import com.empresa.ordenes.domain.model.NuevaOrden;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearOrdenServiceTest {

    private static final CrearOrdenCommand COMANDO =
            new CrearOrdenCommand(ID_CLIENTE, ID_TIPO, "WEB", LLAVE, "usuario.prueba", "peticion-1");

    @Mock
    private OrdenRepositorio repositorio;

    @InjectMocks
    private CrearOrdenService servicio;

    @Test
    void creaLaOrdenEnEstadoInicialCuandoLaLlaveEsNueva() {
        referenciasExistentes();
        Orden creada = ordenEnEstado(ID_ESTADO_CREADA);
        when(repositorio.buscarPorLlaveIdempotencia(LLAVE)).thenReturn(Optional.empty());
        when(repositorio.buscarIdEstadoPorCodigo(EstadoOrden.CODIGO_INICIAL)).thenReturn(Optional.of(ID_ESTADO_CREADA));
        when(repositorio.crear(any())).thenReturn(creada);

        var resultado = servicio.crear(COMANDO);

        assertThat(resultado.nueva()).isTrue();
        assertThat(resultado.orden()).isEqualTo(creada);
        verify(repositorio).crear(new NuevaOrden(ID_CLIENTE, ID_TIPO, ID_ESTADO_CREADA,
                "WEB", LLAVE, "usuario.prueba", "peticion-1"));
    }

    @Test
    void retornaLaOrdenExistenteSinCrearCuandoLaLlaveYaExiste() {
        referenciasExistentes();
        Orden existente = ordenEnEstado(ID_ESTADO_CREADA);
        when(repositorio.buscarPorLlaveIdempotencia(LLAVE)).thenReturn(Optional.of(existente));

        var resultado = servicio.crear(COMANDO);

        assertThat(resultado.nueva()).isFalse();
        assertThat(resultado.orden()).isEqualTo(existente);
        verify(repositorio, never()).crear(any());
    }

    @Test
    void retornaLaOrdenExistenteCuandoOtraPeticionGanaLaCarrera() {
        referenciasExistentes();
        Orden ganadora = ordenEnEstado(ID_ESTADO_CREADA);
        when(repositorio.buscarPorLlaveIdempotencia(LLAVE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ganadora));
        when(repositorio.buscarIdEstadoPorCodigo(EstadoOrden.CODIGO_INICIAL)).thenReturn(Optional.of(ID_ESTADO_CREADA));
        when(repositorio.crear(any())).thenThrow(new LlaveIdempotenciaDuplicadaException(LLAVE));

        var resultado = servicio.crear(COMANDO);

        assertThat(resultado.nueva()).isFalse();
        assertThat(resultado.orden()).isEqualTo(ganadora);
    }

    @Test
    void rechazaUnClienteInexistente() {
        when(repositorio.existeCliente(ID_CLIENTE)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(COMANDO))
                .isInstanceOf(ReferenciaInexistenteException.class)
                .hasMessage("El cliente 1 no existe");
        verify(repositorio, never()).crear(any());
    }

    @Test
    void rechazaUnTipoInexistente() {
        when(repositorio.existeCliente(ID_CLIENTE)).thenReturn(true);
        when(repositorio.existeTipo(ID_TIPO)).thenReturn(false);

        assertThatThrownBy(() -> servicio.crear(COMANDO))
                .isInstanceOf(ReferenciaInexistenteException.class)
                .hasMessage("El tipo de orden 2 no existe");
        verify(repositorio, never()).crear(any());
    }

    @Test
    void fallaSiElCatalogoNoTieneElEstadoInicial() {
        referenciasExistentes();
        when(repositorio.buscarPorLlaveIdempotencia(LLAVE)).thenReturn(Optional.empty());
        when(repositorio.buscarIdEstadoPorCodigo(EstadoOrden.CODIGO_INICIAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(COMANDO)).isInstanceOf(IllegalStateException.class);
        verify(repositorio, never()).crear(any());
    }

    private void referenciasExistentes() {
        when(repositorio.existeCliente(ID_CLIENTE)).thenReturn(true);
        when(repositorio.existeTipo(ID_TIPO)).thenReturn(true);
    }
}
