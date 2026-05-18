# VietSMS Gateway API — Design Spec

**Date:** 2026-05-18
**Author:** Hoang (with Claude)
**Status:** Approved, Day 1 in progress

## 1. Vision

Build a production-quality REST API that simulates a telecom SMS/OTP gateway — the kind of service Viettel sells to banks, e-wallets, and e-commerce platforms. The project must be portfolio-grade: clean architecture, real concurrency, real security primitives, real tests.

The audience is recruiters and hiring engineers at Viettel (Solutions, Digital, VTS, VCS). The signal we want to send: "This candidate understands telecom domain, writes idiomatic Spring Boot, and ships production-realistic code."

## 2. Non-goals

- Real SMS delivery (we simulate)
- High-availability or horizontal scaling (single-node demo)
- Multi-tenant billing
- Admin UI dashboard (CLI / Swagger is enough for v1)

## 3. Core capabilities

### 3.1 Authentication
- Clients authenticate via `x-api-key` header
- Keys are stored hashed (BCrypt) — only the prefix is queryable
- Each key has: name, owner, rate limit override, active flag, created/revoked timestamps
- A seed script provisions one demo key at startup

### 3.2 SMS endpoints
- `POST /v1/sms/send` — accept `{to, content, client_message_id?}`, return message id + status
- `GET /v1/sms/{id}` — return current status and timestamps
- `GET /v1/sms?page=&size=&status=` — paginated history for the calling key
- Idempotency: if `client_message_id` is reused within 24h, return the original message

### 3.3 OTP endpoints
- `POST /v1/otp/send` — accept `{phone, length?, ttl_seconds?}`, return otp id + masked code
- `POST /v1/otp/verify` — accept `{phone, code}`, return verified true/false + reason
- Max 3 verify attempts per OTP. After 3 fails the code is locked.
- Cooldown: max 1 OTP per phone per 30 seconds

### 3.4 Rate limiting
- Sliding-window algorithm per `(api_key, endpoint)`
- Defaults: 10 SMS/min/key, 5 OTP/min/key, 1 OTP/30s/phone
- Returns `429 Too Many Requests` with `Retry-After` header

### 3.5 Background delivery worker
- Scheduled task runs every 1 second
- Picks up to N `QUEUED` messages, transitions them through `SENT` → `DELIVERED` (95%) or `FAILED` (5%)
- Simulated delivery delay: random 1–3 seconds
- On `FAILED`, retry up to 3 times with exponential backoff (2s, 8s, 32s)
- Graceful shutdown: drains in-flight messages before exit

### 3.6 Validation
- Vietnamese phone format: accepts `+84xxxxxxxxx` or `0xxxxxxxxx`, normalizes to `+84` form
- Valid prefixes: `03, 05, 07, 08, 09` (Vinaphone, Viettel, Mobifone, Vietnamobile, Gmobile)
- Message length ≤ 160 chars for single SMS (multi-part not supported in v1)

### 3.7 Observability
- Structured JSON logs (Logback) with request id correlation
- `/actuator/health` and `/actuator/metrics` exposed via Spring Boot Actuator
- Audit log table: who called what when, never logs full phone number (masked)

### 3.8 API docs
- Swagger UI at `/swagger-ui.html` (springdoc-openapi)
- All endpoints annotated with `@Operation`, `@ApiResponse`, examples

## 4. Technical stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.x |
| Build | Maven |
| DB | H2 (file-mode) for dev; PostgreSQL-ready via profile |
| Migrations | Flyway |
| ORM | Spring Data JPA + Hibernate |
| Validation | Jakarta Bean Validation + custom validators |
| Security | Spring Security (API key filter only, no full OAuth) |
| Docs | springdoc-openapi 2.x |
| Lombok | Yes (DTOs, entities) |
| Tests | JUnit 5, MockMvc, AssertJ; later Testcontainers for Postgres |
| Logging | Logback with JSON encoder (logstash-logback-encoder) |
| Containerization | Dockerfile + docker-compose (Day 5+) |

## 5. Architecture

```
                       ┌────────────────────────────────────────┐
                       │            HTTP Layer                  │
                       │   Controllers + DTOs + GlobalExHandler │
                       └────────────────────────────────────────┘
                                       ↓
              ┌────────────────────────────────────────────────────┐
              │           Security & Cross-cutting Filters         │
              │  ApiKeyAuthFilter → RateLimitFilter → AuditFilter  │
              └────────────────────────────────────────────────────┘
                                       ↓
                       ┌────────────────────────────────────────┐
                       │            Service Layer               │
                       │  SmsService, OtpService, ApiKeyService │
                       │  PhoneNormalizer, IdempotencyService   │
                       └────────────────────────────────────────┘
                                       ↓
                       ┌────────────────────────────────────────┐
                       │         Repository / JPA Layer         │
                       │  Repos + Entities + Flyway migrations  │
                       └────────────────────────────────────────┘
                                       ↓
                                  ┌──────────┐
                                  │    H2    │
                                  │ (Postgres│
                                  │  later)  │
                                  └──────────┘

                       ┌────────────────────────────────────────┐
                       │       DeliveryWorker (@Scheduled)      │
                       │  picks QUEUED → simulates → updates    │
                       │  state machine with retries            │
                       └────────────────────────────────────────┘
```

