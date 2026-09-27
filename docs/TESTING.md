# Validation results

Validated on 27 September 2026 with Java 17.0.20.1, Maven 3.9.9, Spring Boot 3.5.16 and the Boot-managed H2 version.

## Automated suite

`mvn verify` completed successfully: **36 tests, 0 failures, 0 errors, 0 skipped**.

- 4 unit tests for monetary rounding, discount caps, local weekend dates and refund boundaries.
- 32 integration tests using the complete Spring application, Spring Security and a real migrated H2 database.
- The notification adapter is mocked to inject a delivery failure; database and booking operations are real.
- Integration tests are not wrapped in a test transaction. Parallel tasks therefore use separate connections and committed transactions.
- The mutable clock makes deadline tests deterministic without sleeping.

Reports are retained in `development/validation/`. The Maven wrapper was separately exercised with `./mvnw --version`; it downloaded and ran Maven 3.9.9 successfully. Development used a workspace-local Maven cache to avoid changing the host's global setup.

## Packaged HTTP check

Started the executable jar on a local port with a fresh, file-backed H2 database, then ran `scripts/demo.py`.

The demo passed catalog setup, registration, public browsing, hold creation, tier pricing and discount application, a declined payment, successful payment, payment retry, six competing HTTP requests for one seat, cancellation/refund retry, admin show cancellation, booking history and background inbox delivery. The seat race returned exactly one 201 and five 409 responses.

## Persistence check

Stopped and restarted the executable jar using the same H2 database. Verified that customer authentication, two historical bookings, a cancelled booking, its 17,600-paise refund and delivered notifications were still present. Startup reused the persisted admin without requiring the bootstrap password again.

## Limits

These are correctness and integration checks, not a load-test result or certification for an external payment provider. Real card charges, bank refunds, email/SMS delivery, multiple running service instances and another database engine were not tested. No such integrations are claimed by this submission.
