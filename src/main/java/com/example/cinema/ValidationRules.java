package com.example.cinema;

final class ValidationRules {
    static final int MIN_USERNAME_LENGTH = 3;
    static final int MAX_USERNAME_LENGTH = 50;
    static final int MIN_PASSWORD_LENGTH = 10;
    static final int MAX_PASSWORD_BYTES = 72;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_TIMEZONE_LENGTH = 60;
    static final int MAX_TITLE_LENGTH = 160;
    static final int MAX_SEAT_LABEL_LENGTH = 10;
    static final int MAX_LAYOUT_SEATS = 1_000;
    static final int MAX_BOOKING_SEATS = 10;
    static final int MAX_REFUND_CUTOFF_MINUTES = 10_080;
    static final long MAX_SEAT_PRICE = 10_000_000;
    static final long MAX_DISCOUNT_VALUE = 100_000_000;
    static final int MIN_DISCOUNT_CODE_LENGTH = 3;
    static final int MAX_DISCOUNT_CODE_LENGTH = 30;
    static final int MIN_IDEMPOTENCY_KEY_LENGTH = 8;
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    static final String USERNAME_PATTERN =
            "[a-zA-Z0-9_.-]{" + MIN_USERNAME_LENGTH + "," + MAX_USERNAME_LENGTH + "}";
    static final String SEAT_LABEL_PATTERN =
            "[A-Z][A-Z0-9-]{0," + (MAX_SEAT_LABEL_LENGTH - 1) + "}";
    static final String DISCOUNT_CODE_PATTERN =
            "[A-Z0-9]{" + MIN_DISCOUNT_CODE_LENGTH + "," + MAX_DISCOUNT_CODE_LENGTH + "}";
    static final String IDEMPOTENCY_KEY_PATTERN =
            "[A-Za-z0-9_-]{" + MIN_IDEMPOTENCY_KEY_LENGTH + "," + MAX_IDEMPOTENCY_KEY_LENGTH + "}";

    private ValidationRules() {}
}
