package vn.unicom.thi360.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import vn.unicom.thi360.audit.infrastructure.AuditPartitionManager.QuarterPartition;

class AuditPartitionManagerTests {

    @Test
    void quarterPartitionConstructor_rejectsNonCanonicalNameAndBounds() {
        QuarterPartition valid = QuarterPartition.of(2023, 1);
        assertThat(valid.tableName()).isEqualTo("audit_event_2023q1");

        assertThatThrownBy(() -> new QuarterPartition(
                "audit_event_2023q1; DROP TABLE audit.audit_event",
                2023,
                1,
                valid.startInclusive(),
                valid.endExclusive()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");

        assertThatThrownBy(() -> new QuarterPartition(
                "audit_event_2023q2",
                2023,
                1,
                valid.startInclusive(),
                valid.endExclusive()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");

        assertThatThrownBy(() -> new QuarterPartition(
                valid.tableName(),
                2023,
                1,
                valid.startInclusive().plusSeconds(1),
                valid.endExclusive()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounds");

        assertThatThrownBy(() -> new QuarterPartition(
                valid.tableName(),
                2023,
                1,
                valid.startInclusive(),
                valid.endExclusive().minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounds");

        assertThatThrownBy(() -> new QuarterPartition(
                null,
                2023,
                1,
                valid.startInclusive(),
                valid.endExclusive()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partitionOperations_rejectUnattachedPartitionBeforeExecutingDdl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(namedJdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(Boolean.FALSE);

        AuditPartitionManager manager = new AuditPartitionManager(jdbcTemplate, namedJdbcTemplate);
        QuarterPartition unattached = QuarterPartition.of(2023, 1);

        assertThatThrownBy(() -> manager.countQuarterPartitionRows(unattached))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not attached");
        assertThatThrownBy(() -> manager.copyQuarterPartitionToArchive(unattached))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not attached");
        assertThatThrownBy(() -> manager.detachAndDropQuarterPartition(unattached))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not attached");

        verifyNoInteractions(jdbcTemplate);
    }
}
