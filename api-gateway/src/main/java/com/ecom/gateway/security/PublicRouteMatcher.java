package com.ecom.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "security")
public class PublicRouteMatcher {
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/v3/api-docs",
            "/swagger-ui"));

    public PublicRouteMatcher() {
    }

    PublicRouteMatcher(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(publicPath ->
                path.equals(publicPath) || (isPrefix(publicPath) && path.startsWith(publicPath)));
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    private boolean isPrefix(String publicPath) {
        return publicPath.equals("/v3/api-docs") || publicPath.equals("/swagger-ui");
    }
}
