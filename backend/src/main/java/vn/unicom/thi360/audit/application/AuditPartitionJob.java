package vn.unicom.thi360.audit.application;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager.QuarterPartition;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Component
@Profile("worker")
public class AuditPartitionJob {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");
    public static final String JOB_NAME = "audit-partition";

    private final AuditPartitionManager partitionManager;
    private final Clock clock;

    public AuditPartitionJob(AuditPartitionManager partitionManager, Clock clock) {
        this.partitionManager = partitionManager;
        this.clock = clock;
    }

    @Scheduled(cron = "${thi360.audit.partition-cron:0 5 0 * * *}", zone = "UTC")
    @Transactional
    public PartitionRunResult run() {
        String prevTraceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        String prevJob = MDC.get("job");
        String prevJobId = MDC.get("jobId");

        if (prevTraceId == null || prevTraceId.isBlank()) {
            MDC.put(TraceIdFilter.MDC_TRACE_ID, Ids.newId().toString());
        }
        MDC.put("job", JOB_NAME);
        MDC.put("jobId", Ids.newId().toString());

        try {
            ZonedDateTime utcNow = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
            int currentYear = utcNow.getYear();
            int currentQuarter = (utcNow.getMonthValue() - 1) / 3 + 1;
            int nextYear = currentQuarter == 4 ? currentYear + 1 : currentYear;
            int nextQuarter = currentQuarter == 4 ? 1 : currentQuarter + 1;

            List<String> created = new ArrayList<>();
            if (partitionManager.ensureQuarterPartition(currentYear, currentQuarter)) {
                created.add(QuarterPartition.of(currentYear, currentQuarter).tableName());
            }
            if (partitionManager.ensureQuarterPartition(nextYear, nextQuarter)) {
                created.add(QuarterPartition.of(nextYear, nextQuarter).tableName());
            }
            if (partitionManager.ensureArchiveYearPartition(currentYear)) {
                created.add("audit_event_archive_" + currentYear);
            }
            if (nextYear != currentYear && partitionManager.ensureArchiveYearPartition(nextYear)) {
                created.add("audit_event_archive_" + nextYear);
            }

            jobLog.atInfo()
                    .addKeyValue("currentQuarter", currentYear + "q" + currentQuarter)
                    .addKeyValue("createdCount", created.size())
                    .log("Audit partition maintenance completed");

            return new PartitionRunResult(List.copyOf(created));
        } finally {
            restoreMdc(TraceIdFilter.MDC_TRACE_ID, prevTraceId);
            restoreMdc("job", prevJob);
            restoreMdc("jobId", prevJobId);
        }
    }

    private static void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

    public record PartitionRunResult(List<String> createdPartitions) {
    }
}
