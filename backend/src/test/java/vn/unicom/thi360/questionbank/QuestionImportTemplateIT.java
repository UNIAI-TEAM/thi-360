package vn.unicom.thi360.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.RateLimitFilter;

class QuestionImportTemplateIT extends AbstractIntegrationTest {
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void clearUploadRateLimitBucket() {
        redisTemplate.delete(RateLimitFilter.UPLOAD_KEY_PREFIX + "127.0.0.1");
    }

    @Test
    void examplesPerTypeValidateAndIncludeBcaPoint() throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/v1/admin/question-imports/template")
                        .with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var types = new HashSet<String>();
            boolean bca = false;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                String type = row.getCell(1).getStringCellValue();
                types.add(type);
                if ("SINGLE_CHOICE".equals(type) && "0,75".equals(row.getCell(3).getStringCellValue())) {
                    bca = true;
                }
            }
            assertThat(types).contains("SINGLE_CHOICE", "MULTIPLE_CHOICE", "SHORT_ANSWER", "CLOZE",
                    "QUESTION_SET", "TRUE_FALSE", "MATCHING", "ESSAY");
            assertThat(bca).isTrue();
        }
        var uploaded = mockMvc.perform(multipart("/api/v1/admin/question-imports/uploads")
                        .file(new MockMultipartFile("file", "template.xlsx", "application/octet-stream", bytes))
                        .with(asUser(Permissions.QUESTION_MODIFY))).andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(uploaded.getResponse().getContentAsByteArray());
        String id = json.get("id").asText();
        String request = "{\"uploadId\":\"" + id + "\",\"mapping\":" + json.get("suggestedMapping") + "}";
        mockMvc.perform(post("/api/v1/admin/question-imports/uploads/{id}/validate", id)
                        .with(asUser(Permissions.QUESTION_MODIFY)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.error").value(0));
    }
}
