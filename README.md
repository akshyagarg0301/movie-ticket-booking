# Movie Ticket Booking

A Spring Boot REST API for booking individual seats across cities, theaters and screens. The interesting part is the booking transaction: a seat can belong to one active booking, even when several customers request it together.

## Run it

Requires JDK 17. The Gradle wrapper downloads Gradle 8.8 and dependencies on the first run. On Windows, use `gradlew.bat` in place of `./gradlew`. No database installation is needed.

```sh
export ADMIN_PASSWORD='choose-a-local-password'
./gradlew bootRun
```

The API listens on `http://localhost:8080`. The first startup creates `admin` using `ADMIN_PASSWORD`; subsequent starts keep the stored account. Set `ADMIN_USERNAME` before the first start to choose another name. There is deliberately no default admin password.

The default H2 database is stored under `data/` and survives a restart. Flyway applies the versioned schema. `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD` override connection settings. This submission is tested against H2; changing database engines requires compatibility checks and the appropriate JDBC driver.

Run the tests and build an executable jar:

```sh
./gradlew clean build
java -jar build/libs/movie-ticket-booking-1.0.0.jar
```

Run the complete HTTP demo in another terminal with the same admin password:

```sh
export ADMIN_PASSWORD='choose-a-local-password'
python3 scripts/demo.py
```

The demo creates its own uniquely named catalog and customer, then checks a declined payment, successful payment, idempotent retries, six competing seat requests, cancellation, refund and asynchronous notifications. It can be rerun without deleting data. It leaves its sample records for inspection.

## Scope and choices

