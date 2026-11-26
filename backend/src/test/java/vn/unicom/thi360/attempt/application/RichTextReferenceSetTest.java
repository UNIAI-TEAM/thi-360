package vn.unicom.thi360.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.attempt.api.AttemptQuestionBody;
import vn.unicom.thi360.attempt.api.AttemptStateResponse;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptAttachmentView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptItemView;
import vn.unicom.thi360.attempt.api.AttemptStateResponse.AttemptPartStateView;
import vn.unicom.thi360.attempt.domain.AnswerValue;
import vn.unicom.thi360.questionbank.application.HtmlContentSanitizerAdapter;
import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;
import vn.unicom.thi360.support.ForbiddenKeysAssert;

class RichTextReferenceSetTest {

    // AttemptQuestionBody is a response-only sealed interface without @JsonTypeInfo; bind subtype
    // resolution in test deserialization while keeping FAIL_ON_UNKNOWN_PROPERTIES strict.
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "type",
            visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = AttemptQuestionBody.SingleChoice.class, name = "SINGLE_CHOICE"),
        @JsonSubTypes.Type(value = AttemptQuestionBody.MultipleChoice.class, name = "MULTIPLE_CHOICE"),
        @JsonSubTypes.Type(value = AttemptQuestionBody.ShortAnswer.class, name = "SHORT_ANSWER"),
        @JsonSubTypes.Type(value = AttemptQuestionBody.Cloze.class, name = "CLOZE"),
        @JsonSubTypes.Type(value = AttemptQuestionBody.QuestionSet.class, name = "QUESTION_SET"),
        @JsonSubTypes.Type(value = AttemptQuestionBody.TrueFalse.class, name = "TRUE_FALSE"),
        @JsonSubTypes.Type(value = AttemptQuestionBody.Matching.class, name = "MATCHING"),
        @JsonSubTypes.Type(value = AttemptQuestionBody.Essay.class, name = "ESSAY")
    })
    private interface AttemptQuestionBodyMixin {}

    private record EssaySample(
            String id, String description, String input, String serverHtml, int wordCount) {}

    private record EssaysFile(int version, List<EssaySample> samples) {}

    private record WordCountCase(String id, String description, String html, int expected) {}

    private record WordCountFile(int version, List<WordCountCase> cases) {}

    private final ObjectMapper strictMapper = buildStrictMapper();
    private final QuestionHtmlSanitizer htmlSanitizer =
            new QuestionHtmlSanitizer(QuestionAssetResolver.none());
    private final AnswerValueValidator validator =
            new AnswerValueValidator(new HtmlContentSanitizerAdapter(htmlSanitizer), strictMapper);

    @Test
    void attempt50MatchesWireType() throws Exception {
        Path attempt50Path = resolveContractFile("attempt-50.json");
        String rawJson = Files.readString(attempt50Path, StandardCharsets.UTF_8);

        JsonNode tree = strictMapper.readTree(rawJson);
        ForbiddenKeysAssert.assertNoAttemptForbiddenKeys(tree);

        AttemptStateResponse state = strictMapper.readValue(rawJson, AttemptStateResponse.class);
        assertThat(state.serverNow()).isNotNull();
        assertThat(state.attemptId()).isNotNull();
        assertThat(state.state()).isNotNull();
        assertThat(state.examId()).isNotNull();
        assertThat(state.examVersionNo()).isNotNull();
        assertThat(state.examName()).isNotBlank();
        assertThat(state.timing()).isNotNull();
        assertThat(state.navigationMode()).isNotNull();
        assertThat(state.startedAt()).isNotNull();
        assertThat(state.deadlineAt()).isNotNull();
        assertThat(state.audioPolicy()).isNotNull();
        assertThat(state.audioPlays()).isNotEmpty();
        assertThat(state.parts()).isNotEmpty();

        List<AttemptItemView> allItems = new ArrayList<>();
        for (AttemptPartStateView part : state.parts()) {
            assertThat(part.partId()).isNotNull();
            assertThat(part.partCode()).isNotBlank();
            assertThat(part.state()).isNotNull();
            allItems.addAll(part.items());
        }

        EnumSet<QuestionType> seenTypes = EnumSet.noneOf(QuestionType.class);
        Set<UUID> setParentIds = new LinkedHashSet<>();
        Set<UUID> referencedParentIds = new LinkedHashSet<>();
        List<Integer> displayNumbers = new ArrayList<>();
        int leafCount = 0;
        int unitCountSum = 0;
        boolean hasAudioAttachment = false;
        boolean hasNonAudioAttachment = false;
        boolean hasMathFormula = false;

        for (AttemptItemView item : allItems) {
            assertThat(item.itemId()).isNotNull();
            assertThat(item.questionType()).isNotNull();
            assertThat(item.questionBody()).isNotNull();
            assertThat(item.questionBody().type()).isEqualTo(item.questionType());
            seenTypes.add(item.questionType());
            unitCountSum += item.unitCount();

            if (item.questionType() == QuestionType.QUESTION_SET) {
                setParentIds.add(item.itemId());
            } else {
                leafCount++;
                assertThat(item.displayNo()).isNotNull();
                displayNumbers.add(item.displayNo());
            }
            if (item.parentItemId() != null) {
                referencedParentIds.add(item.parentItemId());
            }
            for (AttemptAttachmentView attachment : item.attachments()) {
                assertThat(attachment.assetId()).isNotNull();
                assertThat(attachment.fileName()).isNotBlank();
                assertThat(attachment.mime()).isNotBlank();
                assertThat(attachment.url()).startsWith("/api/v1/me/attempts/" + state.attemptId() + "/assets/");
                if (attachment.mime().startsWith("audio/")) {
                    hasAudioAttachment = true;
                } else {
                    hasNonAudioAttachment = true;
                }
            }
            if (item.content() != null && (item.content().contains("math-inline") || item.content().contains("math-block"))) {
                hasMathFormula = true;
            }
        }

        assertThat(seenTypes).containsExactlyInAnyOrder(QuestionType.values());
        assertThat(leafCount).isEqualTo(50);
        assertThat(unitCountSum).isEqualTo(50);
        List<Integer> expectedDisplayNos = java.util.stream.IntStream.rangeClosed(1, 50).boxed().toList();
        assertThat(displayNumbers).containsExactlyElementsOf(expectedDisplayNos);
        assertThat(setParentIds).isNotEmpty();
        assertThat(referencedParentIds).containsAll(setParentIds);
        assertThat(hasAudioAttachment).isTrue();
        assertThat(hasNonAudioAttachment).isTrue();
        assertThat(hasMathFormula).isTrue();

        ObjectNode withExtra = (ObjectNode) tree.deepCopy();
        withExtra.put("unexpectedExtraField", true);
        assertThatThrownBy(() -> strictMapper.treeToValue(withExtra, AttemptStateResponse.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void essaysSurviveServerSanitizer() throws Exception {
        EssaysFile file = strictMapper.readValue(resolveContractFile("essays.json").toFile(), EssaysFile.class);
        assertThat(file.samples()).hasSize(10);

        JsonNode essaySnap = strictMapper.readTree("{\"body\":{\"type\":\"ESSAY\"}}");
        for (EssaySample sample : file.samples()) {
            QuestionHtmlSanitizer.Result sanitized = htmlSanitizer.clean(sample.input(), false);
            assertThat(sanitized.violations())
                    .as("Sanitizer violations for essay %s", sample.id())
                    .isEmpty();

            AnswerValueValidator.ValidationResult result =
                    validator.validate(QuestionType.ESSAY, essaySnap, new AnswerValue.Essay(sample.input(), 0));
            assertThat(result.valid()).as("Validation for essay %s", sample.id()).isTrue();
            AnswerValue.Essay normalized = (AnswerValue.Essay) result.normalized();

            Document inputDoc = Jsoup.parseBodyFragment(sample.input());
            Document serverDoc = Jsoup.parseBodyFragment(normalized.html());
            assertSameHtmlTree(sample.id(), inputDoc.body(), serverDoc.body());
        }
    }

    @Test
    void essaysMatchServerOutput() throws Exception {
        Path essaysPath = resolveContractFile("essays.json");
        EssaysFile file = strictMapper.readValue(essaysPath.toFile(), EssaysFile.class);
        assertThat(file.samples()).hasSize(10);

        JsonNode essaySnap = strictMapper.readTree("{\"body\":{\"type\":\"ESSAY\"}}");
        if (Boolean.getBoolean("rich.reference.write")) {
            List<EssaySample> updated = new ArrayList<>(file.samples().size());
            for (EssaySample sample : file.samples()) {
                AnswerValueValidator.ValidationResult result =
                        validator.validate(QuestionType.ESSAY, essaySnap, new AnswerValue.Essay(sample.input(), 0));
                assertThat(result.valid()).as("Validation for essay %s", sample.id()).isTrue();
                AnswerValue.Essay normalized = (AnswerValue.Essay) result.normalized();
                updated.add(
                        new EssaySample(
                                sample.id(),
                                sample.description(),
                                sample.input(),
                                normalized.html(),
                                normalized.wordCount()));
            }
            writeStableJson(essaysPath, new EssaysFile(file.version(), updated));
            file = strictMapper.readValue(essaysPath.toFile(), EssaysFile.class);
        }

        for (EssaySample sample : file.samples()) {
            AnswerValueValidator.ValidationResult result =
                    validator.validate(QuestionType.ESSAY, essaySnap, new AnswerValue.Essay(sample.input(), 0));
            assertThat(result.valid()).as("Validation for essay %s", sample.id()).isTrue();
            AnswerValue.Essay normalized = (AnswerValue.Essay) result.normalized();
            assertThat(sample.serverHtml())
                    .as("serverHtml mismatch for %s", sample.id())
                    .isEqualTo(normalized.html());
            assertThat(sample.wordCount())
                    .as("wordCount mismatch for %s", sample.id())
                    .isEqualTo(normalized.wordCount());
        }
    }

    @Test
    void wordCountMatchesServer() throws Exception {
        WordCountFile file =
                strictMapper.readValue(resolveContractFile("word-count.json").toFile(), WordCountFile.class);
        assertThat(file.cases()).hasSize(30);

        for (WordCountCase sample : file.cases()) {
            int actual = AnswerValueValidator.countWords(sample.html());
            assertThat(actual)
                    .as("AnswerValueValidator.countWords mismatch for %s (%s)", sample.id(), sample.description())
                    .isEqualTo(sample.expected());
        }
    }

    private static void assertSameHtmlTree(String sampleId, Element expected, Element actual) {
        assertThat(actual.tagName())
                .as("Tag mismatch in %s", sampleId)
                .isEqualTo(expected.tagName());
        List<Node> expectedChildren = expected.childNodes();
        List<Node> actualChildren = actual.childNodes();
        assertThat(actualChildren)
                .as("Child count mismatch under <%s> in %s", expected.tagName(), sampleId)
                .hasSameSizeAs(expectedChildren);

        for (int i = 0; i < expectedChildren.size(); i++) {
            Node expNode = expectedChildren.get(i);
            Node actNode = actualChildren.get(i);
            if (expNode instanceof Element expEl) {
                assertThat(actNode)
                        .as("Expected element <%s> at child %d in %s", expEl.tagName(), i, sampleId)
                        .isInstanceOf(Element.class);
                assertSameHtmlTree(sampleId, expEl, (Element) actNode);
            } else if (expNode instanceof TextNode expText) {
                assertThat(actNode)
                        .as("Expected text node at child %d in %s", i, sampleId)
                        .isInstanceOf(TextNode.class);
                assertThat(((TextNode) actNode).getWholeText())
                        .as("Decoded text mismatch under <%s> in %s", expected.tagName(), sampleId)
                        .isEqualTo(expText.getWholeText());
            } else {
                assertThat(actNode.nodeName()).isEqualTo(expNode.nodeName());
            }
        }
    }

    private static ObjectMapper buildStrictMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        mapper.addMixIn(AttemptQuestionBody.class, AttemptQuestionBodyMixin.class);
        return mapper;
    }

    private void writeStableJson(Path path, Object value) throws IOException {
        DefaultPrettyPrinter printer =
                new DefaultPrettyPrinter(
                        Separators.createDefaultInstance()
                                .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        String json = strictMapper.writer(printer).writeValueAsString(value) + "\n";
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static Path resolveContractFile(String fileName) {
        Path relative = Path.of("contracts", "mobile", "rich-text", fileName);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + relative + " from " + Path.of("").toAbsolutePath());
    }
}
