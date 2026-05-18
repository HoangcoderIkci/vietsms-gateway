# Architecture and design rationale

This document explains *why* the service is shaped the way it is. The README covers what the system does; this document covers the engineering choices behind it and the trade-offs they imply.

## Why a telecom-shaped service at all?

Most portfolio projects are todo lists, blog clones, or e-commerce demos. They demonstrate CRUD plumbing but do not show that the author has thought about a real domain. The Vietnamese telecom space has shape: B2B SMS/OTP gateways are a real product (Viettel sells one), they have non-trivial concurrency requirements (delivery is asynchronous), and they sit in a regulated security context (rate limits, audit trails, idempotency).

Picking that shape on purpose means every architectural decision can be defended with a domain reason rather than a generic one.

## Stack choice: Spring Boot 3 + Java 21

Two real alternatives were considered: **Node.js + TypeScript** (faster to scaffold, smaller deployment surface) and **Go + chi** (better concurrency story, single static binary). Both would have worked.

Spring Boot was chosen because it matches the stack used inside Viettel Solutions, Viettel Digital, and VTS. The signal value of speaking the same language as the target team outweighs the slight ergonomic edge of a smaller framework. Java 21 specifically was chosen to use modern syntax (records, pattern matching, virtual threads if needed later) — anything older signals "haven't kept up."

## The filter chain

`SecurityConfig` registers three filters in a deliberate order:

```
HTTP request
  ↓
ApiKeyAuthFilter    — establishes the authenticated ApiKey principal
  ↓
AuditFilter         — assigns X-Request-Id, records the request after completion
  ↓
RateLimitFilter     — short-circuits with 429 when limits are exhausted
  ↓
Controller
```

The order matters:

- Auth runs first because nothing downstream should see anonymous traffic.
- Audit runs second so it can record the api_key_id from the principal, and so that its `finally` block sees the eventual response status — even if rate limit later rejects the request.
- Rate limit runs last because it short-circuits with 429; placing it before audit would mean rate-limited requests never appear in the audit table.

A common mistake here is to put rate limiting first to "save work." That is correct for performance but wrong for security: failed rate-limit attempts are exactly the events you want auditable.

## API key storage

Keys are issued as `vsms_<24 random bytes base64url>` and stored as a BCrypt hash plus an 8-character prefix in a separate column. The prefix is the lookup key (cheap, indexed); BCrypt verifies the full key. This means:

- A compromised database does not leak callable keys (BCrypt is irreversible).
- Lookup remains O(log n) on a single indexed column.
- We avoid the constant-time-comparison pitfall of comparing entire keys.

We do not implement key rotation in this version. That would be the natural next step: a `parent_id` column on `api_keys`, and an admin endpoint to issue a successor.

## Idempotency

`POST /v1/sms/send` accepts an optional `clientMessageId`. If supplied, we look up `(api_key_id, client_message_id)` before inserting; on a match we return the existing record. A unique index on the pair backs this contract at the database level — even under a lost race, the duplicate insert fails and we re-read.

The reason this matters: SMS APIs are called from retry loops in client code (a checkout flow, an order confirmation). Without idempotency a network blip causes double-charge-on-SMS situations. With it the caller can retry safely.

## Delivery worker state machine

```
   QUEUED ─pickup──→ SENT ─delay+roll──→ DELIVERED
                                 │
                                 ├──→ retry budget remaining ──→ QUEUED (next_retry_at = now + backoff)
                                 └──→ exhausted ──────────────→ FAILED
```

The worker is a `@Scheduled` task running every 1 second. Each tick has two phases:

1. **Pickup:** find `QUEUED` rows where `next_retry_at IS NULL OR next_retry_at <= now`, set them to `SENT`, stamp `sent_at = now`.
2. **Finalize:** find `SENT` rows where `sent_at <= now - min_delay`, roll a random outcome, and either mark `DELIVERED` or schedule a retry.

The two-phase design is what makes the simulated delivery feel realistic. A naive single-phase worker would transition QUEUED → DELIVERED in one tick, which does not match how real carriers work. Splitting the transition lets us simulate carrier latency without storing any extra column.

Retries use exponential backoff (2s, 8s, 32s) capped at three attempts. The `retry_count` column is the authority — once it equals `max_retries`, the state machine refuses to retry again.

`tick()` is annotated `@Transactional` so the entire pickup + finalize cycle is one transaction. This costs a small amount of write throughput but eliminates a class of race conditions where a row could be picked twice if the JVM paused mid-tick.

## OTP design

OTP codes are 6 digits by default, BCrypt-hashed (so the database does not leak them), and live for 300 seconds. The verify endpoint accepts up to three wrong tries before the code is permanently locked. A 30-second cooldown prevents an attacker from spamming the send endpoint to fish for the next code.

Notable choices:

- `verify` always returns 200 with a `verified` boolean. We do not signal "wrong code" via HTTP status, because that would let an attacker distinguish "user has no OTP" from "user has an OTP, wrong code" at the network layer. Both responses look identical to a passive observer.
- The `devCode` field on `SendOtpResponse` is a demo affordance. In a real telecom system the code is only sent over the carrier channel. The field is documented as demo-only in Swagger.

