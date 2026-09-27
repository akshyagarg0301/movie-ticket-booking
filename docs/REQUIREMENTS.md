# Assignment checklist

This maps the supplied PDF to the implementation. Choices and limitations are described in the README; “implemented” refers to the stated local-service scope.

| Requirement | Implementation / evidence |
|---|---|
| Spring Boot REST service | Spring Boot application, Gradle wrapper, catalog and booking controllers |
| Multiple cities, theaters and shows | City → theater → screen → show foreign keys, catalog APIs and browse filters |
| Seat layouts and seat selection | Screen layouts, per-show snapshots, regular/premium seats, atomic 1–10-seat holds |
| Time-bound holds and automatic release | Persisted deadline; worker cleanup; lazy cleanup and effective availability at expiry |
| Correct concurrent allocation | Database row locking per show; all-or-nothing transaction; concurrency integration tests and HTTP demo |
| Regular, premium and weekend pricing | Base prices per seat tier plus weekend markup in the city's timezone |
| Discount codes | Percentage, cap, minimum spend, expiry, enable/disable and concurrency-safe global usage limit |
| Payment and booking confirmation | Explicit simulator tokens; persisted attempt records; idempotency; atomic confirmation |
| Cancellation and configurable refunds | Policy CRUD, snapshotted terms, boundary-tested refund computation, one refund record per booking |
| Admin show cancellation | Cancel active bookings and issue full simulated refunds; suppress pending reminders |
| Nonblocking confirmations and reminders | Transactional outbox, scheduled delivery to persistent inbox, retries and cancellation notices |
| Admin role | Protected catalog, pricing, policy, layout and discount endpoints |
| Customer role | Browse, select seats, pay, cancel, see own history/inbox |
| Database persistence | File-backed H2 with Flyway migration; restart verification |
| Basic RBAC | Spring Security, HTTP Basic, BCrypt, owner checks, role fixed at registration |
| Input validation and error handling | Bean Validation, semantic checks, DB constraints, problem responses |
| Unit and integration tests | Pricing unit tests, application/database/security/concurrency integration tests, runnable HTTP demo |
| README with meaningful assumptions | README scope/choices, locking explanation, limitations and startup instructions |
| Multiple development commits | Separate setup, implementation, validation and documentation commits |
| Agents.md / Claude.md used | `AGENTS.md` (conventional uppercase filename) |
| Skills used | Verbatim skill sources in `development/skills/` |
| Raw development files | Assignment PDF, source, migrations, tests, demo, development scripts and validation evidence |
| Public personal GitHub repository | Created under the user's authorized GitHub account; repository URL supplied with delivery |
| Video, maximum ten minutes | The applicant must record and submit the final Loom link |

Out-of-scope items from the PDF were intentionally excluded: frontend, deployment/containerization/CI, microservices, advanced authentication and production observability.
