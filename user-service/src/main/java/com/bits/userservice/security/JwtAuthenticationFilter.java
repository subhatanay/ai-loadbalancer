package com.bits.userservice.security;

import com.bits.userservice.model.User;
import com.bits.userservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Create a wrapper to add user headers
                    HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request) {
                        private final Map<String, String> customHeaders = new HashMap<>();
                        
                        {
                            customHeaders.put("X-User-Email", userEmail);
                            customHeaders.put("X-User-ID", String.valueOf(((User) userDetails).getId()));
                        }
                        
                        @Override
                        public String getHeader(String name) {
                            String customHeader = customHeaders.get(name);
                            if (customHeader != null) {
                                return customHeader;
                            }
                            return super.getHeader(name);
                        }
                        
                        @Override
                        public Enumeration<String> getHeaderNames() {
                            Set<String> names = new HashSet<>(customHeaders.keySet());
                            Enumeration<String> originalNames = super.getHeaderNames();
                            while (originalNames.hasMoreElements()) {
                                names.add(originalNames.nextElement());
                            }
                            return Collections.enumeration(names);
                        }
                        
                        @Override
                        public Enumeration<String> getHeaders(String name) {
                            String customHeader = customHeaders.get(name);
                            if (customHeader != null) {
                                return Collections.enumeration(Collections.singletonList(customHeader));
                            }
                            return super.getHeaders(name);
                        }
                    };
                    
                    filterChain.doFilter(wrappedRequest, response);
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}