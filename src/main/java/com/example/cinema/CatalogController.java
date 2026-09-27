package com.example.cinema;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class CatalogController {
    private final Catalog catalog;
    private final Db db;
    private final Bookings bookings;
    CatalogController(Catalog catalog, Db db, Bookings bookings) { this.catalog = catalog; this.db = db; this.bookings = bookings; }

    @GetMapping("/cities") List<Map<String, Object>> cities() { return db.rows("SELECT * FROM city ORDER BY name"); }
    @GetMapping("/theaters") List<Map<String, Object>> theaters(@RequestParam(required = false) Long cityId) {
        return cityId == null ? db.rows("SELECT * FROM theater ORDER BY id") : db.rows("SELECT * FROM theater WHERE city_id = ? ORDER BY id", cityId);
    }
    @GetMapping("/shows") List<Map<String, Object>> shows(@RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long theaterId, @RequestParam(required = false) String title,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
        return catalog.browse(cityId, theaterId, title, from, to, limit, offset);
    }
    @GetMapping("/shows/{id}") Map<String, Object> show(@PathVariable long id) { return catalog.show(id); }
    @GetMapping("/shows/{id}/seats") List<Map<String, Object>> seats(@PathVariable long id) { return bookings.seats(id); }

    @PostMapping("/admin/cities") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> city(@Valid @RequestBody Requests.City request) { return Map.of("id", catalog.city(request)); }
    @PutMapping("/admin/cities/{id}") void city(@PathVariable long id, @Valid @RequestBody Requests.City request) { catalog.updateCity(id, request); }
    @PostMapping("/admin/theaters") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> theater(@Valid @RequestBody Requests.Theater request) { return Map.of("id", catalog.theater(request)); }
    @PutMapping("/admin/theaters/{id}") void theater(@PathVariable long id, @Valid @RequestBody Requests.Theater request) { catalog.renameTheater(id, request); }
    @PostMapping("/admin/screens") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> screen(@Valid @RequestBody Requests.Screen request) { return Map.of("id", catalog.screen(request)); }
    @GetMapping("/admin/screens") List<Map<String, Object>> screens() { return db.rows("SELECT * FROM screen ORDER BY id"); }
    @PutMapping("/admin/screens/{id}") void screen(@PathVariable long id, @Valid @RequestBody Requests.Screen request) { catalog.renameScreen(id, request); }
    @GetMapping("/admin/screens/{id}/seats") List<Map<String, Object>> layout(@PathVariable long id) {
        db.one("SELECT id FROM screen WHERE id = ?", id);
        return db.rows("SELECT label, tier FROM layout_seat WHERE screen_id = ? ORDER BY label", id);
    }
    @PutMapping("/admin/screens/{id}/seats") void layout(@PathVariable long id, @Valid @RequestBody Requests.Layout request) { catalog.layout(id, request); }
    @PostMapping("/admin/refund-policies") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> policy(@Valid @RequestBody Requests.Policy request) { return Map.of("id", catalog.policy(request)); }
    @GetMapping("/admin/refund-policies") List<Map<String, Object>> policies() { return db.rows("SELECT * FROM refund_policy ORDER BY id"); }
    @PutMapping("/admin/refund-policies/{id}") void policy(@PathVariable long id, @Valid @RequestBody Requests.Policy request) { catalog.updatePolicy(id, request); }
    @PostMapping("/admin/shows") @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> show(@Valid @RequestBody Requests.Show request) { return Map.of("id", catalog.createShow(request)); }
    @PutMapping("/admin/shows/{id}") void show(@PathVariable long id, @Valid @RequestBody Requests.Show request) { catalog.updateShow(id, request); }
    @PutMapping("/admin/shows/{id}/pricing") void pricing(@PathVariable long id, @Valid @RequestBody Requests.Pricing request) { catalog.pricing(id, request); }
    @PostMapping("/admin/shows/{id}/cancel") void cancel(@PathVariable long id) { bookings.cancelShow(id); }
    @PostMapping("/admin/discounts") @ResponseStatus(HttpStatus.CREATED)
    void discount(@Valid @RequestBody Requests.Discount request) { catalog.discount(request); }
    @GetMapping("/admin/discounts") List<Map<String, Object>> discounts() { return db.rows("SELECT * FROM discount_code ORDER BY code"); }
    @DeleteMapping("/admin/discounts/{code}") void disable(@PathVariable String code) {
        db.one("SELECT code FROM discount_code WHERE code = ?", code);
        db.jdbc.update("UPDATE discount_code SET enabled = FALSE WHERE code = ?", code);
    }
}
