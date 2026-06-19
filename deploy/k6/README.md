# VietSMS Gateway — k6 Load Tests

## What it tests

`send-load.js` runs a **mixed-workload** load test against the gateway with a realistic read-heavy distribution:

| Weight | Endpoint | Notes |
|--------|----------|-------|
| 50% | `GET /v1/sms?page=0&size=20` | Paginated SMS history — main capacity measure |
| 20% | `POST /v1/sms/send` | Write path — subject to rate limiting |
| 20% | `GET /v1/ping` | Health check — baseline latency signal |
| 10% | `POST /v1/otp/send` | OTP write path — subject to stricter rate limiting |

Full-load ramp profile: 0 → 50 → 200 VUs over 5 minutes, then ramp down.

## Prerequisites

- [k6](https://k6.io/docs/getting-started/installation/) installed (or Docker).
- Gateway running and reachable.
- An API key provisioned with a **high rate-limit** (e.g. 10 000/min) to avoid saturating the limiter before the server itself.

## How to run

### Host k6

```bash
# Full load test
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e API_KEY=<your-key> \
  deploy/k6/send-load.js

# Smoke (1 VU × 10s — validates connectivity & config)
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e API_KEY=<your-key> \
  -e SMOKE=1 \
  deploy/k6/send-load.js
```

### Docker k6 (inside Compose network)

```bash
# Full load test
docker run --rm -i --network test_default \
  -e BASE_URL=http://vietsms-gateway:8080 \
  -e API_KEY=<your-key> \
  grafana/k6 run - < deploy/k6/send-load.js

# Smoke
docker run --rm -i --network test_default \
  -e BASE_URL=http://vietsms-gateway:8080 \
  -e API_KEY=<your-key> \
  -e SMOKE=1 \
  grafana/k6 run - < deploy/k6/send-load.js
```

## Reading the summary

After the run, k6 prints a summary table. Key metrics:

| Metric | Target | Meaning |
|--------|--------|---------|
| `http_req_failed` | `< 1%` | Fraction of requests that returned an unexpected status (5xx, timeout). **429 is excluded** — see below. |
| `http_req_duration p(95)` | `< 200 ms` | 95th-percentile end-to-end latency. |
| `rate_limited` (custom) | informational | Fraction of requests that received HTTP 429 from the gateway rate limiter. |

A passing run looks like:

```
✓ http_req_failed.............: 0.00%
✓ http_req_duration...........: p(95)=87ms
  rate_limited................: 3.21%   ← expected, not a failure
```

## About HTTP 429 (rate limiting)

The gateway enforces sliding-window rate limits per API key:

- `/v1/sms/send` — default **10 req/min**
- `/v1/otp/send` — default **5 req/min**

Under load these endpoints will return `429 Too Many Requests`. This is **by design** and is **not counted as a failure** in the load test. The script registers 429 in the custom `rate_limited` metric so you can observe how often the limiter fires at different VU counts.

For a meaningful throughput test, provision the test key with a limit well above the expected peak RPS before running the full load stage.
