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

        JsonNode saveAnswersOp =
                paths.path("/api/v1/me/attempts/{attemptId}/answers").path("put");
        assertThat(saveAnswersOp.path("operationId").asText()).isEqualTo("saveAnswers");
        JsonNode retryAfterSchema =
                saveAnswersOp
                        .path("responses")
                        .path("429")
                        .path("headers")
                        .path("Retry-After")
                        .path("schema");
        assertThat(retryAfterSchema.path("type").asText()).isEqualTo("integer");

        JsonNode downloadAssetOp =
                paths.path("/api/v1/me/attempts/{attemptId}/assets/{assetId}").path("get");
        assertThat(downloadAssetOp.path("operationId").asText())
                .isEqualTo("downloadAttemptAsset");
        JsonNode assetBinarySchema =
                downloadAssetOp
                        .path("responses")
                        .path("200")
                        .path("content")
                        .path("application/octet-stream")
                        .path("schema");
        assertThat(assetBinarySchema.path("type").asText()).isEqualTo("string");
        assertThat(assetBinarySchema.path("format").asText()).isEqualTo("binary");

        JsonNode confirmReq = schemas.path("ConfirmPartChoiceRequest");
        assertThat(confirmReq.path("required")).extracting(JsonNode::asText).contains("optionCode");
        assertThat(confirmReq.path("properties").path("optionCode").path("maxLength").asInt())
                .isEqualTo(64);
        assertThat(confirmReq.path("properties").path("subjectCodes").path("maxItems").asInt())
                .isEqualTo(10);
        assertThat(
                        confirmReq
                                .path("properties")
                                .path("subjectCodes")
                                .path("items")
                                .path("maxLength")
                                .asInt())
                .isEqualTo(64);

        JsonNode partChoiceRespProps = schemas.path("PartChoiceResponse").path("properties");
        assertThat(partChoiceRespProps.path("choice").path("$ref").asText())
                .isEqualTo("#/components/schemas/PartChoiceSummary");

        JsonNode partChoiceSummary = schemas.path("PartChoiceSummary");
        assertThat(partChoiceSummary.path("required"))
                .extracting(JsonNode::asText)
                .contains("optionCode");
        JsonNode summaryProps = partChoiceSummary.path("properties");
        assertThat(summaryProps.has("optionCode")).isTrue();
        assertThat(summaryProps.has("subjectCodes")).isTrue();
        assertThat(summaryProps.has("subjectCounts")).isTrue();
        assertThat(summaryProps.has("seed")).isFalse();

        for (String endpointPath :
                List.of(
                        "/api/v1/me/attempts/{attemptId}/submit",
                        "/api/v1/me/attempts/{attemptId}/parts/{partId}/choice",
                        "/api/v1/me/attempts/{attemptId}/parts/{partId}/retry")) {
            JsonNode params =
                    root.path("paths").path(endpointPath).path("post").path("parameters");
            assertThat(params.isArray()).as("parameters for %s", endpointPath).isTrue();
            boolean foundRequiredIdempotencyKey = false;
            for (JsonNode param : params) {
                if ("header".equals(param.path("in").asText())
                        && "Idempotency-Key".equals(param.path("name").asText())) {
                    assertThat(param.path("required").asBoolean())
                            .as("%s Idempotency-Key required", endpointPath)
                            .isTrue();
                    foundRequiredIdempotencyKey = true;
                }
            }
            assertThat(foundRequiredIdempotencyKey)
                    .as("%s requires Idempotency-Key header", endpointPath)
                    .isTrue();
        }
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

        for (String opId : List.of("submit", "confirmPartChoice", "retryPartGeneration")) {
            String opBlock = extractOperationBlock(schemaDts, opId);
            assertThat(opBlock)
                    .as("%s requires Idempotency-Key header in schema.d.ts", opId)
                    .contains("\"Idempotency-Key\": string;")
                    .doesNotContain("\"Idempotency-Key\"?:");
        }

        String answerChangeBlock = extractComponentSchemaBlock(schemaDts, "AnswerChange");
        assertThat(answerChangeBlock)
                .doesNotContain(": never")
                .contains("SingleChoiceAnswer");
        String conflictBlock =
                extractComponentSchemaBlock(schemaDts, "AttemptSessionConflictResponse");
        assertThat(conflictBlock).contains("lastSeenAt?: string;");

        extractOperationBlock(schemaDts, "claimSession");
        extractOperationBlock(schemaDts, "heartbeat");
        String saveAnswersOpBlock = extractOperationBlock(schemaDts, "saveAnswers");
        assertThat(saveAnswersOpBlock)
                .contains("\"Retry-After\"?: number;")
                .doesNotContain("\"Retry-After\"?: unknown;");
        String downloadAssetOpBlock = extractOperationBlock(schemaDts, "downloadAttemptAsset");
        assertThat(downloadAssetOpBlock).contains("\"application/octet-stream\": string;");

        String partChoiceResponseBlock = extractComponentSchemaBlock(schemaDts, "PartChoiceResponse");
        assertThat(partChoiceResponseBlock)
                .contains("choice?: components[\"schemas\"][\"PartChoiceSummary\"];")
                .doesNotContain("Record<string, never>")
                .doesNotContain(": never")
                .doesNotContain("seed");

        String partChoiceSummaryBlock = extractComponentSchemaBlock(schemaDts, "PartChoiceSummary");
        assertThat(partChoiceSummaryBlock)
                .contains("optionCode: string;")
                .contains("subjectCodes?: string[];")
                .contains("subjectCounts?:")
                .doesNotContain("Record<string, never>")
                .doesNotContain(": never")
                .doesNotContain("seed");

        String confirmChoiceBlock = extractComponentSchemaBlock(schemaDts, "ConfirmPartChoiceRequest");
        assertThat(confirmChoiceBlock)
                .contains("optionCode: string;")
                .doesNotContain("optionCode?:")
                .contains("subjectCodes?: string[];");

        extractOperationBlock(schemaDts, "openAudioPlay");
        extractOperationBlock(schemaDts, "resumeAudioPlay");
        extractOperationBlock(schemaDts, "updateAudioProgress");
        extractOperationBlock(schemaDts, "endAudioPlay");

        String audioPlayResponseBlock = extractComponentSchemaBlock(schemaDts, "AttemptAudioPlayResponse");
        assertThat(audioPlayResponseBlock)
                .contains("durationMs: number;")
                .contains("playsUsed: number;")
                .contains("playsLeft: number;")
                .contains("positionMs: number;")
                .contains("state: \"IDLE\" | \"PLAYING\" | \"ENDED\";");

        String attemptStateBlock = extractComponentSchemaBlock(schemaDts, "AttemptStateResponse");
        assertThat(attemptStateBlock)
                .contains("audioPolicy?: components[\"schemas\"][\"AudioPolicyView\"];")
                .contains("audioPlays?: components[\"schemas\"][\"AttemptAudioPlayView\"][];");

        String attemptItemBlock = extractComponentSchemaBlock(schemaDts, "AttemptItemView");
        assertThat(attemptItemBlock)
                .contains("attachments?: components[\"schemas\"][\"AttemptAttachmentView\"][];");
    }

    @Test
    void at4ResultReviewHistoryAndSolutionAssetContractsPresentWithoutNeverIntersections() throws Exception {
        Path openApiPath = resolveWorkspaceFile(Path.of("packages", "api-client", "openapi.json"));
        JsonNode root = objectMapper.readTree(Files.readString(openApiPath, StandardCharsets.UTF_8));
        JsonNode paths = root.path("paths");
        JsonNode schemas = root.path("components").path("schemas");

        assertThat(paths.path("/api/v1/me/attempts").path("get").path("operationId").asText())
                .isEqualTo("listHistory");
        assertThat(paths.path("/api/v1/me/attempts/{attemptId}/result").path("get").path("operationId").asText())
                .isEqualTo("getResult");
        assertThat(paths.path("/api/v1/me/attempts/{attemptId}/review").path("get").path("operationId").asText())
                .isEqualTo("getReview");
        assertThat(paths.path("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}").path("get").path("operationId").asText())
                .isEqualTo("downloadSolutionAsset");

        List<String> at4Schemas = List.of(
                "AttemptChoiceSummary",
                "AttemptResultResponse",
                "AttemptResultUnitStats",
                "AttemptResultPart",
                "AttemptResultSubject",
                "AttemptResultVactComponent",
                "AttemptResultVactSubsection",
                "AttemptReviewResponse",
                "AttemptReviewPart",
                "AttemptReviewItem",
                "AttemptReviewAttachment",
                "AttemptReviewQuestionBody",
                "AttemptReviewOption",
                "AttemptReviewClozeBlank",
                "AttemptReviewStatement",
                "AttemptReviewMatchingSide",
                "AttemptReviewExpectedAnswer",
                "AttemptReviewClozeKey",
                "AttemptReviewTrueFalseKey",
                "AttemptReviewMatchingKey",
                "AttemptReviewEssayFeedback",
                "AttemptReviewEssayCriterionScore",
                "ReviewGradingDetail",
                "ReviewClozeBlankResult",
                "ReviewStatementResult",
                "ReviewMatchingPairResult",
                "AttemptHistoryItem",
                "PageResponseAttemptHistoryItem");

        for (String schemaName : at4Schemas) {
            assertThat(schemas.has(schemaName))
                    .as("Expected schema %s in openapi.json", schemaName)
                    .isTrue();
        }

        // Result response schema must declare practiceScoreNote and choices, and never leak gradingError or secrets
        JsonNode resultProps = schemas.path("AttemptResultResponse").path("properties");
        assertThat(resultProps.has("practiceScoreNote")).isTrue();
        assertThat(resultProps.has("choices")).isTrue();
        assertThat(resultProps.has("gradingError")).isFalse();
        assertThat(resultProps.has("solution")).isFalse();
        assertThat(resultProps.has("gradingGuide")).isFalse();
        assertThat(resultProps.has("rubric")).isFalse();

        JsonNode vactComponentProps = schemas.path("AttemptResultVactComponent").path("properties");
        assertThat(vactComponentProps.has("correctRate")).isTrue();
        JsonNode vactSubsectionProps = schemas.path("AttemptResultVactSubsection").path("properties");
        assertThat(vactSubsectionProps.has("correctRate")).isTrue();

        String openApiRaw = Files.readString(openApiPath, StandardCharsets.UTF_8);
        assertThat(openApiRaw).doesNotContain("RIGHT_WRONG");

        String schemaDts = Files.readString(
                resolveWorkspaceFile(Path.of("packages", "api-client", "src", "schema.d.ts")),
                StandardCharsets.UTF_8);
        assertThat(schemaDts).doesNotContain("RIGHT_WRONG");

        assertThat(extractOperationBlock(schemaDts, "listHistory"))
                .contains("PageResponseAttemptHistoryItem");
        assertThat(extractOperationBlock(schemaDts, "getResult"))
                .contains("AttemptResultResponse");
        assertThat(extractOperationBlock(schemaDts, "getReview"))
                .contains("AttemptReviewResponse");
        extractOperationBlock(schemaDts, "downloadSolutionAsset");

        for (String schemaName : at4Schemas) {
            String schemaBlock = extractComponentSchemaBlock(schemaDts, schemaName);
            assertThat(schemaBlock)
                    .as("Schema %s must not degenerate to never", schemaName)
                    .doesNotContain(": never")
                    .doesNotContain("Record<string, never>");
        }
        assertThat(extractComponentSchemaBlock(schemaDts, "AttemptResultResponse"))
                .contains("practiceScoreNote?:")
                .contains("choices?:")
                .doesNotContain("gradingError");
        assertThat(extractComponentSchemaBlock(schemaDts, "AttemptHistoryItem"))
                .contains("choices?:")
                .contains("AttemptChoiceSummary");
        assertThat(extractComponentSchemaBlock(schemaDts, "AttemptReviewItem"))
                .contains("gradingDetail?:")
                .contains("ReviewGradingDetail");
    }

    @Test
    void at5AdminGradingOpsAndPreviewTrialContractsPresent() throws Exception {
        Path openApiPath = resolveWorkspaceFile(Path.of("packages", "api-client", "openapi.json"));
        JsonNode root = objectMapper.readTree(Files.readString(openApiPath, StandardCharsets.UTF_8));
        JsonNode paths = root.path("paths");
        JsonNode schemas = root.path("components").path("schemas");

        for (String path : List.of(
                "/api/v1/admin/grading/items",
                "/api/v1/admin/grading/items/counts",
                "/api/v1/admin/grading/items/{itemId}",
                "/api/v1/admin/grading/items/{itemId}/claim",
                "/api/v1/admin/grading/items/{itemId}/finalize",
                "/api/v1/admin/grading/items/{itemId}/reopen",
                "/api/v1/admin/attempts",
                "/api/v1/admin/attempts/{id}",
                "/api/v1/admin/attempts/{id}/regrade",
                "/api/v1/admin/attempts/{id}/fault",
                "/api/v1/admin/attempts/{id}/compensate",
                "/api/v1/admin/exams/{id}/preview/{previewId}/trial",
                "/api/v1/admin/exams/{id}/preview/{previewId}/paper",
                "/api/v1/admin/exams/{id}/preview/{previewId}/grade")) {
            assertThat(paths.has(path)).as("OpenAPI path %s present", path).isTrue();
        }

        // /api/v1/admin/attempts/{id}/compensate MUST require Idempotency-Key header
        JsonNode compensateParams =
                paths.path("/api/v1/admin/attempts/{id}/compensate").path("post").path("parameters");
        boolean foundRequiredIdempotency = false;
        for (JsonNode param : compensateParams) {
            if ("Idempotency-Key".equals(param.path("name").asText())
                    && "header".equals(param.path("in").asText())
                    && param.path("required").asBoolean()) {
                foundRequiredIdempotency = true;
            }
        }
        assertThat(foundRequiredIdempotency)
                .as("/compensate declares required Idempotency-Key header parameter")
                .isTrue();

        // Key AT-5 schemas exist and define expected required/properties in openapi.json
        assertThat(schemas.path("GradingQueueCountsResponse").path("required"))
                .extracting(JsonNode::asText)
                .contains("waiting", "inProgress", "done", "total");
        assertThat(schemas.path("GradingClaimedResponse").path("properties").fieldNames())
                .toIterable()
                .contains("claimedById", "claimedByName", "claimedAt", "lastActivityAt", "code", "message");
        JsonNode previewAnswersSchema =
                schemas.path("PreviewGradeRequest").path("properties").path("answers");
        assertThat(previewAnswersSchema.path("type").asText()).isEqualTo("object");
        assertThat(previewAnswersSchema.path("additionalProperties").path("oneOf").isArray())
                .as("PreviewGradeRequest.answers maps to polymorphic AnswerValue oneOf")
                .isTrue();

        String schemaDts = Files.readString(
                resolveWorkspaceFile(Path.of("packages", "api-client", "src", "schema.d.ts")),
                StandardCharsets.UTF_8);

        for (String opId : List.of(
                "listGradingItems",
                "getGradingItemCounts",
                "getGradingItemDetail",
                "claimGradingItem",
                "saveEssayDraft",
                "finalizeEssayGrade",
                "reopenEssayGrade",
                "listAdminAttempts",
                "getAdminAttemptDetail",
                "regradeAdminAttempt",
                "faultAdminAttempt",
                "compensateAdminAttempt",
                "extendForTrial",
                "getPaper",
                "grade")) {
            extractOperationBlock(schemaDts, opId);
        }

        assertThat(extractOperationBlock(schemaDts, "compensateAdminAttempt"))
                .contains("\"Idempotency-Key\": string;");

        for (String schemaName : List.of(
                "GradingQueueCountsResponse",
                "GradingQueueItemView",
                "GradingItemDetailResponse",
                "EssayGradeView",
                "SaveEssayDraftRequest",
                "FinalizeEssayRequest",
                "FinalizeEssayResponse",
                "ReopenEssayRequest",
                "GradingClaimedResponse",
                "AdminAttemptSummary",
                "AdminAttemptActionResponse",
                "FaultAttemptRequest",
                "CompensateAttemptRequest",
                "CompensateAttemptResponse",
                "PreviewTrialResponse",
                "PreviewTrialPaperResponse",
                "PreviewTrialPart",
                "PreviewTrialSegment",
                "PreviewTrialBlock",
                "PreviewTrialItem",
                "PreviewTrialQuestionSet",
                "PreviewTrialQuestion",
                "PreviewTrialAttachment",
                "PreviewGradeRequest",
                "PreviewGradeResponse",
                "PreviewGradedItem")) {
            String block = extractComponentSchemaBlock(schemaDts, schemaName);
            assertThat(block)
                    .as("Schema %s in schema.d.ts has no : never or Record<string, never>", schemaName)
                    .doesNotContain(": never")
                    .doesNotContain("Record<string, never>");
        }
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
