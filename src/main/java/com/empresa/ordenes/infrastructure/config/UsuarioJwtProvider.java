package com.empresa.ordenes.infrastructure.config;

import com.empresa.ordenes.infrastructure.adapter.in.rest.UsuarioActualProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class UsuarioJwtProvider implements UsuarioActualProvider {

    // Con JWT, el nombre de la autenticación es el claim sub
    @Override
    public String obtener() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            throw new IllegalStateException("No hay un usuario autenticado en la petición");
        }
        return autenticacion.getName();
    }
}
