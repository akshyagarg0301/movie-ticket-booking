package com.example.cinema;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cinema.jobs.enabled", havingValue = "true", matchIfMissing = true)
class Jobs {
    private final Db db;
    private final Clock clock;
    private final Bookings bookings;
    private final Notifications notifications;
    Jobs(Db db, Clock clock, Bookings bookings, Notifications notifications) {
        this.db = db; this.clock = clock; this.bookings = bookings; this.notifications = notifications;
    }
    @Scheduled(fixedDelayString = "${cinema.jobs.delay-ms:5000}")
    public void tick() {
        for (var row : db.rows("SELECT DISTINCT show_id FROM booking WHERE status = 'HELD' AND expires_at <= ? LIMIT 100", clock.instant())) {
            try { bookings.expire(Db.number(row, "showId")); }
            catch (RuntimeException e) { LoggerFactory.getLogger(Jobs.class).warn("Hold cleanup will retry", e); }
        }
        for (var row : db.rows("SELECT id, attempts FROM notification WHERE delivered_at IS NULL AND due_at <= ? ORDER BY due_at LIMIT 100", clock.instant())) {
            UUID id = (UUID) row.get("id");
            try { notifications.deliver(id); }
            catch (RuntimeException e) {
                long delay = Math.min(3600, 5L << Math.min(10, Db.number(row, "attempts")));
                db.jdbc.update("UPDATE notification SET attempts = attempts + 1, due_at = ? WHERE id = ? AND delivered_at IS NULL", clock.instant().plusSeconds(delay), id);
                LoggerFactory.getLogger(Jobs.class).warn("Notification {} will retry", id, e);
            }
        }
    }
}