## Rate limiter

The sliding-window limiter is a `ConcurrentHashMap<String, Deque<Instant>>` with per-bucket `synchronized`. Each request appends a timestamp; aged-out timestamps are pruned on each call.

Why a deque and not a token bucket? Sliding-window with timestamps is **fairer** under bursty traffic: a client cannot front-load 10 requests in the first second of a minute. Token bucket gives more "burst tolerance" but harder-to-reason eviction.

Per-bucket synchronization is the right granularity here. A single global lock would serialize all rate-limit checks. Lock striping was considered and rejected as over-engineering for the demo traffic profile; the move to Redis is the right answer when the service needs to scale beyond a single node.

The limiter is **not** persistent. A restart resets all windows. For a production service this matters; for a demo it does not. The natural upgrade is Bucket4j + Redis, which is mentioned in the README backlog.

## Audit log

Every `/v1/**` request gets a UUID request id (echoed in `X-Request-Id` and recorded in `MDC` for log correlation). The `AuditFilter` records the request to the `audit_log` table in a `finally` block after the chain completes, capturing the final response status — even when the response was an error.

Writes happen via `AuditService.@Async`, so they never block the response thread. If the write fails (DB down, etc.) the request still succeeds; audit failures are logged at WARN.

Audit captures: `api_key_id`, `endpoint`, `method`, `status_code`, `request_id`, `created_at`. It does **not** capture full phone numbers (privacy / regulatory hygiene). If a future requirement needs phone-level audit, a separate masked column was provisioned in the schema.

## Observability

Nine `Counter` instances under the `vietsms.*` namespace track every domain event:

| Counter | Increments when |
|---|---|
| `vietsms.sms.enqueued` | `SmsService.send` accepts |
| `vietsms.sms.delivered` | worker finalizes to `DELIVERED` |
| `vietsms.sms.failed_terminal` | worker exhausts retries |
| `vietsms.sms.retried` | worker schedules a retry |
| `vietsms.otp.issued` | `OtpService.send` creates a code |
| `vietsms.otp.verified` | `OtpService.verify` accepts |
| `vietsms.otp.invalid_attempt` | wrong code submitted |
| `vietsms.otp.locked` | code locked after max attempts |
| `vietsms.ratelimit.tripped` | a request is rate-limited |

All carry an `application=vietsms-gateway` tag so they can be aggregated across instances in Grafana. The `/actuator/prometheus` endpoint serves them in the standard Prometheus exposition format, no extra scraper config needed.

## Testing strategy

Three concentric test layers:

1. **Unit tests** (PhoneNormalizer, SlidingWindowLimiter, VietnamesePhoneValidator) run in milliseconds, no Spring context.
2. **Service-level integration tests** (`@SpringBootTest` + `@Transactional` for rollback) cover service idempotency, OTP lifecycle, and cooldown enforcement.
3. **HTTP-level integration tests** (`@AutoConfigureMockMvc`) cover the auth filter, validation, rate limiting, audit writes, and the SMS controller's pagination/filter contracts.

Tests run against an in-memory H2 with `${random.uuid}` in the JDBC URL, so each test context gets its own database. This was specifically added after observing cross-context interference where one test's `@Scheduled` worker was delivering another test's queued messages.

JaCoCo is bound to the `verify` phase and produces an HTML report. Per-package coverage targets are explicit: 80%+ for services, 100% for DTOs and validation.

## Containerization

The Dockerfile is multi-stage: a builder image with JDK 21 + Maven prepares the fat jar, and a runtime image with only the JRE runs it. The build stage uses BuildKit cache mounts (`--mount=type=cache,target=/root/.m2`) so subsequent builds reuse the Maven dependency cache.

The runtime container runs as a non-root user (`vietsms`), has a healthcheck against `/actuator/health`, and pre-sets JVM flags (`-XX:MaxRAMPercentage=75 -XX:+UseG1GC`) for container-aware memory sizing.

`docker-compose.yml` mounts a named volume at `/app/data` so the H2 database survives container restarts. The PostgreSQL profile is on the backlog but trivially addable later.

## CI

The GitHub Actions workflow has two jobs:

- `build-and-test` runs `mvn verify` on every push and PR. Maven cache via `actions/setup-java@v4`. Failure uploads Surefire reports as an artifact.
- `docker-image` runs only on pushes to main, uses Buildx with GitHub Actions cache for the Docker layer cache, and builds the image without pushing.

The pattern of building without pushing is deliberate: it proves the Dockerfile works, but the actual publish step is a separate manual decision (so an accidental merge cannot ship a half-baked image).

## What I would do next

Listed in the README roadmap, in priority order:

1. PostgreSQL profile with `GENERATED BY DEFAULT AS IDENTITY` migrations.
2. Redis-backed Bucket4j rate limiter for horizontal scale.
3. Kafka consumer replacing the in-process `@Scheduled` worker.
4. Webhook callbacks on delivery state transitions.
5. Public deployment with TLS so the CI badge and live demo URL can sit next to each other on the README.
