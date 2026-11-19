package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/** BE-16 R1-6: the structure 422 message and every field error come from the bundles in the request locale. */
@Transactional
class ExamStructureLocaleIT extends AbstractIntegrationTest {
    private static final String EXAM = "/api/v1/admin/exams/00000000-0000-7000-8700-000000000001";
    private static final Locale VI = Locale.of("vi");
    private static final Locale EN = Locale.of("en");

    @Autowired private ObjectMapper mapper;
    @Autowired private MessageSource messages;

    @Test
    void segmentGapIsLocalisedInViAndEn() throws Exception {
        JsonNode current = load();
        ArrayNode parts = segmentedParts(current, 3, 5);
        String position = "5";
        for (Locale locale : new Locale[] {VI, EN}) {
            JsonNode body = rejected(current, parts, locale);
            assertThat(body.path("code").asText()).isEqualTo("EXAM_STRUCTURE_INVALID");
            assertThat(body.path("message").asText()).isEqualTo(
                    messages.getMessage("EXAM_STRUCTURE_INVALID", null, locale));
            JsonNode error = body.path("fieldErrors").get(0);
            assertThat(error.path("code").asText()).isEqualTo("SEGMENT_GAP");
            assertThat(error.path("field").asText()).isEqualTo("parts[0].displaySegments[1]");
            assertThat(error.path("message").asText()).isEqualTo(
                    messages.getMessage("SEGMENT_GAP", new Object[] {position}, locale));
        }
        assertThat(rejected(current, parts, VI).path("fieldErrors").get(0).path("message").asText())
                .doesNotContain("Display segments").contains("câu 5");
        assertThat(rejected(current, parts, EN).path("fieldErrors").get(0).path("message").asText())
                .contains("gap before question 5").doesNotContain("Các phân đoạn");
        assertThat(rejected(current, parts, VI).path("message").asText())
                .isNotEqualTo(rejected(current, parts, EN).path("message").asText());
    }

    @Test
    void missingPartTotalsAndGenericFieldErrorsAreLocalisedToo() throws Exception {
        JsonNode current = load();
        int count = current.path("parts").get(0).path("questionCount").asInt();
        ArrayNode noCount = segmentedParts(current, count / 2, count / 2 + 1);
        ((ObjectNode) noCount.get(0)).remove("questionCount");
        ArrayNode negative = current.path("parts").deepCopy();
        ((ObjectNode) negative.get(0)).put("durationMinutes", -1);

        for (Locale locale : new Locale[] {VI, EN}) {
            JsonNode missing = rejected(current, noCount, locale).path("fieldErrors").get(0);
            assertThat(missing.path("code").asText()).isEqualTo("SEGMENT_COVERAGE");
            assertThat(missing.path("message").asText())
                    .isEqualTo(messages.getMessage("SEGMENT_COVERAGE_TOTAL_MISSING", null, locale));

            JsonNode generic = rejected(current, negative, locale);
            assertThat(generic.path("message").asText())
                    .isEqualTo(messages.getMessage("EXAM_STRUCTURE_INVALID", null, locale));
            JsonNode error = generic.path("fieldErrors").get(0);
            assertThat(error.path("code").asText()).isEqualTo("STRUCTURE_FIELD_INVALID");
            assertThat(error.path("message").asText()).isEqualTo(messages.getMessage(
                    "STRUCTURE_FIELD_INVALID", new Object[] {"parts.durationMinutes"}, locale));
        }
        assertThat(rejected(current, negative, VI).path("fieldErrors").get(0).path("message").asText())
                .startsWith("Giá trị không hợp lệ");
        assertThat(rejected(current, negative, EN).path("fieldErrors").get(0).path("message").asText())
                .startsWith("Invalid value");
    }

    /** Segment 1..{@code firstTo}, then {@code secondFrom}..the part's last question. */
    private ArrayNode segmentedParts(JsonNode current, int firstTo, int secondFrom) {
        ArrayNode parts = current.path("parts").deepCopy();
        ObjectNode part = (ObjectNode) parts.get(0);
        int count = part.path("questionCount").asInt();
        BigDecimal half = part.path("maxPoints").decimalValue().divide(BigDecimal.valueOf(2));
        part.putArray("displaySegments").add(segment("P1", 1, firstTo, half))
                .add(segment("P2", secondFrom, count, half));
        return parts;
    }

    private ObjectNode segment(String code, int from, int to, BigDecimal points) {
        return mapper.createObjectNode().put("code", code).put("name", code).put("fromPosition", from)
                .put("toPosition", to).put("commandCount", to - from + 1).put("maxPoints", points);
    }

    private JsonNode load() throws Exception {
        return mapper.readTree(mockMvc.perform(get(EXAM + "/structure").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode rejected(JsonNode current, JsonNode parts, Locale locale) throws Exception {
        ObjectNode request = mapper.createObjectNode().put("expectedRevision", current.path("revision").asLong());
        request.set("parts", parts);
        request.set("examQuotas", current.path("examQuotas"));
        return mapper.readTree(mockMvc.perform(put(EXAM + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Accept-Language", locale.getLanguage())
                        .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isUnprocessableEntity()).andReturn().getResponse().getContentAsString());
    }
}
