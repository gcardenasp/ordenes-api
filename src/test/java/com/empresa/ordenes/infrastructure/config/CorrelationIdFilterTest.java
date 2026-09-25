package com.empresa.ordenes.infrastructure.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    @Test
    void usaElIdRecibidoYLoDevuelveEnLaRespuesta() throws Exception {
        var peticion = new MockHttpServletRequest("GET", "/api/v1/orden/10");
        peticion.addHeader("X-Correlation-Id", "peticion-123");
        var enMdc = new AtomicReference<String>();

        var respuesta = filtrar(peticion, (req, res) -> enMdc.set(MDC.get(Trazabilidad.MDC_ID_PETICION)));

        assertThat(respuesta.getHeader("X-Correlation-Id")).isEqualTo("peticion-123");
        assertThat(enMdc.get()).isEqualTo("peticion-123");
    }

    @Test
    void generaUnIdSiNoLlega() throws Exception {
        var respuesta = filtrar(new MockHttpServletRequest("GET", "/api/v1/orden"), (req, res) -> { });

        assertThat(respuesta.getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
    }

    @Test
    void reemplazaUnIdConFormatoInvalido() throws Exception {
        var largo = new MockHttpServletRequest("GET", "/api/v1/orden");
        largo.addHeader("X-Correlation-Id", "x".repeat(101));
        var conSaltoDeLinea = new MockHttpServletRequest("GET", "/api/v1/orden");
        conSaltoDeLinea.addHeader("X-Correlation-Id", "abc\nlog falso");

        assertThat(filtrar(largo, (req, res) -> { }).getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
        assertThat(filtrar(conSaltoDeLinea, (req, res) -> { }).getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
    }

    @Test
    void limpiaElMdcAlTerminarAunqueHayaError() {
        var peticion = new MockHttpServletRequest("GET", "/api/v1/orden/10");

        try {
            filtrar(peticion, (req, res) -> {
                MDC.put(Trazabilidad.MDC_ID_ORDEN, "10");
                throw new IllegalStateException("falla");
            });
        } catch (Exception ignorada) {
            // se verifica el MDC, no la excepción
        }

        assertThat(MDC.get(Trazabilidad.MDC_ID_PETICION)).isNull();
        assertThat(MDC.get(Trazabilidad.MDC_ID_ORDEN)).isNull();
    }

    @Test
    void escribeUnLogJsonAlTerminarLaPeticion(CapturedOutput salida) throws Exception {
        var peticion = new MockHttpServletRequest("PUT", "/api/v1/orden/10/estado");
        peticion.addHeader("X-Correlation-Id", "peticion-log");

        filtrar(peticion, (req, res) -> {
            MDC.put(Trazabilidad.MDC_ID_ORDEN, "10");
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(422);
        });

        assertThat(salida.getOut()).contains("Petición finalizada", "peticion-log", "/api/v1/orden/10/estado",
                "\"estadoHttp\":422", "\"idOrden\":\"10\"", "duracionMs");
    }

    private MockHttpServletResponse filtrar(MockHttpServletRequest peticion, FilterChain siguiente) throws Exception {
        var respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, siguiente);
        return respuesta;
    }
}
