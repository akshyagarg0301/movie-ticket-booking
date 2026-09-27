package com.example.cinema;

import static com.example.cinema.ApiFields.*;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.cinema.Db.*;

@Service
class Catalog {
    private static final long NO_SHOW_ID = -1;
    private final Db db;
    private final Clock clock;
    Catalog(Db db, Clock clock) { this.db = db; this.clock = clock; }

    static final String SHOW_QUERY = """
        SELECT s.*, sc.name AS screen_name, t.id AS theater_id, t.name AS theater_name,
               c.id AS city_id, c.name AS city_name, c.timezone
        FROM movie_show s JOIN screen sc ON sc.id = s.screen_id
        JOIN theater t ON t.id = sc.theater_id JOIN city c ON c.id = t.city_id
        """;

    Map<String, Object> show(long id) { return db.one(SHOW_QUERY + " WHERE s.id = ?", id); }

    Map<String, Object> lockShow(long id) {
        // ponytail: serialize mutations per show. Seat-level locks are the next step if one show's throughput becomes a bottleneck.
        db.one("SELECT id FROM movie_show WHERE id = ? FOR UPDATE", id);
        return show(id);
    }

    List<Map<String, Object>> browse(Long cityId, Long theaterId, String title, Instant from, Instant to, int limit, int offset) {
        Pagination.validate(limit, offset);
        if (from != null && to != null && !to.isAfter(from)) throw ApiException.badRequest("to must be after from");
        var args = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder(SHOW_QUERY + " WHERE s.status = ? AND s.starts_at > ?");
        args.add(ShowStatus.OPEN.name());
        args.add(clock.instant());
        if (cityId != null) { sql.append(" AND c.id = ?"); args.add(cityId); }
        if (theaterId != null) { sql.append(" AND t.id = ?"); args.add(theaterId); }
        if (title != null) { sql.append(" AND LOWER(s.title) LIKE ?"); args.add("%" + title.toLowerCase(Locale.ROOT) + "%"); }
        if (from != null) { sql.append(" AND s.starts_at >= ?"); args.add(from); }
        if (to != null) { sql.append(" AND s.starts_at < ?"); args.add(to); }
        sql.append(" ORDER BY s.starts_at, s.id LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
        return db.rows(sql.toString(), args.toArray());
    }

    long city(Requests.City request) {
        validZone(request.timezone());
        return db.insert("INSERT INTO city(name, timezone) VALUES (?, ?)", request.name().trim(), request.timezone());
    }
    void updateCity(long id, Requests.City request) {
        validZone(request.timezone());
        db.one("SELECT id FROM city WHERE id = ?", id);
        // Changing timezone would change how already published shows are priced.
        if (!string(db.one("SELECT timezone FROM city WHERE id = ?", id), TIMEZONE).equals(request.timezone()))
            throw ApiException.conflict("A city's timezone cannot be changed; create a new city instead");
        db.jdbc.update("UPDATE city SET name = ? WHERE id = ?", request.name().trim(), id);
    }
    private void validZone(String value) {
        try { ZoneId.of(value); } catch (DateTimeException e) { throw ApiException.badRequest("Unknown timezone"); }
    }
    long theater(Requests.Theater request) {
        db.one("SELECT id FROM city WHERE id = ?", request.cityId());
        return db.insert("INSERT INTO theater(city_id, name) VALUES (?, ?)", request.cityId(), request.name().trim());
    }
    long screen(Requests.Screen request) {
        db.one("SELECT id FROM theater WHERE id = ?", request.theaterId());
        return db.insert("INSERT INTO screen(theater_id, name) VALUES (?, ?)", request.theaterId(), request.name().trim());
    }
    void renameTheater(long id, Requests.Theater request) {
        var row = db.one("SELECT * FROM theater WHERE id = ?", id);
        if (number(row, CITY_ID) != request.cityId()) throw ApiException.conflict("A theater cannot move between cities");
        db.jdbc.update("UPDATE theater SET name = ? WHERE id = ?", request.name().trim(), id);
    }
    void renameScreen(long id, Requests.Screen request) {
        var row = db.one("SELECT * FROM screen WHERE id = ?", id);
        if (number(row, THEATER_ID) != request.theaterId()) throw ApiException.conflict("A screen cannot move between theaters");
        db.jdbc.update("UPDATE screen SET name = ? WHERE id = ?", request.name().trim(), id);
    }
    @Transactional
    public void layout(long screenId, Requests.Layout request) {
        db.one("SELECT id FROM screen WHERE id = ? FOR UPDATE", screenId);
        var labels = new HashSet<String>();
        if (request.seats().stream().anyMatch(seat -> !labels.add(seat.label())))
            throw ApiException.badRequest("Seat labels must be unique");
        db.jdbc.update("DELETE FROM layout_seat WHERE screen_id = ?", screenId);
        request.seats().forEach(seat -> db.jdbc.update("INSERT INTO layout_seat VALUES (?, ?, ?)", screenId, seat.label(), seat.tier().name()));
    }
    long policy(Requests.Policy request) {
        return db.insert("INSERT INTO refund_policy(name, cutoff_minutes, refund_percent) VALUES (?, ?, ?)",
            request.name(), request.cutoffMinutes(), request.refundPercent());
    }
    void updatePolicy(long id, Requests.Policy request) {
        db.one("SELECT id FROM refund_policy WHERE id = ?", id);
        db.jdbc.update("UPDATE refund_policy SET name = ?, cutoff_minutes = ?, refund_percent = ? WHERE id = ?",
            request.name(), request.cutoffMinutes(), request.refundPercent(), id);
    }
    @Transactional
    public long createShow(Requests.Show request) {
        db.one("SELECT id FROM screen WHERE id = ? FOR UPDATE", request.screenId());
        validateShow(request, null);
        var seats = db.rows("SELECT * FROM layout_seat WHERE screen_id = ?", request.screenId());
        if (seats.isEmpty()) throw ApiException.conflict("Add a seat layout before scheduling a show");
        var p = request.pricing();
        long id = db.insert("""
            INSERT INTO movie_show(screen_id, title, starts_at, ends_at, regular_price, premium_price, weekend_markup, policy_id, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, request.screenId(), request.title(), request.startsAt(), request.endsAt(), p.regularPrice(), p.premiumPrice(), p.weekendMarkup(), p.policyId(), ShowStatus.OPEN.name());
        seats.forEach(seat -> db.jdbc.update("INSERT INTO show_seat(show_id, label, tier) VALUES (?, ?, ?)", id, seat.get(LABEL), seat.get(TIER)));
        return id;
    }
    @Transactional
    public void updateShow(long id, Requests.Show request) {
        // Always lock screen before show, matching show creation's schedule lock.
        db.one("SELECT id FROM screen WHERE id = ? FOR UPDATE", request.screenId());
        var show = lockShow(id);
        if (number(show, SCREEN_ID) != request.screenId()) throw ApiException.conflict("A show cannot move to another screen");
        if (enumValue(show, STATUS, ShowStatus.class) != ShowStatus.OPEN) throw ApiException.conflict("Show is cancelled");
        if (db.jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE show_id = ?", Integer.class, id) > 0)
            throw ApiException.conflict("A show with booking history cannot be rescheduled; update pricing or cancel it instead");
        validateShow(request, id);
        var p = request.pricing();
        db.jdbc.update("""
            UPDATE movie_show SET title = ?, starts_at = ?, ends_at = ?, regular_price = ?, premium_price = ?,
              weekend_markup = ?, policy_id = ? WHERE id = ?
            """, request.title(), request.startsAt(), request.endsAt(), p.regularPrice(), p.premiumPrice(), p.weekendMarkup(), p.policyId(), id);
    }
    private void validateShow(Requests.Show request, Long excludedId) {
        if (!request.startsAt().isAfter(clock.instant()) || !request.endsAt().isAfter(request.startsAt()))
            throw ApiException.badRequest("Show must start in the future and end after its start");
        db.one("SELECT id FROM refund_policy WHERE id = ?", request.pricing().policyId());
        int overlaps = db.jdbc.queryForObject("""
            SELECT COUNT(*) FROM movie_show WHERE screen_id = ? AND status = ?
            AND starts_at < ? AND ends_at > ? AND id <> ?
            """, Integer.class, request.screenId(), ShowStatus.OPEN.name(), request.endsAt(), request.startsAt(), excludedId == null ? NO_SHOW_ID : excludedId);
        if (overlaps > 0) throw ApiException.conflict("Shows on the same screen cannot overlap");
    }
    @Transactional
    public void pricing(long id, Requests.Pricing request) {
        var show = lockShow(id);
        if (enumValue(show, STATUS, ShowStatus.class) != ShowStatus.OPEN || !instant(show, STARTS_AT).isAfter(clock.instant()))
            throw ApiException.conflict("Show is no longer open");
        db.one("SELECT id FROM refund_policy WHERE id = ?", request.policyId());
        db.jdbc.update("UPDATE movie_show SET regular_price = ?, premium_price = ?, weekend_markup = ?, policy_id = ? WHERE id = ?",
            request.regularPrice(), request.premiumPrice(), request.weekendMarkup(), request.policyId(), id);
    }
    void discount(Requests.Discount request) {
        if (!request.expiresAt().isAfter(clock.instant())) throw ApiException.badRequest("Discount expiry must be in the future");
        db.jdbc.update("INSERT INTO discount_code(code, percent, max_discount, min_spend, max_uses, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
            request.code(), request.percent(), request.maxDiscount(), request.minSpend(), request.maxUses(), request.expiresAt());
    }
}
