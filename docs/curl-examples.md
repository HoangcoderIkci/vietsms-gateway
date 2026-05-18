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

## Day 2 (SMS — to be implemented)

```bash
curl -s -X POST http://localhost:8080/v1/sms/send \
  -H "x-api-key: $KEY" \
  -H "content-type: application/json" \
  -d '{"to":"0987654321","content":"Xin chao tu VietSMS","client_message_id":"abc-1"}' | jq

curl -s -H "x-api-key: $KEY" http://localhost:8080/v1/sms/1 | jq
curl -s -H "x-api-key: $KEY" "http://localhost:8080/v1/sms?page=0&size=20" | jq
```

## Day 3 (OTP — to be implemented)

```bash
curl -s -X POST http://localhost:8080/v1/otp/send \
  -H "x-api-key: $KEY" -H "content-type: application/json" \
  -d '{"phone":"0987654321"}' | jq

curl -s -X POST http://localhost:8080/v1/otp/verify \
  -H "x-api-key: $KEY" -H "content-type: application/json" \
  -d '{"phone":"0987654321","code":"123456"}' | jq
```
