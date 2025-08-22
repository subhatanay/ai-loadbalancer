package com.bits.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    @Autowired
    private JwtService jwtService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        jwt = authHeader.substring(7);
        
        try {
            // Based on memory: JWT subject contains user ID for proper authentication
            String userId = jwtService.extractUserId(jwt);
            username = userId; // For compatibility with existing validation
            
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                if (jwtService.validateToken(jwt, userId)) {
                    UserDetails userDetails = User.builder()
                        .username(userId)
                        .password("")
                        .authorities(new ArrayList<>())
                        .build();
                    
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                    // Add userId to request attributes for controller access (same as order service)
                    request.setAttribute("userId", userId);
                    request.setAttribute("username", userId); // For backward compatibility
                    
                    logger.debug("Successfully authenticated user: {}", userId);
                } else {
                    logger.warn("Invalid JWT token for user: {}", userId);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing JWT token", e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Skip JWT validation for health check and actuator endpoints
        return path.startsWith("/health") || 
               path.startsWith("/actuator") ||
               path.startsWith("/api/inventory/health");
    }
}
