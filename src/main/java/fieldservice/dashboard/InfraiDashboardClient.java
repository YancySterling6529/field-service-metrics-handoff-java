package fieldservice.dashboard;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfraiDashboardClient {
    public static final String HANDOFF = "infrai.metrics.batch -> infrai.metrics.query -> infrai.realtime.publish";

    public static final class InfraiException extends Exception {
        private final String code;
        private final int status;
        private final Map<String, Object> detail;

        InfraiException(String code, int status, Map<String, Object> detail) {
            super("Infrai rejected the request: " + code);
            this.code = code;
            this.status = status;
            this.detail = Map.copyOf(detail);
        }

        public String code() { return code; }
        public int status() { return status; }
        public Map<String, Object> detail() { return detail; }
    }

    private final DashboardConfig config;
    private final HttpClient http;

    public InfraiDashboardClient(DashboardConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiDashboardClient(DashboardConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public void ensureChannel() throws Exception {
        post("/v1/realtime/channel/create", Map.of(
                "channel", config.channel(), "type", "public", "vendor", "infrai"),
                "channel:" + config.channel());
    }

    public Map<String, Object> recordQueryAndPublish(WorkOrderPolicy.WorkOrder order,
                                                      WorkOrderPolicy.Decision decision,
                                                      List<Map<String, Object>> points) throws Exception {
        String operation = "work-order:" + order.workOrderId();
        post("/v1/metrics/batch", Map.of(
                "points", points, "idempotency_key", operation + ":metrics"), operation + ":metrics");

        Map<String, Object> metricData = get("/v1/metrics/query?name="
                + encode("fieldservice.followups.required") + "&agg=sum&window=1h");

        Map<String, Object> eventData = new LinkedHashMap<>(decision.dashboardData());
        eventData.put("metric", metricData);
        post("/v1/realtime/publish", Map.of(
                "channel", config.channel(),
                "event", "fieldservice.work_order.observed",
                "data", eventData,
                "account_id", order.accountId(),
                "idempotency_key", operation + ":publish"), operation + ":publish");
        return Map.copyOf(eventData);
    }

    private Map<String, Object> get(String path) throws Exception {
        return request("GET", path, null, null);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) throws Exception {
        return request("POST", path, JsonCodec.write(body), idempotencyKey);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, String body, String idempotencyKey) throws Exception {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
            if (body != null) builder.header("Content-Type", "application/json");
            HttpRequest request = builder.method(method, publisher).build();

            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (attempt == 3) throw e;
                Thread.sleep(250L << attempt);
                continue;
            }

            Object decoded = JsonCodec.read(response.body());
            if (!(decoded instanceof Map<?, ?> raw)) throw new IOException("Infrai envelope must be a JSON object");
            Map<String, Object> envelope = (Map<String, Object>) raw;
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                if (response.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> value
                        ? (Map<String, Object>) value : Map.of();
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "request_rejected")),
                        response.statusCode(), error);
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            return envelope.get("data") instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
        }
        throw new IOException("request attempts exhausted");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try { return Math.max(1L, Long.parseLong(value)) * 1000L; }
        catch (NumberFormatException ignored) {
            try {
                long delay = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - System.currentTimeMillis();
                if (delay > 0) return delay;
            } catch (RuntimeException ignoredDate) { }
            return 250L << attempt;
        }
    }
}
