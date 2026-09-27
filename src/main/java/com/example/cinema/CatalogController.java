package com.example.cinema;

import static com.example.cinema.ApiFields.*;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping(ApiPaths.ROOT)
class CatalogController {
    private final Catalog catalog;
    private final Db db;
    private final Bookings bookings;

    CatalogController(Catalog catalog, Db db, Bookings bookings) {
        this.catalog = catalog;
        this.db = db;
        this.bookings = bookings;
    }

    @GetMapping(ApiPaths.CITIES)
    List<Map<String, Object>> cities() {
        return db.rows("SELECT * FROM city ORDER BY name");
    }

    @GetMapping(ApiPaths.THEATERS)
    List<Map<String, Object>> theaters(@RequestParam(required = false) Long cityId) {
        return cityId == null
                ? db.rows("SELECT * FROM theater ORDER BY id")
                : db.rows("SELECT * FROM theater WHERE city_id = ? ORDER BY id", cityId);
    }

    @GetMapping(ApiPaths.SHOWS)
    List<Map<String, Object>> shows(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long theaterId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT_VALUE) int limit,
            @RequestParam(defaultValue = Pagination.DEFAULT_OFFSET_VALUE) int offset) {
        return catalog.browse(cityId, theaterId, title, from, to, limit, offset);
    }

    @GetMapping(ApiPaths.SHOW)
    Map<String, Object> show(@PathVariable long id) {
        return catalog.show(id);
    }

    @GetMapping(ApiPaths.SHOW_SEATS)
    List<Map<String, Object>> seats(@PathVariable long id) {
        return bookings.seats(id);
    }

    @PostMapping(ApiPaths.ADMIN_CITIES)
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> city(@Valid @RequestBody Requests.City request) {
        return Map.of(ID, catalog.city(request));
    }

    @PutMapping(ApiPaths.ADMIN_CITY)
    void city(@PathVariable long id, @Valid @RequestBody Requests.City request) {
        catalog.updateCity(id, request);
    }

    @PostMapping(ApiPaths.ADMIN_THEATERS)
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> theater(@Valid @RequestBody Requests.Theater request) {
        return Map.of(ID, catalog.theater(request));
    }

    @PutMapping(ApiPaths.ADMIN_THEATER)
    void theater(@PathVariable long id, @Valid @RequestBody Requests.Theater request) {
        catalog.renameTheater(id, request);
    }

    @PostMapping(ApiPaths.ADMIN_SCREENS)
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> screen(@Valid @RequestBody Requests.Screen request) {
        return Map.of(ID, catalog.screen(request));
    }

    @GetMapping(ApiPaths.ADMIN_SCREENS)
    List<Map<String, Object>> screens() {
        return db.rows("SELECT * FROM screen ORDER BY id");
    }

    @PutMapping(ApiPaths.ADMIN_SCREEN)
    void screen(@PathVariable long id, @Valid @RequestBody Requests.Screen request) {
        catalog.renameScreen(id, request);
    }

    @GetMapping(ApiPaths.ADMIN_SCREEN_SEATS)
    List<Map<String, Object>> layout(@PathVariable long id) {
        db.one("SELECT id FROM screen WHERE id = ?", id);
        return db.rows(
                "SELECT label, tier FROM layout_seat WHERE screen_id = ? ORDER BY label", id);
    }

    @PutMapping(ApiPaths.ADMIN_SCREEN_SEATS)
    void layout(@PathVariable long id, @Valid @RequestBody Requests.Layout request) {
        catalog.layout(id, request);
    }

    @PostMapping(ApiPaths.ADMIN_REFUND_POLICIES)
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> policy(@Valid @RequestBody Requests.Policy request) {
        return Map.of(ID, catalog.policy(request));
    }

    @GetMapping(ApiPaths.ADMIN_REFUND_POLICIES)
    List<Map<String, Object>> policies() {
        return db.rows("SELECT * FROM refund_policy ORDER BY id");
    }

    @PutMapping(ApiPaths.ADMIN_REFUND_POLICY)
    void policy(@PathVariable long id, @Valid @RequestBody Requests.Policy request) {
        catalog.updatePolicy(id, request);
    }

    @PostMapping(ApiPaths.ADMIN_SHOWS)
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Long> show(@Valid @RequestBody Requests.Show request) {
        return Map.of(ID, catalog.createShow(request));
    }

    @PutMapping(ApiPaths.ADMIN_SHOW)
    void show(@PathVariable long id, @Valid @RequestBody Requests.Show request) {
        catalog.updateShow(id, request);
    }

    @PutMapping(ApiPaths.ADMIN_SHOW_PRICING)
    void pricing(@PathVariable long id, @Valid @RequestBody Requests.Pricing request) {
        catalog.pricing(id, request);
    }

    @PostMapping(ApiPaths.ADMIN_SHOW_CANCEL)
    void cancel(@PathVariable long id) {
        bookings.cancelShow(id);
    }

    @PostMapping(ApiPaths.ADMIN_DISCOUNTS)
    @ResponseStatus(HttpStatus.CREATED)
    void discount(@Valid @RequestBody Requests.Discount request) {
        catalog.discount(request);
    }

    @GetMapping(ApiPaths.ADMIN_DISCOUNTS)
    List<Map<String, Object>> discounts() {
        return db.rows("SELECT * FROM discount_code ORDER BY code");
    }

    @DeleteMapping(ApiPaths.ADMIN_DISCOUNT)
    void disable(@PathVariable String code) {
        db.one("SELECT code FROM discount_code WHERE code = ?", code);
        db.jdbc.update("UPDATE discount_code SET enabled = FALSE WHERE code = ?", code);
    }
}
