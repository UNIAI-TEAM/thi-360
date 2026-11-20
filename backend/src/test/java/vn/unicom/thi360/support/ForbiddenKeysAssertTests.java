package vn.unicom.thi360.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.unicom.thi360.exam.domain.QuestionBlock;

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
        // LT 7.3: đáp án đúng, answers, pairs, điểm/đáp án từng ý Đ/S, lời giải,
        // gradingGuide, rubric, sampleAnswer, commonMistakes, MAPID, độ khó.
        assertThat(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_KEYS).containsExactlyInAnyOrder(
                "correct", "answers", "pairs", "scoreTable",
                "solution", "gradingGuide", "rubric", "sampleAnswer",
                "commonMistakes", "knowledge", "knowledgeItemIds",
                "mapId", "mapIds", "mapidIds", "trueFalseTable", "levelItemId", "difficulty", "difficultyCode");
        assertThat(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_KEYS).doesNotContain("answer");
        assertThat(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_SCOPED_KEYS)
                .containsExactlyInAnyOrder("statements[].points", "statements[].answer");
        // Khóa được phép (LT 7.3) không nằm trong danh sách cấm.
        assertThat(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_KEYS)
                .doesNotContain("points", "inputHint", "wordLimit");

        // TM 10.1: danh sách bảng chiếu chứa mọi khóa lượt thi và thêm answer + khóa của
        // id/nội dung câu, nguồn câu của khối, đề chứng minh, đề xem trước.
        assertThat(ForbiddenKeysAssert.STOREFRONT_FORBIDDEN_KEYS)
                .containsAll(ForbiddenKeysAssert.ATTEMPT_FORBIDDEN_KEYS)
                .contains(
                        "answer",
                        "questionId", "questionVersionId", "question", "questions", "body",
                        "snapshot", "source", "sourceCode", "sourceRevision",
                        "knowledgeScope", "matrix", "topicQuotas", "tagCodes", "slots", "childConditions",
                        "itemShape", "answerFormats", "requiredPointsPerQuestion",
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
    void emptyObjectAndArrayPass() {
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys("{}")).doesNotThrowAnyException();
        assertThatCode(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys("[]")).doesNotThrowAnyException();
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(" [ ] \n")).doesNotThrowAnyException();
    }

    // R1-01: mỗi trường nhạy cảm là vi phạm duy nhất trong tài liệu, phải bị báo đúng đường dẫn của nó.
    static Stream<Arguments> attemptFieldsAlone() {
        return Stream.of(
                Arguments.of("""
                        {"items": [{"id": "q1", "type": "TRUE_FALSE", "points": 4, "statements": [
                          {"id": "s1", "content": "<p>a</p>", "points": 1}, {"id": "s2", "content": "<p>b</p>"}]}]}
                        """, "$.items[0].statements[0].points"),
                Arguments.of("""
                        {"items": [{"id": "q1", "points": 1, "mapidIds": ["3f6c2d3e-0b2f-4c2d-8c5e-0a1b2c3d4e5f"]}]}
                        """, "$.items[0].mapidIds"),
                Arguments.of("""
                        {"items": [{"id": "q1", "points": 4, "scoring": {"trueFalseTable": [0, 1, 2, 4]}}]}
                        """, "$.items[0].scoring.trueFalseTable"),
                Arguments.of("""
                        {"items": [{"id": "q1", "statements": [{"id": "s1", "answer": true}]}]}
                        """, "$.items[0].statements[0].answer"),
                Arguments.of("""
                        {"items": [{"id": "q1", "scoreTable": [0, 1]}]}
                        """, "$.items[0].scoreTable"));
    }

    @ParameterizedTest
    @MethodSource("attemptFieldsAlone")
    void attemptListFlagsEachSensitiveFieldAtItsOwnPath(String json, String expectedPath) {
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(expectedPath);
        // Danh sách bảng chiếu chứa danh sách lượt thi nên cũng báo.
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(json))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(expectedPath);
    }

    @Test
    void questionLevelPointsAndStatementTextPass() {
        String trueFalse = """
                {"items": [
                  {"id": "q1", "type": "TRUE_FALSE", "points": 4, "inputHint": "chọn Đúng hoặc Sai", "statements": [
                    {"id": "s1", "content": "<p>Mệnh đề 1</p>"},
                    {"id": "s2", "content": "<p>Mệnh đề 2</p>"}]},
                  {"id": "q2", "type": "ESSAY", "points": 2.5, "wordLimit": 200},
                  {"id": "q3", "type": "QUESTION_SET", "points": 3, "children": [{"id": "c1", "points": 1.5}]}
                ]}
                """;
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(trueFalse)).doesNotThrowAnyException();
        assertThatCode(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(trueFalse))
                .doesNotThrowAnyException();
    }

    @Test
    void statementPointsRuleIsScopedToStatementArrayElements() {
        // "statements" không phải mảng, hoặc points nằm sâu hơn một cấp: không phải điểm từng ý.
        String json = """
                {"statements": {"points": 1}, "detail": {"statements": [[{"points": 1}], {"meta": {"points": 2}}]},
                 "points": 5}
                """;
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json)).doesNotThrowAnyException();
    }

    // R1-02: ràng buộc nguồn câu của khối là vi phạm duy nhất của khối đó.
    static Stream<Arguments> blockSourceFieldsAlone() {
        return Stream.of(
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1", "questionCount": 5,
                          "knowledgeScope": {"mode": "ALL"}}]}}}
                        """, "$.product.detail.blocks[0].knowledgeScope"),
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1", "tagCodes": ["TAG_1"]}]}}}
                        """, "$.product.detail.blocks[0].tagCodes"),
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1", "slots": [{"index": 1}]}]}}}
                        """, "$.product.detail.blocks[0].slots"),
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1", "childConditions": [{"from": 1}]}]}}}
                        """, "$.product.detail.blocks[0].childConditions"),
                // R2-01: hạn ngạch chọn câu của khối; mỗi ca chỉ có đúng một khóa cấm.
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1", "topicQuotas": [
                          {"id": "tq1", "nodeIds": ["3f6c2d3e-0b2f-4c2d-8c5e-0a1b2c3d4e5f"], "count": 3}]}]}}}
                        """, "$.product.detail.blocks[0].topicQuotas"),
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1", "matrix": {
                          "byGrade": [{"value": "G10", "count": 5}, {"value": "G11", "count": 5}]}}]}}}
                        """, "$.product.detail.blocks[0].matrix"),
                // R3-01: các bộ lọc ứng viên còn lại của khối (2B 6.1 -> QuestionSourcePort.Filter);
                // mỗi ca chỉ có đúng một khóa cấm.
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1", "itemShape": {
                          "statements": 4, "leftItems": 4, "rightItems": 6}}]}}}
                        """, "$.product.detail.blocks[0].itemShape"),
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1",
                          "answerFormats": ["PLAIN_TEXT", "NUMBER"]}]}}}
                        """, "$.product.detail.blocks[0].answerFormats"),
                Arguments.of("""
                        {"product": {"detail": {"blocks": [{"name": "Khối 1",
                          "requiredPointsPerQuestion": 0.25}]}}}
                        """, "$.product.detail.blocks[0].requiredPointsPerQuestion"));
    }

    @ParameterizedTest
    @MethodSource("blockSourceFieldsAlone")
    void storefrontListFlagsBlockSourceFieldsAlone(String json, String expectedPath) {
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(json))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(expectedPath);
    }

    // R2-01: mọi trường của QuestionBlock phải được xếp: hoặc là nguồn câu bị cấm, hoặc được phép kèm lý do.
    @Test
    void everyQuestionBlockFieldIsEitherForbiddenSourceOrAllowedWithAReason() {
        List<String> forbiddenSourceFields = List.of(
                "knowledgeScope", "matrix", "topicQuotas", "tagCodes", "slots", "childConditions",
                "itemShape", "answerFormats", "requiredPointsPerQuestion");
        List<String> components = Stream.of(QuestionBlock.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(ForbiddenKeysAssert.STOREFRONT_FORBIDDEN_KEYS).containsAll(forbiddenSourceFields);
        assertThat(ForbiddenKeysAssert.STOREFRONT_ALLOWED_BLOCK_FIELDS.keySet())
                .doesNotContainAnyElementsOf(forbiddenSourceFields);
        assertThat(ForbiddenKeysAssert.STOREFRONT_ALLOWED_BLOCK_FIELDS.values())
                .allSatisfy(reason -> assertThat(reason).isNotBlank());
        // Cả hai phía phủ đúng toàn bộ trường: không sót, không thừa trường không còn trong record.
        assertThat(components).containsExactlyInAnyOrderElementsOf(
                Stream.concat(forbiddenSourceFields.stream(),
                        ForbiddenKeysAssert.STOREFRONT_ALLOWED_BLOCK_FIELDS.keySet().stream()).toList());
        // Khóa được phép không bị danh sách cấm chặn nhầm.
        assertThat(ForbiddenKeysAssert.STOREFRONT_FORBIDDEN_KEYS)
                .doesNotContainAnyElementsOf(ForbiddenKeysAssert.STOREFRONT_ALLOWED_BLOCK_FIELDS.keySet());
    }

    @Test
    void storefrontPublicDetailPartsShapePasses() {
        // TM 10.1: các phần theo thứ tự làm bài, các lựa chọn trong phạm vi mua, cách chấm, quyền lợi.
        // R3-01: mô tả dẫn xuất (số mệnh đề, điểm mỗi câu của từng dạng) phải đặt khóa không trùng
        // tên trường nguồn của khối (itemShape, answerFormats, requiredPointsPerQuestion).
        String json = """
                {"product": {"code": "HSA_FULL", "name": "HSA", "listPrice": 99000, "detail": {
                  "description": "<p>Mô tả</p>", "objectives": ["a"], "audience": "học sinh",
                  "parts": [
                    {"name": "Tư duy định lượng", "order": 1, "questionCount": 50, "durationMinutes": 75,
                     "maxPoints": 50, "required": true},
                    {"name": "Phần 3", "order": 3, "questionCount": 50, "durationMinutes": 60, "maxPoints": 50,
                     "required": false, "options": [{"name": "Khoa học", "subjects": ["Vật lí", "Hóa học"]}]}],
                  "scoring": {"scale": "LINEAR", "byType": [{"type": "MCQ", "count": 40},
                    {"type": "TRUE_FALSE", "count": 10, "statementCount": 4, "pointsPerItem": 0.25}]},
                  "benefits": ["a"], "resultView": "AFTER_SUBMIT"}}}
                """;
        assertThatCode(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(json)).doesNotThrowAnyException();
    }

    // R1-03, R1-07, R1-10: bản chuỗi chỉ nhận một tài liệu JSON trọn vẹn và không lộ nội dung.
    private static final String SENTINEL = "SENTINEL-4f1c9a7e-do-not-leak";

    static Stream<Arguments> unusableDocuments() {
        return Stream.of(
                Arguments.of("blank", ""),
                Arguments.of("whitespace", " \n\t "),
                Arguments.of("null literal", "null"),
                Arguments.of("scalar", "\"" + SENTINEL + "\""),
                Arguments.of("malformed value", "{\"a\": " + SENTINEL + "}"),
                Arguments.of("truncated", "{\"items\": [{\"id\": \"" + SENTINEL),
                Arguments.of("trailing object", "{} {\"answer\": \"" + SENTINEL + "\"}"),
                Arguments.of("trailing garbage", "{}" + SENTINEL),
                Arguments.of("trailing after array", "[] []"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusableDocuments")
    void stringOverloadsRejectUnusableDocumentsWithoutEchoingContent(String label, String json) {
        for (Runnable call : List.<Runnable>of(
                () -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json),
                () -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(json))) {
            Throwable thrown = catchThrowable(call::run);
            assertThat(thrown).as(label).isNotNull();
            assertThat(renderWithCauses(thrown)).doesNotContain(SENTINEL);
            assertThat(thrown.getCause()).as("no payload-bearing cause").isNull();
            assertThat(renderWithCauses(thrown)).as("not mistaken for duplicate keys")
                    .doesNotContain("duplicate object keys");
        }
    }

    // R2-03: khóa trùng là khóa trung tính (hoặc cha trung tính che khóa cấm ở con), nên mọi bản phân
    // tích cho phép khóa trùng đều thấy tài liệu sạch: chỉ STRICT_DUPLICATE_DETECTION mới làm ca đỏ.
    static Stream<Arguments> neutralDuplicateKeyDocuments() {
        return Stream.of(
                Arguments.of("neutral root key", "{\"name\": \"a\", \"name\": \"b\"}"),
                Arguments.of("neutral nested key", "{\"product\": {\"code\": \"x\", \"code\": \"y\"}}"),
                Arguments.of("neutral parent overwrites a subtree holding solution",
                        "{\"detail\": {\"solution\": \"" + SENTINEL + "\"}, \"detail\": {\"note\": \"n\"}}"),
                Arguments.of("neutral parent overwritten by null",
                        "{\"detail\": {\"correct\": \"" + SENTINEL + "\"}, \"detail\": null}"),
                Arguments.of("neutral parent inside an array element",
                        "{\"items\": [{\"meta\": {\"answer\": \"" + SENTINEL + "\", \"points\": 1},"
                                + " \"meta\": {\"ok\": true}}]}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("neutralDuplicateKeyDocuments")
    void duplicateNeutralKeysAreRejectedAsDuplicatesEvenWhenTheOverwrittenSubtreeWasForbidden(
            String label, String json) throws Exception {
        // Điều kiện của ca: bản phân tích mặc định (khóa sau thắng) cho cây sạch, nên cả hai danh sách đạt.
        JsonNode lastWins = MAPPER.readTree(json);
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(lastWins)).doesNotThrowAnyException();
        assertThatCode(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(lastWins))
                .doesNotThrowAnyException();

        for (Runnable call : List.<Runnable>of(
                () -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json),
                () -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(json))) {
            Throwable thrown = catchThrowable(call::run);
            assertThat(thrown).as(label).isInstanceOf(AssertionError.class).hasMessageContaining("duplicate object keys");
            assertThat(renderWithCauses(thrown)).doesNotContain(SENTINEL);
            assertThat(thrown.getCause()).as("no payload-bearing cause").isNull();
        }
    }

    @Test
    void stringOverloadRejectsNull() {
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys((String) null))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void nodeOverloadsRejectAbsentOrScalarDocuments() throws Exception {
        for (JsonNode node : new JsonNode[] {
                null, MAPPER.readTree(""), MAPPER.readTree("null"), MAPPER.readTree("7")}) {
            assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(node))
                    .isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(node))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void forbiddenKeyInValidDocumentFailsWithoutLeakingValueThroughStringOverload() {
        String json = "{\"items\": [{\"statements\": [{\"id\": \"s1\", \"answer\": \"" + SENTINEL + "\"}]}]}";

        Throwable thrown = catchThrowable(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(json));

        assertThat(thrown).isInstanceOf(AssertionError.class).hasMessageContaining("$.items[0].statements[0].answer");
        assertThat(renderWithCauses(thrown)).doesNotContain(SENTINEL);
    }

    @Test
    void learnerAnswerPassesAttemptListAndFailsStorefrontWhileStatementAnswerFailsBoth() {
        String learnerAnswerJson = "{\"items\":[{\"id\":\"i1\",\"answer\":{\"optionId\":\"o1\"}}]}";
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(learnerAnswerJson))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(learnerAnswerJson))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.items[0].answer")
                .hasMessageNotContaining("o1");

        String statementAnswerJson = "{\"items\":[{\"statements\":[{\"id\":\"s1\",\"answer\":true}]}]}";
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(statementAnswerJson))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.items[0].statements[0].answer")
                .hasMessageNotContaining("true");
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(statementAnswerJson))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.items[0].statements[0].answer")
                .hasMessageNotContaining("true");

        String statementSentinelJson = "{\"items\":[{\"statements\":[{\"id\":\"s1\",\"answer\":\"" + SENTINEL + "\"}]}]}";
        for (Runnable call : List.<Runnable>of(
                () -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(statementSentinelJson),
                () -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys(statementSentinelJson))) {
            Throwable thrown = catchThrowable(call::run);
            assertThat(thrown).isInstanceOf(AssertionError.class)
                    .hasMessageContaining("$.items[0].statements[0].answer");
            assertThat(renderWithCauses(thrown)).doesNotContain(SENTINEL);
        }
    }

    @Test
    void matchingAnswerPairsAllowedOnlyDirectlyUnderAnswerInAttemptAndAlwaysForbiddenInStorefront() {
        String validAttemptState = """
                {
                  "attemptId": "11111111-1111-1111-1111-111111111111",
                  "parts": [{
                    "items": [{
                      "itemId": "22222222-2222-2222-2222-222222222222",
                      "questionType": "MATCHING",
                      "answer": {"type": "MATCHING", "pairs": {"l1": "r1", "l2": null}},
                      "questionBody": {"type": "MATCHING", "left": [{"id": "l1"}], "right": [{"id": "r1"}]}
                    }]
                  }]
                }
                """;
        assertThatCode(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(validAttemptState))
                .doesNotThrowAnyException();

        // Storefront still rejects answer and answer.pairs
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoStorefrontForbiddenKeys("{\"answer\":{\"pairs\":{\"l1\":\"r1\"}}}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.answer")
                .hasMessageContaining("$.answer.pairs")
                .hasMessageNotContaining("r1");

        // All other locations of "pairs" in attempt payloads remain strictly forbidden
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys("{\"pairs\":{\"l1\":\"r1\"}}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.pairs");
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys("{\"body\":{\"pairs\":[{\"leftId\":\"l1\"}]}}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.body.pairs");
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys("{\"items\":[{\"pairs\":{\"l1\":\"r1\"}}]}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.items[0].pairs");
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys("{\"answer\":[{\"pairs\":{\"l1\":\"r1\"}}]}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.answer[0].pairs");
        assertThatThrownBy(() -> ForbiddenKeysAssert.assertNoAttemptForbiddenKeys("{\"answer\":{\"nested\":{\"pairs\":{\"l1\":\"r1\"}}}}"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("$.answer.nested.pairs");
    }

    /** Toàn bộ thông báo, nguyên nhân và stack trace như JUnit/CI sẽ in. */
    private static String renderWithCauses(Throwable thrown) {
        StringWriter out = new StringWriter();
        thrown.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
