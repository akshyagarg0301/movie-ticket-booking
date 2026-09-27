exec(open('work/create_project.py').read().split("write('AGENTS.md'")[0])
write('src/main/java/com/example/cinema/ApiException.java','''package com.example.cinema;

import org.springframework.http.HttpStatus;

class ApiException extends RuntimeException {
    final HttpStatus status;
    ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Resource not found"); }
}
''')
write('src/main/java/com/example/cinema/Errors.java','''package com.example.cinema;

import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class Errors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> domain(ApiException e) { return problem(e.status, e.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> malformed(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request. Check field names, values, and date formats.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> constraint(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "A duplicate value or a referenced resource prevents this operation.");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> busy(PessimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "This show is busy. Retry the request.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
''')
write('src/main/java/com/example/cinema/Db.java','''package com.example.cinema;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

@Component
class Db {
    final JdbcTemplate jdbc;
    Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                String label = rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT);
                StringBuilder key = new StringBuilder();
                boolean upper = false;
                for (char c : label.toCharArray()) {
                    if (c == '_') { upper = true; continue; }
                    key.append(upper ? Character.toUpperCase(c) : c); upper = false;
                }
                Object value = rs.getObject(i);
                if (value instanceof java.time.OffsetDateTime date) value = date.toInstant();
                row.put(key.toString(), value);
            }
            return row;
        }, args);
    }

    Map<String, Object> one(String sql, Object... args) {
        return rows(sql, args).stream().findFirst().orElseThrow(ApiException::notFound);
    }

    long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    static Instant instant(Map<String, Object> row, String key) { return (Instant) row.get(key); }
    static String string(Map<String, Object> row, String key) { return Objects.toString(row.get(key), null); }
}
''')
write('src/main/java/com/example/cinema/Requests.java','''package com.example.cinema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

final class Requests {
    record Registration(@Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") @NotNull String username,
                        @NotNull @Size(min = 10, max = 72) String password) {}
    record City(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 60) String timezone) {}
    record Theater(@Positive long cityId, @NotBlank @Size(max = 100) String name) {}
    record Screen(@Positive long theaterId, @NotBlank @Size(max = 100) String name) {}
    enum Tier { REGULAR, PREMIUM }
    record Seat(@NotNull @Pattern(regexp = "[A-Z][A-Z0-9-]{0,9}") String label, @NotNull Tier tier) {}
    record Layout(@NotEmpty @Size(max = 1000) List<@Valid Seat> seats) {}
    record Policy(@NotBlank @Size(max = 100) String name, @Min(0) @Max(10080) int cutoffMinutes,
                  @Min(0) @Max(100) int refundPercent) {}
    record Pricing(@Min(1) @Max(10000000) long regularPrice, @Min(1) @Max(10000000) long premiumPrice,
                   @Min(0) @Max(100) int weekendMarkup, @Positive long policyId) {}
    record Show(@Positive long screenId, @NotBlank @Size(max = 160) String title,
                @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull @Valid Pricing pricing) {}
    record Discount(@NotNull @Pattern(regexp = "[A-Z0-9]{3,30}") String code,
                    @Min(1) @Max(100) int percent, @Min(1) @Max(100000000) long maxDiscount,
                    @Min(0) @Max(100000000) long minSpend, @Min(1) int maxUses,
                    @NotNull Instant expiresAt) {}
    record Hold(@Positive long showId, @NotEmpty @Size(max = 10) List<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9-]{0,9}") String> seats,
                @Pattern(regexp = "[A-Z0-9]{3,30}") String discountCode) {}
    record Payment(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,100}") String idempotencyKey,
                   @NotNull @Pattern(regexp = "tok_success|tok_decline") String token) {}
    private Requests() {}
}
''')
write('src/main/java/com/example/cinema/Security.java','''package com.example.cinema;

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

    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json) throws Exception {
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
''')
write('src/main/java/com/example/cinema/Pricing.java','''package com.example.cinema;

import java.time.*;

final class Pricing {
    static long seat(long base, int weekendMarkup, Instant showTime, ZoneId zone) {
        DayOfWeek day = showTime.atZone(zone).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? percent(base, 100 + weekendMarkup) : base;
    }

    static long discount(long subtotal, int percent, long cap) {
        return Math.min(subtotal, Math.min(cap, percent(subtotal, percent)));
    }

    static long refund(long paid, int percent, int cutoffMinutes, Instant startsAt, Instant now) {
        return !now.isAfter(startsAt.minusSeconds(cutoffMinutes * 60L)) && now.isBefore(startsAt)
            ? percent(paid, percent) : 0;
    }

    // Integer half-up rounding; prices and percentages are bounded at the API boundary.
    static long percent(long amount, int percent) { return (amount * percent + 50) / 100; }
    private Pricing() {}
}
''')
