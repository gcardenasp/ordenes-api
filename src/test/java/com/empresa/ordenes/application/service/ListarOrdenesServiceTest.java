package com.empresa.ordenes.application.service;

import com.empresa.ordenes.application.port.out.OrdenRepositorio;
import com.empresa.ordenes.domain.exception.DatosInvalidosException;
import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static com.empresa.ordenes.application.service.OrdenesDePrueba.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarOrdenesServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 25);

    @Mock
    private OrdenRepositorio repositorio;

    @InjectMocks
    private ListarOrdenesService servicio;

    @Test
    void delegaElFiltroAlPuerto() {
        var filtro = new FiltroOrdenes(ID_ESTADO_CREADA, HOY.minusDays(7), HOY, 0, 20);
        Pagina<Orden> pagina = new Pagina<>(List.of(ordenEnEstado(ID_ESTADO_CREADA)), 0, 20, 1, 1);
        when(repositorio.listar(filtro)).thenReturn(pagina);

        assertThat(servicio.listar(filtro)).isEqualTo(pagina);
    }

    @Test
    void rechazaFechaInicioMayorQueFechaFin() {
        var filtro = new FiltroOrdenes(null, HOY, HOY.minusDays(1), 0, 20);

        assertThatThrownBy(() -> servicio.listar(filtro))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("La fecha inicial no puede ser mayor que la fecha final");
        verify(repositorio, never()).listar(any());
    }

    @Test
    void aceptaElMismoDiaComoInicioYFin() {
        var filtro = new FiltroOrdenes(null, HOY, HOY, 0, 20);
        when(repositorio.listar(filtro)).thenReturn(new Pagina<>(List.of(), 0, 20, 0, 0));

        assertThat(servicio.listar(filtro).contenido()).isEmpty();
    }

    @Test
    void aceptaUnaSolaFechaSinLaOtra() {
        var soloInicio = new FiltroOrdenes(null, HOY, null, 0, 20);
        var soloFin = new FiltroOrdenes(null, null, HOY, 0, 20);
        when(repositorio.listar(any())).thenReturn(new Pagina<>(List.of(), 0, 20, 0, 0));

        servicio.listar(soloInicio);
        servicio.listar(soloFin);

        verify(repositorio).listar(soloInicio);
        verify(repositorio).listar(soloFin);
    }
}
