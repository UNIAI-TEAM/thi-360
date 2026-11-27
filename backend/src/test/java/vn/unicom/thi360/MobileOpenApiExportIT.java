package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import vn.unicom.thi360.mobilecontract.MobileFixtureCertifier;

@ActiveProfiles({"api", "openapi", "test"})
class MobileOpenApiExportIT extends AbstractIntegrationTest {

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
    private static final String ANSWER_VALUE_REF = SCHEMA_REF_PREFIX + "AnswerValue";
    private static final Set<String> ANSWER_VALUE_SUBTYPE_REFS = Set.of(
            SCHEMA_REF_PREFIX + "SingleChoiceAnswer",
            SCHEMA_REF_PREFIX + "MultipleChoiceAnswer",
            SCHEMA_REF_PREFIX + "ShortAnswerValue",
            SCHEMA_REF_PREFIX + "ClozeAnswer",
            SCHEMA_REF_PREFIX + "TrueFalseAnswer",
            SCHEMA_REF_PREFIX + "MatchingAnswer",
            SCHEMA_REF_PREFIX + "EssayAnswer");
    private static final Map<String, String> ATTEMPT_QUESTION_BODY_MAPPING = Map.of(
            "SINGLE_CHOICE", SCHEMA_REF_PREFIX + "SingleChoice",
            "MULTIPLE_CHOICE", SCHEMA_REF_PREFIX + "MultipleChoice",
            "SHORT_ANSWER", SCHEMA_REF_PREFIX + "ShortAnswer",
            "CLOZE", SCHEMA_REF_PREFIX + "Cloze",
            "QUESTION_SET", SCHEMA_REF_PREFIX + "QuestionSet",
            "TRUE_FALSE", SCHEMA_REF_PREFIX + "TrueFalse",
            "MATCHING", SCHEMA_REF_PREFIX + "Matching",
            "ESSAY", SCHEMA_REF_PREFIX + "Essay");

