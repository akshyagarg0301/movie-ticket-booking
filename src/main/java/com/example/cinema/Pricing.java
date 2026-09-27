package com.example.cinema;

import java.time.*;
import static com.example.cinema.BookingRules.FULL_PERCENT;

final class Pricing {
    static long seat(long base, int weekendMarkup, Instant showTime, ZoneId zone) {
        DayOfWeek day = showTime.atZone(zone).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? percent(base, FULL_PERCENT + weekendMarkup) : base;
    }

    static long discount(long subtotal, int percent, long cap) {
        return Math.min(subtotal, Math.min(cap, percent(subtotal, percent)));
    }

    static long refund(long paid, int percent, int cutoffMinutes, Instant startsAt, Instant now) {
        return !now.isAfter(startsAt.minus(Duration.ofMinutes(cutoffMinutes))) && now.isBefore(startsAt)
            ? percent(paid, percent) : 0;
    }

    // Integer half-up rounding; prices and percentages are bounded at the API boundary.
    static long percent(long amount, int percent) { return (amount * percent + FULL_PERCENT / 2) / FULL_PERCENT; }
    private Pricing() {}
}
