# Error code catalog

Every error response follows this JSON shape:

```json
{
  "timestamp": "2026-05-18T22:00:00Z",
  "status": 401,
  "error": "INVALID_API_KEY",
  "message": "API key is invalid or revoked",
  "path": "/v1/sms/send",
  "fieldErrors": {                          // only on VALIDATION_ERROR
    "to": "must be a valid Vietnamese phone number"
  }
}
```

## HTTP status → error catalog

### 400 Bad Request

| `error` | When |
|---|---|
| `VALIDATION_ERROR` | Request body fails Bean Validation. `fieldErrors` lists per-field problems. |

### 401 Unauthorized

| `error` | When |
|---|---|
| `MISSING_API_KEY` | Caller did not send the `x-api-key` header. |
| `INVALID_API_KEY` | The key is unknown, revoked, or otherwise inactive. |

### 404 Not Found

| `error` | When |
|---|---|
| `NOT_FOUND` | The requested resource (e.g., an SMS message id) does not exist or is not owned by the caller's API key. |

### 429 Too Many Requests

| `error` | When | Headers |
|---|---|---|
| `RATE_LIMIT_EXCEEDED` | Caller exceeded the per-endpoint sliding window. | `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` |
| `COOLDOWN_ACTIVE` | Per-phone cooldown still active for OTP send. | `Retry-After` |

### 500 Internal Server Error

| `error` | When |
|---|---|
| `INTERNAL_ERROR` | Unhandled exception. Reported but not exposed in detail. |

## OTP verify response codes

Verify never returns a 4xx for wrong codes — it returns 200 with `verified=false` and a `reason`:

| `reason` | Meaning |
|---|---|
| `NO_OTP_ISSUED` | This phone has never received an OTP from this server. |
| `NO_ACTIVE_OTP` | The latest OTP exists but is not currently active (already verified, expired, etc.). |
| `INVALID_CODE` | The submitted code does not match. `attempts_left` decreases. |
| `LOCKED` | The OTP is permanently locked after exceeding max attempts (default 3). Issuing a new code resets the lock. |
| `EXPIRED` | The TTL elapsed before verification. |
| `ALREADY_VERIFIED` | The latest OTP for this phone was already used successfully. |

## Per-request headers

Every `/v1/**` response carries:

- `X-Request-Id`: server-assigned UUID, also recorded in `audit_log`. Send this back when reporting an issue.
