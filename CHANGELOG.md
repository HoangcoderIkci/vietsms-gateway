# Changelog

All notable changes to VietSMS Gateway are tracked here. The project was built across a single multi-hour evening session on **2026-05-18**, scoped as seven daily-sized slices.

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
