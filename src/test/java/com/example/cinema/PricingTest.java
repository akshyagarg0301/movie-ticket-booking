package com.example.cinema;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PricingTest {
    @Test void weekendUsesTheTheatersLocalDate() {
        Instant fridayUtc = Instant.parse("2030-01-04T20:00:00Z");
        assertThat(Pricing.seat(10000, 20, fridayUtc, ZoneId.of("Asia/Kolkata"))).isEqualTo(12000);
        assertThat(Pricing.seat(10000, 20, fridayUtc, ZoneOffset.UTC)).isEqualTo(10000);
    }
    @Test void halfPaiseRoundsUp() { assertThat(Pricing.percent(101, 50)).isEqualTo(51); }
    @Test void discountIsCappedAndNeverExceedsSubtotal() {
        assertThat(Pricing.discount(10000, 20, 1000)).isEqualTo(1000);
        assertThat(Pricing.discount(100, 100, 1000)).isEqualTo(100);
    }
    @Test void refundCutoffIsInclusiveButShowStartIsNot() {
        Instant show = Instant.parse("2030-01-03T15:00:00Z");
        assertThat(Pricing.refund(10000, 80, 120, show, show.minusSeconds(7200))).isEqualTo(8000);
        assertThat(Pricing.refund(10000, 80, 120, show, show.minusSeconds(7199))).isZero();
        assertThat(Pricing.refund(10000, 100, 0, show, show)).isZero();
    }
}
