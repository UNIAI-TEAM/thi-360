package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
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

        JsonNode tree = sortObjectKeys(objectMapper.readTree(rawJson));
        String openApiJson = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(tree);

        assertThat(openApiJson)
                .contains("\"/api/v1/ping\"")
                .contains("\"PingResponse\"")
                .contains("\"ApiError\"");
        assertObjectKeysSorted(objectMapper.readTree(openApiJson), "$");

        Path targetOut = Path.of("target", "openapi.json");
        Files.createDirectories(targetOut.getParent());
        Files.writeString(targetOut, openApiJson + "\n", StandardCharsets.UTF_8);
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
