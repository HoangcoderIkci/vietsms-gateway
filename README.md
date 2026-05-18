# VietSMS Gateway

A telecom-style SMS / OTP gateway API simulator, built with Spring Boot 3 and Java 21.

The shape of the service mirrors what a Vietnamese carrier (Viettel, Vinaphone, Mobifone) actually sells to banks, e-wallets, and e-commerce platforms: clients hold an API key, hit a REST endpoint to enqueue an SMS or OTP, and the system handles delivery asynchronously with retries, status reporting, and audit trails.

This repository is a multi-day build. Each day is roughly a two-hour focused session that adds one well-scoped slice of the system.

## Why this project

Most portfolio projects are todo lists, blog clones, or e-commerce demos. They prove a candidate can wire CRUD; they do not prove the candidate understands a domain or production engineering.

This service is intentionally narrow and intentionally Viettel-flavored. The architecture covers patterns that come up in real telecom systems:

- API key authentication with hashed storage
- Sliding-window rate limiting per key and per phone number
- Asynchronous delivery worker with a state machine, retries, and graceful shutdown
- Idempotency on accept (`client_message_id`)
- OTP issuance with TTL, attempt locking, and per-phone cooldown
- Audit logging that never stores full phone numbers
- Vietnamese phone number validation and normalization
- OpenAPI / Swagger documentation

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Maven |
| Persistence | Spring Data JPA + Flyway, H2 (dev), PostgreSQL (planned) |
| Security | Spring Security with a custom `OncePerRequestFilter` |
| Validation | Jakarta Bean Validation |
| Docs | springdoc-openapi |
| Tests | JUnit 5, MockMvc, AssertJ |

## Quick start

Requirements: JDK 21, Maven 3.9+.

```bash
mvn spring-boot:run
```

On first start the application creates an H2 database under `./data/`, applies Flyway migrations, and prints a demo API key to the console:

```
=========================================================
  VietSMS demo API key (save this — shown only once)
  vsms_AbCdEf...
=========================================================
```

Save this key. It is the only credential issued automatically.

Endpoints once the app is running:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- H2 console: <http://localhost:8080/h2-console>
- Health: <http://localhost:8080/actuator/health>
- Prometheus metrics: <http://localhost:8080/actuator/prometheus> (filter `vietsms_*` counters for domain events)
- Error code catalog: [`docs/error-codes.md`](docs/error-codes.md)

### Smoke test

```bash
# Wrong key → 401
curl -i http://localhost:8080/v1/ping

# Correct key → 200 with key metadata
curl -i -H "x-api-key: vsms_<the-key-you-saved>" http://localhost:8080/v1/ping
```

## Roadmap

The project is built in seven daily slices. Each day's deliverable is small but complete.

| Day | Theme | Status |
|---|---|---|
| 1 | Bootstrap: project skeleton, DB schema, API key auth, demo seeder, Swagger | **Done** |
| 2 | SMS endpoints (send, get, list) and the delivery worker with retries | **Done** |
| 3 | OTP endpoints (send, verify) with attempt locking and cooldown | **Done** |
| 4 | Rate limiting (sliding window) and audit logging | **Done** |
| 5 | Micrometer / Prometheus metrics, Swagger polish, error catalog | **Done** |
| 6 | Test coverage and documentation polish | Planned |
| 7 | Dockerfile, docker-compose, GitHub Actions CI | Planned |

Design document lives at `docs/superpowers/specs/2026-05-18-vietsms-gateway-design.md`.

## Project layout

```
src/main/java/com/hoangcoder/vietsms/
  VietSmsApplication.java
  config/        # Security, OpenAPI
  security/      # ApiKey entity, filter, service
  sms/           # SMS controller, service, repository, status enum
  otp/           # OTP controller, service, repository
  worker/        # DeliveryWorker (scheduled)
  ratelimit/     # Sliding-window limiter and filter
  audit/         # AuditLog entity and writer
  validation/    # Vietnamese phone validator
  common/        # ApiError, GlobalExceptionHandler, PhoneNormalizer
  seed/          # Demo data seeder
src/main/resources/
  application.yml
  db/migration/  # Flyway migrations V1–V4
```

## License

MIT. See `LICENSE` (to be added).
