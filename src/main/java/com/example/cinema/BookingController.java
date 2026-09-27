package com.example.cinema;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class BookingController {
    private final Bookings bookings;
    private final Db db;
    private final PasswordEncoder passwords;
    BookingController(Bookings bookings, Db db, PasswordEncoder passwords) {
        this.bookings = bookings; this.db = db; this.passwords = passwords;
    }
    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@Valid @RequestBody Requests.Registration request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.badRequest("Password must be at most 72 UTF-8 bytes");
        db.jdbc.update("INSERT INTO app_user VALUES (?, ?, 'CUSTOMER')", request.username(), passwords.encode(request.password()));
        return Map.of("username", request.username(), "role", "CUSTOMER");
    }
    @PostMapping("/bookings") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> hold(Principal user, @Valid @RequestBody Requests.Hold request) { return bookings.hold(user.getName(), request); }
    @GetMapping("/bookings") List<Map<String, Object>> history(Principal user, @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) { return bookings.history(user.getName(), limit, offset); }
    @GetMapping("/bookings/{id}") Map<String, Object> booking(Principal user, @PathVariable UUID id) { return bookings.view(id, user.getName()); }
    @PostMapping("/bookings/{id}/payments") Map<String, Object> pay(Principal user, @PathVariable UUID id,
            @Valid @RequestBody Requests.Payment request) { return bookings.pay(id, user.getName(), request); }
    @PostMapping("/bookings/{id}/cancel") Map<String, Object> cancel(Principal user, @PathVariable UUID id) { return bookings.cancel(id, user.getName()); }
    @GetMapping("/notifications") List<Map<String, Object>> notifications(Principal user,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw ApiException.badRequest("limit must be 1..100 and offset must be nonnegative");
        return db.rows("""
            SELECT n.id, n.booking_id, n.kind, n.message, n.delivered_at FROM notification n
            JOIN booking b ON b.id = n.booking_id WHERE b.username = ? AND n.delivered_at IS NOT NULL
            ORDER BY n.delivered_at DESC, n.id LIMIT ? OFFSET ?
            """, user.getName(), limit, offset);
    }
}
