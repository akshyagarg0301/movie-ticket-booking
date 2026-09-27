package com.example.cinema;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class Security {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService users(Db db) {
        return username -> db.rows("SELECT * FROM app_user WHERE username = ?", username).stream()
            .map(row -> User.withUsername(username).password(Db.string(row, "passwordHash"))
                .roles(Db.string(row, "role")).build()).findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    @Bean SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/customers").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cities", "/api/theaters", "/api/shows", "/api/shows/*", "/api/shows/*/seats").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/bookings/**", "/api/notifications").hasRole("CUSTOMER")
                .anyRequest().denyAll())
            .httpBasic(Customizer.withDefaults())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401); res.setHeader("WWW-Authenticate", "Basic realm=cinema");
                    res.setContentType("application/problem+json");
                    json.writeValue(res.getOutputStream(), Map.of("status", 401, "title", "Unauthorized", "detail", "Valid credentials are required."));
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403); res.setContentType("application/problem+json");
                    json.writeValue(res.getOutputStream(), Map.of("status", 403, "title", "Forbidden", "detail", "Your role cannot perform this action."));
                })).build();
    }

    @Bean ApplicationRunner admin(Db db, PasswordEncoder passwords,
            @Value("${cinema.admin.username}") String username, @Value("${cinema.admin.password}") String password) {
        return args -> {
            if (db.jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN'", Integer.class) == 0) {
                if (!username.matches("[a-zA-Z0-9_.-]{3,50}") || password.length() < 10
                        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                    throw new IllegalStateException("Set ADMIN_PASSWORD (10+ characters, at most 72 UTF-8 bytes) before the first start.");
                }
                db.jdbc.update("INSERT INTO app_user VALUES (?, ?, 'ADMIN')", username, passwords.encode(password));
            }
        };
    }
}
