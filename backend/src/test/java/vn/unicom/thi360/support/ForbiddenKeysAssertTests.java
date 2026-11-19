package vn.unicom.thi360.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ForbiddenKeysAssertTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SECRET_VALUE = "9f8e7d6c-secret-answer";

    @Test
    void reportsNestedPathWithoutValue() throws Exception {
        String json = """
                {
                  "attempt": {
                    "parts": [
                      {"items": [{"id": "q1"}, {"id": "q2"}]},
                      {"items": [{"id": "q3", "options": [
                        {"id": "a", "label": "A"},
                        {"id": "b", "label": "B", "correct": "%s"}
                      ], "solution": {"text": "%s"}}]}
                    ]
                  }
                }
                """.formatted(SECRET_VALUE, SECRET_VALUE);

        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(MAPPER.readTree(json)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.attempt.parts[1].items[0].options[1].correct")
                .hasMessageContaining("$.attempt.parts[1].items[0].solution")
                .hasMessageNotContaining(SECRET_VALUE);
    }

    @Test
    void listsMatchSpecAndAllowedKeysPass() throws Exception {
        // LT 7.3: đáp án đúng, answers, pairs, điểm từng ý Đ/S, lời giải,
        // gradingGuide, rubric, sampleAnswer, commonMistakes, MAPID, độ khó.
        assertThat(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_KEYS).containsExactlyInAnyOrder(
                "correct", "answer", "answers", "pairs", "scoreTable",
                "solution", "gradingGuide", "rubric", "sampleAnswer",
                "commonMistakes", "knowledge", "knowledgeItemIds",
                "mapId", "mapIds", "levelItemId", "difficulty", "difficultyCode");
        // Khóa được phép (LT 7.3) không nằm trong danh sách cấm.
        assertThat(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_KEYS)
                .doesNotContain("points", "inputHint", "wordLimit");

        // TM 10.1: danh sách bảng chiếu chứa mọi khóa lượt thi và thêm khóa của
        // id/nội dung câu, nguồn câu của khối, đề chứng minh, đề xem trước.
        assertThat(ForbiddenKeysAssert.STOREFRONT_FORBIDDEN_KEYS)
                .containsAll(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_KEYS)
                .contains(
                        "questionId", "questionVersionId", "question", "questions", "body",
                        "snapshot", "source", "sourceCode", "sourceRevision",
                        "evidence", "witness", "witnessPicks",
                        "preview", "previewId", "previewPaper");

        // JSON sạch thì đạt: mọi khóa được phép lồng ở cấp sâu đều không báo.
        String clean = """
                {
                  "items": [
                    {"id": "q1", "content": "<p>2+2?</p>", "points": 2, "inputHint": "nhập số"},
                    {"type": "ESSAY", "wordLimit": 300, "points": 10}
                  ],
                  "nested": [{"points": [{"points": 1.5}]}]
                }
                """;
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(MAPPER.readTree(clean)))
                .doesNotThrowAnyException();
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(clean))
                .doesNotThrowAnyException();

        String cleanStorefront = """
                {
                  "products": [
                    {"code": "HSA_FULL", "name": "HSA", "listPrice": 99000, "detail": {"parts": [{"name": "Toán"}]}}
                  ]
                }
                """;
        assertThatCode(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(MAPPER.readTree(cleanStorefront)))
                .doesNotThrowAnyException();
    }

    @Test
    void storefrontListFlagsQuestionPayloadAttemptListDoesNot() throws Exception {
        String json = """
                {"product": {"detail": {"body": {"statement": "<p>x</p>"}, "questionId": "123e4567-e89b-42d3-a456-426614174000", "source": {"bank": "S-1"}, "previewPaper": {"id": "p1"}, "evidence": {"refs": []}}}}
                """;

        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(MAPPER.readTree(json)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(MAPPER.readTree(json)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.product.detail.body")
                .hasMessageContaining("$.product.detail.questionId")
                .hasMessageContaining("$.product.detail.source")
                .hasMessageContaining("$.product.detail.previewPaper")
                .hasMessageContaining("$.product.detail.evidence")
                .hasMessageNotContaining("426614174000")
                .hasMessageNotContaining("S-1");
    }

    @Test
    void scansAllArrayElementsAndReportsSortedPaths() throws Exception {
        String json = """
                [{"ok": true}, {"rubric": [{"points": 1}]}, {"deep": [{"gradingGuide": "x"}]}]
                """;

        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(MAPPER.readTree(json)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$[1].rubric")
                .hasMessageContaining("$[2].deep[0].gradingGuide");
    }

    @Test
    void emptyDocumentPasses() throws Exception {
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(MAPPER.readTree("{}")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(MAPPER.readTree("[]")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys((com.fasterxml.jackson.databind.JsonNode) null))
                .doesNotThrowAnyException();
    }
}
