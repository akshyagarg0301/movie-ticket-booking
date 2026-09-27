package com.example.cinema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

final class Requests {
    record Registration(@Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") @NotNull String username,
                        @NotNull @Size(min = 10, max = 72) String password) {}
    record City(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 60) String timezone) {}
    record Theater(@Positive long cityId, @NotBlank @Size(max = 100) String name) {}
    record Screen(@Positive long theaterId, @NotBlank @Size(max = 100) String name) {}
    enum Tier { REGULAR, PREMIUM }
    record Seat(@NotNull @Pattern(regexp = "[A-Z][A-Z0-9-]{0,9}") String label, @NotNull Tier tier) {}
    record Layout(@NotEmpty @Size(max = 1000) List<@NotNull @Valid Seat> seats) {}
    record Policy(@NotBlank @Size(max = 100) String name, @Min(0) @Max(10080) int cutoffMinutes,
                  @Min(0) @Max(100) int refundPercent) {}
    record Pricing(@Min(1) @Max(10000000) long regularPrice, @Min(1) @Max(10000000) long premiumPrice,
                   @Min(0) @Max(100) int weekendMarkup, @Positive long policyId) {}
    record Show(@Positive long screenId, @NotBlank @Size(max = 160) String title,
                @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull @Valid Pricing pricing) {}
    record Discount(@NotNull @Pattern(regexp = "[A-Z0-9]{3,30}") String code,
                    @Min(1) @Max(100) int percent, @Min(1) @Max(100000000) long maxDiscount,
                    @Min(0) @Max(100000000) long minSpend, @Min(1) int maxUses,
                    @NotNull Instant expiresAt) {}
    record Hold(@Positive long showId, @NotEmpty @Size(max = 10) List<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9-]{0,9}") String> seats,
                @Pattern(regexp = "[A-Z0-9]{3,30}") String discountCode) {}
    record Payment(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{8,100}") String idempotencyKey,
                   @NotNull @Pattern(regexp = "tok_success|tok_decline") String token) {}
    private Requests() {}
}
