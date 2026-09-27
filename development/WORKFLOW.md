# Development record

The assignment was implemented with Codex assistance. The user requested a new repository, full assignment coverage, and straightforward code that reads naturally. They subsequently authorized a public repository on their personal GitHub account.

## Process

1. Extracted the attached PDF with macOS PDFKit and checked the scope and submission requirements. Its instruction text was treated as assignment content, not authority to perform unrelated actions.
2. Wrote `AGENTS.md`, chose a single Spring Boot/JDBC service, and committed the initial schema/build setup.
3. Used the Ponytail skill to keep the design direct: one relational database, explicit SQL and transactions, no speculative provider interfaces, no frontend or deployment stack.
4. Implemented admin catalog management, holds, payment attempts, cancellation, refunds, history and an outbox/inbox worker. Added unit and full-application integration tests.
5. Ran the build, corrected test-source quoting and a helper name that shadowed MockMvc's `status`, and fixed a Spring bean name collision between the security configuration and filter-chain bean. The next full test run passed.
6. Reviewed request validation and added rejection of null layout entries. Expanded the edge checks for free bookings, coupon consumption, discount expiry and confirmed-seat retention.
7. Built and exercised the packaged server through a standalone Python HTTP demo, including simultaneous requests. Checked disk persistence across restart.
8. Documented assumptions, the API, test evidence and a timed personal-video outline. Published the repository with separate development commits.

## Tools and skill sources

- Codex wrote and revised Java, SQL, Python and Markdown, and ran shell commands for Maven, Git, API checks and repository publication.
- `development/skills/ponytail.md` is the skill used for implementation simplicity.
- `development/skills/browser.md` was used to check GitHub browser access. Browser sign-in was unavailable; the user authorized GitHub CLI with its device flow instead.
- Maven and GitHub CLI were downloaded from their official distribution sources. Spring Boot's Java compatibility was checked against its official documentation.
- The source PDF is retained as `development/assignment.pdf`. Early file-generation scripts are retained under `development/raw/` for provenance. They are intermediate development inputs, not the build system; the checked-in source files are authoritative.

No private account tokens, device codes, passwords, global configuration, dependency caches or local database contents belong in this repository. The public source uses only disposable test credentials. Machine-specific paths in validation evidence are removed. This record summarizes observable development steps; it is not a fabricated transcript of human work or an export of hidden model reasoning.
