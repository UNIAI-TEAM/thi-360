package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Contract tests guarding generated {@code packages/api-client/openapi.json} and {@code
 * packages/api-client/src/schema.d.ts} against circular {@code AnswerValue} regression, variant
 * collisions, missing {@code maxItems}, or optional {@code Idempotency-Key} (R2-5).
 */
class AttemptOpenApiContractTest {

    private static final Map<String, String> EXPECTED_ANSWER_DISCRIMINATOR_MAPPINGS =
            Map.of(
                    "SINGLE_CHOICE", "SingleChoiceAnswer",
                    "MULTIPLE_CHOICE", "MultipleChoiceAnswer",
                    "SHORT_ANSWER", "ShortAnswerValue",
                    "CLOZE", "ClozeAnswer",
                    "TRUE_FALSE", "TrueFalseAnswer",
                    "MATCHING", "MatchingAnswer",
                    "ESSAY", "EssayAnswer");

    private static final List<String> ANSWER_SUBTYPE_SCHEMAS =
            List.of(
                    "SingleChoiceAnswer",
                    "MultipleChoiceAnswer",
                    "ShortAnswerValue",
                    "ClozeAnswer",
                    "TrueFalseAnswer",
                    "MatchingAnswer",
                    "EssayAnswer");

    private static final List<String> QUESTION_BODY_SUBTYPE_SCHEMAS =
            List.of(
                    "SingleChoice",
                    "MultipleChoice",
                    "ShortAnswer",
                    "Cloze",
                    "TrueFalse",
                    "Matching",
                    "Essay");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openApiJsonPreservesAnswerValueDiscriminatorAndAttemptSubmitContract() throws Exception {
        Path openApiPath = resolveWorkspaceFile(Path.of("packages", "api-client", "openapi.json"));
        JsonNode root = objectMapper.readTree(Files.readString(openApiPath, StandardCharsets.UTF_8));
        JsonNode schemas = root.path("components").path("schemas");

        JsonNode answerValue = schemas.path("AnswerValue");
        assertThat(answerValue.isMissingNode()).isFalse();
        assertThat(answerValue.has("oneOf"))
                .as("AnswerValue must not declare oneOf alongside subtype allOf (prevents TS2502)")
                .isFalse();
        assertThat(answerValue.path("discriminator").path("propertyName").asText())
                .isEqualTo("type");

        JsonNode mapping = answerValue.path("discriminator").path("mapping");
        assertThat(mapping.size()).isEqualTo(EXPECTED_ANSWER_DISCRIMINATOR_MAPPINGS.size());
        EXPECTED_ANSWER_DISCRIMINATOR_MAPPINGS.forEach(
                (wireType, schemaName) ->
                        assertThat(mapping.path(wireType).asText())
                                .as("AnswerValue discriminator mapping for %s", wireType)
                                .isEqualTo("#/components/schemas/" + schemaName));

        for (String answerSubtype : ANSWER_SUBTYPE_SCHEMAS) {
            JsonNode subtypeNode = schemas.path(answerSubtype);
            assertThat(subtypeNode.isMissingNode())
                    .as("AnswerValue subtype schema %s exists", answerSubtype)
                    .isFalse();
            String raw = subtypeNode.toString();
            assertThat(raw).contains("#/components/schemas/AnswerValue");
            assertThat(raw)
                    .as("AnswerValue subtype %s must not reference AttemptQuestionBody", answerSubtype)
                    .doesNotContain("AttemptQuestionBody");
        }

        for (String bodySubtype : QUESTION_BODY_SUBTYPE_SCHEMAS) {
            JsonNode subtypeNode = schemas.path(bodySubtype);
            assertThat(subtypeNode.isMissingNode())
                    .as("AttemptQuestionBody subtype schema %s exists", bodySubtype)
                    .isFalse();
            String raw = subtypeNode.toString();
            assertThat(raw).contains("#/components/schemas/AttemptQuestionBody");
            assertThat(raw)
                    .as("AttemptQuestionBody subtype %s must not reference AnswerValue", bodySubtype)
                    .doesNotContain("AnswerValue");
        }

        Set<String> expectedAnswerRefs = new HashSet<>();
        for (String schemaName : ANSWER_SUBTYPE_SCHEMAS) {
            expectedAnswerRefs.add("#/components/schemas/" + schemaName);
        }
        for (String ownerSchema : List.of("FinalAnswerChange", "AnswerChange", "AttemptItemView")) {
            JsonNode answerProp = schemas.path(ownerSchema).path("properties").path("answer");
            assertThat(answerProp.isMissingNode()).isFalse();
            assertThat(answerProp.toString()).doesNotContain("AttemptQuestionBody");
            JsonNode oneOf = answerProp.path("oneOf");
            assertThat(oneOf.isArray())
                    .as("%s.properties.answer must expose oneOf subtypes", ownerSchema)
                    .isTrue();
            Set<String> actualRefs = new HashSet<>();
            oneOf.forEach(entry -> actualRefs.add(entry.path("$ref").asText()));
            assertThat(actualRefs)
                    .as("%s.properties.answer subtype refs", ownerSchema)
                    .containsExactlyInAnyOrderElementsOf(expectedAnswerRefs);
        }

        assertThat(
                        schemas.path("FinishPartRequest")
                                .path("properties")
                                .path("finalAnswers")
                                .path("maxItems")
                                .asInt())
                .isEqualTo(500);
        assertThat(
                        schemas.path("SubmitAttemptRequest")
                                .path("properties")
                                .path("finalAnswers")
                                .path("maxItems")
                                .asInt())
                .isEqualTo(500);
        assertThat(
                        schemas.path("SaveAnswersRequest")
                                .path("properties")
                                .path("changes")
                                .path("maxItems")
                                .asInt())
                .isEqualTo(50);
        assertThat(
                        schemas.path("SaveAnswersRequest")
                                .path("properties")
                                .path("flags")
                                .path("maxItems")
                                .asInt())
                .isEqualTo(50);

        JsonNode sessionConflictLastSeen =
                schemas.path("AttemptSessionConflictResponse")
                        .path("properties")
                        .path("lastSeenAt");
        assertThat(sessionConflictLastSeen.isMissingNode()).isFalse();
        assertThat(sessionConflictLastSeen.path("format").asText()).isEqualTo("date-time");

        JsonNode paths = root.path("paths");
        assertThat(paths.path("/api/v1/me/attempts/{attemptId}/session").path("post").path("operationId").asText())
                .isEqualTo("claimSession");
        assertThat(paths.path("/api/v1/me/attempts/{attemptId}/heartbeat").path("post").path("operationId").asText())
                .isEqualTo("heartbeat");
        assertThat(paths.path("/api/v1/me/attempts/{attemptId}/answers").path("put").path("operationId").asText())
                .isEqualTo("saveAnswers");
        assertThat(paths.path("/api/v1/me/attempts/{attemptId}/assets/{assetId}").path("get").path("operationId").asText())
                .isEqualTo("downloadAttemptAsset");

        JsonNode submitParams =
                root.path("paths")
                        .path("/api/v1/me/attempts/{attemptId}/submit")
                        .path("post")
                        .path("parameters");
        assertThat(submitParams.isArray()).isTrue();
        boolean foundRequiredIdempotencyKey = false;
        for (JsonNode param : submitParams) {
            if ("header".equals(param.path("in").asText())
                    && "Idempotency-Key".equals(param.path("name").asText())) {
                assertThat(param.path("required").asBoolean()).isTrue();
                foundRequiredIdempotencyKey = true;
            }
        }
        assertThat(foundRequiredIdempotencyKey)
                .as("/api/v1/me/attempts/{attemptId}/submit requires Idempotency-Key header")
                .isTrue();
    }

