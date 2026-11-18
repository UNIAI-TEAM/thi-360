package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.ExamQuota;
import vn.unicom.thi360.exam.domain.ExamSection;

class ExamCheckUnitFingerprintTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExamCheckUnitFingerprint fingerprint = new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper));
    private final ExamCheckFingerprint.Config config = new ExamCheckFingerprint.Config(null, "SUBJECT", null, null,
            null, null, null, false, null, null, null, null, null);

    @Test
    void sectionsAreOrderIndependentAndDisplayNamesDoNotInvalidate() throws Exception {
        var first = section("a", "Original", 2);
        var renamed = section("a", "Renamed", 2);
        var second = section("b", "Second", 3);
        var before = fingerprint.calculate("TOAN", List.of(first, second), List.of(), config, 1L, 2L);
        var reordered = fingerprint.calculate("TOAN", List.of(second, renamed), List.of(), config, 1L, 2L);
        assertThat(reordered).isEqualTo(before);
        assertThat(before.sectionIds()).containsExactly("a", "b");
        var changed = fingerprint.calculate("TOAN", List.of(section("a", "Original", 4), second),
                List.of(), config, 1L, 2L);
        assertThat(changed.key()).isEqualTo(before.key());
        assertThat(changed.fingerprint()).isNotEqualTo(before.fingerprint());
    }

    @Test
    void onlyRequiredQuotasForTheSubjectChangeItsFingerprint() throws Exception {
        var section = section("a", "A", 2);
        var empty = fingerprint.calculate("TOAN", List.of(section), List.of(), config, 1L, 2L);
        var other = quota("LY", "REQUIRED");
        var reference = quota("TOAN", "REFERENCE");
        assertThat(fingerprint.calculate("TOAN", List.of(section), List.of(other, reference), config, 1L, 2L))
                .isEqualTo(empty);
        var required = fingerprint.calculate("TOAN", List.of(section), List.of(quota("TOAN", "REQUIRED")), config, 1L, 2L);
        assertThat(required.key()).isEqualTo(empty.key());
        assertThat(required.fingerprint()).isNotEqualTo(empty.fingerprint());
    }

    @Test
    void subjectChoiceResolvedCountChangesItsFingerprint() {
        var section = new ExamSection("science", "TOAN", null, null, null, null, null, List.of());
        var seventeen = new vn.unicom.thi360.exam.domain.solver.SectionOnPath("TOAN", "science", 17, null, List.of());
        var eighteen = new vn.unicom.thi360.exam.domain.solver.SectionOnPath("TOAN", "science", 18, null, List.of());
        var before = fingerprint.calculate("TOAN", List.of(section), List.of(), config, 1L, 2L, List.of(seventeen));
        var after = fingerprint.calculate("TOAN", List.of(section), List.of(), config, 1L, 2L, List.of(eighteen));
        assertThat(after.key()).isEqualTo(before.key());
        assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
    }

    private ExamSection section(String id, String name, int count) throws Exception {
        return mapper.readValue("""
                {"id":"%s","subjectCode":"TOAN","questionCount":%d,"blocks":[
                  {"id":"block-%s","name":"%s","questionType":"SINGLE_CHOICE","questionCount":%d}]}
                """.formatted(id, count, id, name, count), ExamSection.class);
    }

    private ExamQuota quota(String subject, String enforcement) throws Exception {
        return mapper.readValue("""
                {"id":"q","name":"Quota","dimension":"GRADE","buckets":[{"codes":["G12"],"count":1}],
                 "basis":"QUESTION","enforcement":"%s","subjectCodes":["%s"]}
                """.formatted(enforcement, subject), ExamQuota.class);
    }
}
