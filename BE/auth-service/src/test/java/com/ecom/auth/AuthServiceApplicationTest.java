package com.ecom.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "keycloak.jwk-set-uri=http://localhost:8081/realms/ecom/protocol/openid-connect/certs",
        "keycloak.issuer=http://localhost:8081/realms/ecom"
})
class AuthServiceApplicationTest {
    @Test
    void contextLoads() {
    }
}
