# Validation results

Validated on 27 September 2026 with Java 17.0.20.1, Gradle 8.8, Spring Boot 3.5.16 and the Boot-managed H2 version.

## Automated suite

`./gradlew clean build` completed successfully: **39 tests, 0 failures, 0 errors, 0 skipped**.

- 4 unit tests for monetary rounding, discount caps, local weekend dates and refund boundaries.
- 35 integration tests using the complete Spring application, Spring Security and a real migrated H2 database.
- The notification adapter is mocked to inject a delivery failure; database and booking operations are real.
- Integration tests are not wrapped in a test transaction. Parallel tasks therefore use separate connections and committed transactions.
- The mutable clock makes deadline tests deterministic without sleeping.

Reports are retained in `development/validation/`. The Gradle wrapper ran the complete build using a workspace-local Gradle cache. Its distribution is pinned to 8.8 with an official SHA-256 checksum. Earlier Maven reports are retained as development history; `gradle-build.txt` records the current build. The generated HTML report is `build/reports/tests/test/index.html`.

The enum/constants refactor also checks the existing payment token JSON values, invalid enum names and ordinals, and consistent pagination across shows, booking history and notifications.

## Packaged HTTP check

Started the Gradle-built executable jar from `build/libs/` on a local port with a fresh, file-backed H2 database, then ran `scripts/demo.py`.

The demo passed catalog setup, registration, public browsing, hold creation, tier pricing and discount application, a declined payment, successful payment, payment retry, six competing HTTP requests for one seat, cancellation/refund retry, admin show cancellation, booking history and background inbox delivery. The seat race returned exactly one 201 and five 409 responses.

## Persistence check

Stopped and restarted the executable jar using the same H2 database. Verified that customer authentication, two historical bookings, a cancelled booking, its 17,600-paise refund and delivered notifications were still present. Startup reused the persisted admin without requiring the bootstrap password again.

After the enum/constants refactor, the new jar was started against the previous version's database. Existing accounts, bookings, refunds, notifications and seat availability remained readable; see `enum-upgrade.txt`. The complete HTTP demo also passed against the refactored application (`enum-http-demo.txt`).

## Limits

These are correctness and integration checks, not a load-test result or certification for an external payment provider. Real card charges, bank refunds, email/SMS delivery, multiple running service instances and another database engine were not tested. No such integrations are claimed by this submission.
