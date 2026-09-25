package com.empresa.ordenes.infrastructure.adapter.in.rest;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Los 401 y 403 los genera Spring Security antes de llegar al controller, así que el
 * GlobalExceptionHandler no los ve. Aquí se responden con el mismo esquema Error del contrato.
 */
@Component
public class RespuestaErrorSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    // Los manejadores estándar ponen el estado y el header WWW-Authenticate; aquí se agrega el cuerpo
    private final AuthenticationEntryPoint noAutenticado = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler sinPermiso = new BearerTokenAccessDeniedHandler();
    private final JsonMapper jsonMapper;

    public RespuestaErrorSeguridad(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException error)
            throws IOException, ServletException {
        noAutenticado.commence(peticion, respuesta, error);
        escribir(respuesta, "NO_AUTORIZADO", "Token ausente o inválido");
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException error)
            throws IOException, ServletException {
        sinPermiso.handle(peticion, respuesta, error);
        escribir(respuesta, "ACCESO_DENEGADO", "El token no tiene el permiso requerido para esta acción");
    }

    private void escribir(HttpServletResponse respuesta, String codigo, String mensaje) throws IOException {
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8);
        jsonMapper.writeValue(respuesta.getOutputStream(), OrdenRestMapper.error(codigo, mensaje));
    }
}
