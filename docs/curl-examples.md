# cURL examples

Replace `$KEY` with the demo API key printed at server startup.

```bash
KEY="vsms_..."   # the key printed once on first run
```

## Day 1 (auth + ping)

Wrong / missing key → 401:

```bash
curl -i http://localhost:8080/v1/ping
curl -i -H "x-api-key: wrong" http://localhost:8080/v1/ping
```

Valid key → 200 with key metadata:

```bash
curl -s -H "x-api-key: $KEY" http://localhost:8080/v1/ping | jq
```

## Day 2 (SMS — done)

Send an SMS. Phone numbers in `0xxxxxxxxx` and `+84xxxxxxxxx` form are both accepted and normalized to `+84` form.

```bash
curl -s -X POST http://localhost:8080/v1/sms/send \
  -H "x-api-key: $KEY" \
  -H "content-type: application/json" \
  -d '{"to":"0987654321","content":"Xin chao tu VietSMS","clientMessageId":"abc-1"}' | jq
```

Retrieve a single message. Right after send the status is `QUEUED`; after 1–3s the delivery worker transitions it to `DELIVERED` (95%) or `FAILED` (5%, with retry).

```bash
curl -s -H "x-api-key: $KEY" http://localhost:8080/v1/sms/1 | jq
```

List your messages (newest first). Filter by `status` if you want.

```bash
curl -s -H "x-api-key: $KEY" "http://localhost:8080/v1/sms?page=0&size=20" | jq
curl -s -H "x-api-key: $KEY" "http://localhost:8080/v1/sms?status=DELIVERED" | jq
```

Idempotency: resending with the same `clientMessageId` returns the original message rather than creating a duplicate.

```bash
# Both calls return the SAME message id and status
curl -s -X POST http://localhost:8080/v1/sms/send -H "x-api-key: $KEY" \
  -H "content-type: application/json" \
  -d '{"to":"0987654321","content":"order otp","clientMessageId":"order-42"}'
curl -s -X POST http://localhost:8080/v1/sms/send -H "x-api-key: $KEY" \
  -H "content-type: application/json" \
  -d '{"to":"0987654321","content":"order otp","clientMessageId":"order-42"}'
```

Validation errors return 400 with a structured `ApiError`:

```bash
curl -s -X POST http://localhost:8080/v1/sms/send -H "x-api-key: $KEY" \
  -H "content-type: application/json" -d '{"to":"0123456789","content":"x"}'
```

## Day 3 (OTP — done)

Issue an OTP (default 6 digits, 5 minute TTL). The `devCode` field in the response is for demo only — real telecom systems never return the code over the API.

```bash
curl -s -X POST http://localhost:8080/v1/otp/send \
  -H "x-api-key: $KEY" -H "content-type: application/json" \
  -d '{"phone":"0987654321"}' | jq
```

Verify. After 3 wrong attempts the code is permanently locked, regardless of whether you later submit the correct code.

```bash
curl -s -X POST http://localhost:8080/v1/otp/verify \
  -H "x-api-key: $KEY" -H "content-type: application/json" \
  -d '{"phone":"0987654321","code":"123456"}' | jq
```

Cooldown: a second `send` for the same phone within 30s returns **429** with a `Retry-After` header.

Response codes:

| Status | Meaning |
|---|---|
| `{"verified":true}` | Code accepted |
| `{"verified":false,"reason":"INVALID_CODE","attemptsLeft":N}` | Wrong code, N attempts remaining |
| `{"verified":false,"reason":"LOCKED","attemptsLeft":0}` | Too many wrong attempts, code locked |
| `{"verified":false,"reason":"EXPIRED"}` | TTL elapsed |
| `{"verified":false,"reason":"NO_OTP_ISSUED"}` | This phone never received an OTP |
