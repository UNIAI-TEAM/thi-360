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
