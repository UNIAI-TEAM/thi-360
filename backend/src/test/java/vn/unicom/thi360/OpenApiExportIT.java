package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"api", "openapi", "test"})
class OpenApiExportIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exportsOpenApiJsonToTarget() throws Exception {
        String rawJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String openApiJson = serializeDeterministicLf(objectMapper, objectMapper.readTree(rawJson));

        assertThat(openApiJson)
                .contains("\"/api/v1/ping\"")
                .contains("\"PingResponse\"")
                .contains("\"ApiError\"")
                .doesNotContain("\r");
        JsonNode root = objectMapper.readTree(openApiJson);
        assertObjectKeysSorted(root, "$");

        JsonNode schemas = root.path("components").path("schemas");
        assertThat(root.at("/paths/~1api~1v1~1admin~1exams~1{id}~1overview/put").isMissingNode()).isFalse();
        assertThat(root.at("/paths/~1api~1v1~1admin~1exams~1{id}~1structure~1summary/get").isMissingNode()).isFalse();
        assertThat(schemas.path("ExamTemplateResponse").path("properties").has("isDefault")).isTrue();
        assertThat(schemas.path("CreateExamRequest").path("properties").has("overview")).isTrue();
        assertThat(schemas.path("LoadExamTemplateRequest").path("properties").has("overviewPolicy")).isTrue();
        assertThat(schemas.path("ExamCheckRequest").path("properties").has("scope")).isTrue();
        JsonNode previewRequest = schemas.path("ExamPreviewRequest").path("properties");
        assertThat(previewRequest.path("pathId").path("deprecated").asBoolean()).isTrue();
        assertThat(previewRequest.path("subjectCodes").path("deprecated").asBoolean()).isTrue();
        assertThat(previewRequest.has("target")).isTrue();
        assertThat(previewRequest.has("basePreviewId")).isTrue();
        assertThat(previewRequest.has("regeneratePartIds")).isTrue();
        assertThat(schemas.path("ExamPreviewTarget").path("properties").has("subjectCodes")).isTrue();
        assertThat(schemas.path("ExamPreviewResult").path("properties").has("compliance")).isTrue();
        assertThat(schemas.path("ExamPreviewQuestion").path("properties").has("optionOrder")).isTrue();
        assertThat(schemas.path("ExamPreviewQuestion").path("properties").has("originalOptionOrder")).isTrue();
        assertThat(schemas.path("ExamPreviewQuestion").path("properties").has("originalNumber")).isTrue();
        assertThat(schemas.path("ExamPreviewItem").path("properties").has("originalOrder")).isTrue();
        assertThat(schemas.path("ExamStructureResponse").path("properties").has("sectionStates")).isTrue();
        assertThat(schemas.path("ExamCheckResult").path("properties").has("sectionStates")).isTrue();
        for (String[] nullableProperty : List.of(
                new String[] {"ExamPreviewResult", "compliance", "array"},
                new String[] {"ExamPreviewQuestion", "optionOrder", "array"},
                new String[] {"ExamPreviewQuestion", "originalOptionOrder", "array"},
                new String[] {"ExamPreviewQuestion", "originalNumber", "integer"},
                new String[] {"ExamPreviewItem", "originalOrder", "integer"},
                new String[] {"ExamStructureResponse", "sectionStates", "array"},
                new String[] {"ExamCheckResult", "sectionStates", "array"})) {
            JsonNode type = schemas.path(nullableProperty[0]).path("properties")
                    .path(nullableProperty[1]).path("type");
            assertThat(type).as("%s.%s type", nullableProperty[0], nullableProperty[1])
                    .extracting(JsonNode::asText).containsExactlyInAnyOrder(nullableProperty[2], "null");
        }
        JsonNode matching = schemas.path("MatchingBody").path("allOf").get(1).path("properties");
        assertThat(matching.has("shuffleLeft")).isFalse();
        assertThat(matching.has("shuffleRight")).isFalse();
        JsonNode versionParts = schemas.path("ExamVersionDetail").path("properties").path("parts");
        assertThat(versionParts.path("type").asText()).isEqualTo("array");
        assertThat(versionParts.path("items").path("$ref").asText()).isEqualTo("#/components/schemas/ExamPart");
        JsonNode createClassCode = schemas.path("CreateClassRequest").path("properties").path("code");
        assertThat(createClassCode.path("minLength").asInt()).isEqualTo(2);
        assertThat(createClassCode.path("maxLength").asInt()).isEqualTo(30);
        assertThat(createClassCode.path("pattern").asText())
                .isEqualTo("^[A-Za-z0-9][A-Za-z0-9._-]{1,29}$");

