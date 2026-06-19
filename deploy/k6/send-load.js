/**
 * VietSMS Gateway — k6 Mixed-Workload Load Test
 * ================================================
 * Tests: SMS send (write), OTP send (write), SMS history list (read), ping (health).
 * Rate-limit awareness: 429 responses from the gateway's sliding-window limiter are
 * EXPECTED on write paths and are tracked via a custom `rate_limited` metric; they
 * do NOT count as http failures (see http.setResponseCallback below).
 *
 * HOW TO RUN
 * ----------
 * Host k6 (full load):
 *   k6 run -e BASE_URL=http://localhost:8080 -e API_KEY=<your-key> deploy/k6/send-load.js
 *
 * Host k6 (smoke — quick sanity, 1 VU × 10s):
 *   k6 run -e BASE_URL=http://localhost:8080 -e API_KEY=<your-key> -e SMOKE=1 deploy/k6/send-load.js
 *
 * Docker k6 (inside Compose network):
 *   docker run --rm -i --network test_default \
 *     -e BASE_URL=http://vietsms-gateway:8080 \
 *     -e API_KEY=<your-key> \
 *     grafana/k6 run - < deploy/k6/send-load.js
 *
 * Docker k6 (smoke):
 *   docker run --rm -i --network test_default \
 *     -e BASE_URL=http://vietsms-gateway:8080 \
 *     -e API_KEY=<your-key> \
 *     -e SMOKE=1 \
 *     grafana/k6 run - < deploy/k6/send-load.js
 *
 * ENVIRONMENT VARIABLES
 * ---------------------
 *   BASE_URL  default: http://localhost:8080
 *   API_KEY   required — the test will abort if missing
 *   SMOKE     optional — any non-empty value enables smoke mode (1 VU, 10s)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import exec from 'k6/execution';

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------

/** Tracks the fraction of requests that were rate-limited (HTTP 429). */
const rateLimited = new Rate('rate_limited');

// ---------------------------------------------------------------------------
// Tell k6 which HTTP status codes are "expected" so they are NOT counted in
// http_req_failed.  429 is intentional from the gateway's rate limiter.
// ---------------------------------------------------------------------------
http.setResponseCallback(http.expectedStatuses(200, 201, 202, 429));

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY  = __ENV.API_KEY;
const SMOKE    = !!__ENV.SMOKE;

/** Full ramp profile — 0→50→200→200→0 over 5 minutes. */
const FULL_STAGES = [
  { duration: '1m', target: 50  },  // ramp up
  { duration: '2m', target: 200 },  // climb to peak
  { duration: '1m', target: 200 },  // hold
  { duration: '1m', target: 0   },  // ramp down
];

/** Smoke profile — just enough to validate config & connectivity. */
const SMOKE_STAGES = [
  { duration: '10s', target: 1 },
];

export const options = {
  stages: SMOKE ? SMOKE_STAGES : FULL_STAGES,
  thresholds: {
    // Less than 1% of requests should fail (429s excluded by expectedStatuses above).
    'http_req_failed': ['rate<0.01'],
    // 95th-percentile latency target across all requests.
    'http_req_duration': ['p(95)<200'],
    // Same target scoped to requests that received an expected response.
    'http_req_duration{expected_response:true}': ['p(95)<200'],
    // Informational — no hard limit on rate-limit fraction (key has high quota in tests).
    'rate_limited': ['rate<1.0'],
  },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Returns a Vietnamese-format phone number that is unique per VU + iteration,
 * avoiding per-phone OTP cooldown collisions across concurrent VUs.
 */
function uniquePhone() {
  const vuId   = exec.vu.idInTest;       // 1-based, unique per VU
  const iter   = exec.vu.iterationInScenario; // 0-based within this VU
  // Combine to form a number in range 0000000–9999999 (7 digits).
  // VU ids go up to 200; iterations up to ~300 over 5m at default think-time.
  const suffix = String((vuId * 10000 + iter) % 10000000).padStart(7, '0');
  return `09${suffix.slice(0, 8)}`; // "09" + up to 8 digits → valid VN format
}

/**
 * Weighted random scenario selector.
 * Returns one of: 'sms_list' | 'sms_send' | 'ping' | 'otp_send'
 *   50% sms_list, 20% sms_send, 20% ping, 10% otp_send
 */
function pickScenario() {
  const r = Math.random();
  if (r < 0.50) return 'sms_list';
  if (r < 0.70) return 'sms_send';
  if (r < 0.90) return 'ping';
  return 'otp_send';
}

/** Shared headers for every request. */
function headers() {
  return { 'x-api-key': API_KEY, 'Content-Type': 'application/json' };
}

// ---------------------------------------------------------------------------
// Setup — runs once before any VUs start
// ---------------------------------------------------------------------------

export function setup() {
  if (!API_KEY) {
    exec.test.abort('API_KEY env var is required but not set. Aborting load test.');
  }
  // Quick connectivity check — fails fast before spawning all VUs.
  const res = http.get(`${BASE_URL}/v1/ping`, { headers: headers() });
  if (res.status !== 200) {
    exec.test.abort(
      `Ping failed during setup (status=${res.status}). Is the gateway up at ${BASE_URL}?`
    );
  }
}

// ---------------------------------------------------------------------------
// Scenario handlers
// ---------------------------------------------------------------------------

function doSmsList() {
  const res = http.get(`${BASE_URL}/v1/sms?page=0&size=20`, { headers: headers() });
  check(res, {
    'sms_list: status 200': (r) => r.status === 200,
  });
}

function doSmsSend(phone) {
  const payload = JSON.stringify({ to: phone, content: 'VietSMS k6 load test message' });
  const res = http.post(`${BASE_URL}/v1/sms/send`, payload, { headers: headers() });

  const is429 = res.status === 429;
  rateLimited.add(is429 ? 1 : 0);

  check(res, {
    // 202 Accepted on success, 429 when rate-limited — both are acceptable.
    'sms_send: status 2xx or 429': (r) => r.status === 202 || r.status === 429,
  });
}

function doPing() {
  const res = http.get(`${BASE_URL}/v1/ping`, { headers: headers() });
  check(res, {
    'ping: status 200': (r) => r.status === 200,
  });
}

function doOtpSend(phone) {
  const payload = JSON.stringify({ phone: phone });
  const res = http.post(`${BASE_URL}/v1/otp/send`, payload, { headers: headers() });

  const is429 = res.status === 429;
  rateLimited.add(is429 ? 1 : 0);

  check(res, {
    // 202 Accepted on success, 429 when rate-limited — both are acceptable.
    'otp_send: status 2xx or 429': (r) => r.status === 202 || r.status === 429,
  });
}

// ---------------------------------------------------------------------------
// Default function — called once per VU iteration
// ---------------------------------------------------------------------------

export default function () {
  const scenario = pickScenario();
  const phone    = uniquePhone();

  switch (scenario) {
    case 'sms_list': doSmsList();        break;
    case 'sms_send': doSmsSend(phone);   break;
    case 'ping':     doPing();           break;
    case 'otp_send': doOtpSend(phone);   break;
  }

  // Think-time: random between 100–300 ms, simulates realistic client pacing.
  sleep(0.1 + Math.random() * 0.2);
}
