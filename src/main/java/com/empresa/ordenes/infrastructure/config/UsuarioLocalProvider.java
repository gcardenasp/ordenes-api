package com.empresa.ordenes.infrastructure.config;

import com.empresa.ordenes.infrastructure.adapter.in.rest.UsuarioActualProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class UsuarioLocalProvider implements UsuarioActualProvider {

    private final String usuario;

    public UsuarioLocalProvider(@Value("${seguridad.usuario-local}") String usuario) {
        this.usuario = usuario;
    }

    @Override
    public String obtener() {
        return usuario;
    }
}
