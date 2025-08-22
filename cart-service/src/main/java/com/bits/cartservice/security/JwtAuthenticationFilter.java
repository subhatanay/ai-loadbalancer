package com.bits.cartservice.security;

import com.bits.cartservice.exception.TokenValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        final String userId;

        // Skip JWT validation for public endpoints
        if (isPublicEndpoint(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No JWT token found in request headers");
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);
            userId = jwtService.extractUserId(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                if (jwtService.isTokenValid(jwt)) {
                    // Create a simple authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            Collections.emptyList() // No authorities needed for cart service
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    // Add user information to request attributes for controller access
                    request.setAttribute("X-User-Email", userEmail);
                    request.setAttribute("X-User-ID", userId);
                    
                    log.debug("JWT token validated successfully for user: {}", userEmail);
                } else {
                    log.warn("Invalid JWT token for user: {}", userEmail);
                    throw new TokenValidationException("Invalid or expired JWT token");
                }
            }
        } catch (Exception e) {
            log.error("JWT token validation failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            response.setContentType("application/json");
            return;
        }

        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String uri) {
        return uri.contains("/actuator/") || 
               uri.contains("/health") || 
               uri.contains("/metrics") || 
               uri.contains("/prometheus");
    }
}
