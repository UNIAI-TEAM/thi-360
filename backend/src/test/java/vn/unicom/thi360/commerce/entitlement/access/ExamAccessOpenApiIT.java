package vn.unicom.thi360.commerce.entitlement.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import vn.unicom.thi360.AbstractIntegrationTest;

/**
 * The OpenAPI 3.1 document of the two exam-access APIs tells a typed client which fields can be JSON {@code null}: both
 * APIs write every field (an absent value is {@code null}, never missing), so each property that is not required must
 * accept {@code null} - as a type union, an {@code anyOf} with {@code null}, or an enum that lists {@code null}.
 */
@ActiveProfiles({"api", "openapi", "test"})
class ExamAccessOpenApiIT extends AbstractIntegrationTest {

    @Autowired private ObjectMapper mapper;

    @Test
    void everyOptionalFieldOfTheCardAndTheAccessRowAcceptsNull() throws Exception {
        JsonNode schemas = mapper.readTree(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).path("components").path("schemas");

        List<String> notNullable = new ArrayList<>();
        int checked = 0;
        for (String name : List.of("ExamAccessItem", "MyExamCard")) {
            JsonNode schema = schemas.path(name);
            assertThat(schema.isMissingNode()).as("schema %s", name).isFalse();
            List<String> required = new ArrayList<>();
            schema.path("required").forEach(field -> required.add(field.asText()));
            var names = schema.path("properties").fieldNames();
            while (names.hasNext()) {
                String property = names.next();
                if (required.contains(property)) {
                    continue;
                }
                checked++;
                if (!acceptsNull(schema.path("properties").path(property))) {
                    notNullable.add(name + "." + property);
                }
            }
        }

        assertThat(checked).as("optional fields looked at").isEqualTo(4 + 10);
        assertThat(notNullable).as("optional fields that do not accept null").isEmpty();
    }

    private static boolean acceptsNull(JsonNode property) {
        JsonNode type = property.path("type");
        boolean typeAllowsNull = type.isArray() && contains(type, "null");
        JsonNode enumValues = property.path("enum");
        if (typeAllowsNull) {
            // JSON Schema: a closed enum must list null too, or the union never matches a null.
            return !enumValues.isArray() || hasNullMember(enumValues);
        }
        for (JsonNode branch : property.path("anyOf")) {
            if ("null".equals(branch.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNullMember(JsonNode values) {
        for (JsonNode value : values) {
            if (value.isNull()) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(JsonNode array, String text) {
        for (JsonNode element : array) {
            if (text.equals(element.asText())) {
                return true;
            }
        }
        return false;
    }
}
