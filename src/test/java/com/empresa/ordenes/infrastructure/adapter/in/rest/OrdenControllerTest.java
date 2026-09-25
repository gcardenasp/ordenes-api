package com.empresa.ordenes.infrastructure.adapter.in.rest;

import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenCommand;
import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenUseCase;
import com.empresa.ordenes.application.port.in.ConsultarOrdenUseCase;
import com.empresa.ordenes.application.port.in.CrearOrdenCommand;
import com.empresa.ordenes.application.port.in.CrearOrdenUseCase;
import com.empresa.ordenes.application.port.in.ListarOrdenesUseCase;
import com.empresa.ordenes.application.port.in.ResultadoCrearOrden;
import com.empresa.ordenes.domain.exception.DatosInvalidosException;
import com.empresa.ordenes.domain.exception.OrdenBloqueadaException;
import com.empresa.ordenes.domain.exception.OrdenNoEncontradaException;
import com.empresa.ordenes.domain.exception.ReferenciaInexistenteException;
import com.empresa.ordenes.domain.exception.TransicionInvalidaException;
import com.empresa.ordenes.domain.model.FiltroOrdenes;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Los filtros de seguridad se prueban en la Fase 5; aquí solo el contrato HTTP y la traducción de errores
@WebMvcTest(OrdenController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrdenControllerTest {

    private static final String USUARIO = "usuario.token";
    private static final Orden ORDEN = new Orden(10L, 1L, 2L, 1L, "WEB", "llave-1", USUARIO,
            LocalDateTime.of(2026, 9, 25, 20, 30), null, null);

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
    @MockitoBean
    private UsuarioActualProvider usuarioActual;

    @BeforeEach
    void usuarioAutenticado() {
        when(usuarioActual.obtener()).thenReturn(USUARIO);
    }

    @Nested
    class CrearOrden {

        private static final String CUERPO = """
                {"idCliente": 1, "idTipo": 2, "canal": "WEB", "llaveIdempotencia": "llave-1"}""";

        @Test
        void responde201ConLaOrdenCreadaYElUsuarioDelToken() throws Exception {
            when(crearOrden.crear(any())).thenReturn(ResultadoCrearOrden.nueva(ORDEN));

            crear(CUERPO)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.idEstado").value(1))
                    .andExpect(jsonPath("$.usuarioCreacion").value(USUARIO))
                    .andExpect(jsonPath("$.fechaCreacion").value("2026-09-25T20:30:00-05:00"));
            verify(crearOrden).crear(new CrearOrdenCommand(1L, 2L, "WEB", "llave-1", USUARIO, null));
        }

        @Test
        void ignoraElUsuarioSiVieneEnElCuerpo() throws Exception {
            when(crearOrden.crear(any())).thenReturn(ResultadoCrearOrden.nueva(ORDEN));

            crear("""
                    {"idCliente": 1, "idTipo": 2, "canal": "WEB", "llaveIdempotencia": "llave-1",
                     "usuarioCreacion": "intruso"}""")
                    .andExpect(status().isCreated());
            verify(crearOrden).crear(new CrearOrdenCommand(1L, 2L, "WEB", "llave-1", USUARIO, null));
        }

        @Test
        void responde200ConLaOrdenExistenteSiLaLlaveYaExistia() throws Exception {
            when(crearOrden.crear(any())).thenReturn(ResultadoCrearOrden.existente(ORDEN));

            crear(CUERPO)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        void responde400SiFaltaUnCampoObligatorio() throws Exception {
            crear("""
                    {"idTipo": 2, "canal": "WEB", "llaveIdempotencia": "llave-1"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.mensaje").value("idCliente: no debe ser nulo"))
                    .andExpect(jsonPath("$.fecha").exists());
            verifyNoInteractions(crearOrden);
        }

        @Test
        void responde400SiElCanalEstaVacioOEsDemasiadoLargo() throws Exception {
            crear("""
                    {"idCliente": 1, "idTipo": 2, "canal": "", "llaveIdempotencia": "%s"}""".formatted("x".repeat(101)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(
                            "canal: el tamaño debe estar entre 1 y 30; llaveIdempotencia: el tamaño debe estar entre 1 y 100"));
            verifyNoInteractions(crearOrden);
        }

        @Test
        void responde400SiElJsonEsInvalido() throws Exception {
            crear("{\"idCliente\": \"uno\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }

        @Test
        void responde400SiElClienteNoExiste() throws Exception {
            when(crearOrden.crear(any())).thenThrow(new ReferenciaInexistenteException("El cliente 1 no existe"));

            crear(CUERPO)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("REFERENCIA_INEXISTENTE"))
                    .andExpect(jsonPath("$.mensaje").value("El cliente 1 no existe"));
        }

        private ResultActions crear(String cuerpo) throws Exception {
            return mvc.perform(post("/api/v1/orden").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
        }
    }

    @Nested
    class ConsultarOrden {

        @Test
        void responde200ConLaOrden() throws Exception {
            when(consultarOrden.consultar(10L)).thenReturn(ORDEN);

            mvc.perform(get("/api/v1/orden/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.llaveIdempotencia").value("llave-1"));
        }

        @Test
        void responde404SiNoExiste() throws Exception {
            when(consultarOrden.consultar(99L)).thenThrow(new OrdenNoEncontradaException(99L));

            mvc.perform(get("/api/v1/orden/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("ORDEN_NO_ENCONTRADA"))
                    .andExpect(jsonPath("$.mensaje").value("La orden 99 no existe"));
        }

        @Test
        void responde400SiElIdNoEsNumerico() throws Exception {
            mvc.perform(get("/api/v1/orden/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.mensaje").value("El valor del parámetro 'id' no es válido"));
        }
    }

    @Nested
    class ActualizarEstado {

        private static final String CUERPO = """
                {"idEstadoNuevo": 2, "observacion": "Asignada a cuadrilla"}""";

        @Test
        void responde200ConLaOrdenActualizada() throws Exception {
            when(cambiarEstado.cambiarEstado(any())).thenReturn(ORDEN);

            actualizar(CUERPO).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
            verify(cambiarEstado).cambiarEstado(
                    new CambiarEstadoOrdenCommand(10L, 2L, USUARIO, "Asignada a cuadrilla", null));
        }

        @Test
        void responde400SiFaltaElEstadoNuevo() throws Exception {
            actualizar("{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("idEstadoNuevo: no debe ser nulo"));
            verify(cambiarEstado, never()).cambiarEstado(any());
        }

        @Test
        void responde400SiElProcedimientoRechazaLosDatos() throws Exception {
            when(cambiarEstado.cambiarEstado(any()))
                    .thenThrow(new DatosInvalidosException("La orden, el estado nuevo y el usuario son obligatorios"));

            actualizar(CUERPO).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }

        @Test
        void responde404SiLaOrdenNoExiste() throws Exception {
            when(cambiarEstado.cambiarEstado(any())).thenThrow(new OrdenNoEncontradaException(10L));

            actualizar(CUERPO).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ORDEN_NO_ENCONTRADA"));
        }

        @Test
        void responde409SiLaOrdenEstaBloqueada() throws Exception {
            when(cambiarEstado.cambiarEstado(any())).thenThrow(new OrdenBloqueadaException(10L));

            actualizar(CUERPO).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ORDEN_BLOQUEADA"));
        }

        @Test
        void responde422SiLaTransicionNoEstaPermitida() throws Exception {
            when(cambiarEstado.cambiarEstado(any())).thenThrow(new TransicionInvalidaException(10L, 2L));

            actualizar(CUERPO)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
        }

        private ResultActions actualizar(String cuerpo) throws Exception {
            return mvc.perform(put("/api/v1/orden/10/estado").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
        }
    }

    @Nested
    class ListarOrdenes {

        @Test
        void responde200ConLaPaginaYUsaLosValoresPorDefecto() throws Exception {
            var filtro = new FiltroOrdenes(null, null, null, 0, 20);
            when(listarOrdenes.listar(filtro)).thenReturn(new Pagina<>(List.of(ORDEN), 0, 20, 1, 1));

            mvc.perform(get("/api/v1/orden"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].id").value(10))
                    .andExpect(jsonPath("$.pagina").value(0))
                    .andExpect(jsonPath("$.tamano").value(20))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.totalPaginas").value(1));
        }

        @Test
        void pasaTodosLosFiltrosAlCasoDeUso() throws Exception {
            var filtro = new FiltroOrdenes(3L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 2, 50);
            when(listarOrdenes.listar(filtro)).thenReturn(new Pagina<>(List.of(), 2, 50, 0, 0));

            mvc.perform(get("/api/v1/orden?estado=3&fechaInicio=2026-09-01&fechaFin=2026-09-30&pagina=2&tamano=50"))
                    .andExpect(status().isOk());
            verify(listarOrdenes).listar(filtro);
        }

        @Test
        void responde400SiFechaInicioEsMayorQueFechaFin() throws Exception {
            when(listarOrdenes.listar(any()))
                    .thenThrow(new DatosInvalidosException("La fecha inicial no puede ser mayor que la fecha final"));

            mvc.perform(get("/api/v1/orden?fechaInicio=2026-09-30&fechaFin=2026-09-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }

        @Test
        void responde400SiElTamanoEstaFueraDeRango() throws Exception {
            mvc.perform(get("/api/v1/orden?tamano=101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("tamano: debe ser menor que o igual a 100"));
            verifyNoInteractions(listarOrdenes);
        }

        @Test
        void responde400SiLaPaginaEsNegativa() throws Exception {
            mvc.perform(get("/api/v1/orden?pagina=-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("pagina: debe ser mayor que o igual a 0"));
        }

        @Test
        void responde400SiLaFechaNoTieneFormatoValido() throws Exception {
            mvc.perform(get("/api/v1/orden?fechaInicio=25-09-2026"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value("El valor del parámetro 'fechaInicio' no es válido"));
        }
    }

    @Nested
    class ErroresGenerales {

        @Test
        void responde500SinExponerElDetalleInterno() throws Exception {
            when(consultarOrden.consultar(10L)).thenThrow(new IllegalStateException("ORA-01017: detalle interno"));

            mvc.perform(get("/api/v1/orden/10"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                    .andExpect(jsonPath("$.mensaje").value("Ocurrió un error interno, intente más tarde"));
        }

        @Test
        void responde405ConElEsquemaDeError() throws Exception {
            mvc.perform(delete("/api/v1/orden/10"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_SOPORTADA"));
        }

        @Test
        void responde415SiElCuerpoNoEsJson() throws Exception {
            mvc.perform(post("/api/v1/orden").contentType(MediaType.TEXT_PLAIN).content("hola"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_SOPORTADA"));
        }

        @Test
        void responde404SiLaRutaNoExiste() throws Exception {
            mvc.perform(get("/api/v1/no-existe"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        }

        @Test
        void lasRutasSinPrefijoNoExisten() throws Exception {
            mvc.perform(get("/orden/10")).andExpect(status().isNotFound());
        }
    }
}
