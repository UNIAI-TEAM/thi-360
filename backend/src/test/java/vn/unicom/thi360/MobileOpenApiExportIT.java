package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
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

@ActiveProfiles({"api", "openapi", "test"})
class MobileOpenApiExportIT extends AbstractIntegrationTest {

    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

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
        assertThat(exportedPaths.size()).isEqualTo(MOBILE_OPERATIONS.size());
        for (Map.Entry<String, List<String>> entry : MOBILE_OPERATIONS.entrySet()) {
            JsonNode pathItem = exportedPaths.path(entry.getKey());
            assertThat(pathItem.isObject()).as("path %s must exist", entry.getKey()).isTrue();
            for (String method : entry.getValue()) {
                JsonNode op = pathItem.path(method);
                assertThat(op.isObject()).as("%s %s must exist", method, entry.getKey()).isTrue();
                assertNoCookieParameters(entry.getKey(), method, op);
            }
        }
        assertThat(exportedPaths.has("/api/v1/auth/login")).isFalse();
        assertThat(exportedPaths.has("/api/v1/auth/refresh")).isFalse();
        assertThat(exportedPaths.has("/api/v1/auth/logout")).isFalse();
        assertThat(exportedPaths.path("/api/v1/me/profile").has("patch")).isFalse();
        assertThat(exportedRoot.path("components").path("schemas").has("MobileTokenResponse")).isTrue();
        assertThat(exportedRoot.path("components").path("schemas").has("ProfileResponse")).isTrue();

        Path targetOut = Path.of("target", "openapi-mobile.json");
        Files.createDirectories(targetOut.getParent());
        Files.writeString(targetOut, mobileOpenApiJson, StandardCharsets.UTF_8);
        assertThat(Files.readString(targetOut, StandardCharsets.UTF_8)).doesNotContain("\r");
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
        return filtered;
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
        ops.put("/api/v1/me/profile", List.of("get"));
        return ops;
    }
}