    @Test
    void schemaDtsHasNoCircularAnswerValueNoNeverIntersectionsAndRequiredSubmitHeader()
            throws IOException {
        Path schemaDtsPath =
                resolveWorkspaceFile(Path.of("packages", "api-client", "src", "schema.d.ts"));
        String schemaDts = Files.readString(schemaDtsPath, StandardCharsets.UTF_8);

        String answerValueBlock = extractComponentSchemaBlock(schemaDts, "AnswerValue");
        assertThat(answerValueBlock).contains("type: string;");
        for (String subtype : ANSWER_SUBTYPE_SCHEMAS) {
            assertThat(answerValueBlock)
                    .as("Base AnswerValue in schema.d.ts must not reference %s", subtype)
                    .doesNotContain(subtype);
        }

        for (String schemaName : ANSWER_SUBTYPE_SCHEMAS) {
            String block = extractComponentSchemaBlock(schemaDts, schemaName);
            assertThat(block)
                    .as("AnswerValue subtype %s must not collapse to never", schemaName)
                    .doesNotContain(": never");
        }
        for (String schemaName : QUESTION_BODY_SUBTYPE_SCHEMAS) {
            String block = extractComponentSchemaBlock(schemaDts, schemaName);
            assertThat(block)
                    .as("AttemptQuestionBody subtype %s must not collapse to never", schemaName)
                    .doesNotContain(": never");
        }

        String submitOpBlock = extractOperationBlock(schemaDts, "submit");
        assertThat(submitOpBlock)
                .contains("\"Idempotency-Key\": string;")
                .doesNotContain("\"Idempotency-Key\"?:");

        String answerChangeBlock = extractComponentSchemaBlock(schemaDts, "AnswerChange");
        assertThat(answerChangeBlock)
                .doesNotContain(": never")
                .contains("SingleChoiceAnswer");
        String conflictBlock =
                extractComponentSchemaBlock(schemaDts, "AttemptSessionConflictResponse");
        assertThat(conflictBlock).contains("lastSeenAt?: string;");

        extractOperationBlock(schemaDts, "claimSession");
        extractOperationBlock(schemaDts, "heartbeat");
        extractOperationBlock(schemaDts, "saveAnswers");
        extractOperationBlock(schemaDts, "downloadAttemptAsset");
    }

    private static String extractComponentSchemaBlock(String schemaDts, String schemaName) {
        Pattern pattern =
                Pattern.compile(
                        "(?ms)^ {8}" + Pattern.quote(schemaName) + ": .*?^ {8}\\};");
        Matcher matcher = pattern.matcher(schemaDts);
        assertThat(matcher.find())
                .as("Found schema block for %s in schema.d.ts", schemaName)
                .isTrue();
        return matcher.group();
    }

    private static String extractOperationBlock(String schemaDts, String operationId) {
        Pattern pattern =
                Pattern.compile(
                        "(?ms)^ {4}" + Pattern.quote(operationId) + ": \\{.*?^ {4}\\};");
        Matcher matcher = pattern.matcher(schemaDts);
        assertThat(matcher.find())
                .as("Found operation block for %s in schema.d.ts", operationId)
                .isTrue();
        return matcher.group();
    }

    private static Path resolveWorkspaceFile(Path relativeFromRepoRoot) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativeFromRepoRoot);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + relativeFromRepoRoot + " from " + Path.of("").toAbsolutePath());
    }
}
