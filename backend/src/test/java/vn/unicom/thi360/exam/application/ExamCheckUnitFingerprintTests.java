package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.exam.domain.ExamQuota;
import vn.unicom.thi360.exam.domain.ExamSection;

class ExamCheckUnitFingerprintTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExamCheckUnitFingerprint fingerprint = new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper));
    private final ExamCheckUnitCatalogSlice catalog = new ExamCheckUnitCatalogSlice(mock(CatalogLookup.class), mapper);
    private final ExamCheckFingerprint.Config config = new ExamCheckFingerprint.Config(null, "SUBJECT", null, null,
            null, null, null, false, null, null, null, null, null);

    @Test
    void sectionsAreOrderIndependentAndDisplayNamesDoNotInvalidate() throws Exception {
        var first = section("a", "Original", 2);
        var renamed = section("a", "Renamed", 2);
        var second = section("b", "Second", 3);
        var before = fingerprint.calculate("TOAN", List.of(first, second), List.of(), config, 1L, catalog, List.of());
        var reordered = fingerprint.calculate("TOAN", List.of(second, renamed), List.of(), config, 1L, catalog, List.of());
        assertThat(reordered).isEqualTo(before);
        assertThat(before.sectionIds()).containsExactly("a", "b");
        var changed = fingerprint.calculate("TOAN", List.of(section("a", "Original", 4), second),
                List.of(), config, 1L, catalog, List.of());
        assertThat(changed.key()).isEqualTo(before.key());
        assertThat(changed.fingerprint()).isNotEqualTo(before.fingerprint());
    }

    @Test
    void onlyRequiredQuotasForTheSubjectChangeItsFingerprint() throws Exception {
        var section = section("a", "A", 2);
        var empty = fingerprint.calculate("TOAN", List.of(section), List.of(), config, 1L, catalog, List.of());
        var other = quota("LY", "REQUIRED");
        var reference = quota("TOAN", "REFERENCE");
        assertThat(fingerprint.calculate("TOAN", List.of(section), List.of(other, reference), config, 1L, catalog, List.of()))
                .isEqualTo(empty);
        var required = fingerprint.calculate("TOAN", List.of(section), List.of(quota("TOAN", "REQUIRED")), config, 1L, catalog, List.of());
        assertThat(required.key()).isEqualTo(empty.key());
        assertThat(required.fingerprint()).isNotEqualTo(empty.fingerprint());
    }

    @Test
    void subjectChoiceResolvedCountChangesItsFingerprint() {
        var section = new ExamSection("science", "TOAN", null, null, null, null, null, List.of());
        var seventeen = new vn.unicom.thi360.exam.domain.solver.SectionOnPath("TOAN", "science", 17, null, List.of());
        var eighteen = new vn.unicom.thi360.exam.domain.solver.SectionOnPath("TOAN", "science", 18, null, List.of());
        var before = fingerprint.calculate("TOAN", List.of(section), List.of(), config, 1L, catalog, List.of(seventeen));
        var after = fingerprint.calculate("TOAN", List.of(section), List.of(), config, 1L, catalog, List.of(eighteen));
        assertThat(after.key()).isEqualTo(before.key());
        assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
    }

    @Test
    void mapidRuleDescendantChangeInvalidatesOnlyAffectedUnit() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        CatalogItemView root = knowledgeItem(groupId, nodeId, "N1", null, (short) 0, "/N1/");
        CatalogItemView child = knowledgeItem(groupId, childId, "N1_1", nodeId, (short) 1, "/N1/N1_1/");
        CatalogItemView other = knowledgeItem(groupId, otherId, "N2", null, (short) 0, "/N2/");

        CatalogLookup lookup = mock(CatalogLookup.class);
        when(lookup.listActiveGroupItems("KNOWLEDGE")).thenReturn(List.of(root, other), List.of(root, child, other));

        ExamSection affected = sectionWithMapidRule("sec-a", "TOAN", nodeId);
        ExamSection unrelated = sectionWithMapidRule("sec-b", "LY", otherId);

        var beforeSlice = new ExamCheckUnitCatalogSlice(lookup, mapper);
        var beforeAffected = fingerprint.calculate("TOAN", List.of(affected), List.of(), config, 1L, beforeSlice, List.of());
        var beforeUnrelated = fingerprint.calculate("LY", List.of(unrelated), List.of(), config, 1L, beforeSlice, List.of());

        var afterSlice = new ExamCheckUnitCatalogSlice(lookup, mapper);
        var afterAffected = fingerprint.calculate("TOAN", List.of(affected), List.of(), config, 1L, afterSlice, List.of());
        var afterUnrelated = fingerprint.calculate("LY", List.of(unrelated), List.of(), config, 1L, afterSlice, List.of());

        assertThat(afterAffected.key()).isEqualTo(beforeAffected.key());
        assertThat(afterAffected.fingerprint()).isNotEqualTo(beforeAffected.fingerprint());
        assertThat(afterUnrelated).isEqualTo(beforeUnrelated);
    }

    private CatalogItemView knowledgeItem(UUID groupId, UUID id, String code, UUID parentId, short depth, String path) {
        return new CatalogItemView(id, groupId, "KNOWLEDGE", code, code.toLowerCase(java.util.Locale.ROOT), 0,
                code, code, CatalogStatus.ACTIVE, CatalogStatus.ACTIVE, false, parentId, depth, path);
    }

    private ExamSection sectionWithMapidRule(String id, String subject, UUID nodeId) throws Exception {
        return mapper.readValue("""
                {"id":"%s","subjectCode":"%s","questionCount":2,"mapidMode":"MATRIX",
                 "mapidRules":[{"id":"r1","type":"COUNT","nodeIds":["%s"],"comparator":"EXACT","count":1}],
                 "blocks":[{"id":"block-%s","name":"B","questionType":"SINGLE_CHOICE","questionCount":2}]}
                """.formatted(id, subject, nodeId, id), ExamSection.class);
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
