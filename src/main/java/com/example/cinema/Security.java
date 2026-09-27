package com.example.cinema;

import static com.example.cinema.ApiFields.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
class Security {
    private static final String BASIC_AUTH_CHALLENGE = "Basic realm=cinema";

    @Bean
    PasswordEncoder passwords() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(Db db) {
        return username ->
                db.rows("SELECT * FROM app_user WHERE username = ?", username).stream()
                        .map(
                                row ->
                                        User.withUsername(username)
                                                .password(Db.string(row, PASSWORD_HASH))
                                                .roles(Db.enumValue(row, ROLE, Role.class).name())
                                                .build())
                        .findFirst()
                        .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.POST, ApiPaths.ROOT + ApiPaths.CUSTOMERS)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                ApiPaths.ROOT + ApiPaths.CITIES,
                                                ApiPaths.ROOT + ApiPaths.THEATERS,
                                                ApiPaths.ROOT + ApiPaths.SHOWS,
                                                ApiPaths.ROOT + ApiPaths.SHOW,
                                                ApiPaths.ROOT + ApiPaths.SHOW_SEATS)
                                        .permitAll()
                                        .requestMatchers(
                                                ApiPaths.ROOT
                                                        + ApiPaths.ADMIN
                                                        + ApiPaths.DESCENDANTS)
                                        .hasRole(Role.ADMIN.name())
                                        .requestMatchers(
                                                ApiPaths.ROOT
                                                        + ApiPaths.BOOKINGS
                                                        + ApiPaths.DESCENDANTS,
                                                ApiPaths.ROOT + ApiPaths.NOTIFICATIONS)
                                        .hasRole(Role.CUSTOMER.name())
                                        .anyRequest()
                                        .denyAll())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (req, res, e) -> {
                                                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                                                    res.setHeader(
                                                            HttpHeaders.WWW_AUTHENTICATE,
                                                            BASIC_AUTH_CHALLENGE);
                                                    res.setContentType(
                                                            MediaType
                                                                    .APPLICATION_PROBLEM_JSON_VALUE);
                                                    json.writeValue(
                                                            res.getOutputStream(),
                                                            ProblemDetail.forStatusAndDetail(
                                                                    HttpStatus.UNAUTHORIZED,
                                                                    "Valid credentials are required."));
                                                })
                                        .accessDeniedHandler(
                                                (req, res, e) -> {
                                                    res.setStatus(HttpStatus.FORBIDDEN.value());
                                                    res.setContentType(
                                                            MediaType
                                                                    .APPLICATION_PROBLEM_JSON_VALUE);
                                                    json.writeValue(
                                                            res.getOutputStream(),
                                                            ProblemDetail.forStatusAndDetail(
                                                                    HttpStatus.FORBIDDEN,
                                                                    "Your role cannot perform this action."));
                                                }))
                .build();
    }

    @Bean
    ApplicationRunner admin(
            Db db,
            PasswordEncoder passwords,
            @Value("${cinema.admin.username}") String username,
            @Value("${cinema.admin.password}") String password) {
        return args -> {
            if (db.jdbc.queryForObject(
                            "SELECT COUNT(*) FROM app_user WHERE role = ?",
                            Integer.class,
                            Role.ADMIN.name())
                    == 0) {
                if (!username.matches(ValidationRules.USERNAME_PATTERN)
                        || password.length() < ValidationRules.MIN_PASSWORD_LENGTH
                        || password.getBytes(StandardCharsets.UTF_8).length
                                > ValidationRules.MAX_PASSWORD_BYTES) {
                    throw new IllegalStateException(
                            "Set ADMIN_PASSWORD ("
                                    + ValidationRules.MIN_PASSWORD_LENGTH
                                    + "+ characters, at most "
                                    + ValidationRules.MAX_PASSWORD_BYTES
                                    + " UTF-8 bytes) before the first start.");
                }
                db.jdbc.update(
                        "INSERT INTO app_user VALUES (?, ?, ?)",
                        username,
                        passwords.encode(password),
                        Role.ADMIN.name());
            }
        };
    }
}
