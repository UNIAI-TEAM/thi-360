package vn.unicom.thi360.audit.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.infrastructure.AuditProperties;
import vn.unicom.thi360.audit.infrastructure.AuditStoreRepository;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditQuery;
import vn.unicom.thi360.audit.spi.AuditQueryCriteria;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.web.AppMessages;

@Service
public class AuditQueryService implements AuditQuery {

    private final AuditStoreRepository repository;
    private final AuditProperties properties;
    private final Clock clock;
    private final AppMessages messages;

    public AuditQueryService(
            AuditStoreRepository repository,
            AuditProperties properties,
            Clock clock) {
        this(repository, properties, clock, AppMessages.defaultInstance());
    }

    @Autowired
    public AuditQueryService(
            AuditStoreRepository repository,
            AuditProperties properties,
            Clock clock,
            AppMessages messages) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.messages = messages;
    }
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditEventView> findByTarget(
            String targetType,
            String targetId,
            PageQuery pageQuery) {
        return findByTarget(targetType, targetId, null, null, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditEventView> findByTarget(
            String targetType,
            String targetId,
            Instant from,
            Instant to,
            PageQuery pageQuery) {
        return query(AuditQueryCriteria.forTarget(targetType, targetId, from, to), pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditEventView> query(AuditQueryCriteria criteria, PageQuery pageQuery) {
        AuditQueryCriteria effectiveCriteria = criteria == null
                ? new AuditQueryCriteria(null, null, null, null, null, null)
                : criteria;

        if (effectiveCriteria.from() != null
                && effectiveCriteria.to() != null
                && effectiveCriteria.to().isBefore(effectiveCriteria.from())) {
            throw new ValidationException(List.of(
                    new FieldError("to", "INVALID_TIME_RANGE", messages.get("validation.audit.timeRange.invalid"))));
        }

        boolean includeArchive = shouldIncludeArchive(effectiveCriteria.from(), effectiveCriteria.to());
        return PageResponse.from(repository.findEvents(effectiveCriteria, includeArchive, pageQuery.toPageable()));
    }

    Instant archiveCutoffInstant() {
        ZonedDateTime utcNow = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        int currentQuarter = (utcNow.getMonthValue() - 1) / 3 + 1;
        int startMonth = (currentQuarter - 1) * 3 + 1;
        ZonedDateTime currentQuarterStart = ZonedDateTime.of(
                utcNow.getYear(), startMonth, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        return currentQuarterStart.minusMonths(properties.archiveAfterQuarters() * 3L).toInstant();
    }

    private boolean shouldIncludeArchive(Instant from, Instant to) {
        Instant cutoff = archiveCutoffInstant();
        if (from != null) {
            return from.isBefore(cutoff);
        }
        if (to != null) {
            return to.isBefore(cutoff);
        }
        return false;
    }
}
