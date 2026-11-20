package vn.unicom.thi360.exam.devdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/**
 * The constants file {@code devdata/published-exam-set.json}: the single source of the exam codes, product codes, sample
 * accounts and special exams of the published exam set (DS). Unknown fields are ignored so that other tasks can add rows
 * and fields (CM-6 adds the SP2E bundle exams) without touching this reader.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublishedExamSetDefinition(int version, String actor, List<Account> accounts, String discontinuedExam,
        String voucherExam, String scoreOnlyExam, String reviewExam, List<Exam> exams) {
    public static final String RESOURCE = "devdata/published-exam-set.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String username, String role) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Listing(long priceVnd, int attemptsGranted, String resultView) { }

    /**
     * One exam. {@code size} is {@code FULL} (the template as it is) or {@code MINI} (cut down by {@link MiniStructure});
     * {@code sectionUnits} lets a mini raise the subjects that must match the longest path of a choice part.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Exam(String code, String productCode, String name, String kind, String scope, String partCode,
            String subjectCode, String template, String buildMode, Integer durationMinutes, String size,
            String purpose, String miniCode, String miniOf, Listing listing, Map<String, Integer> sectionUnits) {
        public boolean mini() {
            return "MINI".equals(size);
        }

        public boolean review() {
            return "REVIEW".equals(buildMode);
        }
    }

    public static PublishedExamSetDefinition load(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readValue(in, PublishedExamSetDefinition.class);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }
}
