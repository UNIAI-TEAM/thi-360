package vn.unicom.thi360.audit.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventTests {

    @Test
    void successWithoutAfterSetsExpectedDefaults() {
        AuditEvent event = AuditEvent.success("identity", "LOGIN", "user", "u-1", "student01");

        assertThat(event.module()).isEqualTo("identity");
        assertThat(event.action()).isEqualTo("LOGIN");
        assertThat(event.targetType()).isEqualTo("user");
        assertThat(event.targetId()).isEqualTo("u-1");
        assertThat(event.targetLabel()).isEqualTo("student01");
        assertThat(event.before()).isNull();
        assertThat(event.after()).isNull();
        assertThat(event.reason()).isNull();
        assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.errorCode()).isNull();
        assertThat(event.actorId()).isNull();
        assertThat(event.actorUsername()).isNull();
        assertThat(event.actorRoles()).isNull();
        assertThat(event.actorType()).isNull();
    }

    @Test
    void successWithAfterPreservesNullAndDefensivelyCopiesMap() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "ACTIVE");
        after.put("note", null);

        AuditEvent withAfter = AuditEvent.success("identity", "REGISTER", "user", "u-2", "student02", after);
        after.put("status", "MUTATED");

        assertThat(withAfter.before()).isNull();
        assertThat(withAfter.after()).containsEntry("status", "ACTIVE").containsEntry("note", null);
        assertThatThrownBy(() -> withAfter.after().put("extra", "x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(withAfter.reason()).isNull();
        assertThat(withAfter.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(withAfter.errorCode()).isNull();
        assertThat(withAfter.actorId()).isNull();
        assertThat(withAfter.actorUsername()).isNull();
        assertThat(withAfter.actorRoles()).isNull();
        assertThat(withAfter.actorType()).isNull();

        AuditEvent withNullAfter = AuditEvent.success("identity", "REGISTER", "user", "u-3", "student03", null);
        assertThat(withNullAfter.before()).isNull();
        assertThat(withNullAfter.after()).isNull();
        assertThat(withNullAfter.outcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void failureSetsOutcomeAndErrorCode() {
        AuditEvent event = AuditEvent.failure("identity", "LOGIN", "user", "u-4", "student04", "INVALID_CREDENTIALS");

        assertThat(event.module()).isEqualTo("identity");
        assertThat(event.action()).isEqualTo("LOGIN");
        assertThat(event.targetType()).isEqualTo("user");
        assertThat(event.targetId()).isEqualTo("u-4");
        assertThat(event.targetLabel()).isEqualTo("student04");
        assertThat(event.before()).isNull();
        assertThat(event.after()).isNull();
        assertThat(event.reason()).isNull();
        assertThat(event.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(event.errorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(event.actorId()).isNull();
        assertThat(event.actorUsername()).isNull();
        assertThat(event.actorRoles()).isNull();
        assertThat(event.actorType()).isNull();
    }
}
