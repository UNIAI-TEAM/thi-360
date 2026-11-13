package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

        JsonNode tree = objectMapper.readTree(rawJson);
        String openApiJson = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(tree);

        assertThat(openApiJson)
                .contains("\"/api/v1/ping\"")
                .contains("\"PingResponse\"")
                .contains("\"ApiError\"");

        Path targetOut = Path.of("target", "openapi.json");
        Files.createDirectories(targetOut.getParent());
        Files.writeString(targetOut, openApiJson + "\n", StandardCharsets.UTF_8);
    }
}
