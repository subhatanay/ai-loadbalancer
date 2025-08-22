package com.bits.userservice.controller;

import com.bits.userservice.dto.*;
import com.bits.userservice.service.UserService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    private final UserService userService;
    private final Counter registrationCounter;
    private final Counter loginCounter;

    public UserController(UserService userService, MeterRegistry meterRegistry) {
        this.userService = userService;
        this.registrationCounter = Counter.builder("user_registrations_total")
                .description("Total number of user registrations")
                .register(meterRegistry);
        this.loginCounter = Counter.builder("user_logins_total")
                .description("Total number of user logins")
                .register(meterRegistry);
    }

    @PostMapping("/register")
    @Timed(value = "user_registration_duration", description = "Time taken to register a user")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegistrationRequest request) {
        log.info("Received user registration request for email: {}", request.getEmail());

        UserResponse response = userService.registerUser(request);
        registrationCounter.increment();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Timed(value = "user_login_duration", description = "Time taken to authenticate a user")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for email: {}", request.getEmail());

        AuthResponse response = userService.authenticate(request);
        loginCounter.increment();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    @Timed(value = "user_profile_fetch_duration", description = "Time taken to fetch user profile")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("X-User-Email") String email) {
        log.debug("Fetching current user profile for: {}", email);

        UserResponse response = userService.getUserByEmail(email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        log.debug("Fetching user by ID: {}", userId);

        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }
}

