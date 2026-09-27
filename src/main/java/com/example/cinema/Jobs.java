package com.example.cinema;

import static com.example.cinema.ApiFields.*;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cinema.jobs.enabled", havingValue = "true", matchIfMissing = true)
class Jobs {
    private static final int BATCH_SIZE = 100;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final int MAX_RETRY_EXPONENT = 10;
    private final Db db;
    private final Clock clock;
    private final Bookings bookings;
    private final Notifications notifications;
    Jobs(Db db, Clock clock, Bookings bookings, Notifications notifications) {
        this.db = db; this.clock = clock; this.bookings = bookings; this.notifications = notifications;
    }
    @Scheduled(fixedDelayString = "${cinema.jobs.delay-ms}")
    public void tick() {
        for (var row : db.rows("SELECT DISTINCT show_id FROM booking WHERE status = ? AND expires_at <= ? LIMIT ?",
                BookingStatus.HELD.name(), clock.instant(), BATCH_SIZE)) {
            try { bookings.expire(Db.number(row, SHOW_ID)); }
            catch (RuntimeException e) { LoggerFactory.getLogger(Jobs.class).warn("Hold cleanup will retry", e); }
        }
        for (var row : db.rows("SELECT id, attempts FROM notification WHERE delivered_at IS NULL AND due_at <= ? ORDER BY due_at LIMIT ?", clock.instant(), BATCH_SIZE)) {
            UUID id = (UUID) row.get(ID);
            try { notifications.deliver(id); }
            catch (RuntimeException e) {
                long multiplier = 1L << Math.min(MAX_RETRY_EXPONENT, Db.number(row, ATTEMPTS));
                Duration delay = INITIAL_RETRY_DELAY.multipliedBy(multiplier);
                if (delay.compareTo(MAX_RETRY_DELAY) > 0) delay = MAX_RETRY_DELAY;
                db.jdbc.update("UPDATE notification SET attempts = attempts + 1, due_at = ? WHERE id = ? AND delivered_at IS NULL", clock.instant().plus(delay), id);
                LoggerFactory.getLogger(Jobs.class).warn("Notification {} will retry", id, e);
            }
        }
    }
}
