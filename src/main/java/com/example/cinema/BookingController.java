package com.example.cinema;

import static com.example.cinema.ApiFields.*;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.ROOT)
class BookingController {
    private final Bookings bookings;
    private final Db db;
    private final PasswordEncoder passwords;
    BookingController(Bookings bookings, Db db, PasswordEncoder passwords) {
        this.bookings = bookings; this.db = db; this.passwords = passwords;
    }
    @PostMapping(ApiPaths.CUSTOMERS) @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@Valid @RequestBody Requests.Registration request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > ValidationRules.MAX_PASSWORD_BYTES)
            throw ApiException.badRequest("Password must be at most " + ValidationRules.MAX_PASSWORD_BYTES + " UTF-8 bytes");
        db.jdbc.update("INSERT INTO app_user VALUES (?, ?, ?)", request.username(), passwords.encode(request.password()), Role.CUSTOMER.name());
        return Map.of(USERNAME, request.username(), ROLE, Role.CUSTOMER.name());
    }
    @PostMapping(ApiPaths.BOOKINGS) @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> hold(Principal user, @Valid @RequestBody Requests.Hold request) { return bookings.hold(user.getName(), request); }
    @GetMapping(ApiPaths.BOOKINGS) List<Map<String, Object>> history(Principal user, @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT_VALUE) int limit,
            @RequestParam(defaultValue = Pagination.DEFAULT_OFFSET_VALUE) int offset) { return bookings.history(user.getName(), limit, offset); }
    @GetMapping(ApiPaths.BOOKING) Map<String, Object> booking(Principal user, @PathVariable UUID id) { return bookings.view(id, user.getName()); }
    @PostMapping(ApiPaths.BOOKING_PAYMENTS) Map<String, Object> pay(Principal user, @PathVariable UUID id,
            @Valid @RequestBody Requests.Payment request) { return bookings.pay(id, user.getName(), request); }
    @PostMapping(ApiPaths.BOOKING_CANCEL) Map<String, Object> cancel(Principal user, @PathVariable UUID id) { return bookings.cancel(id, user.getName()); }
    @GetMapping(ApiPaths.NOTIFICATIONS) List<Map<String, Object>> notifications(Principal user,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT_VALUE) int limit, @RequestParam(defaultValue = Pagination.DEFAULT_OFFSET_VALUE) int offset) {
        Pagination.validate(limit, offset);
        return db.rows("""
            SELECT n.id, n.booking_id, n.kind, n.message, n.delivered_at FROM notification n
            JOIN booking b ON b.id = n.booking_id WHERE b.username = ? AND n.delivered_at IS NOT NULL
            ORDER BY n.delivered_at DESC, n.id LIMIT ? OFFSET ?
            """, user.getName(), limit, offset);
    }
}
