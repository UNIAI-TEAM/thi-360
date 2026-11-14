package vn.unicom.thi360.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import vn.unicom.thi360.audit.infrastructure.AuditProperties;
import vn.unicom.thi360.audit.infrastructure.AuditStoreRepository;
import vn.unicom.thi360.audit.spi.AuditQueryCriteria;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.ValidationException;

class AuditQueryServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);
    // 2026 Q4 start = 2026-10-01T00:00:00Z, minus 8 quarters (24 months) = 2024-10-01T00:00:00Z
    private static final Instant OLD_INSTANT = Instant.parse("2023-12-31T23:59:59Z");
    private static final Instant RECENT_INSTANT = Instant.parse("2026-10-15T00:00:00Z");

    @Test
    void query_includesArchiveWhenFromIsOldOrFromIsAbsentAndToIsOld() {
        AuditStoreRepository repository = mock(AuditStoreRepository.class);
        when(repository.findEvents(any(), any(Boolean.class), any()))
                .thenReturn(new PageImpl<>(List.of()));

        AuditQueryService service = new AuditQueryService(repository, new AuditProperties(8), FIXED_CLOCK);
        PageQuery pageQuery = PageQuery.of(0, 20, null, List.of("occurredAt"));

        // Both absent -> active only
        service.query(new AuditQueryCriteria(null, null, null, null, null, null), pageQuery);
        verify(repository).findEvents(any(), eq(false), any());
        clearInvocations(repository);

        // Only 'to' and old -> include archive
        service.query(new AuditQueryCriteria(null, null, null, null, null, OLD_INSTANT), pageQuery);
        verify(repository).findEvents(any(), eq(true), any());
        clearInvocations(repository);

        // Only 'to' and recent -> active only
        service.query(new AuditQueryCriteria(null, null, null, null, null, RECENT_INSTANT), pageQuery);
        verify(repository).findEvents(any(), eq(false), any());
        clearInvocations(repository);

        // 'from' old and 'to' recent -> include archive
        service.query(new AuditQueryCriteria(null, null, null, null, OLD_INSTANT, RECENT_INSTANT), pageQuery);
        verify(repository).findEvents(
                eq(new AuditQueryCriteria(null, null, null, null, OLD_INSTANT, RECENT_INSTANT)),
                eq(true),
                any());
    }

    @Test
    void query_validatesRangeBeforeQueryingRepository() {
        AuditStoreRepository repository = mock(AuditStoreRepository.class);
        AuditQueryService service = new AuditQueryService(repository, new AuditProperties(8), FIXED_CLOCK);
        PageQuery pageQuery = PageQuery.of(0, 20, null, List.of("occurredAt"));

        assertThatThrownBy(() -> service.query(
                new AuditQueryCriteria(null, null, null, null, RECENT_INSTANT, OLD_INSTANT),
                pageQuery))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(repository);
        assertThat(service.archiveCutoffInstant()).isEqualTo(Instant.parse("2024-10-01T00:00:00Z"));
    }
}
