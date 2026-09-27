package fieldservice.dashboard;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkOrderPolicy {
    public enum DispatchStatus { ASSIGNED, EN_ROUTE, ON_SITE, COMPLETED }
    public enum FollowUp { NONE, REQUIRED }

    public record WorkOrder(
            String workOrderId,
            String accountId,
            String technicianId,
            DispatchStatus dispatchStatus,
            List<String> photoIds,
            int requiredPhotoCount,
            Instant observedAt) {
        public WorkOrder {
            if (workOrderId.isBlank() || accountId.isBlank() || technicianId.isBlank()) {
                throw new IllegalArgumentException("work order, account, and technician IDs are required");
            }
            photoIds = List.copyOf(photoIds);
            if (requiredPhotoCount < 0) throw new IllegalArgumentException("required photo count must be non-negative");
        }
    }

    public record Decision(FollowUp followUp, String reason, Map<String, Object> dashboardData) {}

    public Decision evaluate(WorkOrder order) {
        boolean evidenceMissing = order.dispatchStatus() == DispatchStatus.COMPLETED
                && order.photoIds().size() < order.requiredPhotoCount();
        FollowUp followUp = evidenceMissing ? FollowUp.REQUIRED : FollowUp.NONE;
        String reason = evidenceMissing ? "completion_evidence_incomplete" : "dispatch_state_accepted";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("work_order_id", order.workOrderId());
        data.put("technician_id", order.technicianId());
        data.put("dispatch_status", order.dispatchStatus().name().toLowerCase());
        data.put("photo_count", order.photoIds().size());
        data.put("required_photo_count", order.requiredPhotoCount());
        data.put("follow_up", followUp.name().toLowerCase());
        data.put("reason", reason);
        data.put("observed_at", order.observedAt().toString());
        return new Decision(followUp, reason, Map.copyOf(data));
    }

    public List<Map<String, Object>> metricPoints(WorkOrder order, Decision decision) {
        Map<String, String> tags = Map.of(
                "dispatch_status", order.dispatchStatus().name().toLowerCase(),
                "follow_up", decision.followUp().name().toLowerCase());
        return List.of(
                point("fieldservice.work_orders", 1, tags, order.observedAt()),
                point("fieldservice.photos", order.photoIds().size(), tags, order.observedAt()),
                point("fieldservice.followups.required", decision.followUp() == FollowUp.REQUIRED ? 1 : 0,
                        tags, order.observedAt()));
    }

    private static Map<String, Object> point(String name, int value, Map<String, String> tags, Instant timestamp) {
        return Map.of("name", name, "value", value, "type", "gauge", "tags", tags,
                "timestamp", timestamp.toString());
    }
}
