package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

/**
 * ADV:CR-4:21: the exam-level conservative rule looks for any stored node id, of any kind and depth, equal to the
 * "{optionId}:blocks" holder name of any option of the structure.
 */
class ExamStoredIdCollisionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExamStructureCodec codec = new ExamStructureCodec(mapper);

    /** Option "a-opt" (with or without direct blocks) plus the stored node under test: one id placed at {@code where}. */
    private ExamStructureDocument draft(String where, String id, boolean optionHasBlocks) {
        String direct = optionHasBlocks
                ? ",\"blocks\":[{\"id\":\"%s\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":1}]".formatted("direct".equals(where) ? id : "a-direct")
                : "";
        String partId = "part".equals(where) ? id : "p";
        String sharedId = "shared".equals(where) ? id : "shared-sec";
        String sharedBlockId = "shared-block".equals(where) ? id : "shared-blk";
        String poolId = "pool".equals(where) ? id : "pool-sec";
        String poolBlockId = "pool-block".equals(where) ? id : "pool-blk";
        String optionId = "option".equals(where) ? id : "b-opt";
        String sectionId = "option-section".equals(where) ? id : "b-sec";
        String sectionBlockId = "option-section-block".equals(where) ? id : "b-blk";
        String json = """
                {"parts":[{"id":"%s","order":1,"name":"P","questionCount":3,
                  "participation":{"mode":"CHOICE","choose":1},
                  "sections":[{"id":"%s","subjectCode":"LY","questionCount":1,"blocks":[{"id":"%s","questionType":"SINGLE_CHOICE","questionCount":1}]}],
                  "subjectPool":[{"id":"%s","subjectCode":"HOA","questionCount":1,"blocks":[{"id":"%s","questionType":"SINGLE_CHOICE","questionCount":1}]}],
                  "options":[
                    {"id":"a-opt","kind":"SUBJECT","code":"LY"%s},
                    {"id":"%s","kind":"SUBJECT","code":"LY","sections":[{"id":"%s","subjectCode":"LY","questionCount":1,
                      "blocks":[{"id":"%s","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}]}
                """.formatted(partId, sharedId, sharedBlockId, poolId, poolBlockId, direct, optionId, sectionId, sectionBlockId);
        return codec.read(json);
    }

    @ParameterizedTest(name = "{0} named like the holder, option with direct blocks = {1}")
    @CsvSource({
            "part,             true",
            "part,             false",
            "shared,           true",
            "shared,           false",
            "shared-block,     false",
            "pool,             false",
            "pool-block,       false",
            "option,           false",
            "option-section,   false",
            "option-section-block, false",
            "direct,           true"})
    void everyStoredNodeKindAtEveryDepthIsFound(String where, boolean optionHasBlocks) {
        assertThat(ExamStoredIdCollision.find(draft(where, "a-opt:blocks", optionHasBlocks)))
                .as("%s collides", where).hasValue("a-opt:blocks");
    }

    @Test
    void theHolderNameOfAnotherOptionCollidesToo() {
        // "b-opt:blocks" names the holder of option "b-opt", which has no direct blocks: still reserved.
        assertThat(ExamStoredIdCollision.find(draft("shared", "b-opt:blocks", false))).hasValue("b-opt:blocks");
    }

    @Test
    void distinctIdsAndHolderNamesOfNoOptionDoNotCollide() {
        assertThat(ExamStoredIdCollision.find(draft("shared", "shared-sec", true))).isEmpty();
        // "zzz:blocks" is the holder name of no option of the structure.
        assertThat(ExamStoredIdCollision.find(draft("shared", "zzz:blocks", true))).isEmpty();
        assertThat(ExamStoredIdCollision.find(draft("direct", "a-direct", true))).isEmpty();
    }

    @Test
    void aStructureWithoutOptionsHasNothingToCollideWith() {
        ExamStructureDocument document = codec.read("""
                {"parts":[{"id":"x:blocks","order":1,"name":"P","questionCount":1,
                  "sections":[{"id":"y:blocks","subjectCode":"LY","questionCount":1,
                    "blocks":[{"id":"z:blocks","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        assertThat(ExamStoredIdCollision.find(document)).isEmpty();
    }

    @Test
    void theIssueIsAnExamLevelBlockingStructureErrorCarryingTheStoredId() {
        var issue = ExamStoredIdCollision.issue(mapper, "a-opt:blocks");
        assertThat(issue.path("code").asText()).isEqualTo("STORED_ID_COLLISION");
        assertThat(issue.path("group").asText()).isEqualTo("STRUCTURE");
        assertThat(issue.path("severity").asText()).isEqualTo("ERROR");
        assertThat(issue.path("blockId").asText()).isEqualTo("a-opt:blocks");
        assertThat(issue.path("location").path("blockId").asText()).isEqualTo("a-opt:blocks");
        for (String field : new String[] {"partId", "sectionId", "optionId"}) {
            assertThat(issue.path(field).isNull()).as(field).isTrue();
        }
    }
}
