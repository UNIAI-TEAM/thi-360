package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckUnitStore;

@Transactional
class ExamCheckUnitStoreIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ExamCheckUnitStore store;

    @Test
    void savesAllSectionIdsAndRejectsStaleRevision() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) "
                + "VALUES (?,?,?,'Units','FULL','SHARED')", id, id.toString(), id.toString());
        var unit = new ExamCheckUnitStore.Unit("TOAN:s1,s2,s3", List.of("s1", "s2", "s3"), "hash", 7,
                "VALID", mapper.createArrayNode(), mapper.createArrayNode(), Instant.now(), 12);
        assertThat(store.save(id, 0, List.of(unit))).isTrue();
        var saved = store.load(id).get(unit.key());
        assertThat(saved.sectionIds()).containsExactly("s1", "s2", "s3");
        assertThat(saved.fingerprint()).isEqualTo("hash");
        assertThat(saved.sourceRevision()).isEqualTo(7);
        jdbc.update("UPDATE exam.exam SET revision=revision+1 WHERE id=?", id);
        var stale = new ExamCheckUnitStore.Unit(unit.key(), unit.sectionIds(), "stale", 8,
                "INVALID", mapper.createArrayNode(), null, Instant.now(), 10);
        assertThat(store.save(id, 0, List.of(stale))).isFalse();
        assertThat(store.load(id).get(unit.key()).fingerprint()).isEqualTo("hash");
    }
}