## 6. Data model

```sql
api_keys
  id              BIGINT PK
  key_prefix      VARCHAR(8)  UNIQUE  -- first 8 chars, for lookup
  key_hash        VARCHAR(60)         -- BCrypt of full key
  name            VARCHAR(64)
  owner_email     VARCHAR(128)
  rate_limit_rpm  INT DEFAULT 10
  active          BOOLEAN
  created_at      TIMESTAMP
  revoked_at      TIMESTAMP NULL

sms_messages
  id                  BIGINT PK
  api_key_id          BIGINT FK
  client_message_id   VARCHAR(64)  -- idempotency
  to_phone            VARCHAR(16)
  content             VARCHAR(160)
  status              VARCHAR(16)  -- QUEUED|SENT|DELIVERED|FAILED
  retry_count         INT
  next_retry_at       TIMESTAMP NULL
  error_code          VARCHAR(32) NULL
  created_at          TIMESTAMP
  sent_at             TIMESTAMP NULL
  delivered_at        TIMESTAMP NULL
  UNIQUE (api_key_id, client_message_id)

otp_codes
  id              BIGINT PK
  api_key_id      BIGINT FK
  phone           VARCHAR(16)
  code_hash       VARCHAR(60)
  attempts        INT DEFAULT 0
  max_attempts    INT DEFAULT 3
  expires_at      TIMESTAMP
  verified_at     TIMESTAMP NULL
  locked          BOOLEAN DEFAULT FALSE
  created_at      TIMESTAMP

audit_log
  id           BIGINT PK
  api_key_id   BIGINT FK NULL
  endpoint     VARCHAR(128)
  method       VARCHAR(8)
  status_code  INT
  phone_masked VARCHAR(16) NULL
  request_id   VARCHAR(36)
  created_at   TIMESTAMP
```

## 7. State machines

### SMS message
```
   QUEUED ──worker pick──→ SENT ──99% chance──→ DELIVERED
                            │
                            └──1% chance──→ FAILED (final)

   QUEUED ──worker pick──→ FAILED ──retry?──→ QUEUED (retry_count++)
                                  └──max──→ FAILED (final)
```

### OTP code
```
   ACTIVE ──verify OK──→ VERIFIED
   ACTIVE ──verify fail x3──→ LOCKED
   ACTIVE ──TTL expires──→ EXPIRED (implicit, derived from expires_at)
```

## 8. Project structure

```
vietsms-gateway/
├── pom.xml
├── README.md
├── .gitignore
├── docs/
│   ├── superpowers/specs/2026-05-18-vietsms-gateway-design.md
│   └── curl-examples.md
├── src/
│   ├── main/
│   │   ├── java/com/hoangcoder/vietsms/
│   │   │   ├── VietSmsApplication.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   └── SchedulingConfig.java
│   │   │   ├── security/
│   │   │   │   ├── ApiKeyAuthFilter.java
│   │   │   │   ├── ApiKeyPrincipal.java
│   │   │   │   └── ApiKeyService.java
│   │   │   ├── ratelimit/
│   │   │   │   ├── RateLimitFilter.java
│   │   │   │   └── SlidingWindowLimiter.java
│   │   │   ├── sms/
│   │   │   │   ├── SmsController.java
│   │   │   │   ├── SmsService.java
│   │   │   │   ├── SmsRepository.java
│   │   │   │   ├── SmsMessage.java
│   │   │   │   ├── SmsStatus.java
│   │   │   │   └── dto/
│   │   │   ├── otp/
│   │   │   │   ├── OtpController.java
│   │   │   │   ├── OtpService.java
│   │   │   │   ├── OtpRepository.java
│   │   │   │   ├── OtpCode.java
│   │   │   │   └── dto/
│   │   │   ├── worker/
│   │   │   │   └── DeliveryWorker.java
│   │   │   ├── audit/
│   │   │   │   ├── AuditLog.java
│   │   │   │   ├── AuditRepository.java
│   │   │   │   └── AuditService.java
│   │   │   ├── validation/
│   │   │   │   ├── VietnamesePhone.java
│   │   │   │   └── VietnamesePhoneValidator.java
│   │   │   ├── common/
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── ApiError.java
│   │   │   │   └── PhoneNormalizer.java
│   │   │   └── seed/
│   │   │       └── DemoDataSeeder.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── db/migration/
│   │       │   ├── V1__create_api_keys.sql
│   │       │   ├── V2__create_sms_messages.sql
│   │       │   ├── V3__create_otp_codes.sql
│   │       │   └── V4__create_audit_log.sql
│   │       └── logback-spring.xml
│   └── test/
│       └── java/com/hoangcoder/vietsms/
│           ├── sms/SmsControllerTest.java
│           ├── otp/OtpServiceTest.java
│           ├── security/ApiKeyAuthFilterTest.java
│           ├── ratelimit/SlidingWindowLimiterTest.java
│           ├── validation/PhoneValidatorTest.java
│           └── worker/DeliveryWorkerTest.java
```

