package com.example.cinema;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.cinema.Db.*;

@Service
class Bookings {
    private final Db db;
    private final Catalog catalog;
    private final Clock clock;
    private final int holdMinutes;

    Bookings(Db db, Catalog catalog, Clock clock, @Value("${cinema.hold-minutes}") int holdMinutes) {
        if (holdMinutes < 1 || holdMinutes > 30) throw new IllegalArgumentException("Hold duration must be 1..30 minutes");
        this.db = db; this.catalog = catalog; this.clock = clock; this.holdMinutes = holdMinutes;
    }

    List<Map<String, Object>> seats(long showId) {
        var show = catalog.show(showId);
        var rows = db.rows("""
            SELECT s.label, s.tier, CASE WHEN b.status = 'CONFIRMED' THEN 'BOOKED'
              WHEN b.status = 'HELD' AND b.expires_at > ? THEN 'HELD' ELSE 'AVAILABLE' END AS availability
            FROM show_seat s LEFT JOIN booking b ON b.id = s.booking_id WHERE s.show_id = ? ORDER BY s.label
            """, clock.instant(), showId);
        boolean open = string(show, "status").equals("OPEN") && instant(show, "startsAt").isAfter(clock.instant());
        rows.forEach(row -> {
            row.put("price", seatPrice(show, string(row, "tier")));
            row.put("currency", "INR");
            if (!open) row.put("availability", "UNAVAILABLE");
        });
        return rows;
    }

