package com.example.cinema;

import static com.example.cinema.ApiFields.*;

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
        if (holdMinutes < BookingRules.MIN_HOLD_MINUTES || holdMinutes > BookingRules.MAX_HOLD_MINUTES)
            throw new IllegalArgumentException("Hold duration must be " + BookingRules.MIN_HOLD_MINUTES + ".." + BookingRules.MAX_HOLD_MINUTES + " minutes");
        this.db = db; this.catalog = catalog; this.clock = clock; this.holdMinutes = holdMinutes;
    }

    List<Map<String, Object>> seats(long showId) {
        var show = catalog.show(showId);
        var rows = db.rows("""
            SELECT s.label, s.tier, CASE WHEN b.status = ? THEN ?
              WHEN b.status = ? AND b.expires_at > ? THEN ? ELSE ? END AS availability
            FROM show_seat s LEFT JOIN booking b ON b.id = s.booking_id WHERE s.show_id = ? ORDER BY s.label
            """, BookingStatus.CONFIRMED.name(), SeatAvailability.BOOKED.name(), BookingStatus.HELD.name(),
                clock.instant(), SeatAvailability.HELD.name(), SeatAvailability.AVAILABLE.name(), showId);
        boolean open = enumValue(show, STATUS, ShowStatus.class) == ShowStatus.OPEN && instant(show, STARTS_AT).isAfter(clock.instant());
        rows.forEach(row -> {
            row.put(PRICE, seatPrice(show, enumValue(row, TIER, SeatTier.class)));
            row.put(CURRENCY, BookingRules.CURRENCY.getCurrencyCode());
            if (!open) row.put(AVAILABILITY, SeatAvailability.UNAVAILABLE.name());
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
            if (seat.get(BOOKING_ID) != null) throw ApiException.conflict("Seat " + label + " is no longer available");
            chosen.put(label, seatPrice(show, enumValue(seat, TIER, SeatTier.class)));
        }
        long subtotal = chosen.values().stream().mapToLong(Long::longValue).sum();
        long discount = discount(request.discountCode(), subtotal, now);
        var policy = db.one("SELECT * FROM refund_policy WHERE id = ?", show.get(POLICY_ID));
        UUID id = UUID.randomUUID();
        Instant expiry = now.plus(Duration.ofMinutes(holdMinutes));
        if (expiry.isAfter(instant(show, STARTS_AT))) expiry = instant(show, STARTS_AT);
        db.jdbc.update("""
            INSERT INTO booking(id, username, show_id, status, created_at, expires_at, subtotal, discount, total,
              discount_code, refund_cutoff, refund_percent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, username, request.showId(), BookingStatus.HELD.name(), now, expiry, subtotal, discount, subtotal - discount,
            request.discountCode(), policy.get(CUTOFF_MINUTES), policy.get(REFUND_PERCENT));
        chosen.forEach((label, price) -> {
            db.jdbc.update("INSERT INTO booking_seat VALUES (?, ?, ?)", id, label, price);
            db.jdbc.update("UPDATE show_seat SET booking_id = ? WHERE show_id = ? AND label = ?", id, request.showId(), label);
        });
        return view(id, username);
    }

    private long seatPrice(Map<String, Object> show, SeatTier tier) {
        return Pricing.seat(number(show, tier == SeatTier.PREMIUM ? PREMIUM_PRICE : REGULAR_PRICE),
            (int) number(show, WEEKEND_MARKUP), instant(show, STARTS_AT), ZoneId.of(string(show, TIMEZONE)));
    }

    private long discount(String code, long subtotal, Instant now) {
        if (code == null) return 0;
        var coupon = db.rows("SELECT * FROM discount_code WHERE code = ? FOR UPDATE", code).stream().findFirst()
            .orElseThrow(() -> ApiException.badRequest("Invalid discount code"));
        if (!Boolean.TRUE.equals(coupon.get(ENABLED)) || !instant(coupon, EXPIRES_AT).isAfter(now))
            throw ApiException.badRequest("Discount code is disabled or expired");
        if (subtotal < number(coupon, MIN_SPEND)) throw ApiException.badRequest("Discount minimum spend is not met");
        int used = db.jdbc.queryForObject("""
            SELECT COUNT(*) FROM booking WHERE discount_code = ? AND (paid = TRUE OR (status = ? AND expires_at > ?))
            """, Integer.class, code, BookingStatus.HELD.name(), now);
        if (used >= number(coupon, MAX_USES)) throw ApiException.conflict("Discount code has no uses remaining");
        return Pricing.discount(subtotal, (int) number(coupon, PERCENT), number(coupon, MAX_DISCOUNT));
    }

    @Transactional
    public Map<String, Object> pay(UUID id, String username, Requests.Payment request) {
        var booking = owned(id, username);
        var show = catalog.lockShow(number(booking, SHOW_ID));
        booking = owned(id, username);
        var previous = db.rows("SELECT * FROM payment WHERE request_key = ?", request.idempotencyKey());
        PaymentOutcome outcome = request.token().outcome();
        if (!previous.isEmpty()) {
            var payment = previous.get(0);
            if (!id.equals(payment.get(BOOKING_ID)) || outcome != enumValue(payment, OUTCOME, PaymentOutcome.class))
                throw ApiException.conflict("Idempotency key was used for a different request");
            return Map.of(PAYMENT, payment, BOOKING, view(id, username));
        }
        Instant now = clock.instant();
        requireOpen(show, now);
        if (enumValue(booking, STATUS, BookingStatus.class) != BookingStatus.HELD || !instant(booking, EXPIRES_AT).isAfter(now))
            throw ApiException.conflict("Only an unexpired hold can be paid");
        UUID paymentId = UUID.randomUUID();
        // The simulator has no network side effects: charge, confirmation and outbox commit together.
        db.jdbc.update("INSERT INTO payment VALUES (?, ?, ?, ?, ?, ?)", paymentId, id, request.idempotencyKey(), outcome.name(), booking.get(TOTAL), now);
        if (outcome == PaymentOutcome.SUCCEEDED) {
            db.jdbc.update("UPDATE booking SET status = ?, paid = TRUE WHERE id = ?", BookingStatus.CONFIRMED.name(), id);
            enqueue(id, NotificationType.CONFIRMATION, "Booking confirmed for " + show.get(TITLE), now);
            Instant reminder = instant(show, STARTS_AT).minus(BookingRules.REMINDER_LEAD_TIME);
            enqueue(id, NotificationType.REMINDER, "Your show " + show.get(TITLE) + " starts at " + show.get(STARTS_AT), reminder.isBefore(now) ? now : reminder);
        }
        return Map.of(PAYMENT, db.one("SELECT * FROM payment WHERE id = ?", paymentId), BOOKING, view(id, username));
    }

    @Transactional
    public Map<String, Object> cancel(UUID id, String username) {
        var booking = owned(id, username);
        var show = catalog.lockShow(number(booking, SHOW_ID));
        expireLocked(number(booking, SHOW_ID), clock.instant());
        booking = owned(id, username);
        BookingStatus status = enumValue(booking, STATUS, BookingStatus.class);
        if (status == BookingStatus.CANCELLED || status == BookingStatus.EXPIRED) return view(id, username);
        if (!instant(show, STARTS_AT).isAfter(clock.instant())) throw ApiException.conflict("A booking cannot be cancelled after the show starts");
        long amount = Boolean.TRUE.equals(booking.get(PAID))
            ? Pricing.refund(number(booking, TOTAL), (int) number(booking, REFUND_PERCENT),
                (int) number(booking, REFUND_CUTOFF), instant(show, STARTS_AT), clock.instant()) : 0;
        cancelLocked(booking, amount);
        return view(id, username);
    }

    @Transactional
    public void cancelShow(long showId) {
        var show = catalog.lockShow(showId);
        if (enumValue(show, STATUS, ShowStatus.class) == ShowStatus.CANCELLED) return;
        if (!instant(show, STARTS_AT).isAfter(clock.instant())) throw ApiException.conflict("A show cannot be cancelled after it starts");
        expireLocked(showId, clock.instant());
        for (var booking : db.rows("SELECT * FROM booking WHERE show_id = ? AND status IN (?, ?)",
                showId, BookingStatus.HELD.name(), BookingStatus.CONFIRMED.name()))
            cancelLocked(booking, Boolean.TRUE.equals(booking.get(PAID)) ? number(booking, TOTAL) : 0);
        db.jdbc.update("UPDATE movie_show SET status = ? WHERE id = ?", ShowStatus.CANCELLED.name(), showId);
    }

    private void cancelLocked(Map<String, Object> booking, long refund) {
        Object id = booking.get(ID);
        db.jdbc.update("UPDATE booking SET status = ? WHERE id = ?", BookingStatus.CANCELLED.name(), id);
        db.jdbc.update("UPDATE show_seat SET booking_id = NULL WHERE booking_id = ?", id);
        if (Boolean.TRUE.equals(booking.get(PAID)))
            db.jdbc.update("INSERT INTO refund VALUES (?, ?, ?)", id, refund, clock.instant());
        db.jdbc.update("DELETE FROM notification WHERE booking_id = ? AND kind = ? AND delivered_at IS NULL", id, NotificationType.REMINDER.name());
        enqueue((UUID) id, NotificationType.CANCELLATION, "Booking cancelled. Refund: " + refund + " " + BookingRules.MINOR_UNIT_NAME + ".", clock.instant());
    }

    @Transactional
    public void expire(long showId) { catalog.lockShow(showId); expireLocked(showId, clock.instant()); }

    private void expireLocked(long showId, Instant now) {
        db.jdbc.update("""
            UPDATE show_seat SET booking_id = NULL WHERE show_id = ? AND booking_id IN
              (SELECT id FROM booking WHERE show_id = ? AND status = ? AND expires_at <= ?)
            """, showId, showId, BookingStatus.HELD.name(), now);
        db.jdbc.update("UPDATE booking SET status = ? WHERE show_id = ? AND status = ? AND expires_at <= ?",
            BookingStatus.EXPIRED.name(), showId, BookingStatus.HELD.name(), now);
    }

    private void enqueue(UUID id, NotificationType kind, String message, Instant due) {
        db.jdbc.update("INSERT INTO notification(id, booking_id, kind, message, due_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), id, kind.name(), message, due);
    }

    private void requireOpen(Map<String, Object> show, Instant now) {
        if (enumValue(show, STATUS, ShowStatus.class) != ShowStatus.OPEN || !instant(show, STARTS_AT).isAfter(now))
            throw ApiException.conflict("Show is no longer open for booking");
    }

    private Map<String, Object> owned(UUID id, String username) {
        return db.one("SELECT * FROM booking WHERE id = ? AND username = ?", id, username);
    }

    Map<String, Object> view(UUID id, String username) {
        var booking = owned(id, username);
        if (enumValue(booking, STATUS, BookingStatus.class) == BookingStatus.HELD && !instant(booking, EXPIRES_AT).isAfter(clock.instant())) booking.put(STATUS, BookingStatus.EXPIRED.name());
        booking.put(CURRENCY, BookingRules.CURRENCY.getCurrencyCode());
        booking.put(SEATS, db.rows("SELECT label, price FROM booking_seat WHERE booking_id = ? ORDER BY label", id));
        booking.put(PAYMENTS, db.rows("SELECT id, outcome, amount, created_at FROM payment WHERE booking_id = ? ORDER BY created_at", id));
        booking.put(REFUND, db.rows("SELECT amount, created_at FROM refund WHERE booking_id = ?", id).stream().findFirst().orElse(null));
        return booking;
    }

    List<Map<String, Object>> history(String username, int limit, int offset) {
        Pagination.validate(limit, offset);
        return db.rows("SELECT id FROM booking WHERE username = ? ORDER BY created_at DESC, id LIMIT ? OFFSET ?", username, limit, offset)
            .stream().map(row -> view((UUID) row.get(ID), username)).toList();
    }
}