- **Stack:** Java 17, Gradle 8.8, Spring Boot 3.5.16, Spring MVC, Spring Security, Bean Validation, JDBC, Flyway and H2. JDBC makes the lock order and SQL constraints visible. H2 keeps the assignment runnable without infrastructure. [Spring Boot's requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) describe supported Java and build-tool versions.
- **One service, one database.** No UI, containers, deployment configuration, CI or microservices, as requested in the assignment.
- **Hierarchy:** city → theater → screen → show. A theater can contain several screens; each screen has its own layout. Shows on the same screen cannot overlap. Adjacent show times are allowed; cleaning time is not added automatically.
- **Show details:** a title and start/end times are enough here. A separate movie catalog, actors, language and age ratings are left out.
- **Times:** API timestamps include an offset and are stored as UTC instants. Weekend pricing uses the city's IANA timezone. City timezones are immutable so published pricing does not change unexpectedly.
- **Money:** all amounts are integer paise in INR, including API fields named `price`, `total`, `amount` and `maxDiscount`. ₹100 is `10000`. Percentage calculations round half up to the nearest paise. No taxes or booking fees are added.
- **Pricing:** seats are `REGULAR` or `PREMIUM`; a configurable weekend percentage is added on Saturday/Sunday. One percentage discount can then apply to the subtotal, subject to minimum spend, expiry, maximum discount and a global usage limit. Codes are case-sensitive uppercase.
- **Discount usage:** an unexpired hold reserves one use per booking. Expiry or cancelling an unpaid hold releases it. A successful payment consumes it permanently, including after cancellation. Disabling a code prevents new holds; existing quoted holds retain their discount.
- **Holds:** five minutes by default, capped at show start; up to ten distinct seats per booking. Change `cinema.hold-minutes` to 1–30. Multi-seat requests are atomic. There is no hold extension or partial cancellation.
- **Price and refund snapshots:** a hold stores seat prices, discount, total and the refund terms. Later admin edits only affect new holds. Existing show seats are snapshots of the screen layout. New layouts only affect subsequently created shows.
- **Cancellation:** customers can cancel a whole booking before show start. The policy gives a percentage refund when at least `cutoffMinutes` remain; the cutoff is inclusive. Later cancellation still frees the seats but refunds zero. Cancellation after show start is rejected. An admin cancelling a future show refunds every paid, active booking in full.
- **Catalog management:** admins create and rename cities/theaters/screens, replace layouts, create/update policies, schedule/update shows and prices, cancel shows, and create/disable discounts. Resources with historical references are retained rather than deleted. A show with any booking history cannot be rescheduled.
- **Authentication:** stateless HTTP Basic and BCrypt passwords. Public registration always creates a customer; the client cannot assign a role. Customers can only read/pay/cancel their own bookings and read their own inbox. Unknown and other users' booking IDs both return 404. Admins manage the catalog and do not impersonate customers. This is a local API assignment; use HTTPS if exposing Basic authentication beyond localhost. No browser session or frontend is supported.
- **Payments are simulated.** `tok_success` succeeds, `tok_decline` declines. These are test tokens, not card details. Every attempt has a caller-supplied idempotency key. Repeating the same request returns its previous payment; reusing a key for different input is rejected. A decline leaves the hold available for another attempt until expiry. A new key is required to retry with a different token. Fully discounted bookings still go through the same confirmation endpoint.
- **Refunds are simulated.** A persisted refund entry is created in the same transaction as cancellation. The unique booking key prevents duplicate refunds. No actual money moves. A real gateway would need a pending-payment/refund state and idempotent reconciliation; holding a DB transaction open across a remote charge would be the wrong extension.
- **Notifications:** confirmation, cancellation and one-hour reminders go into a transactional outbox. A scheduled worker publishes them to a persistent customer inbox and the application log. Delivery is outside the booking request. Failure retries with exponential backoff capped at one hour. Cancellation removes pending reminders. A booking made less than an hour before its show gets a reminder on the next worker run. If the process was down, overdue notifications are delivered after restart. This local adapter does not send email or SMS.

## Shared domain definitions

Booking/show states, seat tiers and availability, user roles, payment tokens/outcomes, and notification types are Java enums. The database stores their names as strings; payment tokens keep their existing `tok_success` and `tok_decline` JSON values. Invalid names and numeric enum ordinals are rejected at the request boundary.

`ValidationRules` defines request limits and patterns shared with admin bootstrap validation. `BookingRules` names the currency, percentage base, allowed hold range and reminder lead time. `Pagination` owns defaults and bounds for all paginated endpoints. `ApiPaths` is shared by controllers and security; `ApiFields` owns the JDBC alias/JSON keys used by map-based views. Worker batch/retry limits are named constants in `Jobs`, and elapsed times use `Duration`. The hold duration and worker interval remain external configuration in `application.properties`.

Historical Flyway migrations keep their original SQL literals so existing databases retain valid checksums. Runtime SQL binds enum names as parameters. Test fixtures and expected API strings remain explicit so tests can detect accidental changes to the public contract.

## Why seats cannot be allocated twice

Every operation that changes a show's allocations first locks its `movie_show` row with `SELECT … FOR UPDATE`. Under that lock it expires old holds, checks availability, and changes booking/seat state in one transaction. The lock is a database lock, not a Java mutex. A second transaction waits and then sees the first transaction's committed allocation.

`show_seat` has one row per `(show_id, label)` and a single booking reference. `booking_seat` preserves historical selections after cancellation. There is no in-memory availability cache. Seat reads treat an expired hold as available immediately, even if cleanup has not run. Payment checks the deadline again after acquiring the lock; a hold is invalid at `now >= expiresAt`.

The lock deliberately serializes even non-overlapping requests **within one show**. Different shows can proceed independently, except when competing for the same discount's final uses. Discount rows are locked after show rows. Schedule/layout writes lock the screen; scheduling updates then lock the show. Notification delivery uses the same show-first order as cancellation. The tests exercise real concurrent transactions with separate connections. This is a correctness-focused single-service design, not a claim of measured high-throughput capacity. Seat-level ordered locking is the next change if a load test shows a hot-show bottleneck.

## API guide

Full request examples and the endpoint list are in [docs/API.md](docs/API.md). Create a catalog as admin, register a customer, browse seats, create a hold, and pay using the returned booking ID. Use `curl -u username:password` for protected endpoints.

Errors use problem JSON with `status`, `title` and `detail`. Invalid input is 400, missing credentials 401, wrong role 403, missing/foreign resources 404, and state/uniqueness conflicts 409. A declined simulator payment is a successful HTTP request with `payment.outcome = DECLINED`; the booking remains held.

## Tests

`PricingTest` covers rounding, discount caps, local-date weekend pricing and refund boundaries. `BookingIntegrationTest` starts the full Spring application with the migrated H2 schema and checks booking/payment/cancellation, authorization, input validation, concurrency, layout snapshots, overlapping schedules, limited discounts and notification retries. Tests control a `Clock`; expiry tests do not sleep. The HTTP demo additionally exercises the packaged server over the network.

See [docs/TESTING.md](docs/TESTING.md) for the recorded validation results and limits. Test credentials are isolated in test configuration. Runtime passwords, database files and build output are ignored by Git.

## Submission materials

- [Requirement checklist](docs/REQUIREMENTS.md)
- [API reference](docs/API.md)
- [Development and AI workflow record](development/WORKFLOW.md)
- [Instructions used during development](AGENTS.md)
- [Skills used](development/skills/)
- [Original assignment](development/assignment.pdf)

The implementation was developed with AI assistance. The workflow record explains what was generated, reviewed and tested.
