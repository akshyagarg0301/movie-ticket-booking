package com.example.cinema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import static com.example.cinema.ValidationRules.*;
import static com.example.cinema.BookingRules.FULL_PERCENT;

final class Requests {
    record Registration(@Pattern(regexp = USERNAME_PATTERN) @NotNull String username,
                        @NotNull @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_BYTES) String password) {}
    record City(@NotBlank @Size(max = MAX_NAME_LENGTH) String name, @NotBlank @Size(max = MAX_TIMEZONE_LENGTH) String timezone) {}
    record Theater(@Positive long cityId, @NotBlank @Size(max = MAX_NAME_LENGTH) String name) {}
    record Screen(@Positive long theaterId, @NotBlank @Size(max = MAX_NAME_LENGTH) String name) {}
    record Seat(@NotNull @Pattern(regexp = SEAT_LABEL_PATTERN) String label, @NotNull SeatTier tier) {}
    record Layout(@NotEmpty @Size(max = MAX_LAYOUT_SEATS) List<@NotNull @Valid Seat> seats) {}
    record Policy(@NotBlank @Size(max = MAX_NAME_LENGTH) String name, @Min(0) @Max(MAX_REFUND_CUTOFF_MINUTES) int cutoffMinutes,
                  @Min(0) @Max(FULL_PERCENT) int refundPercent) {}
    record Pricing(@Min(1) @Max(MAX_SEAT_PRICE) long regularPrice, @Min(1) @Max(MAX_SEAT_PRICE) long premiumPrice,
                   @Min(0) @Max(FULL_PERCENT) int weekendMarkup, @Positive long policyId) {}
    record Show(@Positive long screenId, @NotBlank @Size(max = MAX_TITLE_LENGTH) String title,
                @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull @Valid Pricing pricing) {}
    record Discount(@NotNull @Pattern(regexp = DISCOUNT_CODE_PATTERN) String code,
                    @Min(1) @Max(FULL_PERCENT) int percent, @Min(1) @Max(MAX_DISCOUNT_VALUE) long maxDiscount,
                    @Min(0) @Max(MAX_DISCOUNT_VALUE) long minSpend, @Min(1) int maxUses,
                    @NotNull Instant expiresAt) {}
    record Hold(@Positive long showId, @NotEmpty @Size(max = MAX_BOOKING_SEATS) List<@NotNull @Pattern(regexp = SEAT_LABEL_PATTERN) String> seats,
                @Pattern(regexp = DISCOUNT_CODE_PATTERN) String discountCode) {}
    record Payment(@NotNull @Pattern(regexp = IDEMPOTENCY_KEY_PATTERN) String idempotencyKey,
                   @NotNull PaymentToken token) {}
    private Requests() {}
}
