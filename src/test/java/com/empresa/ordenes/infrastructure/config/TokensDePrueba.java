package com.empresa.ordenes.infrastructure.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

final class TokensDePrueba {

    static final String SECRETO = "secreto-de-prueba-con-mas-de-32-bytes-para-hs256";

    private TokensDePrueba() {
    }

    static String token(String sub, String scope) {
        return firmar(SECRETO, sub, scope, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    static String firmar(String secreto, String sub, String scope, Instant expiracion) {
        var claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .claim("scope", scope)
                .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .expirationTime(Date.from(expiracion))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }
}
