package com.empresa.ordenes.infrastructure.adapter.in.rest;

/**
 * Usuario autenticado de la petición en curso. Los casos de uso lo reciben en el comando:
 * nunca sale del cuerpo de la petición ni el dominio conoce Spring Security.
 */
public interface UsuarioActualProvider {

    String obtener();
}
