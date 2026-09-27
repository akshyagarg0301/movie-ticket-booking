package com.example.cinema;

import java.time.*;

final class Pricing {
    static long seat(long base, int weekendMarkup, Instant showTime, ZoneId zone) {
        DayOfWeek day = showTime.atZone(zone).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? percent(base, 100 + weekendMarkup) : base;
    }

    static long discount(long subtotal, int percent, long cap) {
        return Math.min(subtotal, Math.min(cap, percent(subtotal, percent)));
    }

    static long refund(long paid, int percent, int cutoffMinutes, Instant startsAt, Instant now) {
        return !now.isAfter(startsAt.minusSeconds(cutoffMinutes * 60L)) && now.isBefore(startsAt)
            ? percent(paid, percent) : 0;
    }

    // Integer half-up rounding; prices and percentages are bounded at the API boundary.
    static long percent(long amount, int percent) { return (amount * percent + 50) / 100; }
    private Pricing() {}
}