## 9. API contract (selected)

### POST /v1/sms/send
**Request**
```json
{
  "to": "0987654321",
  "content": "Xin chao tu VietSMS",
  "client_message_id": "order-1234-otp"
}
```
**Response 202**
```json
{
  "id": 42,
  "to": "+84987654321",
  "status": "QUEUED",
  "created_at": "2026-05-18T14:30:00Z"
}
```
**Errors:** 400 (validation), 401 (auth), 409 (idempotency conflict), 429 (rate limit)

### POST /v1/otp/send
**Request** `{"phone": "0987654321", "length": 6, "ttl_seconds": 300}`
**Response 202** `{"otp_id": 17, "phone": "+84987654321", "expires_at": "..."}`

### POST /v1/otp/verify
**Request** `{"phone": "0987654321", "code": "123456"}`
**Response 200** `{"verified": true}` or `{"verified": false, "reason": "INVALID_CODE", "attempts_left": 2}`

## 10. Day-by-day milestone plan

| Day | Focus | Deliverable |
|---|---|---|
| **1** | Env + scaffold + DB + auth | Project compiles, H2 schema migrated, API key filter works, demo key seeded, README skeleton, push GitHub |
| 2 | SMS endpoints + worker | Send/list/get SMS, delivery worker simulates async, state machine + retries, MockMvc tests |
| 3 | OTP endpoints | Send/verify OTP, attempt locking, cooldown, tests |
| 4 | Rate limiting + audit | Sliding window limiter, audit log table, 429 responses with Retry-After |
| 5 | Validation + Swagger + observability | Phone validator, idempotency, full Swagger annotations, Actuator, JSON logs |
| 6 | Testing + docs polish | Coverage > 70%, curl examples doc, README with architecture diagram, screenshots |
| 7 | Docker + CI | Dockerfile, docker-compose, GitHub Actions CI workflow |

Each day is roughly 2 hours of focused work.

## 11. Day 1 detailed scope (today)

1. Verify JDK 21 + Maven installation
2. Generate Spring Boot project (Maven, deps: Web, JPA, Validation, Security, Flyway, H2, Lombok, Actuator, springdoc-openapi)
3. Set up package structure, application.yml with `dev` profile
4. Flyway migrations V1–V4 (all four tables)
5. JPA entities for `ApiKey`, `SmsMessage`, `OtpCode`, `AuditLog`
6. Repositories (Spring Data interfaces only)
7. `ApiKeyService` with key generation and BCrypt verification
8. `ApiKeyAuthFilter` integrated into Spring Security chain — protects `/v1/**`
9. `DemoDataSeeder` (`CommandLineRunner`) that creates one demo key, logs it once at startup
10. README skeleton with sections (intro, why this project, stack, quick start, roadmap)
11. `.gitignore` for Java/Maven/IDE
12. `git init` and initial commit

**Day 1 success criteria:**
- `mvn spring-boot:run` starts the app cleanly
- Demo API key printed to console on first run
- `curl -H 'x-api-key: <wrong>' http://localhost:8080/v1/sms` → 401
- `curl -H 'x-api-key: <demo>' http://localhost:8080/v1/sms` → 200 with empty list (or 404 if endpoint not yet implemented, which is acceptable on Day 1 — the auth chain is the key deliverable)
- H2 console at `/h2-console` shows all 4 tables
- Code pushed to a public GitHub repo

## 12. Risks & mitigations

| Risk | Mitigation |
|---|---|
| JDK/Maven install fails | Fallback to manual download from Adoptium |
| First Maven build slow (dep download) | Accept it on Day 1; subsequent days fast |
| Spring Security learning curve | Use minimal `OncePerRequestFilter` + `SecurityFilterChain` config, no OAuth complexity |
| Scope creep | Strict day-by-day milestones; defer anything not in today's list to roadmap |

## 13. Out of scope (for the whole project)

- Real SMS provider integration (Twilio, etc.)
- Multi-tenant / multi-org account hierarchy
- Billing, pricing, invoices
- Admin web UI
- Internationalization beyond Vietnamese phone format
- Push notifications, email, voice
- WebSocket or SSE delivery report streaming (could be a future enhancement)

## 14. Future enhancements (post-Day 7)

- Swap H2 → PostgreSQL via profile, deploy with docker-compose
- Replace in-memory rate limiter with Redis (Bucket4j + Redis)
- Replace `@Scheduled` worker with Kafka consumer for the delivery queue
- Prometheus + Grafana dashboard
- Add WebSocket endpoint pushing live delivery events
- Multi-language message templates with placeholder substitution
- Cost ledger per API key

---

**This spec is the source of truth for the project. Updates require a new entry under each section's revision note.**
