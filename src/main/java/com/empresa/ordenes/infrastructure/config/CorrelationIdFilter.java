package com.empresa.ordenes.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Primer filtro de la cadena (antes que la seguridad), para que también los 401 y 403
 * lleven el id de petición y queden en el log de fin de petición.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    // Cabe en ORDEN_HISTORICO.ID_PETICION y evita escribir texto arbitrario del cliente en los logs
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        String idPeticion = idPeticion(peticion.getHeader(Trazabilidad.HEADER_ID_PETICION));
        MDC.put(Trazabilidad.MDC_ID_PETICION, idPeticion);
        respuesta.setHeader(Trazabilidad.HEADER_ID_PETICION, idPeticion);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            log.atInfo()
                    .addKeyValue("metodo", peticion.getMethod())
                    .addKeyValue("endpoint", peticion.getRequestURI())
                    .addKeyValue("estadoHttp", respuesta.getStatus())
                    .addKeyValue("duracionMs", (System.nanoTime() - inicio) / 1_000_000)
                    .log("Petición finalizada");
            MDC.remove(Trazabilidad.MDC_ID_PETICION);
            MDC.remove(Trazabilidad.MDC_ID_ORDEN);
            MDC.remove(Trazabilidad.MDC_CANAL);
        }
    }

    private static String idPeticion(String recibido) {
        return recibido != null && FORMATO_VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
    }
}
