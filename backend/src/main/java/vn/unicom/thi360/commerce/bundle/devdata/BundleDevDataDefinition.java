package vn.unicom.thi360.commerce.bundle.devdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/**
 * Định nghĩa bộ đề mẫu (DS) đọc từ {@code classpath:devdata/published-exam-set.json} (AC-28).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BundleDevDataDefinition(Sp2eBundle sp2eBundle) {

    private static final String RESOURCE_PATH = "devdata/published-exam-set.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sp2eBundle(
            String code,
            String name,
            String summary,
            String description,
            String kind,
            List<String> examCodes,
            long priceVnd,
            int attemptsPerExam,
            String resultView) {
    }

    public static BundleDevDataDefinition load(ObjectMapper mapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (InputStream in = resource.getInputStream()) {
            return mapper.readValue(in, BundleDevDataDefinition.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load " + RESOURCE_PATH, ex);
        }
    }
}
