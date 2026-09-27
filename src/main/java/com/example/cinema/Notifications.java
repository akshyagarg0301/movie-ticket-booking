package com.example.cinema;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Notifications {
    private final Db db;
    private final Catalog catalog;
    private final Clock clock;
    private final NotificationSender sender;
    Notifications(Db db, Catalog catalog, Clock clock, NotificationSender sender) {
        this.db = db; this.catalog = catalog; this.clock = clock; this.sender = sender;
    }
    @Transactional
    public void deliver(UUID id) {
        var rows = db.rows("SELECT b.show_id FROM notification n JOIN booking b ON b.id = n.booking_id WHERE n.id = ?", id);
        if (rows.isEmpty()) return;
        catalog.lockShow(Db.number(rows.get(0), "showId"));
        rows = db.rows("SELECT * FROM notification WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) return;
        var notification = rows.get(0);
        if (notification.get("deliveredAt") != null || Db.instant(notification, "dueAt").isAfter(clock.instant())) return;
        var booking = db.one("SELECT status FROM booking WHERE id = ?", notification.get("bookingId"));
        if ("REMINDER".equals(notification.get("kind")) && !"CONFIRMED".equals(booking.get("status"))) {
            db.jdbc.update("DELETE FROM notification WHERE id = ?", id); return;
        }
        sender.send(id, Db.string(notification, "message"));
        db.jdbc.update("UPDATE notification SET delivered_at = ? WHERE id = ?", clock.instant(), id);
    }
}
