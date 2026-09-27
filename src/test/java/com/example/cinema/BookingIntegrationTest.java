package com.example.cinema;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(BookingIntegrationTest.TimeConfig.class)
class BookingIntegrationTest {
    @Autowired Db db;
    @Autowired Catalog catalog;
    @Autowired Bookings bookings;
    @Autowired Notifications notifications;
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean NotificationSender sender;
    long cityId, theaterId, screenId, policyId, showId;

    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2030-01-03T10:00:00Z");
        void set(Instant value) { now = value; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach void setUp() {
        clock.set(Instant.parse("2030-01-03T10:00:00Z"));
        for (String table : List.of("notification", "refund", "payment", "show_seat", "booking_seat", "booking",
                "movie_show", "discount_code", "layout_seat", "screen", "theater", "city", "refund_policy"))
            db.jdbc.update("DELETE FROM " + table);
        db.jdbc.update("DELETE FROM app_user WHERE role = 'CUSTOMER'");
        String hash = passwords.encode("customer-password");
        for (String name : List.of("alice", "bob")) db.jdbc.update("INSERT INTO app_user VALUES (?, ?, 'CUSTOMER')", name, hash);
        cityId = catalog.city(new Requests.City("Pune", "Asia/Kolkata"));
        theaterId = catalog.theater(new Requests.Theater(cityId, "Central Cinema"));
        screenId = catalog.screen(new Requests.Screen(theaterId, "Screen 1"));
        catalog.layout(screenId, new Requests.Layout(List.of(new Requests.Seat("A1", SeatTier.REGULAR),
            new Requests.Seat("A2", SeatTier.REGULAR), new Requests.Seat("B1", SeatTier.PREMIUM))));
        policyId = catalog.policy(new Requests.Policy("Standard", 120, 80));
        showId = catalog.createShow(showRequest(clock.instant().plusSeconds(10800)));
    }
    Requests.Show showRequest(Instant starts) {
        return new Requests.Show(screenId, "Arrival", starts, starts.plusSeconds(7200), new Requests.Pricing(10000, 15000, 20, policyId));
    }
    UUID hold(String user, String... seats) { return (UUID) bookings.hold(user, new Requests.Hold(showId, List.of(seats), null)).get("id"); }
    Map<String, Object> pay(UUID id) { return bookings.pay(id, "alice", new Requests.Payment("payment-" + id, PaymentToken.SUCCESS)); }
    String bookingStatus(UUID id) { return Db.string(bookings.view(id, "alice"), "status"); }
    int count(String table) { return db.jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    void coupon(int uses) { catalog.discount(new Requests.Discount("SAVE20", 20, 3000, 10000, uses, clock.instant().plusSeconds(86400))); }

    @Test void holdPaymentCancellationAndHistory() {
        coupon(10);
        var held = bookings.hold("alice", new Requests.Hold(showId, List.of("A1", "B1"), "SAVE20"));
        UUID id = (UUID) held.get("id");
        assertThat(held).containsEntry("subtotal", 25000L).containsEntry("discount", 3000L).containsEntry("total", 22000L);
        pay(id);
        assertThat(bookingStatus(id)).isEqualTo("CONFIRMED");
        assertThat(count("notification")).isEqualTo(2);
        verifyNoInteractions(sender);
        assertThat(bookings.history("bob", 50, 0)).isEmpty();
        assertThat(bookings.history("alice", 50, 0)).hasSize(1);
        var cancelled = bookings.cancel(id, "alice");
        assertThat(cancelled.get("status")).isEqualTo("CANCELLED");
        assertThat(db.one("SELECT amount FROM refund WHERE booking_id = ?", id)).containsEntry("amount", 17600L);
        bookings.cancel(id, "alice");
        assertThat(count("refund")).isEqualTo(1);
        assertThat(bookings.seats(showId)).allMatch(row -> "AVAILABLE".equals(row.get("availability")));
    }

    @Test void concurrentSeatRequestsHaveExactlyOneWinner() throws Exception {
        var outcomes = race(8, () -> {
            try { hold("alice", "A1"); return "won"; }
            catch (ApiException e) { assertThat(e.status.value()).isEqualTo(409); return "lost"; }
        });
        assertThat(outcomes.stream().filter("won"::equals)).hasSize(1);
        assertThat(count("booking")).isEqualTo(1);
        assertThat(count("booking_seat")).isEqualTo(1);
    }

    @Test void overlappingSeatRequestsAreAllOrNothing() {
        hold("bob", "A2");
        assertThatThrownBy(() -> hold("alice", "A1", "A2")).isInstanceOf(ApiException.class);
        assertThat(count("booking")).isEqualTo(1);
        assertThat(bookings.seats(showId).get(0)).containsEntry("availability", "AVAILABLE");
        hold("alice", "A1");
    }

    @Test void exactExpiryReleasesSeatsEvenBeforeTheJobRuns() {
        UUID id = hold("alice", "A1");
        clock.advance(300);
        assertThat(bookingStatus(id)).isEqualTo("EXPIRED");
        assertThat(bookings.seats(showId).get(0)).containsEntry("availability", "AVAILABLE");
        assertThatThrownBy(() -> pay(id)).isInstanceOf(ApiException.class);
        hold("bob", "A1");
        assertThat(db.one("SELECT status FROM booking WHERE id = ?", id)).containsEntry("status", "EXPIRED");
        assertThat(count("payment")).isZero();
    }

    @Test void scheduledCleanupReleasesExpiredHolds() {
        UUID id = hold("alice", "A1"); clock.advance(300);
        new Jobs(db, clock, bookings, notifications).tick();
        assertThat(db.one("SELECT status FROM booking WHERE id = ?", id)).containsEntry("status", "EXPIRED");
        assertThat(db.one("SELECT booking_id FROM show_seat WHERE show_id = ? AND label = 'A1'", showId).get("bookingId")).isNull();
    }

    @Test void paymentRetriesAreIdempotentUnderConcurrency() throws Exception {
        UUID id = hold("alice", "A1");
        race(6, () -> { pay(id); return true; });
        assertThat(count("payment")).isEqualTo(1);
        assertThat(count("notification")).isEqualTo(2);
        assertThat(bookingStatus(id)).isEqualTo("CONFIRMED");
    }

    @Test void declinedPaymentCanBeRetriedWithANewKey() {
        UUID id = hold("alice", "A1");
        var declined = new Requests.Payment("declined-key", PaymentToken.DECLINE);
        bookings.pay(id, "alice", declined); bookings.pay(id, "alice", declined);
        assertThat(bookingStatus(id)).isEqualTo("HELD");
        assertThat(count("notification")).isZero();
        assertThat(count("payment")).isEqualTo(1);
        assertThatThrownBy(() -> bookings.pay(id, "alice", new Requests.Payment("declined-key", PaymentToken.SUCCESS))).isInstanceOf(ApiException.class);
        pay(id);
        assertThat(count("payment")).isEqualTo(2);
    }

    @Test void idempotencyKeyCannotBeReusedForAnotherBooking() {
        UUID first = hold("alice", "A1"); pay(first);
        UUID second = hold("alice", "A2");
        assertThatThrownBy(() -> bookings.pay(second, "alice", new Requests.Payment("payment-" + first, PaymentToken.SUCCESS))).isInstanceOf(ApiException.class);
        assertThat(bookingStatus(second)).isEqualTo("HELD");
    }

    @Test void customersCannotReadPayOrCancelSomeoneElsesBooking() throws Exception {
        UUID id = hold("alice", "A1");
        mvc.perform(get("/api/bookings/" + id).with(httpBasic("bob", "customer-password"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/" + id + "/cancel").with(httpBasic("bob", "customer-password"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/bookings/" + id + "/payments").with(httpBasic("bob", "customer-password"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"someone-else\",\"token\":\"tok_success\"}"))
            .andExpect(status().isNotFound());
        assertThat(bookingStatus(id)).isEqualTo("HELD");
    }

    @Test void pricingAndRefundTermsAreSnapshottedAtHoldTime() {
        UUID id = hold("alice", "A1");
        catalog.pricing(showId, new Requests.Pricing(20000, 30000, 50, policyId));
        catalog.updatePolicy(policyId, new Requests.Policy("No refunds", 120, 0));
        pay(id); bookings.cancel(id, "alice");
        assertThat(db.one("SELECT amount FROM refund WHERE booking_id = ?", id)).containsEntry("amount", 8000L);
        UUID next = hold("alice", "A1");
        assertThat(bookings.view(next, "alice")).containsEntry("total", 20000L).containsEntry("refundPercent", 0);
    }

    @Test void lateCancellationFreesSeatWithoutRefund() {
        UUID id = hold("alice", "A1"); pay(id); clock.advance(3601);
        bookings.cancel(id, "alice");
        assertThat(db.one("SELECT amount FROM refund WHERE booking_id = ?", id)).containsEntry("amount", 0L);
        hold("bob", "A1");
    }

    @Test void showCancellationRefundsFullAmountAndRemovesReminders() {
        UUID id = hold("alice", "A1"); pay(id); hold("bob", "A2");
        catalog.updatePolicy(policyId, new Requests.Policy("No refunds", 120, 0));
        clock.advance(3601);
        bookings.cancelShow(showId); bookings.cancelShow(showId);
        assertThat(db.one("SELECT amount FROM refund WHERE booking_id = ?", id)).containsEntry("amount", 10000L);
        assertThat(count("refund")).isEqualTo(1);
        assertThat(db.rows("SELECT * FROM notification WHERE kind = 'REMINDER'")).isEmpty();
        assertThat(bookings.seats(showId)).allMatch(row -> "UNAVAILABLE".equals(row.get("availability")));
        assertThatThrownBy(() -> hold("alice", "B1")).isInstanceOf(ApiException.class);
    }

    @Test void cancellationAndPaymentCannotLeaveAnAllocatedCancelledSeat() throws Exception {
        UUID id = hold("alice", "A1");
        raceTasks(List.of(() -> { try { pay(id); } catch (ApiException e) { assertThat(e.status.value()).isEqualTo(409); } return true; },
            () -> { bookings.cancel(id, "alice"); return true; }));
        assertThat(bookingStatus(id)).isEqualTo("CANCELLED");
        assertThat(count("refund")).isEqualTo(count("payment"));
        assertThat(bookings.seats(showId).get(0)).containsEntry("availability", "AVAILABLE");
    }

    @Test void holdsReserveLimitedDiscountUsesAndExpiryReturnsThem() {
        coupon(1);
        UUID id = (UUID) bookings.hold("alice", new Requests.Hold(showId, List.of("A1"), "SAVE20")).get("id");
        assertThatThrownBy(() -> bookings.hold("bob", new Requests.Hold(showId, List.of("A2"), "SAVE20"))).isInstanceOf(ApiException.class);
        clock.advance(300);
        bookings.hold("bob", new Requests.Hold(showId, List.of("A2"), "SAVE20"));
        assertThat(bookingStatus(id)).isEqualTo("EXPIRED");
    }

    @Test void discountLimitIsSafeAcrossDifferentShows() throws Exception {
        coupon(1);
        long secondShow = catalog.createShow(showRequest(clock.instant().plusSeconds(21600)));
        List<Supplier<String>> tasks = new ArrayList<>();
        for (long selected : List.of(showId, secondShow)) tasks.add(() -> {
            try { bookings.hold("alice", new Requests.Hold(selected, List.of("A1"), "SAVE20")); return "won"; }
            catch (ApiException e) { return "lost"; }
        });
        assertThat(raceTasks(tasks).stream().filter("won"::equals)).hasSize(1);
    }

    @Test void invalidDiscountRollsBackEntireHold() {
        assertThatThrownBy(() -> bookings.hold("alice", new Requests.Hold(showId, List.of("A1"), "MISSING"))).isInstanceOf(ApiException.class);
        assertThat(count("booking")).isZero();
        hold("alice", "A1");
    }

    @Test void duplicateAndUnknownSeatSelectionsFail() {
        assertThatThrownBy(() -> hold("alice", "A1", "A1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> hold("alice", "Z9")).isInstanceOf(ApiException.class);
        assertThat(count("booking")).isZero();
    }

    @Test void showsCannotOverlapButCanMeetAtAnEndpoint() {
        assertThatThrownBy(() -> catalog.createShow(showRequest(clock.instant().plusSeconds(12000)))).isInstanceOf(ApiException.class);
        catalog.createShow(showRequest(clock.instant().plusSeconds(18000)));
        assertThat(count("movie_show")).isEqualTo(2);
    }

    @Test void concurrentOverlappingShowsHaveOneWinner() throws Exception {
        var outcomes = race(4, () -> {
            try { catalog.createShow(showRequest(clock.instant().plusSeconds(86400))); return "won"; }
            catch (ApiException e) { return "lost"; }
        });
        assertThat(outcomes.stream().filter("won"::equals)).hasSize(1);
    }

    @Test void layoutChangesOnlyAffectNewShows() {
        catalog.layout(screenId, new Requests.Layout(List.of(new Requests.Seat("C1", SeatTier.PREMIUM))));
        long next = catalog.createShow(showRequest(clock.instant().plusSeconds(86400)));
        assertThat(bookings.seats(showId)).hasSize(3);
        assertThat(bookings.seats(next)).hasSize(1).first().satisfies(seat -> assertThat(seat.get("label")).isEqualTo("C1"));
    }

    @Test void showWithBookingHistoryCannotBeRescheduled() {
        hold("alice", "A1");
        assertThatThrownBy(() -> catalog.updateShow(showId, showRequest(clock.instant().plusSeconds(86400)))).isInstanceOf(ApiException.class);
    }

    @Test void showStartCapsHoldExpiryAndClosesBooking() {
        clock.advance(10700);
        UUID id = hold("alice", "A1");
        assertThat(bookings.view(id, "alice").get("expiresAt")).isEqualTo(Instant.parse("2030-01-03T13:00:00Z"));
        clock.advance(100);
        assertThatThrownBy(() -> pay(id)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> hold("bob", "A2")).isInstanceOf(ApiException.class);
    }

    @Test void notificationDeliveryIsDeferredIdempotentAndOwnerScoped() throws Exception {
        UUID id = hold("alice", "A1"); pay(id);
        var jobs = new Jobs(db, clock, bookings, notifications);
        jobs.tick(); jobs.tick();
        verify(sender, times(1)).send(any(), anyString());
        mvc.perform(get("/api/notifications").with(httpBasic("alice", "customer-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].kind").value("CONFIRMATION"));
        mvc.perform(get("/api/notifications").with(httpBasic("bob", "customer-password")))
            .andExpect(jsonPath("$.length()").value(0));
        clock.advance(7200); jobs.tick();
        verify(sender, times(2)).send(any(), anyString());
    }

    @Test void notificationFailureDoesNotUndoPaymentAndIsRetried() {
        UUID id = hold("alice", "A1"); pay(id);
        doThrow(new IllegalStateException("delivery unavailable")).doNothing().when(sender).send(any(), anyString());
        var jobs = new Jobs(db, clock, bookings, notifications); jobs.tick();
        assertThat(bookingStatus(id)).isEqualTo("CONFIRMED");
        assertThat(db.one("SELECT attempts, delivered_at FROM notification WHERE booking_id = ? AND kind = 'CONFIRMATION'", id))
            .containsEntry("attempts", 1).containsEntry("deliveredAt", null);
        clock.advance(5); jobs.tick();
        assertThat(db.one("SELECT delivered_at FROM notification WHERE booking_id = ? AND kind = 'CONFIRMATION'", id).get("deliveredAt")).isNotNull();
    }

    @Test void accessControlValidationAndRegistration() throws Exception {
        mvc.perform(get("/api/cities")).andExpect(status().isOk());
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/screens").with(httpBasic("alice", "customer-password"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/screens").with(httpBasic("admin", "test-admin-password"))).andExpect(status().isOk());
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"charlie\",\"password\":\"charlie-password\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"));
        assertThat(Db.string(db.one("SELECT password_hash FROM app_user WHERE username = 'charlie'"), "passwordHash"))
            .doesNotContain("charlie-password");
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"mallory\",\"password\":\"long-password\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").with(httpBasic("alice", "customer-password")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"showId\":" + showId + ",\"seats\":[]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").exists());
        mvc.perform(get("/api/shows?limit=1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookings/not-a-uuid").with(httpBasic("alice", "customer-password"))).andExpect(status().isBadRequest());
    }

    @Test void publicBrowsingFiltersCityTitleAndTime() throws Exception {
        mvc.perform(get("/api/shows").param("cityId", Long.toString(cityId)).param("title", "arrival"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/shows").param("cityId", "99999")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/shows").param("from", "2030-01-04T00:00:00Z")).andExpect(jsonPath("$.length()").value(0));
    }


    @Test void confirmedSeatsDoNotExpireWithTheirOriginalHoldDeadline() {
        UUID id = hold("alice", "A1");
        clock.advance(299); pay(id); clock.advance(1);
        new Jobs(db, clock, bookings, notifications).tick();
        assertThat(bookingStatus(id)).isEqualTo("CONFIRMED");
        assertThatThrownBy(() -> hold("bob", "A1")).isInstanceOf(ApiException.class);
    }

    @Test void cancellingAnUnpaidHoldReturnsItsDiscountUse() {
        coupon(1);
        UUID id = (UUID) bookings.hold("alice", new Requests.Hold(showId, List.of("A1"), "SAVE20")).get("id");
        bookings.cancel(id, "alice");
        assertThat(count("refund")).isZero();
        bookings.hold("bob", new Requests.Hold(showId, List.of("A1"), "SAVE20"));
    }

    @Test void cancellingAPaidBookingDoesNotReturnItsDiscountUse() {
        coupon(1);
        UUID id = (UUID) bookings.hold("alice", new Requests.Hold(showId, List.of("A1"), "SAVE20")).get("id");
        pay(id); bookings.cancel(id, "alice");
        assertThatThrownBy(() -> bookings.hold("bob", new Requests.Hold(showId, List.of("A1"), "SAVE20"))).isInstanceOf(ApiException.class);
    }

    @Test void discountExpiryAndMinimumSpendAreEnforced() {
        catalog.discount(new Requests.Discount("BIG20", 20, 3000, 20000, 5, clock.instant().plusSeconds(10)));
        assertThatThrownBy(() -> bookings.hold("alice", new Requests.Hold(showId, List.of("A1"), "BIG20"))).isInstanceOf(ApiException.class);
        clock.advance(10);
        assertThatThrownBy(() -> bookings.hold("alice", new Requests.Hold(showId, List.of("A1", "B1"), "BIG20"))).isInstanceOf(ApiException.class);
        assertThat(count("booking")).isZero();
    }

    @Test void zeroTotalBookingCanBeConfirmedAndCancelled() {
        catalog.discount(new Requests.Discount("FREE", 100, 10000, 0, 1, clock.instant().plusSeconds(60)));
        UUID id = (UUID) bookings.hold("alice", new Requests.Hold(showId, List.of("A1"), "FREE")).get("id");
        assertThat(bookings.view(id, "alice")).containsEntry("total", 0L);
        pay(id); bookings.cancel(id, "alice");
        assertThat(db.one("SELECT amount FROM refund WHERE booking_id = ?", id)).containsEntry("amount", 0L);
    }

    @Test void nullLayoutSeatIsAValidationError() throws Exception {
        mvc.perform(put("/api/admin/screens/" + screenId + "/seats").with(httpBasic("admin", "test-admin-password"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"seats\":[null]}"))
            .andExpect(status().isBadRequest());
    }


    @Test void paymentEnumPreservesApiTokensAndRejectsInvalidValues() throws Exception {
        UUID id = hold("alice", "A1");
        String path = "/api/bookings/" + id + "/payments";
        for (String token : List.of("\"unknown\"", "\"SUCCESS\"", "0", "true", "null")) {
            mvc.perform(post(path).with(httpBasic("alice", "customer-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"enum-payment-key\",\"token\":" + token + "}"))
                .andExpect(status().isBadRequest());
        }
        assertThat(count("payment")).isZero();
        mvc.perform(post(path).with(httpBasic("alice", "customer-password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"enum-declined-key\",\"token\":\"tok_decline\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payment.outcome").value("DECLINED"))
            .andExpect(jsonPath("$.booking.status").value("HELD"));
        mvc.perform(post(path).with(httpBasic("alice", "customer-password"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"enum-payment-key\",\"token\":\"tok_success\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payment.outcome").value("SUCCEEDED"))
            .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        assertThat(count("payment")).isEqualTo(2);
    }

    @Test void seatTierRequiresAValidNameRatherThanAnOrdinal() throws Exception {
        for (String tier : List.of("0", "\"VIP\"", "\"premium\"")) {
            mvc.perform(put("/api/admin/screens/" + screenId + "/seats").with(httpBasic("admin", "test-admin-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seats\":[{\"label\":\"C1\",\"tier\":" + tier + "}]}"))
                .andExpect(status().isBadRequest());
        }
        assertThat(count("layout_seat")).isEqualTo(3);
        mvc.perform(put("/api/admin/screens/" + screenId + "/seats").with(httpBasic("admin", "test-admin-password"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"seats\":[{\"label\":\"C1\",\"tier\":\"PREMIUM\"}]}"))
            .andExpect(status().isOk());
        assertThat(db.one("SELECT tier FROM layout_seat WHERE screen_id = ?", screenId)).containsEntry("tier", "PREMIUM");
    }

    @Test void paginationDefaultsAndBoundsApplyToAllPagedEndpoints() throws Exception {
        for (String path : List.of("/api/shows", "/api/bookings", "/api/notifications")) {
            mvc.perform(get(path).with(httpBasic("alice", "customer-password"))).andExpect(status().isOk());
            mvc.perform(get(path).param("limit", "100").with(httpBasic("alice", "customer-password")))
                .andExpect(status().isOk());
            for (String limit : List.of("0", "-1", "101")) {
                mvc.perform(get(path).param("limit", limit).with(httpBasic("alice", "customer-password")))
                    .andExpect(status().isBadRequest());
            }
            mvc.perform(get(path).param("offset", "-1").with(httpBasic("alice", "customer-password")))
                .andExpect(status().isBadRequest());
        }
    }

    private <T> List<T> race(int workers, Supplier<T> action) throws Exception {
        return raceTasks(Collections.nCopies(workers, action));
    }
    private <T> List<T> raceTasks(List<Supplier<T>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (var action : actions) futures.add(executor.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                return action.get();
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { executor.shutdownNow(); }
    }
}