    private static final Map<String, List<String>> MOBILE_OPERATIONS = orderedMobileOperations();

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportsFilteredMobileOpenApiJsonToTarget() throws Exception {
        String rawJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode fullSpec = objectMapper.readTree(rawJson);
        ObjectNode filtered = filterForMobile(fullSpec);

        String mobileOpenApiJson = OpenApiExportIT.serializeDeterministicLf(objectMapper, filtered);
        assertThat(mobileOpenApiJson).doesNotContain("\r");

        JsonNode exportedRoot = objectMapper.readTree(mobileOpenApiJson);
        JsonNode exportedPaths = exportedRoot.path("paths");
        assertThat(MOBILE_OPERATIONS).hasSize(49);
        assertThat(exportedPaths.size()).isEqualTo(MOBILE_OPERATIONS.size());
        for (Map.Entry<String, List<String>> entry : MOBILE_OPERATIONS.entrySet()) {
            JsonNode pathItem = exportedPaths.path(entry.getKey());
            assertThat(pathItem.isObject()).as("path %s must exist", entry.getKey()).isTrue();
            assertThat(pathItem.size())
                    .as("path %s must expose only declared mobile methods", entry.getKey())
                    .isEqualTo(entry.getValue().size());
            for (String method : entry.getValue()) {
                JsonNode op = pathItem.path(method);
                assertThat(op.isObject()).as("%s %s must exist", method, entry.getKey()).isTrue();
                assertNoCookieParameters(entry.getKey(), method, op);
            }
        }
        assertThat(exportedPaths.has("/api/v1/auth/login")).isFalse();
        assertThat(exportedPaths.has("/api/v1/auth/refresh")).isFalse();
        assertThat(exportedPaths.has("/api/v1/auth/logout")).isFalse();
        assertThat(exportedPaths.has("/api/v1/public/storefront/covers/{assetId}")).isFalse();
        assertThat(exportedPaths.path("/api/v1/me/profile").has("patch")).isTrue();
        assertThat(exportedPaths.path("/api/v1/me/avatar").has("put")).isTrue();
        assertThat(exportedPaths.path("/api/v1/me/avatar").has("delete")).isTrue();
        assertThat(exportedPaths.path("/api/v1/avatars/{assetId}").has("get")).isTrue();
        assertThat(exportedPaths.path("/api/v1/me/email/verification").has("post")).isTrue();
        assertThat(exportedPaths.path("/api/v1/me/email/verify").has("post")).isTrue();
        assertThat(exportedPaths.path("/api/v1/me/email/change").has("post")).isTrue();
        assertThat(exportedPaths.path("/api/v1/me/email/change/confirm").has("post")).isTrue();
        JsonNode deletionCheckOp = exportedPaths.path("/api/v1/me/account/deletion-check").path("get");
        assertThat(deletionCheckOp.isObject()).isTrue();
        for (String code : List.of("200", "401", "403")) {
            assertThat(deletionCheckOp.path("responses").has(code))
                    .as("GET /api/v1/me/account/deletion-check response %s", code)
                    .isTrue();
        }
        JsonNode deletionOp = exportedPaths.path("/api/v1/me/account/deletion").path("post");
        assertThat(deletionOp.isObject()).isTrue();
        for (String code : List.of("204", "400", "401", "403", "409", "429")) {
            assertThat(deletionOp.path("responses").has(code))
                    .as("POST /api/v1/me/account/deletion response %s", code)
                    .isTrue();
        }

        JsonNode startAttemptOp = exportedPaths.path("/api/v1/me/attempts").path("post");
        assertRequiredHeader(startAttemptOp, "Idempotency-Key");
        assertThat(startAttemptOp.path("responses").has("202")).isTrue();

        JsonNode schemas = exportedRoot.path("components").path("schemas");
        for (String requiredSchema : List.of(
                "MobileTokenResponse",
                "ProfileResponse",
                "ProfileUpdateRequest",
                "EmailCodeChangeRequest",
                "EmailCodeVerifyRequest",
                "EmailCodeErrorResponse",
                "AttemptSessionConflictResponse",
                "AttemptAudioPlayView",
                "AudioPolicyView",
                "AttemptResultResponse",
                "AttemptReviewResponse",
                "MyExamsResponse",
                "PageResponseAttemptHistoryItem",
                "CatalogOptionResponse",
                "AccountDeletionCheckResponse",
                "AccountDeletionRequest",
                "AccountDeletionBlockedResponse")) {
            assertThat(schemas.has(requiredSchema))
                    .as("mobile schema %s must exist", requiredSchema)
                    .isTrue();
        }
        JsonNode deletionCheckProps = schemas.path("AccountDeletionCheckResponse").path("properties");
        for (String prop : List.of("canDelete", "blockers", "unusedAttempts", "pendingGrading")) {
            assertThat(deletionCheckProps.has(prop))
                    .as("AccountDeletionCheckResponse.%s must exist", prop)
                    .isTrue();
        }
        JsonNode blockerEnum = deletionCheckProps.path("blockers").path("items").path("enum");
        if (blockerEnum.isMissingNode() || !blockerEnum.isArray()) {
            blockerEnum = schemas.path("AccountDeletionBlocker").path("enum");
        }
        assertThat(blockerEnum)
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "ATTEMPT_IN_PROGRESS", "ORDER_BEING_CHECKED", "ORDER_PENDING_PAYMENT");
        JsonNode fullNameSchema = schemas.path("ProfileUpdateRequest").path("properties").path("fullName");
        assertThat(fullNameSchema.path("maxLength").asInt()).isEqualTo(200);
        assertThat(fullNameSchema.path("description").asText()).contains("200");

        assertNoObjectTypedAdditionalProperties(exportedRoot);
        assertAnswerValueUsesDiscriminatorRef(exportedRoot);
        assertAttemptQuestionBodyUsesDiscriminatorWithoutCycle(schemas);
        assertOrderPriceChangedQuoteIsOptional(schemas);

