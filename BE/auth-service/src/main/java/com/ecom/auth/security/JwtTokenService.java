package com.ecom.auth.security;

import com.ecom.auth.domain.UserRole;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtTokenService {
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(UUID userId, String email, Set<UserRole> roles) {
        try {
            Instant now = clock.instant();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getIssuer())
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("roles", roles.stream().map(Enum::name).toList())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(properties.getAccessTokenTtlMinutes(), ChronoUnit.MINUTES)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new InvalidJwtException("Unable to create JWT", ex);
        }
    }

    public JwtPrincipal parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret());
            if (!jwt.verify(verifier)) {
                throw new InvalidJwtException("Invalid JWT signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!properties.getIssuer().equals(claims.getIssuer())) {
                throw new InvalidJwtException("Invalid JWT issuer");
            }
            if (claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(clock.instant())) {
                throw new InvalidJwtException("JWT expired");
            }
            List<String> roles = claims.getStringListClaim("roles");
            return new JwtPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.getStringClaim("email"),
                    roles == null ? Set.of() : roles.stream().collect(Collectors.toCollection(LinkedHashSet::new)));
        } catch (InvalidJwtException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidJwtException("Invalid JWT", ex);
        }
    }

    private byte[] secret() {
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new InvalidJwtException("JWT secret must be at least 32 bytes");
        }
        return bytes;
    }
}
