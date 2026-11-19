package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;

class ExamTemplateOrderIT extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void allTemplatesKeepPartsInDeclaredOrder() throws Exception {
        var templates = jdbc.queryForList("select code, structure::text as document from exam.exam_template");
        assertThat(templates).isNotEmpty();
        for (var template : templates) {
            String code = (String) template.get("code");
            JsonNode parts = mapper.readTree((String) template.get("document")).path("parts");
            assertThat(parts.isArray()).as("%s parts", code).isTrue();
            assertThat(parts).as("%s parts", code).isNotEmpty();
            for (int i = 0; i < parts.size(); i++) {
                assertThat(parts.get(i).path("order").isIntegralNumber())
                        .as("%s part %s order", code, i + 1).isTrue();
                assertThat(parts.get(i).path("order").asInt())
                        .as("%s part position %s", code, i + 1).isEqualTo(i + 1);
            }
        }
    }

    @Test
    void subjectVersionsKeepPartsInBusinessOrder() throws Exception {
        var templates = jdbc.queryForList("""
                select code, kind_code, subject_code, structure::text as document
                from exam.exam_template
                where kind_code in ('SP2E', 'SPT') and scope = 'SUBJECT' and build_mode = 'STANDARD'
                order by kind_code, subject_code, code
                """);
        assertThat(templates).isNotEmpty();
        for (var template : templates) {
            String code = (String) template.get("code");
            JsonNode parts = mapper.readTree((String) template.get("document")).path("parts");
            assertThat(parts).as("%s parts", code).isNotEmpty();
            for (int i = 0; i < parts.size(); i++) {
                JsonNode part = parts.get(i);
                assertThat(part.path("order").isIntegralNumber()).as("%s part %s order", code, i + 1).isTrue();
                int ordinal = part.path("order").asInt();
                assertThat(ordinal).as("%s part position %s", code, i + 1).isEqualTo(i + 1);
                String roman = roman(i + 1);
                assertThat(part.path("name").asText()).as("%s part label", code)
                        .matches("^" + java.util.regex.Pattern.quote("Phần " + roman) + "(?![IVX]).*");
            }
        }
    }

    private String roman(int number) {
        StringBuilder result = new StringBuilder();
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                result.append(symbols[i]);
                number -= values[i];
            }
        }
        return result.toString();
    }
}