        JsonNode updateClassProps = schemas.path("UpdateClassRequest").path("properties");
        JsonNode updateClassCode = updateClassProps.path("code");
        assertThat(updateClassCode.path("minLength").asInt()).isEqualTo(2);
        assertThat(updateClassCode.path("maxLength").asInt()).isEqualTo(30);
        assertThat(updateClassCode.path("pattern").asText())
                .isEqualTo("^[A-Za-z0-9][A-Za-z0-9._-]{1,29}$");
        assertThat(updateClassProps.has("status")).isFalse();

        JsonNode columnKey = schemas.path("ImportRowError").path("properties").path("columnKey");
        assertThat(columnKey.path("type")).extracting(JsonNode::asText).containsExactly("string", "null");
        assertThat(columnKey.path("enum").get(columnKey.path("enum").size() - 1).isNull()).isTrue();

        // ID-1 (UNI-2710): đăng ký, xác minh và đổi email; hợp đồng lỗi mà FS-2 dùng (retryAfter, remainingAttempts).
        assertThat(root.at("/paths/~1api~1v1~1auth~1register/post").isMissingNode()).isFalse();
        JsonNode registerProps = schemas.path("RegisterRequest").path("properties");
        for (String property : List.of(
                "username", "fullName", "email", "password", "acceptTerms", "termsVersion", "callbackUrl")) {
            assertThat(registerProps.has(property)).as("RegisterRequest.%s", property).isTrue();
        }
        assertThat(registerProps.path("password").path("format").asText()).isEqualTo("password");
        assertThat(schemas.path("RegisterResponse").path("properties").has("redirectTo")).isTrue();
        JsonNode changeProps = schemas.path("EmailCodeChangeRequest").path("properties");
        assertThat(changeProps.has("newEmail")).isTrue();
        assertThat(changeProps.path("currentPassword").path("format").asText()).isEqualTo("password");
        assertThat(schemas.path("EmailCodeVerifyRequest").path("properties").has("code")).isTrue();
        JsonNode emailErrorProps = schemas.path("EmailCodeErrorResponse").path("properties");
        assertThat(emailErrorProps.has("retryAfter")).isTrue();
        assertThat(emailErrorProps.has("remainingAttempts")).isTrue();
        for (String path : List.of("verification", "verify", "change", "change~1confirm")) {
            assertThat(root.at("/paths/~1api~1v1~1me~1email~1" + path + "/post").isMissingNode())
                    .as("/api/v1/me/email/%s", path).isFalse();
        }
        for (String[] errorResponse : List.of(
                new String[] {"verification", "429"},
                new String[] {"change", "429"},
                new String[] {"verify", "422"},
                new String[] {"change~1confirm", "422"},
                new String[] {"change~1confirm", "503"})) {
            JsonNode content = root.at(
                    "/paths/~1api~1v1~1me~1email~1" + errorResponse[0] + "/post/responses/" + errorResponse[1] + "/content");
            assertThat(content.toString())
                    .as("%s %s response schema", errorResponse[0], errorResponse[1])
                    .contains("EmailCodeErrorResponse");
        }
        // ID-2 (UNI-2711): mã trạng thái thành công thật của các API quên/đặt lại mật khẩu và ảnh đại diện.
        JsonNode forgot = root.at("/paths/~1api~1v1~1auth~1password~1forgot/post");
        assertThat(forgot.path("operationId").asText()).isEqualTo("forgotPassword");
        assertThat(forgot.path("responses").has("202")).isTrue();
        assertThat(forgot.path("responses").has("200")).as("forgot has no 200").isFalse();
        assertThat(forgot.at("/responses/202/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PasswordForgotResponse");
        JsonNode resetPassword = root.at("/paths/~1api~1v1~1auth~1password~1reset/post");
        assertThat(resetPassword.path("operationId").asText()).isEqualTo("resetPasswordByToken");
        assertThat(resetPassword.path("responses").has("204")).isTrue();
        assertThat(resetPassword.path("responses").has("200")).as("reset has no 200").isFalse();
        assertThat(resetPassword.at("/responses/204/content").isMissingNode()).as("204 has no body").isTrue();
        JsonNode removeAvatar = root.at("/paths/~1api~1v1~1me~1avatar/delete");
        assertThat(removeAvatar.path("operationId").asText()).isEqualTo("removeMyAvatar");
        assertThat(removeAvatar.path("responses").has("204")).isTrue();
        assertThat(removeAvatar.path("responses").has("200")).as("delete avatar has no 200").isFalse();
        assertThat(root.at("/paths/~1api~1v1~1me~1avatar/put/responses/200/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/ProfileResponse");
        assertThat(root.at("/paths/~1api~1v1~1me~1profile/get/operationId").asText()).isEqualTo("getMyProfile");
        assertThat(root.at("/paths/~1api~1v1~1me~1profile/patch/operationId").asText()).isEqualTo("updateMyProfile");
        assertThat(root.at("/paths/~1api~1v1~1avatars~1{assetId}/get/responses/200/content/image~1png").isMissingNode())
                .isFalse();
        JsonNode profile = schemas.path("ProfileResponse").path("properties");
        for (String nullable : List.of("email", "phone", "avatarAssetId", "avatarUrl")) {
            assertThat(profile.path(nullable).path("type")).as("ProfileResponse.%s type", nullable)
                    .extracting(JsonNode::asText).containsExactlyInAnyOrder("string", "null");
        }
        assertThat(profile.path("avatarAssetId").path("format").asText()).isEqualTo("uuid");
        assertThat(profile.path("username").path("type").asText()).isEqualTo("string");
        assertThat(schemas.path("ProfileResponse").path("required").toString())
                .contains("username", "fullName", "emailVerified").doesNotContain("phone", "avatarUrl");

        Path targetOut = Path.of("target", "openapi.json");
        Files.createDirectories(targetOut.getParent());
        Files.writeString(targetOut, openApiJson, StandardCharsets.UTF_8);
        assertThat(Files.readString(targetOut, StandardCharsets.UTF_8)).doesNotContain("\r");
    }

    static String serializeDeterministicLf(ObjectMapper mapper, JsonNode root)
            throws JsonProcessingException {
        JsonNode tree = sortObjectKeys(root);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter()
                .withObjectIndenter(new DefaultIndenter("  ", "\n"));
        return mapper.writer(prettyPrinter).writeValueAsString(tree) + "\n";
    }

    private static JsonNode sortObjectKeys(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), sortObjectKeys(entry.getValue())));
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode(node.size());
            for (JsonNode element : node) {
                sorted.add(sortObjectKeys(element));
            }
            return sorted;
        }
        return node;
    }

    private static void assertObjectKeysSorted(JsonNode node, String path) {
        if (node.isObject()) {
            List<String> actualKeys = new ArrayList<>();
            node.fieldNames().forEachRemaining(actualKeys::add);
            List<String> expectedKeys = actualKeys.stream().sorted().toList();
            assertThat(actualKeys)
                    .as("Object keys at %s must be sorted", path)
                    .containsExactlyElementsOf(expectedKeys);
            node.properties().forEach(entry ->
                    assertObjectKeysSorted(entry.getValue(), path + "." + entry.getKey()));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                assertObjectKeysSorted(node.get(i), path + "[" + i + "]");
            }
        }
    }
}
