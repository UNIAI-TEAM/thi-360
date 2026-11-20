package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledItem;
import vn.unicom.thi360.attempt.application.AttemptPaperAssembler.AssembledPart;
import vn.unicom.thi360.attempt.application.AttemptQuestionSanitizer;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedGroup;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedQuestion;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

/** Unit tests for {@link AttemptPaperAssembler} and {@link AttemptQuestionSanitizer} (AC-16, AC-17, AC-18, AC-24, AC-25, AC-27). */
class AttemptPaperAssemblerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AttemptPaperAssembler assembler = new AttemptPaperAssembler();
    private final AttemptQuestionSanitizer sanitizer = new AttemptQuestionSanitizer(mapper);

    @Test
    void sameSeedSameOrder() {
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        UUID q4 = UUID.randomUUID();
        Map<VersionKey, Version> versions = Map.of(
                key(q1), singleChoiceVersion(q1, true, new BigDecimal("1.0")),
                key(q2), singleChoiceVersion(q2, true, new BigDecimal("1.0")),
                key(q3), multipleChoiceVersion(q3, true, new BigDecimal("1.0")),
                key(q4), matchingVersion(q4, new BigDecimal("1.0")));
        GeneratedPart part = new GeneratedPart("P1", List.of(
                new GeneratedSection("S1", "TOAN", 4, List.of(
                        new GeneratedGroup("G1", List.of(gq(q1), gq(q2), gq(q3), gq(q4)))))));

        AssembledPart first = assembler.assemble(part, versions, Map.of("G1", true), 123456L, 1);
        AssembledPart second = assembler.assemble(part, versions, Map.of("G1", true), 123456L, 1);

        assertThat(first).isEqualTo(second);
        assertThat(first.items()).extracting(AssembledItem::questionId)
                .containsExactlyElementsOf(second.items().stream().map(AssembledItem::questionId).toList());
        assertThat(first.items()).extracting(AssembledItem::optionOrder)
                .containsExactlyElementsOf(second.items().stream().map(AssembledItem::optionOrder).toList());
    }

    @Test
    void setsStayContiguous() {
        UUID standalone = UUID.randomUUID();
        UUID setRoot = UUID.randomUUID();
        UUID child1 = UUID.randomUUID();
        UUID child2 = UUID.randomUUID();
        UUID child3 = UUID.randomUUID();
        Map<VersionKey, Version> versions = Map.of(
                key(standalone), singleChoiceVersion(standalone, true, BigDecimal.ONE),
                key(setRoot), setVersion(setRoot, List.of(
                        childJson(child1, true, BigDecimal.ONE),
                        childJson(child2, true, BigDecimal.ONE),
                        childJson(child3, true, BigDecimal.ONE))));
        GeneratedPart part = new GeneratedPart("P1", List.of(
                new GeneratedSection("S1", "NGU_VAN", 4, List.of(
                        new GeneratedGroup("G1", List.of(gq(standalone), gq(setRoot)))))));

        // Test several seeds so set appears before and after standalone
        for (long seed : List.of(1L, 2L, 7L, 42L, 99L)) {
            AssembledPart assembled = assembler.assemble(part, versions, Map.of("G1", true), seed, 1);
            List<AssembledItem> items = assembled.items();
            int rootIdx = indexOf(items, setRoot);
            assertThat(items.get(rootIdx).questionType()).isEqualTo(QuestionType.QUESTION_SET);
            assertThat(items.get(rootIdx).displayNo()).isNull();
            assertThat(items.get(rootIdx).unitCount()).isZero();
            assertThat(items.get(rootIdx + 1).questionId()).isEqualTo(child1);
            assertThat(items.get(rootIdx + 2).questionId()).isEqualTo(child2);
            assertThat(items.get(rootIdx + 3).questionId()).isEqualTo(child3);
            assertThat(items.get(rootIdx + 1).parentIndex()).isEqualTo(rootIdx);
            assertThat(items.get(rootIdx + 2).parentIndex()).isEqualTo(rootIdx);
            assertThat(items.get(rootIdx + 3).parentIndex()).isEqualTo(rootIdx);
        }
    }

    @Test
    void unshuffledGroupKeepsOrder() {
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        UUID q4 = UUID.randomUUID();
        Map<VersionKey, Version> versions = Map.of(
                key(q1), singleChoiceVersion(q1, false, BigDecimal.ONE),
                key(q2), singleChoiceVersion(q2, false, BigDecimal.ONE),
                key(q3), singleChoiceVersion(q3, false, BigDecimal.ONE),
                key(q4), singleChoiceVersion(q4, false, BigDecimal.ONE));
        GeneratedPart part = new GeneratedPart("P1", List.of(
                new GeneratedSection("S1", "NGU_VAN", 4, List.of(
                        new GeneratedGroup("G_KEEP", List.of(gq(q1), gq(q2), gq(q3), gq(q4)))))));

        AssembledPart assembled = assembler.assemble(part, versions, Map.of("G_KEEP", false), 98765L, 1);

        assertThat(assembled.items()).extracting(AssembledItem::questionId)
                .containsExactly(q1, q2, q3, q4);
    }

    @Test
    void keepOptionOrderFlagIsHonoured() {
        UUID scKeep = UUID.randomUUID();
        UUID mcKeep = UUID.randomUUID();
        UUID setRoot = UUID.randomUUID();
        UUID childKeep = UUID.randomUUID();
        UUID otherSc = UUID.randomUUID();

        Map<VersionKey, Version> versionsKept = Map.of(
                key(scKeep), singleChoiceVersion(scKeep, false, BigDecimal.ONE),
                key(mcKeep), multipleChoiceVersion(mcKeep, false, BigDecimal.ONE),
                key(setRoot), setVersion(setRoot, List.of(
                        childJson(childKeep, false, BigDecimal.ONE),
                        childJson(UUID.randomUUID(), true, BigDecimal.ONE))),
                key(otherSc), singleChoiceVersion(otherSc, true, BigDecimal.ONE));

        Map<VersionKey, Version> versionsShuffled = Map.of(
                key(scKeep), singleChoiceVersion(scKeep, true, BigDecimal.ONE),
                key(mcKeep), multipleChoiceVersion(mcKeep, false, BigDecimal.ONE),
                key(setRoot), setVersion(setRoot, List.of(
                        childJson(childKeep, false, BigDecimal.ONE),
                        childJson(versionsKept.get(key(setRoot)).snapshot().path("body").path("children").get(1).path("id").asText() != null
                                ? UUID.fromString(versionsKept.get(key(setRoot)).snapshot().path("body").path("children").get(1).path("id").asText())
                                : UUID.randomUUID(), true, BigDecimal.ONE))),
                key(otherSc), singleChoiceVersion(otherSc, true, BigDecimal.ONE));

        GeneratedPart part = new GeneratedPart("P1", List.of(
                new GeneratedSection("S1", "TOAN", 5, List.of(
                        new GeneratedGroup("G1", List.of(gq(scKeep), gq(mcKeep), gq(setRoot), gq(otherSc)))))));

        AssembledPart kept = assembler.assemble(part, versionsKept, Map.of("G1", false), 555L, 1);
        AssembledPart toggled = assembler.assemble(part, versionsShuffled, Map.of("G1", false), 555L, 1);

        assertThat(findItem(kept, scKeep).optionOrder()).containsExactly("o1", "o2", "o3", "o4");
        assertThat(findItem(kept, mcKeep).optionOrder()).containsExactly("o1", "o2", "o3", "o4");
        assertThat(findItem(kept, childKeep).optionOrder()).containsExactly("o1", "o2", "o3", "o4");

        // Toggling shuffleOptions on scKeep does NOT alter other questions' option order with the same seed!
        assertThat(findItem(kept, otherSc).optionOrder())
                .isEqualTo(findItem(toggled, otherSc).optionOrder());
        assertThat(findItem(kept, otherSc).optionOrder()).containsExactlyInAnyOrder("o1", "o2", "o3", "o4");
    }

    @Test
    void matchingRightColumnAlwaysShuffled() {
        UUID matchingId = UUID.randomUUID();
        UUID tfId = UUID.randomUUID();
        Map<VersionKey, Version> versions = Map.of(
                key(matchingId), matchingVersion(matchingId, BigDecimal.ONE),
                key(tfId), trueFalseVersion(tfId, BigDecimal.ONE));
        GeneratedPart part = new GeneratedPart("P1", List.of(
                new GeneratedSection("S1", "TIENG_ANH", 2, List.of(
                        new GeneratedGroup("G1", List.of(gq(matchingId), gq(tfId)))))));

        boolean sawShuffledRight = false;
        for (long seed = 1; seed <= 20; seed++) {
            AssembledPart assembled = assembler.assemble(part, versions, Map.of("G1", false), seed, 1);
            AssembledItem matchItem = findItem(assembled, matchingId);
            AssembledItem tfItem = findItem(assembled, tfId);

            assertThat(matchItem.optionOrder()).containsExactlyInAnyOrder("r1", "r2", "r3", "r4");
            if (!matchItem.optionOrder().equals(List.of("r1", "r2", "r3", "r4"))) {
                sawShuffledRight = true;
            }
            assertThat(tfItem.optionOrder()).isNull();

            // Verify sanitizer preserves Matching left order, applies shuffled right order, and preserves TF order
            ObjectNode sanitizedMatch = sanitizer.sanitizeSnapshot(
                    UUID.randomUUID(), versions.get(key(matchingId)).snapshot(), matchItem.optionOrder(), Map.of());
            assertThat(sanitizedMatch.path("body").path("left"))
                    .extracting(n -> n.path("id").asText())
                    .containsExactly("l1", "l2");
            assertThat(sanitizedMatch.path("body").path("right"))
                    .extracting(n -> n.path("id").asText())
                    .containsExactlyElementsOf(matchItem.optionOrder());

            ObjectNode sanitizedTf = sanitizer.sanitizeSnapshot(
                    UUID.randomUUID(), versions.get(key(tfId)).snapshot(), tfItem.optionOrder(), Map.of());
            assertThat(sanitizedTf.path("body").path("statements"))
                    .extracting(n -> n.path("id").asText())
                    .containsExactly("s1", "s2", "s3", "s4");
        }
        assertThat(sawShuffledRight).isTrue();
    }

    @Test
    void numberingRunsThroughPaper() {
        UUID p1Set = UUID.randomUUID();
        UUID p1Child1 = UUID.randomUUID();
        UUID p1Child2 = UUID.randomUUID();
        UUID p1Cloze = UUID.randomUUID();
        UUID p2Single = UUID.randomUUID();

        Map<VersionKey, Version> versions = Map.of(
                key(p1Set), setVersion(p1Set, List.of(
                        childJson(p1Child1, false, new BigDecimal("0.125")),
                        childJson(p1Child2, false, new BigDecimal("0.125")))),
                key(p1Cloze), clozeVersion(p1Cloze, 3, new BigDecimal("1.5")),
                key(p2Single), singleChoiceVersion(p2Single, false, new BigDecimal("2.0")));

        GeneratedPart part1 = new GeneratedPart("P1", List.of(
                new GeneratedSection("SEC_VN", "TIENG_VIET", 5, List.of(
                        new GeneratedGroup("G1", List.of(gq(p1Set), gq(p1Cloze)))))));
        GeneratedPart part2 = new GeneratedPart("P2", List.of(
                new GeneratedSection("SEC_MATH", "TOAN", 1, List.of(
                        new GeneratedGroup("G2", List.of(gq(p2Single)))))));

        AssembledPart assembled1 = assembler.assemble(part1, versions, Map.of("G1", false), 10L, 1);
        AssembledPart assembled2 = assembler.assemble(part2, versions, Map.of("G2", false), 20L, assembled1.nextDisplayNo());

        List<AssembledItem> p1Items = assembled1.items();
        assertThat(p1Items).hasSize(4);
        assertThat(p1Items.get(0).questionType()).isEqualTo(QuestionType.QUESTION_SET);
        assertThat(p1Items.get(0).displayNo()).isNull();
        assertThat(p1Items.get(0).unitCount()).isZero();
        assertThat(p1Items.get(0).pointsMax()).isEqualByComparingTo("0.250");
        assertThat(p1Items.get(0).componentCode()).isEqualTo("TIENG_VIET");

        assertThat(p1Items.get(1).displayNo()).isEqualTo(1);
        assertThat(p1Items.get(1).unitCount()).isEqualTo(1);
        assertThat(p1Items.get(1).pointsMax()).isEqualByComparingTo("0.125");
        assertThat(p1Items.get(2).displayNo()).isEqualTo(2);
        assertThat(p1Items.get(2).unitCount()).isEqualTo(1);
        assertThat(p1Items.get(2).pointsMax()).isEqualByComparingTo("0.125");

        assertThat(p1Items.get(3).questionType()).isEqualTo(QuestionType.CLOZE);
        assertThat(p1Items.get(3).displayNo()).isEqualTo(3);
        assertThat(p1Items.get(3).unitCount()).isEqualTo(3);
        assertThat(p1Items.get(3).pointsMax()).isEqualByComparingTo("4.5");
        assertThat(assembled1.nextDisplayNo()).isEqualTo(6);
        assertThat(assembled1.totalUnitCount()).isEqualTo(5);

        assertThat(assembled2.items()).hasSize(1);
        assertThat(assembled2.items().getFirst().displayNo()).isEqualTo(6);
        assertThat(assembled2.items().getFirst().componentCode()).isEqualTo("TOAN");
        assertThat(assembled2.nextDisplayNo()).isEqualTo(7);
    }

    @Test
    void sanitizerStripsAllForbiddenKeysAndRewritesAssetUrls() throws Exception {
        UUID attemptId = UUID.randomUUID();
        UUID promptAsset = UUID.randomUUID();
        UUID passageAsset = UUID.randomUUID();
        UUID optAsset = UUID.randomUUID();
        UUID stmtAsset = UUID.randomUUID();
        UUID leftAsset = UUID.randomUUID();
        UUID rightAsset = UUID.randomUUID();

        // Check every question type for forbidden key stripping & URL rewrite
        ObjectNode setSnap = mapper.createObjectNode();
        setSnap.put("content", "<p>Root <img data-asset-id=\"" + promptAsset + "\" src=\"/api/v1/admin/question-assets/" + promptAsset + "\"></p>");
        setSnap.put("points", new BigDecimal("5.0"));
        setSnap.put("solution", "secret solution");
        setSnap.putArray("commonMistakes").add("mistake");
        setSnap.putArray("knowledge").addObject().put("itemId", UUID.randomUUID().toString());
        setSnap.putArray("knowledgeItemIds").add(UUID.randomUUID().toString());
        setSnap.put("mapId", "M1");
        setSnap.putArray("mapIds").add("M1");
        setSnap.putArray("mapidIds").add(UUID.randomUUID().toString());
        setSnap.put("levelItemId", UUID.randomUUID().toString());
        setSnap.put("difficulty", "VD");
        setSnap.put("difficultyCode", "VD");

        ObjectNode setBody = setSnap.putObject("body");
        setBody.put("type", "QUESTION_SET");
        setBody.put("passage", "<p>Passage <img src=\"/api/v1/admin/question-assets/" + passageAsset + "\"></p>");
        setBody.put("blankNumberStart", 4);
        ArrayNode children = setBody.putArray("children");

        UUID scId = UUID.randomUUID();
        ObjectNode scChild = children.addObject();
        scChild.put("id", scId.toString());
        scChild.put("type", "SINGLE_CHOICE");
        scChild.put("content", "<p>Child prompt</p>");
        scChild.put("points", new BigDecimal("0.125"));
        scChild.put("solution", "child solution");
        scChild.put("levelItemId", UUID.randomUUID().toString());
        scChild.putArray("knowledgeItemIds").add(UUID.randomUUID().toString());
        ObjectNode scBody = scChild.putObject("body");
        scBody.put("type", "SINGLE_CHOICE");
        ArrayNode scOpts = scBody.putArray("options");
        scOpts.addObject().put("id", "o1").put("content", "<p>O1 <img src=\"/api/v1/admin/question-assets/" + optAsset + "\"></p>").put("correct", true);
        scOpts.addObject().put("id", "o2").put("content", "<p>O2</p>").put("correct", false);

        ObjectNode tfSnap = (ObjectNode) trueFalseVersion(UUID.randomUUID(), BigDecimal.ONE).snapshot().deepCopy();
        ((ObjectNode) tfSnap.path("body").path("statements").get(0))
                .put("content", "<p>S1 <img src=\"/api/v1/admin/question-assets/" + stmtAsset + "\"></p>");

        ObjectNode matchSnap = (ObjectNode) matchingVersion(UUID.randomUUID(), BigDecimal.ONE).snapshot().deepCopy();
        ((ObjectNode) matchSnap.path("body").path("left").get(0))
                .put("content", "<p>L1 <img src=\"/api/v1/admin/question-assets/" + leftAsset + "\"></p>");
        ((ObjectNode) matchSnap.path("body").path("right").get(0))
                .put("content", "<p>R1 <img src=\"/api/v1/admin/question-assets/" + rightAsset + "\"></p>");

        ObjectNode essaySnap = mapper.createObjectNode();
        essaySnap.put("content", "<p>Write an essay</p>");
        essaySnap.put("points", new BigDecimal("25"));
        ObjectNode essayBody = essaySnap.putObject("body");
        essayBody.put("type", "ESSAY");
        essayBody.put("gradingGuide", "secret guide");
        essayBody.put("sampleAnswer", "secret sample");
        essayBody.put("wordLimit", 200);
        essayBody.putArray("rubric").addObject().put("id", "r1").put("description", "criteria").put("points", 25);

        ObjectNode shortSnap = mapper.createObjectNode();
        shortSnap.put("content", "<p>Short</p>");
        shortSnap.put("points", BigDecimal.ONE);
        ObjectNode shortBody = shortSnap.putObject("body");
        shortBody.put("type", "SHORT_ANSWER");
        shortBody.putArray("answers").add("42");
        shortBody.put("inputHint", "Enter integer");
        shortBody.put("answerFormat", "NUMERIC");

        ObjectNode clozeSnap = (ObjectNode) clozeVersion(UUID.randomUUID(), 2, BigDecimal.ONE).snapshot().deepCopy();

        ObjectNode sanitizedSet = sanitizer.sanitizeSnapshot(
                attemptId, setSnap, null, Map.of(scId.toString(), List.of("o2", "o1")));
        ObjectNode sanitizedTf = sanitizer.sanitizeSnapshot(attemptId, tfSnap, null, Map.of());
        ObjectNode sanitizedMatch = sanitizer.sanitizeSnapshot(
                attemptId, matchSnap, List.of("r4", "r3", "r2", "r1"), Map.of());
        ObjectNode sanitizedEssay = sanitizer.sanitizeSnapshot(attemptId, essaySnap, null, Map.of());
        ObjectNode sanitizedShort = sanitizer.sanitizeSnapshot(attemptId, shortSnap, null, Map.of());
        ObjectNode sanitizedCloze = sanitizer.sanitizeSnapshot(attemptId, clozeSnap, null, Map.of());

        ArrayNode bundle = mapper.createArrayNode()
                .add(sanitizedSet)
                .add(sanitizedTf)
                .add(sanitizedMatch)
                .add(sanitizedEssay)
                .add(sanitizedShort)
                .add(sanitizedCloze);

        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(bundle);
        String json = mapper.writeValueAsString(bundle);
        assertThat(json).doesNotContain("/api/v1/admin/");

        // Verify statements[].answer also absent (beyond ForbiddenKeysAssert scoped check)
        for (JsonNode stmt : sanitizedTf.path("body").path("statements")) {
            assertThat(stmt.has("answer")).isFalse();
            assertThat(stmt.has("points")).isFalse();
        }
        // Verify blanks keep id only
        for (JsonNode blank : sanitizedCloze.path("body").path("blanks")) {
            assertThat(blank.fieldNames()).toIterable().containsExactly("id");
        }
        // Verify preserved fields
        assertThat(sanitizedSet.path("body").path("blankNumberStart").asInt()).isEqualTo(4);
        assertThat(sanitizedSet.path("body").path("children").get(0).path("points").decimalValue())
                .isEqualByComparingTo("0.125");
        assertThat(sanitizedSet.path("body").path("children").get(0).path("body").path("options"))
                .extracting(n -> n.path("id").asText())
                .containsExactly("o2", "o1");
        assertThat(sanitizedEssay.path("body").path("wordLimit").asInt()).isEqualTo(200);
        assertThat(sanitizedShort.path("body").path("inputHint").asText()).isEqualTo("Enter integer");
        assertThat(sanitizedShort.path("body").path("answerFormat").asText()).isEqualTo("NUMERIC");
        assertThat(sanitizedMatch.path("body").path("reuseRight").asBoolean()).isFalse();

        // Verify rewritten URLs
        assertThat(sanitizedSet.path("content").asText())
                .contains("/api/v1/me/attempts/" + attemptId + "/assets/" + promptAsset);
        assertThat(sanitizedSet.path("body").path("passage").asText())
                .contains("/api/v1/me/attempts/" + attemptId + "/assets/" + passageAsset);
        assertThat(sanitizedSet.path("body").path("children").get(0).path("body").path("options").get(1).path("content").asText())
                .contains("/api/v1/me/attempts/" + attemptId + "/assets/" + optAsset);
        assertThat(sanitizedTf.path("body").path("statements").get(0).path("content").asText())
                .contains("/api/v1/me/attempts/" + attemptId + "/assets/" + stmtAsset);
        assertThat(sanitizedMatch.path("body").path("left").get(0).path("content").asText())
                .contains("/api/v1/me/attempts/" + attemptId + "/assets/" + leftAsset);
        assertThat(sanitizedMatch.path("body").path("right").get(3).path("content").asText())
                .contains("/api/v1/me/attempts/" + attemptId + "/assets/" + rightAsset);
    }

    @Test
    void answerValueJsonRoundTripsAllVariants() throws Exception {
        List<AnswerValue> values = List.of(
                new AnswerValue.SingleChoice("opt-1"),
                new AnswerValue.MultipleChoice(List.of("opt-1", "opt-3")),
                new AnswerValue.ShortAnswer("42"),
                new AnswerValue.Cloze(Map.of("b1", "word")),
                new AnswerValue.TrueFalse(new java.util.LinkedHashMap<>(Map.of("s1", true, "s2", false))),
                new AnswerValue.Matching(Map.of("l1", "r2")),
                new AnswerValue.Essay("<p>Text</p>", 1));
        for (AnswerValue original : values) {
            String json = mapper.writeValueAsString(original);
            AnswerValue restored = mapper.readValue(json, AnswerValue.class);
            assertThat(restored).isEqualTo(original);
        }
    }

    private static VersionKey key(UUID id) {
        return new VersionKey(id, 1);
    }

    private static GeneratedQuestion gq(UUID id) {
        return new GeneratedQuestion(id, 1);
    }

    private static int indexOf(List<AssembledItem> items, UUID questionId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).questionId().equals(questionId)) {
                return i;
            }
        }
        throw new AssertionError("Missing questionId: " + questionId);
    }

    private static AssembledItem findItem(AssembledPart part, UUID questionId) {
        return part.items().stream()
                .filter(i -> i.questionId().equals(questionId))
                .findFirst()
                .orElseThrow();
    }

    private Version singleChoiceVersion(UUID id, boolean shuffleOptions, BigDecimal points) {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("points", points);
        snap.put("content", "<p>SC " + id + "</p>");
        ObjectNode body = snap.putObject("body");
        body.put("type", "SINGLE_CHOICE");
        body.put("shuffleOptions", shuffleOptions);
        ArrayNode options = body.putArray("options");
        for (int i = 1; i <= 4; i++) {
            options.addObject().put("id", "o" + i).put("content", "<p>Option " + i + "</p>").put("correct", i == 1);
        }
        return new Version(key(id), snap);
    }

    private Version multipleChoiceVersion(UUID id, boolean shuffleOptions, BigDecimal points) {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("points", points);
        snap.put("content", "<p>MC " + id + "</p>");
        ObjectNode body = snap.putObject("body");
        body.put("type", "MULTIPLE_CHOICE");
        body.put("shuffleOptions", shuffleOptions);
        body.put("scoringRule", "PARTIAL");
        ArrayNode options = body.putArray("options");
        for (int i = 1; i <= 4; i++) {
            options.addObject().put("id", "o" + i).put("content", "<p>Option " + i + "</p>").put("correct", i <= 2);
        }
        return new Version(key(id), snap);
    }

    private Version matchingVersion(UUID id, BigDecimal points) {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("points", points);
        snap.put("content", "<p>Match " + id + "</p>");
        ObjectNode body = snap.putObject("body");
        body.put("type", "MATCHING");
        body.put("reuseRight", false);
        ArrayNode left = body.putArray("left");
        left.addObject().put("id", "l1").put("content", "<p>L1</p>");
        left.addObject().put("id", "l2").put("content", "<p>L2</p>");
        ArrayNode right = body.putArray("right");
        for (int i = 1; i <= 4; i++) {
            right.addObject().put("id", "r" + i).put("content", "<p>R" + i + "</p>");
        }
        ArrayNode pairs = body.putArray("pairs");
        pairs.addObject().put("leftId", "l1").put("rightId", "r1").put("points", points);
        return new Version(key(id), snap);
    }

    private Version trueFalseVersion(UUID id, BigDecimal points) {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("points", points);
        snap.put("content", "<p>TF " + id + "</p>");
        ObjectNode body = snap.putObject("body");
        body.put("type", "TRUE_FALSE");
        body.put("scoringMethod", "COUNT_TABLE");
        body.putArray("scoreTable").add(0.1).add(0.25).add(0.5).add(1.0);
        ArrayNode statements = body.putArray("statements");
        for (int i = 1; i <= 4; i++) {
            statements.addObject()
                    .put("id", "s" + i)
                    .put("content", "<p>Statement " + i + "</p>")
                    .put("answer", i % 2 == 0)
                    .put("points", 0.25);
        }
        return new Version(key(id), snap);
    }

    private Version clozeVersion(UUID id, int blankCount, BigDecimal pointsPerBlank) {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("points", pointsPerBlank.multiply(BigDecimal.valueOf(blankCount)));
        snap.put("content", "<p>Cloze</p>");
        ObjectNode body = snap.putObject("body");
        body.put("type", "CLOZE");
        body.put("passage", "<p>Fill [[b1]] and [[b2]]</p>");
        ArrayNode blanks = body.putArray("blanks");
        for (int i = 1; i <= blankCount; i++) {
            ObjectNode b = blanks.addObject().put("id", "b" + i).put("points", pointsPerBlank);
            b.putArray("answers").add("ans" + i);
        }
        return new Version(key(id), snap);
    }

    private Version setVersion(UUID setId, List<ObjectNode> childrenNodes) {
        ObjectNode snap = mapper.createObjectNode();
        snap.put("points", BigDecimal.valueOf(childrenNodes.size()));
        snap.put("content", "<p>Set root</p>");
        ObjectNode body = snap.putObject("body");
        body.put("type", "QUESTION_SET");
        body.put("passage", "<p>Set passage</p>");
        ArrayNode children = body.putArray("children");
        childrenNodes.forEach(children::add);
        return new Version(key(setId), snap);
    }

    private ObjectNode childJson(UUID childId, boolean shuffleOptions, BigDecimal points) {
        ObjectNode child = mapper.createObjectNode();
        child.put("id", childId.toString());
        child.put("type", "SINGLE_CHOICE");
        child.put("content", "<p>Child " + childId + "</p>");
        child.put("points", points);
        ObjectNode body = child.putObject("body");
        body.put("type", "SINGLE_CHOICE");
        body.put("shuffleOptions", shuffleOptions);
        ArrayNode options = body.putArray("options");
        for (int i = 1; i <= 4; i++) {
            options.addObject().put("id", "o" + i).put("content", "<p>Opt " + i + "</p>").put("correct", i == 1);
        }
        return child;
    }
}
