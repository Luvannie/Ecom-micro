package com.ecom.order.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GatewayUserContextFilter extends OncePerRequestFilter {
    public static final String USER_ID_ATTRIBUTE = "gatewayUserId";
    public static final String USER_EMAIL_ATTRIBUTE = "gatewayUserEmail";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/orders")) {
            filterChain.doFilter(request, response);
            return;
        }
        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        if (userId == null || userId.isBlank() || email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        request.setAttribute(USER_EMAIL_ATTRIBUTE, email);
        filterChain.doFilter(request, response);
    }
}