        Path targetOut = Path.of("target", "openapi-mobile.json");
        Files.createDirectories(targetOut.getParent());
        Files.writeString(targetOut, mobileOpenApiJson, StandardCharsets.UTF_8);
        assertThat(Files.readString(targetOut, StandardCharsets.UTF_8)).doesNotContain("\r");
    }

    @Test
    void committedContractHasNoObjectTypedAdditionalProperties() throws IOException {
        assertNoObjectTypedAdditionalProperties(readCommittedMobileContract());
    }

    @Test
    void committedContractReferencesAnswerValueSchema() throws IOException {
        assertAnswerValueUsesDiscriminatorRef(readCommittedMobileContract());
    }

    @Test
    void committedContractNormalizesAttemptQuestionBodyDiscriminator() throws IOException {
        assertAttemptQuestionBodyUsesDiscriminatorWithoutCycle(
                readCommittedMobileContract().path("components").path("schemas"));
    }

    @Test
    void committedContractMakesOrderPriceChangedQuoteOptionalWithLfEndings() throws IOException {
        Path committedPath = MobileFixtureCertifier.contractsMobileDir().resolve("openapi-mobile.json");
        String content = Files.readString(committedPath, StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("\r");
        assertOrderPriceChangedQuoteIsOptional(
                objectMapper.readTree(content).path("components").path("schemas"));
    }

    private JsonNode readCommittedMobileContract() throws IOException {
        Path committedPath = MobileFixtureCertifier.contractsMobileDir().resolve("openapi-mobile.json");
        assertThat(committedPath).as("contracts/mobile/openapi-mobile.json must exist").isRegularFile();
        return objectMapper.readTree(Files.readString(committedPath, StandardCharsets.UTF_8));
    }

    private static ObjectNode filterForMobile(JsonNode fullSpec) {
        ObjectNode filtered = JsonNodeFactory.instance.objectNode();
        copyIfPresent(fullSpec, filtered, "openapi");
        copyIfPresent(fullSpec, filtered, "info");
        copyIfPresent(fullSpec, filtered, "servers");
        copyIfPresent(fullSpec, filtered, "security");

        JsonNode sourcePaths = fullSpec.path("paths");
        ObjectNode targetPaths = JsonNodeFactory.instance.objectNode();
        Set<String> usedTags = new TreeSet<>();

        for (Map.Entry<String, List<String>> entry : MOBILE_OPERATIONS.entrySet()) {
            String pathKey = entry.getKey();
            JsonNode sourcePathItem = sourcePaths.path(pathKey);
            assertThat(sourcePathItem.isObject())
                    .as("Expected mobile path %s in /v3/api-docs", pathKey)
                    .isTrue();

            ObjectNode targetPathItem = JsonNodeFactory.instance.objectNode();
            for (String method : entry.getValue()) {
                JsonNode operation = sourcePathItem.path(method);
                assertThat(operation.isObject())
                        .as("Expected %s %s in /v3/api-docs", method, pathKey)
                        .isTrue();
                assertNoCookieParameters(pathKey, method, operation);
                targetPathItem.set(method, operation.deepCopy());
                JsonNode opTags = operation.path("tags");
                if (opTags.isArray()) {
                    for (JsonNode tag : opTags) {
                        usedTags.add(tag.asText());
                    }
                }
            }
            targetPaths.set(pathKey, targetPathItem);
        }
        filtered.set("paths", targetPaths);

        if (fullSpec.has("tags") && fullSpec.get("tags").isArray()) {
            ArrayNode filteredTags = JsonNodeFactory.instance.arrayNode();
            for (JsonNode tag : fullSpec.get("tags")) {
                if (usedTags.contains(tag.path("name").asText())) {
                    filteredTags.add(tag.deepCopy());
                }
            }
            if (!filteredTags.isEmpty()) {
                filtered.set("tags", filteredTags);
            }
        }

        JsonNode sourceComponents = fullSpec.path("components");
        if (sourceComponents.isObject()) {
            ObjectNode targetComponents = JsonNodeFactory.instance.objectNode();
            copyIfPresent(sourceComponents, targetComponents, "securitySchemes");

            JsonNode allSchemas = sourceComponents.path("schemas");
            if (allSchemas.isObject()) {
                Set<String> reachableSchemas = collectReachableSchemas(targetPaths, allSchemas);
                ObjectNode filteredSchemas = JsonNodeFactory.instance.objectNode();
                for (String schemaName : reachableSchemas) {
                    JsonNode schema = allSchemas.get(schemaName);
                    assertThat(schema).as("Referenced schema %s must exist", schemaName).isNotNull();
                    filteredSchemas.set(schemaName, schema.deepCopy());
                }
                targetComponents.set("schemas", filteredSchemas);
            }
            filtered.set("components", targetComponents);
        }
        normalizeForMobileGenerators(filtered);
        return filtered;
    }

    private static void normalizeForMobileGenerators(ObjectNode filteredSpec) {
        normalizeArbitraryObjectMaps(filteredSpec);
        normalizeInlineAnswerValueOneOf(filteredSpec);
        JsonNode schemasNode = filteredSpec.path("components").get("schemas");
        if (schemasNode instanceof ObjectNode schemas) {
            normalizeAttemptQuestionBody(schemas);
            normalizeOrderPriceChangedResponse(schemas);
        }
    }

    private static void normalizeArbitraryObjectMaps(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            if ("object".equals(obj.path("type").asText())
                    && isBareObjectSchema(obj.get("additionalProperties"))) {
                obj.set("additionalProperties", BooleanNode.TRUE);
            }
            obj.properties().forEach(entry -> normalizeArbitraryObjectMaps(entry.getValue()));
        } else if (node instanceof ArrayNode array) {
            for (JsonNode item : array) {
                normalizeArbitraryObjectMaps(item);
            }
        }
    }

    private static boolean isBareObjectSchema(JsonNode node) {
        return node != null
                && node.isObject()
                && node.size() == 1
                && "object".equals(node.path("type").asText());
    }

    private static void normalizeInlineAnswerValueOneOf(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            if (isAnswerValueOneOf(obj.get("oneOf"))) {
                obj.removeAll();
                obj.put("$ref", ANSWER_VALUE_REF);
                return;
            }
            obj.properties().forEach(entry -> normalizeInlineAnswerValueOneOf(entry.getValue()));
        } else if (node instanceof ArrayNode array) {
            for (JsonNode item : array) {
                normalizeInlineAnswerValueOneOf(item);
            }
        }
    }

    private static boolean isAnswerValueOneOf(JsonNode oneOf) {
        if (oneOf == null || !oneOf.isArray() || oneOf.size() != ANSWER_VALUE_SUBTYPE_REFS.size()) {
            return false;
        }
        Set<String> refs = new LinkedHashSet<>();
        for (JsonNode branch : oneOf) {
            if (!branch.isObject() || branch.size() != 1) {
                return false;
            }
            JsonNode ref = branch.get("$ref");
            if (ref == null || !ref.isTextual()) {
                return false;
            }
            refs.add(ref.asText());
        }
        return refs.equals(ANSWER_VALUE_SUBTYPE_REFS);
    }

    private static void normalizeAttemptQuestionBody(ObjectNode schemas) {
        JsonNode bodyNode = schemas.get("AttemptQuestionBody");
        if (bodyNode instanceof ObjectNode body) {
            JsonNode description = body.get("description");
            body.removeAll();
            if (description != null) {
                body.set("description", description);
            }
            body.put("type", "object");
            ArrayNode required = JsonNodeFactory.instance.arrayNode();
            required.add("type");
            body.set("required", required);

            ObjectNode typeProperty = JsonNodeFactory.instance.objectNode();
            typeProperty.put("type", "string");
            ObjectNode properties = JsonNodeFactory.instance.objectNode();
            properties.set("type", typeProperty);
            body.set("properties", properties);

            ObjectNode mapping = JsonNodeFactory.instance.objectNode();
            ATTEMPT_QUESTION_BODY_MAPPING.forEach(mapping::put);
            ObjectNode discriminator = JsonNodeFactory.instance.objectNode();
            discriminator.put("propertyName", "type");
            discriminator.set("mapping", mapping);
            body.set("discriminator", discriminator);
        }

        for (String subtypeRef : ATTEMPT_QUESTION_BODY_MAPPING.values()) {
            String subtypeName = subtypeRef.substring(SCHEMA_REF_PREFIX.length());
            JsonNode subtypeNode = schemas.get(subtypeName);
            if (subtypeNode != null && subtypeNode.path("allOf").isArray()) {
                for (JsonNode part : subtypeNode.path("allOf")) {
                    if (part instanceof ObjectNode partObj) {
                        if (partObj.get("properties") instanceof ObjectNode subtypeProps) {
                            subtypeProps.remove("type");
                        }
                        if (partObj.get("required") instanceof ArrayNode subtypeReq) {
                            ArrayNode kept = removeStringElement(subtypeReq, "type");
                            if (kept.isEmpty()) {
                                partObj.remove("required");
                            } else {
                                partObj.set("required", kept);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void normalizeOrderPriceChangedResponse(ObjectNode schemas) {
        JsonNode node = schemas.get("OrderPriceChangedResponse");
        if (node instanceof ObjectNode schema && schema.get("required") instanceof ArrayNode required) {
            schema.set("required", removeStringElement(required, "quote"));
        }
    }

    private static ArrayNode removeStringElement(ArrayNode source, String target) {
        ArrayNode filtered = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : source) {
            if (!item.isTextual() || !target.equals(item.asText())) {
                filtered.add(item.deepCopy());
            }
        }
        return filtered;
    }

    private static void assertNoObjectTypedAdditionalProperties(JsonNode root) {
        List<String> offendingPaths = new ArrayList<>();
        collectObjectTypedAdditionalProperties(root, "$", offendingPaths);
        assertThat(offendingPaths)
                .as("openapi-mobile.json must not contain additionalProperties: {\"type\":\"object\"}")
                .isEmpty();

        JsonNode schemas = root.path("components").path("schemas");
        for (String[] propertyPath : List.of(
                new String[] {"AttemptPartStateView", "choice"},
                new String[] {"AttemptStateResponse", "choices"},
                new String[] {"AttemptStateResponse", "lastPosition"},
                new String[] {"OrderDetail", "benefits"},
                new String[] {"OrderItemView", "benefits"})) {
            JsonNode prop = schemas.path(propertyPath[0]).path("properties").path(propertyPath[1]);
            assertThat(prop.path("type").asText())
                    .as("%s.%s type", propertyPath[0], propertyPath[1])
                    .isEqualTo("object");
            JsonNode additionalProps = prop.get("additionalProperties");
            assertThat(additionalProps != null && additionalProps.isBoolean() && additionalProps.asBoolean())
                    .as("%s.%s must declare additionalProperties: true", propertyPath[0], propertyPath[1])
                    .isTrue();
        }
        assertThat(schemas.path("ClozeAnswer").path("allOf").path(1).path("properties")
                        .path("blanks").path("additionalProperties").path("type").asText())
                .isEqualTo("string");
        assertThat(schemas.path("AttemptHistoryItem").path("properties").path("choices")
                        .path("additionalProperties").path("$ref").asText())
                .isEqualTo(SCHEMA_REF_PREFIX + "AttemptChoiceSummary");
    }

    private static void collectObjectTypedAdditionalProperties(
            JsonNode node, String path, List<String> offendingPaths) {
        if (node.isObject()) {
            if (isBareObjectSchema(node.get("additionalProperties"))) {
                offendingPaths.add(path);
            }
            node.properties().forEach(entry ->
                    collectObjectTypedAdditionalProperties(
                            entry.getValue(), path + "." + entry.getKey(), offendingPaths));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectObjectTypedAdditionalProperties(node.get(i), path + "[" + i + "]", offendingPaths);
            }
        }
    }

    private static void assertAnswerValueUsesDiscriminatorRef(JsonNode root) {
        JsonNode schemas = root.path("components").path("schemas");
        for (String[] propertyPath : List.of(
                new String[] {"AnswerChange", "answer"},
                new String[] {"AttemptItemView", "answer"},
                new String[] {"AttemptReviewItem", "studentAnswer"},
                new String[] {"FinalAnswerChange", "answer"})) {
            JsonNode prop = schemas.path(propertyPath[0]).path("properties").path(propertyPath[1]);
            assertThat(prop.has("oneOf"))
                    .as("%s.%s must not emit undispatched inline oneOf", propertyPath[0], propertyPath[1])
                    .isFalse();
            assertThat(prop.path("$ref").asText())
                    .as("%s.%s must reference AnswerValue", propertyPath[0], propertyPath[1])
                    .isEqualTo(ANSWER_VALUE_REF);
        }

        List<String> inlineAnswerOneOfPaths = new ArrayList<>();
        collectInlineAnswerValueOneOf(root, "$", inlineAnswerOneOfPaths);
        assertThat(inlineAnswerOneOfPaths)
                .as("No schema may retain undispatched inline AnswerValue oneOf")
                .isEmpty();

        JsonNode answerValue = schemas.path("AnswerValue");
        assertThat(answerValue.path("discriminator").path("propertyName").asText()).isEqualTo("type");
        Set<String> mappedRefs = new LinkedHashSet<>();
        answerValue.path("discriminator").path("mapping")
                .properties()
                .forEach(entry -> mappedRefs.add(entry.getValue().asText()));
        assertThat(mappedRefs).containsExactlyInAnyOrderElementsOf(ANSWER_VALUE_SUBTYPE_REFS);
        for (String subtypeRef : ANSWER_VALUE_SUBTYPE_REFS) {
            String subtypeName = subtypeRef.substring(SCHEMA_REF_PREFIX.length());
            assertThat(schemas.has(subtypeName))
                    .as("AnswerValue subtype schema %s must remain reachable", subtypeName)
                    .isTrue();
        }
    }

    private static void collectInlineAnswerValueOneOf(
            JsonNode node, String path, List<String> offendingPaths) {
        if (node.isObject()) {
            if (isAnswerValueOneOf(node.get("oneOf"))) {
                offendingPaths.add(path);
            }
            node.properties().forEach(entry ->
                    collectInlineAnswerValueOneOf(
                            entry.getValue(), path + "." + entry.getKey(), offendingPaths));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectInlineAnswerValueOneOf(node.get(i), path + "[" + i + "]", offendingPaths);
            }
        }
    }

    private static void assertAttemptQuestionBodyUsesDiscriminatorWithoutCycle(JsonNode schemas) {
        JsonNode body = schemas.path("AttemptQuestionBody");
        assertThat(body.isObject()).as("AttemptQuestionBody must exist").isTrue();
        assertThat(body.has("oneOf")).as("AttemptQuestionBody must not declare cyclic oneOf").isFalse();
        assertThat(body.has("allOf")).as("AttemptQuestionBody must not declare cyclic allOf").isFalse();
        assertThat(body.path("description").asText()).isNotBlank();
        assertThat(body.path("type").asText()).isEqualTo("object");
        assertThat(body.path("required")).extracting(JsonNode::asText).containsExactly("type");
        assertThat(body.path("properties").size()).isEqualTo(1);
        assertThat(body.path("properties").path("type").path("type").asText()).isEqualTo("string");

        JsonNode discriminator = body.path("discriminator");
        assertThat(discriminator.path("propertyName").asText()).isEqualTo("type");
        Map<String, String> actualMapping = new LinkedHashMap<>();
        discriminator.path("mapping")
                .properties()
                .forEach(entry -> actualMapping.put(entry.getKey(), entry.getValue().asText()));
        assertThat(actualMapping).containsExactlyInAnyOrderEntriesOf(ATTEMPT_QUESTION_BODY_MAPPING);

        for (String subtypeRef : ATTEMPT_QUESTION_BODY_MAPPING.values()) {
            String subtypeName = subtypeRef.substring(SCHEMA_REF_PREFIX.length());
            JsonNode subtype = schemas.path(subtypeName);
            assertThat(subtype.isObject()).as("subtype %s must exist", subtypeName).isTrue();
            assertThat(subtype.has("oneOf")).as("subtype %s must not declare oneOf", subtypeName).isFalse();
            JsonNode allOf = subtype.path("allOf");
            assertThat(allOf.isArray() && allOf.size() >= 2)
                    .as("subtype %s must use allOf inheritance", subtypeName)
                    .isTrue();
            assertThat(allOf.get(0).path("$ref").asText())
                    .as("subtype %s allOf[0] must reference AttemptQuestionBody", subtypeName)
                    .isEqualTo(SCHEMA_REF_PREFIX + "AttemptQuestionBody");
            JsonNode subtypeProps = allOf.get(1).path("properties");
            assertThat(subtypeProps.has("type"))
                    .as("subtype %s allOf[1].properties must not redeclare type", subtypeName)
                    .isFalse();
            assertThat(subtypeProps.size())
                    .as("subtype %s must retain its own properties", subtypeName)
                    .isPositive();
        }
    }

    private static void assertOrderPriceChangedQuoteIsOptional(JsonNode schemas) {
        JsonNode schema = schemas.path("OrderPriceChangedResponse");
        assertThat(schema.isObject()).as("OrderPriceChangedResponse must exist").isTrue();
        assertThat(schema.path("properties").path("quote").path("$ref").asText())
                .isEqualTo(SCHEMA_REF_PREFIX + "PriceQuote");
        assertThat(schema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("code", "fieldErrors", "message")
                .doesNotContain("quote");
    }

    private static Set<String> collectReachableSchemas(JsonNode root, JsonNode allSchemas) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        findSchemaRefs(root, queue);
        while (!queue.isEmpty()) {
            String schemaName = queue.removeFirst();
            if (visited.add(schemaName)) {
                JsonNode schema = allSchemas.get(schemaName);
                if (schema != null) {
                    findSchemaRefs(schema, queue);
                }
            }
        }
        return visited;
    }

    private static void findSchemaRefs(JsonNode node, Deque<String> queue) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual() && ref.asText().startsWith(SCHEMA_REF_PREFIX)) {
                queue.addLast(ref.asText().substring(SCHEMA_REF_PREFIX.length()));
            }
            node.properties().forEach(entry -> findSchemaRefs(entry.getValue(), queue));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                findSchemaRefs(item, queue);
            }
        }
    }

    private static void assertNoCookieParameters(String path, String method, JsonNode operation) {
        JsonNode params = operation.path("parameters");
        if (params.isArray()) {
            for (JsonNode param : params) {
                assertThat(param.path("in").asText())
                        .as("Operation %s %s must not declare cookie parameters", method, path)
                        .isNotEqualTo("cookie");
            }
        }
    }

    private static void copyIfPresent(JsonNode source, ObjectNode target, String field) {
        if (source.has(field)) {
            target.set(field, source.get(field).deepCopy());
        }
    }

    private static void assertRequiredHeader(JsonNode operation, String headerName) {
        JsonNode params = operation.path("parameters");
        assertThat(params.isArray()).as("parameters must be an array").isTrue();
        boolean found = false;
        for (JsonNode param : params) {
            if ("header".equals(param.path("in").asText()) && headerName.equals(param.path("name").asText())) {
                assertThat(param.path("required").asBoolean())
                        .as("header %s must be required", headerName)
                        .isTrue();
                found = true;
            }
        }
        assertThat(found).as("required header %s must exist", headerName).isTrue();
    }

    private static Map<String, List<String>> orderedMobileOperations() {
        Map<String, List<String>> ops = new LinkedHashMap<>();
        ops.put("/api/v1/auth/mobile/login", List.of("post"));
        ops.put("/api/v1/auth/mobile/refresh", List.of("post"));
        ops.put("/api/v1/auth/mobile/logout", List.of("post"));
        ops.put("/api/v1/auth/mobile/register/verify", List.of("post"));
        ops.put("/api/v1/auth/register", List.of("post"));
        ops.put("/api/v1/auth/register/resend", List.of("post"));
        ops.put("/api/v1/auth/password/forgot", List.of("post"));
        ops.put("/api/v1/auth/password/reset", List.of("post"));
        ops.put("/api/v1/auth/change-password", List.of("post"));
        ops.put("/api/v1/me", List.of("get"));
        ops.put("/api/v1/me/profile", List.of("get", "patch"));
        ops.put("/api/v1/me/avatar", List.of("put", "delete"));
        ops.put("/api/v1/avatars/{assetId}", List.of("get"));
        ops.put("/api/v1/me/email/verification", List.of("post"));
        ops.put("/api/v1/me/email/verify", List.of("post"));
        ops.put("/api/v1/me/email/change", List.of("post"));
        ops.put("/api/v1/me/email/change/confirm", List.of("post"));
        ops.put("/api/v1/me/account/deletion-check", List.of("get"));
        ops.put("/api/v1/me/account/deletion", List.of("post"));
        ops.put("/api/v1/public/storefront/filters", List.of("get"));
        ops.put("/api/v1/public/storefront/products", List.of("get"));
        ops.put("/api/v1/public/storefront/products/{code}", List.of("get"));
        ops.put("/api/v1/me/exam-access/query", List.of("post"));
        ops.put("/api/v1/me/price-quote", List.of("post"));
        ops.put("/api/v1/me/orders", List.of("get", "post"));
        ops.put("/api/v1/me/orders/{code}", List.of("get"));
        ops.put("/api/v1/me/orders/{code}/pay", List.of("post"));
        ops.put("/api/v1/me/orders/{code}/cancel", List.of("post"));
        ops.put("/api/v1/public/payments/vnpay/return", List.of("get"));
        ops.put("/api/v1/me/exams", List.of("get"));
        ops.put("/api/v1/me/attempts", List.of("get", "post"));
        ops.put("/api/v1/catalog/options", List.of("get"));
        ops.put("/api/v1/me/entitlements/{entitlementId}/prepare", List.of("get"));
        ops.put("/api/v1/me/attempts/{attemptId}", List.of("get"));
        ops.put("/api/v1/me/attempts/{attemptId}/session", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/heartbeat", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/answers", List.of("put"));
        ops.put("/api/v1/me/attempts/{attemptId}/parts/{partId}/finish", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/submit", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/parts/{partId}/choice", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/parts/{partId}/retry", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/assets/{assetId}", List.of("get"));
        ops.put("/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}", List.of("get"));
        ops.put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/end", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/progress", List.of("put"));
        ops.put("/api/v1/me/attempts/{attemptId}/audio/{assetId}/plays/{playId}/resume", List.of("post"));
        ops.put("/api/v1/me/attempts/{attemptId}/result", List.of("get"));
        ops.put("/api/v1/me/attempts/{attemptId}/review", List.of("get"));
        return ops;
    }
}
