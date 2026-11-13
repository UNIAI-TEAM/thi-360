package vn.unicom.thi360;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenApiExportLfTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializeDeterministicLf_usesLfOnlyAndSortsKeys() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {"z": 1, "a": {"d": [1, 2], "b": "ok"}}
                """);

        String json = OpenApiExportIT.serializeDeterministicLf(objectMapper, root);

        assertThat(json)
                .doesNotContain("\r")
                .endsWith("\n")
                .isEqualTo("""
                        {
                          "a" : {
                            "b" : "ok",
                            "d" : [ 1, 2 ]
                          },
                          "z" : 1
                        }
                        """);
    }
}
