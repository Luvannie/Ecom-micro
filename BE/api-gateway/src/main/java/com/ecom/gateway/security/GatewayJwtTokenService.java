package com.ecom.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class GatewayJwtTokenService {
    private final GatewayJwtProperties properties;
    private final Clock clock;

    @Autowired
    public GatewayJwtTokenService(GatewayJwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public GatewayJwtTokenService(GatewayJwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    GatewayJwtPrincipal parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret()))) {
                throw new IllegalArgumentException("Invalid signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!properties.getIssuer().equals(claims.getIssuer())) {
                throw new IllegalArgumentException("Invalid issuer");
            }
            if (claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(clock.instant())) {
                throw new IllegalArgumentException("Token expired");
            }
            return new GatewayJwtPrincipal(UUID.fromString(claims.getSubject()), claims.getStringClaim("email"), claims.getStringListClaim("roles"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid token", ex);
        }
    }

    String createAccessToken(UUID userId, String email, List<String> roles) {
        try {
            Instant now = clock.instant();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getIssuer())
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("roles", roles)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create token", ex);
        }
    }

    private byte[] secret() {
        return properties.getSecret().getBytes(StandardCharsets.UTF_8);
    }
}
