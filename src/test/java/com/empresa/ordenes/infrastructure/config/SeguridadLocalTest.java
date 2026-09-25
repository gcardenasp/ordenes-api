package com.empresa.ordenes.infrastructure.config;

import com.empresa.ordenes.application.port.in.CrearOrdenCommand;
import com.empresa.ordenes.application.port.in.CrearOrdenUseCase;
import com.empresa.ordenes.application.port.in.ResultadoCrearOrden;
import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenUseCase;
import com.empresa.ordenes.application.port.in.ConsultarOrdenUseCase;
import com.empresa.ordenes.application.port.in.ListarOrdenesUseCase;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.infrastructure.adapter.in.rest.OrdenController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrdenController.class)
@ActiveProfiles("local")
@Import({SecurityConfig.class, UsuarioLocalProvider.class})
class SeguridadLocalTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CrearOrdenUseCase crearOrden;
    @MockitoBean
    private ConsultarOrdenUseCase consultarOrden;
    @MockitoBean
    private CambiarEstadoOrdenUseCase cambiarEstado;
    @MockitoBean
    private ListarOrdenesUseCase listarOrdenes;

    @Test
    void permiteLaPeticionSinTokenConElUsuarioLocal() throws Exception {
        when(crearOrden.crear(any())).thenReturn(ResultadoCrearOrden.nueva(new Orden(10L, 1L, 2L, 1L, "WEB",
                "llave-1", "usuario.local", LocalDateTime.of(2026, 9, 25, 10, 0), null, null)));

        mvc.perform(post("/api/v1/orden").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idCliente": 1, "idTipo": 2, "canal": "WEB", "llaveIdempotencia": "llave-1"}"""))
                .andExpect(status().isCreated());

        verify(crearOrden).crear(argThat((CrearOrdenCommand comando) -> comando.usuario().equals("usuario.local")));
    }
}
