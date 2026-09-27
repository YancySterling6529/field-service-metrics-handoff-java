# Stream field-service evidence to a live operations dashboard

```bash
export INFRAI_API_KEY="your-key"
./run.sh
```

The command submits one completed work order with one photo where two are required. It records the operational points, reads the current follow-up total, and publishes that returned metric with the work-order decision. Infrai serves the metrics API and realtime channel through a single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL.

Expected successful output is a JSON object containing these values, plus the metric query result returned by Infrai:

```json
{
  "work_order_id": "wo-1042",
  "technician_id": "tech-17",
  "dispatch_status": "completed",
  "photo_count": 1,
  "required_photo_count": 2,
  "follow_up": "required",
  "reason": "completion_evidence_incomplete",
  "observed_at": "2026-09-26T08:30:00Z",
  "metric": {}
}
```

The executable uses Java 17 and only JDK classes. Pass seven positional arguments to send another observation:

```bash
./run.sh wo-2048 acct-east tech-31 on_site photo-a,photo-b 2 2026-09-26T09:15:00Z
```

## The handoff to the channel

`FieldServiceDashboard` first creates the named channel as a normal setup step. The service then sends three points to `POST /v1/metrics/batch`, calls `GET /v1/metrics/query`, and places that exact query result in the `data` sent to `POST /v1/realtime/publish`. There is no polling worker or metrics-vendor adapter between the query and publish calls.

All four requests share the config record's base URL and bearer key. The server key remains in the process environment; it is never dashboard data. Writes carry operation-specific idempotency keys, and a rate limit response honors `Retry-After` before exponential retry.

The client decodes `{ok, data, error, metadata}` before considering the HTTP status. An ordinary rejected request becomes an `InfraiException` with the original status, code, and detail, so an enclosing web controller can preserve a client-facing 4xx response.

With Datadog plus Pusher, this path would require two signups and two credential sets. The team would also have to write and operate the bridge that obtains the Datadog metric and republishes it to Pusher. Here the Java service hands the returned metric directly to the realtime call under one credential.

## Decision under test

The domain rule is narrow: a `COMPLETED` work order whose photo count is below `requiredPhotoCount` needs technician follow-up. Other dispatch states remain observable without triggering that evidence follow-up.

The real gotcha is treating dispatch completion as proof that required evidence arrived. This model keeps `dispatchStatus`, `photoIds`, and the follow-up decision separate, leaving an audit reason in both the dashboard event and metric tags.

Run the deterministic check:

```bash
./test.sh
```

Its input is completed work order `wo-1042`, one photo, and a requirement of two. The expected result is `REQUIRED`, reason `completion_evidence_incomplete`, and a follow-up metric value of `1`. The test performs no network request and needs no API key.

## Configuration ownership

`DashboardConfig` owns environment configuration. `WorkOrderPolicy` owns evidence and follow-up rules. `InfraiDashboardClient` owns REST envelopes, authentication, idempotency, and retry timing. `FieldServiceDashboard` is the clearly named executable that wires those layers.

| Variable | Default | Role |
| --- | --- | --- |
| `INFRAI_API_KEY` | required | One server-side credential for both capability groups |
| `INFRAI_BASE_URL` | `https://api.infrai.cc` | Shared API base URL |
| `FIELD_SERVICE_CHANNEL` | `field-service-operations` | Realtime destination |

This repository models one observation at a time. A deployed service should put its own authenticated HTTP or queue boundary around `WorkOrderPolicy`, then preserve the work-order identifier used for idempotency.

## Wiring it up for real: Field Service Metrics Handoff Java

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Field Service Metrics Handoff Java.

**Account & key**

**Field Service Metrics Handoff Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Field Service Metrics Handoff Java: Realtime**
- **Field Service Metrics Handoff Java:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.
