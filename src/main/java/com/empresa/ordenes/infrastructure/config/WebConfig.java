package com.empresa.ordenes.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // El contrato declara /api/v1 en servers.url y las rutas sin prefijo. Se agrega solo a los
    // controllers del adaptador REST (y no como context-path) para que Swagger UI quede en /swagger-ui.html.
    private static final String PREFIJO_API = "/api/v1";
    private static final String PAQUETE_REST = "com.empresa.ordenes.infrastructure.adapter.in.rest";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(PREFIJO_API, HandlerTypePredicate.forBasePackage(PAQUETE_REST));
    }
}
