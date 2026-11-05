# ADR-006: Notification Outbox

## Status
Proposed. The mechanism is gate D-09 and is decided before Phase 2c.

## Context
The old application sends email inside request handling. A Mailgun failure after the data was saved
gives the client a 500, and the retry then hits the duplicate rule (finding OTH-04). Email must never
fail a committed write, and must never be sent for data that was rolled back.

## Decision (proposed)
A transactional outbox. The caller writes an `outbox_message` row in its own transaction. A scheduled
dispatcher picks due rows with `FOR UPDATE SKIP LOCKED`, sends them outside any transaction, retries
with backoff, and marks a row failed after a fixed number of attempts. The outbox table is hand-rolled.

## Consequences

### Positive
- An email exists only if its data was committed.
- A Mailgun outage delays email and never breaks a request.
- `SKIP LOCKED` makes the dispatcher safe with more than one instance.

### Negative
- A table, a dispatcher and retry rules to build and monitor.

### Neutral
- Email is eventually sent, not sent inside the request.

## Alternatives Considered
**Spring Modulith event publication registry:** not chosen. ADR-005 already rejected Modulith, so it
would be a new dependency for one feature.

**`@TransactionalEventListener(AFTER_COMMIT)` alone:** a failure after commit is lost with no retry.

## References
- `docs/revamp-audit-report.md`, finding OTH-04 and section 6.6
- `docs/revamp-implementation-plan.md`, Phase 2 work packages 2c to 2e