    @Transactional
    public Map<String, Object> hold(String username, Requests.Hold request) {
        var show = catalog.lockShow(request.showId());
        Instant now = clock.instant();
        requireOpen(show, now);
        expireLocked(request.showId(), now);
        if (new HashSet<>(request.seats()).size() != request.seats().size())
            throw ApiException.badRequest("Select each seat only once");
        var chosen = new LinkedHashMap<String, Long>();
        for (String label : request.seats()) {
            var seat = db.rows("SELECT * FROM show_seat WHERE show_id = ? AND label = ?", request.showId(), label)
                .stream().findFirst().orElseThrow(() -> ApiException.badRequest("Unknown seat: " + label));
            if (seat.get("bookingId") != null) throw ApiException.conflict("Seat " + label + " is no longer available");
            chosen.put(label, seatPrice(show, string(seat, "tier")));
        }
        long subtotal = chosen.values().stream().mapToLong(Long::longValue).sum();
        long discount = discount(request.discountCode(), subtotal, now);
        var policy = db.one("SELECT * FROM refund_policy WHERE id = ?", show.get("policyId"));
        UUID id = UUID.randomUUID();
        Instant expiry = now.plusSeconds(holdMinutes * 60L);
        if (expiry.isAfter(instant(show, "startsAt"))) expiry = instant(show, "startsAt");
        db.jdbc.update("""
            INSERT INTO booking(id, username, show_id, status, created_at, expires_at, subtotal, discount, total,
              discount_code, refund_cutoff, refund_percent) VALUES (?, ?, ?, 'HELD', ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, username, request.showId(), now, expiry, subtotal, discount, subtotal - discount,
            request.discountCode(), policy.get("cutoffMinutes"), policy.get("refundPercent"));
        chosen.forEach((label, price) -> {
            db.jdbc.update("INSERT INTO booking_seat VALUES (?, ?, ?)", id, label, price);
            db.jdbc.update("UPDATE show_seat SET booking_id = ? WHERE show_id = ? AND label = ?", id, request.showId(), label);
        });
        return view(id, username);
    }

    private long seatPrice(Map<String, Object> show, String tier) {
        return Pricing.seat(number(show, tier.equals("PREMIUM") ? "premiumPrice" : "regularPrice"),
            (int) number(show, "weekendMarkup"), instant(show, "startsAt"), ZoneId.of(string(show, "timezone")));
    }

    private long discount(String code, long subtotal, Instant now) {
        if (code == null) return 0;
        var coupon = db.rows("SELECT * FROM discount_code WHERE code = ? FOR UPDATE", code).stream().findFirst()
            .orElseThrow(() -> ApiException.badRequest("Invalid discount code"));
        if (!Boolean.TRUE.equals(coupon.get("enabled")) || !instant(coupon, "expiresAt").isAfter(now))
            throw ApiException.badRequest("Discount code is disabled or expired");
        if (subtotal < number(coupon, "minSpend")) throw ApiException.badRequest("Discount minimum spend is not met");
        int used = db.jdbc.queryForObject("""
            SELECT COUNT(*) FROM booking WHERE discount_code = ? AND (paid = TRUE OR (status = 'HELD' AND expires_at > ?))
            """, Integer.class, code, now);
        if (used >= number(coupon, "maxUses")) throw ApiException.conflict("Discount code has no uses remaining");
        return Pricing.discount(subtotal, (int) number(coupon, "percent"), number(coupon, "maxDiscount"));
    }

    @Transactional
    public Map<String, Object> pay(UUID id, String username, Requests.Payment request) {
        var booking = owned(id, username);
        var show = catalog.lockShow(number(booking, "showId"));
        booking = owned(id, username);
        var previous = db.rows("SELECT * FROM payment WHERE request_key = ?", request.idempotencyKey());
        String outcome = request.token().equals("tok_success") ? "SUCCEEDED" : "DECLINED";
        if (!previous.isEmpty()) {
            var payment = previous.get(0);
            if (!id.equals(payment.get("bookingId")) || !outcome.equals(payment.get("outcome")))
                throw ApiException.conflict("Idempotency key was used for a different request");
            return Map.of("payment", payment, "booking", view(id, username));
        }
        Instant now = clock.instant();
        requireOpen(show, now);
        if (!string(booking, "status").equals("HELD") || !instant(booking, "expiresAt").isAfter(now))
            throw ApiException.conflict("Only an unexpired hold can be paid");
        UUID paymentId = UUID.randomUUID();
        // The simulator has no network side effects: charge, confirmation and outbox commit together.
        db.jdbc.update("INSERT INTO payment VALUES (?, ?, ?, ?, ?, ?)", paymentId, id, request.idempotencyKey(), outcome, booking.get("total"), now);
        if (outcome.equals("SUCCEEDED")) {
            db.jdbc.update("UPDATE booking SET status = 'CONFIRMED', paid = TRUE WHERE id = ?", id);
            enqueue(id, "CONFIRMATION", "Booking confirmed for " + show.get("title"), now);
            Instant reminder = instant(show, "startsAt").minusSeconds(3600);
            enqueue(id, "REMINDER", "Your show " + show.get("title") + " starts at " + show.get("startsAt"), reminder.isBefore(now) ? now : reminder);
        }
        return Map.of("payment", db.one("SELECT * FROM payment WHERE id = ?", paymentId), "booking", view(id, username));
    }

    @Transactional
    public Map<String, Object> cancel(UUID id, String username) {
        var booking = owned(id, username);
        var show = catalog.lockShow(number(booking, "showId"));
        expireLocked(number(booking, "showId"), clock.instant());
        booking = owned(id, username);
        if (Set.of("CANCELLED", "EXPIRED").contains(string(booking, "status"))) return view(id, username);
        if (!instant(show, "startsAt").isAfter(clock.instant())) throw ApiException.conflict("A booking cannot be cancelled after the show starts");
        long amount = Boolean.TRUE.equals(booking.get("paid"))
            ? Pricing.refund(number(booking, "total"), (int) number(booking, "refundPercent"),
                (int) number(booking, "refundCutoff"), instant(show, "startsAt"), clock.instant()) : 0;
        cancelLocked(booking, amount);
        return view(id, username);
    }

    @Transactional
    public void cancelShow(long showId) {
        var show = catalog.lockShow(showId);
        if (string(show, "status").equals("CANCELLED")) return;
        if (!instant(show, "startsAt").isAfter(clock.instant())) throw ApiException.conflict("A show cannot be cancelled after it starts");
        expireLocked(showId, clock.instant());
        for (var booking : db.rows("SELECT * FROM booking WHERE show_id = ? AND status IN ('HELD', 'CONFIRMED')", showId))
            cancelLocked(booking, Boolean.TRUE.equals(booking.get("paid")) ? number(booking, "total") : 0);
        db.jdbc.update("UPDATE movie_show SET status = 'CANCELLED' WHERE id = ?", showId);
    }

    private void cancelLocked(Map<String, Object> booking, long refund) {
        Object id = booking.get("id");
        db.jdbc.update("UPDATE booking SET status = 'CANCELLED' WHERE id = ?", id);
        db.jdbc.update("UPDATE show_seat SET booking_id = NULL WHERE booking_id = ?", id);
        if (Boolean.TRUE.equals(booking.get("paid")))
            db.jdbc.update("INSERT INTO refund VALUES (?, ?, ?)", id, refund, clock.instant());
        db.jdbc.update("DELETE FROM notification WHERE booking_id = ? AND kind = 'REMINDER' AND delivered_at IS NULL", id);
        enqueue((UUID) id, "CANCELLATION", "Booking cancelled. Refund: " + refund + " paise.", clock.instant());
    }

    @Transactional
    public void expire(long showId) { catalog.lockShow(showId); expireLocked(showId, clock.instant()); }

    private void expireLocked(long showId, Instant now) {
        db.jdbc.update("""
            UPDATE show_seat SET booking_id = NULL WHERE show_id = ? AND booking_id IN
              (SELECT id FROM booking WHERE show_id = ? AND status = 'HELD' AND expires_at <= ?)
            """, showId, showId, now);
        db.jdbc.update("UPDATE booking SET status = 'EXPIRED' WHERE show_id = ? AND status = 'HELD' AND expires_at <= ?", showId, now);
    }

    private void enqueue(UUID id, String kind, String message, Instant due) {
        db.jdbc.update("INSERT INTO notification(id, booking_id, kind, message, due_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), id, kind, message, due);
    }

    private void requireOpen(Map<String, Object> show, Instant now) {
        if (!string(show, "status").equals("OPEN") || !instant(show, "startsAt").isAfter(now))
            throw ApiException.conflict("Show is no longer open for booking");
    }

    private Map<String, Object> owned(UUID id, String username) {
        return db.one("SELECT * FROM booking WHERE id = ? AND username = ?", id, username);
    }

    Map<String, Object> view(UUID id, String username) {
        var booking = owned(id, username);
        if (string(booking, "status").equals("HELD") && !instant(booking, "expiresAt").isAfter(clock.instant())) booking.put("status", "EXPIRED");
        booking.put("currency", "INR");
        booking.put("seats", db.rows("SELECT label, price FROM booking_seat WHERE booking_id = ? ORDER BY label", id));
        booking.put("payments", db.rows("SELECT id, outcome, amount, created_at FROM payment WHERE booking_id = ? ORDER BY created_at", id));
        booking.put("refund", db.rows("SELECT amount, created_at FROM refund WHERE booking_id = ?", id).stream().findFirst().orElse(null));
        return booking;
    }

    List<Map<String, Object>> history(String username, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw ApiException.badRequest("limit must be 1..100 and offset must be nonnegative");
        return db.rows("SELECT id FROM booking WHERE username = ? ORDER BY created_at DESC, id LIMIT ? OFFSET ?", username, limit, offset)
            .stream().map(row -> view((UUID) row.get("id"), username)).toList();
    }
}
