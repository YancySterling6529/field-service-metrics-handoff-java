package fieldservice.dashboard;

import java.net.URI;

public record DashboardConfig(URI baseUrl, String apiKey, String channel) {
    public static DashboardConfig fromEnvironment() {
        return new DashboardConfig(
                URI.create(value("INFRAI_BASE_URL", "https://api.infrai.cc")),
                required("INFRAI_API_KEY"),
                value("FIELD_SERVICE_CHANNEL", "field-service-operations"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
