package com.ecom.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that materialises the authenticated user into request
 * attributes ({@link #USER_ID_ATTRIBUTE}, {@link #USER_EMAIL_ATTRIBUTE}) so
 * downstream services can call {@link GatewayUserContext#from(HttpServletRequest)}.
 *
 * <p>Each service supplies the Ant-style URL patterns it considers
 * protected (e.g. {@code /api/cart/**}). Requests outside those patterns
 * pass through untouched. Requests inside the patterns require non-blank
 * {@code X-User-Id} and {@code X-User-Email} headers, which the API gateway
 * forwards after verifying the Keycloak access token.
 */
public class GatewayUserContextFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "gatewayUserId";
    public static final String USER_EMAIL_ATTRIBUTE = "gatewayUserEmail";

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final List<String> protectedPathPatterns;

    public GatewayUserContextFilter(List<String> protectedPathPatterns) {
        this.protectedPathPatterns = List.copyOf(protectedPathPatterns);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!requiresAuth(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String userId = request.getHeader(USER_ID_HEADER);
        String email = request.getHeader(USER_EMAIL_HEADER);
        if (userId == null || userId.isBlank() || email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        request.setAttribute(USER_EMAIL_ATTRIBUTE, email);
        chain.doFilter(request, response);
    }

    private boolean requiresAuth(String uri) {
        return protectedPathPatterns.stream().anyMatch(p -> MATCHER.match(p, uri));
    }
}
