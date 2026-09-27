package fieldservice.dashboard;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class FieldServiceDashboard {
    private FieldServiceDashboard() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("usage: <work-order-id> <account-id> <technician-id> <status> <photo-ids-csv> <required-photo-count> <observed-at>");
            System.exit(2);
        }

        DashboardConfig config = DashboardConfig.fromEnvironment();
        WorkOrderPolicy policy = new WorkOrderPolicy();
        List<String> photos = args[4].isBlank() ? List.of() : Arrays.asList(args[4].split(","));
        WorkOrderPolicy.WorkOrder order = new WorkOrderPolicy.WorkOrder(
                args[0], args[1], args[2], WorkOrderPolicy.DispatchStatus.valueOf(args[3].toUpperCase()),
                photos, Integer.parseInt(args[5]), Instant.parse(args[6]));
        WorkOrderPolicy.Decision decision = policy.evaluate(order);

        InfraiDashboardClient client = new InfraiDashboardClient(config);
        client.ensureChannel();
        System.out.println(JsonCodec.write(client.recordQueryAndPublish(
                order, decision, policy.metricPoints(order, decision))));
    }
}
