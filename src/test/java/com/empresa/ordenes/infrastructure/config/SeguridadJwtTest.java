package com.empresa.ordenes.infrastructure.config;

import com.empresa.ordenes.application.port.in.CambiarEstadoOrdenUseCase;
import com.empresa.ordenes.application.port.in.ConsultarOrdenUseCase;
import com.empresa.ordenes.application.port.in.CrearOrdenCommand;
import com.empresa.ordenes.application.port.in.CrearOrdenUseCase;
import com.empresa.ordenes.application.port.in.ListarOrdenesUseCase;
import com.empresa.ordenes.application.port.in.ResultadoCrearOrden;
import com.empresa.ordenes.domain.model.Orden;
import com.empresa.ordenes.domain.model.Pagina;
import com.empresa.ordenes.infrastructure.adapter.in.rest.OrdenController;
import com.empresa.ordenes.infrastructure.adapter.in.rest.RespuestaErrorSeguridad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrdenController.class)
@Import({SecurityConfig.class, RespuestaErrorSeguridad.class, UsuarioJwtProvider.class})
@TestPropertySource(properties = "seguridad.jwt.secreto=" + TokensDePrueba.SECRETO)
class SeguridadJwtTest {

    private static final Orden ORDEN = new Orden(10L, 1L, 2L, 1L, "WEB", "llave-1", "ana",
            LocalDateTime.of(2026, 9, 25, 10, 0), null, null);

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

    @BeforeEach
    void casosDeUso() {
        when(crearOrden.crear(any())).thenReturn(ResultadoCrearOrden.nueva(ORDEN));
        when(consultarOrden.consultar(any())).thenReturn(ORDEN);
        when(cambiarEstado.cambiarEstado(any())).thenReturn(ORDEN);
        when(listarOrdenes.listar(any())).thenReturn(new Pagina<>(List.of(), 0, 20, 0, 0));
    }

    static Stream<Arguments> permisoPorEndpoint() {
        return Stream.of(
                Arguments.of("crear", "ordenes:crear", 201),
                Arguments.of("consultar", "ordenes:leer", 200),
                Arguments.of("listar", "ordenes:leer", 200),
                Arguments.of("actualizarEstado", "ordenes:actualizar-estado", 200));
    }

    @ParameterizedTest(name = "{0} con {1}")
    @MethodSource("permisoPorEndpoint")
    void permiteElEndpointConSuPermiso(String endpoint, String permiso, int estadoEsperado) throws Exception {
        mvc.perform(peticion(endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.token("ana", permiso)))
                .andExpect(status().is(estadoEsperado));
    }

    @ParameterizedTest(name = "{0} sin {1}")
    @MethodSource("permisoPorEndpoint")
    void responde403SinElPermisoDelEndpoint(String endpoint, String permiso, int estadoEsperado) throws Exception {
        String otrosPermisos = Stream.of("ordenes:crear", "ordenes:leer", "ordenes:actualizar-estado")
                .filter(p -> !p.equals(permiso))
                .reduce((a, b) -> a + " " + b).orElseThrow();

        mvc.perform(peticion(endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.token("ana", otrosPermisos)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.fecha").exists());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("permisoPorEndpoint")
    void responde401SinToken(String endpoint, String permiso, int estadoEsperado) throws Exception {
        mvc.perform(peticion(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"))
                .andExpect(jsonPath("$.mensaje").value("Token ausente o inválido"));
    }

    @Test
    void responde401SiElTokenEstaFirmadoConOtraClave() throws Exception {
        String ajeno = TokensDePrueba.firmar("otra-clave-distinta-tambien-de-mas-de-32-bytes", "ana", "ordenes:leer",
                Instant.now().plus(10, ChronoUnit.MINUTES));

        mvc.perform(get("/api/v1/orden/10").header(HttpHeaders.AUTHORIZATION, "Bearer " + ajeno))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTORIZADO"));
    }

    @Test
    void responde401SiElTokenEstaVencido() throws Exception {
        String vencido = TokensDePrueba.firmar(TokensDePrueba.SECRETO, "ana", "ordenes:leer",
                Instant.now().minus(10, ChronoUnit.MINUTES));

        mvc.perform(get("/api/v1/orden/10").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void responde401SiElTokenNoTieneSub() throws Exception {
        String sinSub = TokensDePrueba.firmar(TokensDePrueba.SECRETO, null, "ordenes:leer",
                Instant.now().plus(10, ChronoUnit.MINUTES));

        mvc.perform(get("/api/v1/orden/10").header(HttpHeaders.AUTHORIZATION, "Bearer " + sinSub))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elUsuarioDeLaOrdenSaleDelSubDelTokenYElIdDePeticionDelHeader() throws Exception {
        mvc.perform(peticion("crear")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.token("ana", "ordenes:crear"))
                        .header("X-Correlation-Id", "peticion-123"))
                .andExpect(status().isCreated());

        verify(crearOrden).crear(new CrearOrdenCommand(1L, 2L, "WEB", "llave-1", "ana", "peticion-123"));
    }

    private static MockHttpServletRequestBuilder peticion(String endpoint) {
        return switch (endpoint) {
            case "crear" -> post("/api/v1/orden").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"idCliente": 1, "idTipo": 2, "canal": "WEB", "llaveIdempotencia": "llave-1"}""");
            case "consultar" -> get("/api/v1/orden/10");
            case "listar" -> get("/api/v1/orden");
            case "actualizarEstado" -> put("/api/v1/orden/10/estado").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idEstadoNuevo\": 2}");
            default -> throw new IllegalArgumentException(endpoint);
        };
    }
}
