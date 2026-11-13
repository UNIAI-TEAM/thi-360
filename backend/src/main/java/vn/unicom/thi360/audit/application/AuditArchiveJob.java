package vn.unicom.thi360.audit.application;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager.QuarterPartition;
import vn.unicom.thi360.audit.infrastructure.AuditProperties;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditOutcome;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.web.TraceIdFilter;

@Component
@Profile("worker")
public class AuditArchiveJob {

    private static final Logger jobLog = LoggerFactory.getLogger("thi360.job");
    public static final String JOB_NAME = "audit-archive";
    public static final String ACTION_AUDIT_ARCHIVED = "AUDIT_ARCHIVED";

    private final AuditPartitionManager partitionManager;
    private final AuditProperties properties;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;

    public AuditArchiveJob(
            AuditPartitionManager partitionManager,
            AuditProperties properties,
            AuditRecorder auditRecorder,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.partitionManager = partitionManager;
        this.properties = properties;
        this.auditRecorder = auditRecorder;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx = template;
        this.clock = clock;
    }

    @Scheduled(cron = "${thi360.audit.archive-cron:0 15 2 * * SUN}", zone = "UTC")
    public ArchiveRunResult run() {
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
            int currentTotalQuarters = currentYear * 4 + (currentQuarter - 1);
            int retentionQuarters = properties.archiveAfterQuarters();

            List<QuarterPartition> candidates = partitionManager.listAttachedQuarterPartitions();
            List<String> archivedPartitions = new ArrayList<>();
            List<String> failedPartitions = new ArrayList<>();
            long totalArchivedRows = 0L;

            for (QuarterPartition partition : candidates) {
                int ageQuarters = currentTotalQuarters - partition.totalQuarters();
                if (ageQuarters <= 0 || ageQuarters <= retentionQuarters) {
                    continue;
                }
                try {
                    long rows = archivePartition(partition);
                    archivedPartitions.add(partition.tableName());
                    totalArchivedRows += rows;
                } catch (RuntimeException ex) {
                    failedPartitions.add(partition.tableName());
                    jobLog.atError()
                            .setCause(ex)
                            .addKeyValue("partition", partition.tableName())
                            .log("Audit archive aborted for quarter partition");
                }
            }

            jobLog.atInfo()
                    .addKeyValue("archivedPartitions", archivedPartitions.size())
                    .addKeyValue("failedPartitions", failedPartitions.size())
                    .addKeyValue("archivedRows", totalArchivedRows)
                    .log("Audit archive job finished");

            return new ArchiveRunResult(
                    List.copyOf(archivedPartitions),
                    List.copyOf(failedPartitions),
                    totalArchivedRows);
        } finally {
            restoreMdc(TraceIdFilter.MDC_TRACE_ID, prevTraceId);
            restoreMdc("job", prevJob);
            restoreMdc("jobId", prevJobId);
        }
    }

    public long archivePartition(QuarterPartition partition) {
        Long archivedCount = requiresNewTx.execute(status -> {
            partitionManager.enableMaintenanceBypassForTransaction();
            partitionManager.ensureArchiveYearPartition(partition.year());

            long sourceCount = partitionManager.countQuarterPartitionRows(partition);
            long copiedCount = partitionManager.copyQuarterPartitionToArchive(partition);
            if (copiedCount != sourceCount) {
                throw new AuditArchiveMismatchException(partition.tableName(), sourceCount, copiedCount);
            }

            long archiveRangeCount = partitionManager.countArchiveRowsInRange(
                    partition.startInclusive(),
                    partition.endExclusive());
            if (archiveRangeCount < sourceCount) {
                throw new AuditArchiveMismatchException(partition.tableName(), sourceCount, archiveRangeCount);
            }

            partitionManager.detachAndDropQuarterPartition(partition);

            auditRecorder.record(AuditEvent.builder("audit", ACTION_AUDIT_ARCHIVED)
                    .target("AUDIT_PARTITION", partition.tableName(), partition.tableName())
                    .after(Map.of(
                            "partition", partition.tableName(),
                            "rowCount", sourceCount,
                            "archiveYear", partition.year()))
                    .outcome(AuditOutcome.SUCCESS)
                    .actorType(AuditActorType.SYSTEM)
                    .build());

            return sourceCount;
        });
        return archivedCount == null ? 0L : archivedCount;
    }

    private static void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }

    public record ArchiveRunResult(
            List<String> archivedPartitions,
            List<String> failedPartitions,
            long archivedRows
    ) {
    }

    public static class AuditArchiveMismatchException extends IllegalStateException {

        public AuditArchiveMismatchException(String partition, long sourceCount, long copiedCount) {
            super("Row count mismatch while archiving " + partition
                    + ": source=" + sourceCount + ", archived=" + copiedCount);
        }
    }
}
