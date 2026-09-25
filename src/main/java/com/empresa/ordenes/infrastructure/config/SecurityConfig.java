package com.empresa.ordenes.infrastructure.config;

import com.empresa.ordenes.infrastructure.adapter.in.rest.RespuestaErrorSeguridad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

// Solo con servidor web: un contexto sin HTTP (p. ej. la prueba de integración de persistencia) no la necesita
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private static final String ORDENES = "/api/v1/orden";
    private static final int BYTES_MINIMOS_HS256 = 32;

    /**
     * Resource Server JWT: el permiso de cada endpoint viene en el claim scope,
     * que Spring convierte en autoridades con prefijo SCOPE_.
     */
    @Bean
    @Profile("!local")
    SecurityFilterChain seguridadJwt(HttpSecurity http, RespuestaErrorSeguridad respuestaError) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(permisos -> permisos
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, ORDENES).hasAuthority("SCOPE_ordenes:crear")
                        .requestMatchers(HttpMethod.GET, ORDENES, ORDENES + "/*").hasAuthority("SCOPE_ordenes:leer")
                        .requestMatchers(HttpMethod.PUT, ORDENES + "/*/estado").hasAuthority("SCOPE_ordenes:actualizar-estado")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(respuestaError)
                        .accessDeniedHandler(respuestaError))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestaError)
                        .accessDeniedHandler(respuestaError))
                .build();
    }

    @Bean
    @Profile("!local")
    JwtDecoder jwtDecoder(@Value("${seguridad.jwt.secreto}") String secreto) {
        byte[] clave = secreto.getBytes(StandardCharsets.UTF_8);
        if (clave.length < BYTES_MINIMOS_HS256) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + BYTES_MINIMOS_HS256 + " bytes para HS256");
        }
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(clave, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Además de firma y vigencia, exige sub: de ahí sale el usuario que se guarda en la orden
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank())));
        return decoder;
    }

    // Perfil local: sin token, para probar a mano. Nunca activar fuera de desarrollo
    @Bean
    @Profile("local")
    SecurityFilterChain seguridadLocal(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(permisos -> permisos.anyRequest().permitAll())
                .build();
    }
}
