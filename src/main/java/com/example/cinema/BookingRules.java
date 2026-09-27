package com.example.cinema;

import java.time.Duration;
import java.util.Currency;

final class BookingRules {
    static final Currency CURRENCY = Currency.getInstance("INR");
    static final String MINOR_UNIT_NAME = "paise";
    static final int FULL_PERCENT = 100;
    static final int MIN_HOLD_MINUTES = 1;
    static final int MAX_HOLD_MINUTES = 30;
    static final Duration REMINDER_LEAD_TIME = Duration.ofHours(1);

    private BookingRules() {}
}
