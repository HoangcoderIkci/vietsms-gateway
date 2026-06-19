# VietSMS Gateway — Observability Stack

## Start the full stack

```bash
docker compose up -d
```

This starts: PostgreSQL, VietSMS Gateway, Prometheus, Grafana.

## URLs

| Service    | URL                         | Notes                              |
|------------|-----------------------------|------------------------------------|
| App        | http://localhost:8080       | REST API                           |
| Prometheus | http://localhost:9090       | Metrics store; scrapes app every 5s |
| Grafana    | http://localhost:3000       | Anonymous admin (no login needed)  |

## Dashboard

Grafana auto-provisions the **VietSMS Gateway** dashboard on first boot.
Navigate to http://localhost:3000/dashboards to find it — no manual import needed.

Panels include:
- SMS throughput (enqueued vs delivered/s)
- SMS terminal failures/s
- OTP locked (total, stat panel)
- Rate-limit trips/s
- Webhook delivered vs dead/s
- Webhook delivery latency p95

## Stop

```bash
docker compose down
```

Add `-v` to also remove persistent volumes (Postgres data, Grafana state).
