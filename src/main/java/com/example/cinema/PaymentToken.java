package com.example.cinema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Tokens accepted by the local payment simulator. */
enum PaymentToken {
    SUCCESS("tok_success", PaymentOutcome.SUCCEEDED),
    DECLINE("tok_decline", PaymentOutcome.DECLINED);

    private final String value;
    private final PaymentOutcome outcome;

    PaymentToken(String value, PaymentOutcome outcome) {
        this.value = value;
        this.outcome = outcome;
    }

    @JsonValue
    public String value() { return value; }

    PaymentOutcome outcome() { return outcome; }

    @JsonCreator
    public static PaymentToken fromValue(String value) {
        for (PaymentToken token : values()) {
            if (token.value.equals(value)) return token;
        }
        throw new IllegalArgumentException("Unknown payment simulator token");
    }
}
