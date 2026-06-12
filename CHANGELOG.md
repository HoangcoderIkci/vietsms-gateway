# Changelog

All notable changes to VietSMS Gateway are tracked here. The project was built across a single multi-hour evening session on **2026-05-18**, scoped as seven daily-sized slices.

## 0.2.0 — 2026-06-12 (WOW Roadmap Phase 1)

### Webhook callbacks
- Register/list/delete webhook endpoints per API key (`/v1/webhooks`), max 5, SSRF-safe URL validation (scheme + resolved-IP checks).
- Events: `sms.sent`, `sms.delivered`, `sms.failed` (+ `webhook.test` via `POST /v1/webhooks/{id}/test`); `otp.locked` planned.
- **Transactional outbox**: state transitions in `DeliveryWorker` enqueue `webhook_delivery` rows in the same transaction — no HTTP inside DB transactions.
- `WebhookWorker`: HMAC-SHA256 signed POSTs (`X-VietSMS-Signature: sha256=<hex>`), 5s timeout, retry backoff 1m → 5m → 30m, dead-letter after 4 attempts (inspectable via `GET /v1/webhooks/{id}/deliveries?status=DEAD`).
- Payload phone numbers masked; per-endpoint secret shown once at registration.
- 4 new Micrometer metrics (`vietsms.webhook.*`), `docs/webhooks.md` with verification snippets (Java + Python), 41 new tests (95 total).
- Verified end-to-end against a live external receiver: both `webhook.test` and `sms.delivered` delivered with independently-validated signatures (one transient failure auto-recovered by retry).

## 0.1.0 — 2026-05-18

### Day 7 — Containerization & CI
- Multi-stage Dockerfile (Temurin 21 JDK builder → JRE runtime), non-root user, healthcheck.
- `docker-compose.yml` with named volume and restart policy.
- GitHub Actions workflow: `build-and-test` job and gated `docker-image` build.

### Day 6 — Coverage & docs polish
- JaCoCo plugin producing HTML + CSV coverage on `mvn verify`.
- Mermaid architecture diagram and SMS-lifecycle sequence diagram in README.
- Testing section listing per-package coverage.

### Day 5 — Observability & API polish
- Nine Micrometer counters (`vietsms_sms_*`, `vietsms_otp_*`, `vietsms_ratelimit_tripped`).
- `/actuator/prometheus` endpoint exposed.
- `@Schema` annotations with examples on request DTOs.
- `docs/error-codes.md` catalog.

### Day 4 — Cross-cutting concerns
- `SlidingWindowLimiter` in-memory implementation.
- `RateLimitFilter` on write endpoints with `Retry-After` and `X-RateLimit-*` headers.
- `AuditFilter` with `X-Request-Id` correlation; `AuditService` writes rows asynchronously.
- Filter order in `SecurityConfig`: ApiKey → Audit → RateLimit.

### Day 3 — OTP
- `OtpService.send` (BCrypt-hashed code, configurable length/TTL, 30s cooldown).
- `OtpService.verify` (attempt counting, lock on max, reason codes for every failure mode).
- 429 + `Retry-After` for cooldown via `TooEarlyException`.

### Day 2 — SMS & delivery worker
- `SmsService` with idempotency via `(api_key_id, client_message_id)` unique key.
- `SmsController` (send/get/list), pagination + status filter.
- `DeliveryWorker` two-phase scheduled task with exponential-backoff retries.
- `@VietnamesePhone` constraint annotation + 19-case `PhoneNormalizerTest`.

### Day 1 — Bootstrap
- Spring Boot 3.3.5 + Java 21 scaffolding.
- Flyway V1–V4 migrations: `api_keys`, `sms_messages`, `otp_codes`, `audit_log`.
- `ApiKeyAuthFilter` (BCrypt verification with 8-char prefix lookup).
- `DemoDataSeeder` prints a single demo key on first run.
- Swagger UI, Actuator, structured `ApiError` JSON.
