package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolvePath;

class PreviewRegenerationTest {
    @Test
    void anOmittedOnlyPartDoesNotRequireAStoredSelection() {
        var document = new ExamStructureCodec(new ObjectMapper()).read("""
                {"parts":[{"id":"empty","sections":[{"id":"empty-section","subjectCode":"A","questionCount":0}]},
                          {"id":"changed","sections":[{"id":"active","subjectCode":"B","questionCount":1,
                           "blocks":[{"id":"g","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        var empty = new SectionOnPath("A", "empty-section", 0, null, List.of());
        var active = new SectionOnPath("B", "active", 1, null, List.of(GroupQuota.single("g", 1)));
        var base = new PreviewSnapshotStore.Snapshot(UUID.randomUUID(), null, 1, Map.of());

        var selected = PreviewRegeneration.prepare(document, new PreviewPartIndex(document),
                SolvePath.of(0, List.of(empty, active)), base, List.of("changed"));
        assertThat(selected.path().sections()).containsExactly(active);
        assertThat(selected.excluded()).isEmpty();
    }
}
