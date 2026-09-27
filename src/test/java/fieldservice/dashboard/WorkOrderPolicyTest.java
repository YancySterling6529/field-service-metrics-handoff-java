package fieldservice.dashboard;

import java.time.Instant;
import java.util.List;

public final class WorkOrderPolicyTest {
    public static void main(String[] args) {
        WorkOrderPolicy policy = new WorkOrderPolicy();
        WorkOrderPolicy.WorkOrder order = new WorkOrderPolicy.WorkOrder(
                "wo-1042", "acct-north", "tech-17", WorkOrderPolicy.DispatchStatus.COMPLETED,
                List.of("photo-arrival"), 2, Instant.parse("2026-09-26T08:30:00Z"));

        WorkOrderPolicy.Decision decision = policy.evaluate(order);
        if (decision.followUp() != WorkOrderPolicy.FollowUp.REQUIRED) {
            throw new AssertionError("completed work with missing evidence must require follow-up");
        }
        if (!"completion_evidence_incomplete".equals(decision.reason())) {
            throw new AssertionError("decision must retain its audit reason");
        }
        Object followUpValue = policy.metricPoints(order, decision).get(2).get("value");
        if (!Integer.valueOf(1).equals(followUpValue)) {
            throw new AssertionError("follow-up metric must be 1");
        }
        System.out.println("PASS completed work with 1/2 photos requires technician follow-up");
    }
}
