package vn.unicom.thi360.exam.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "thi360.exam.structure")
public record ExamStructureProperties(@DefaultValue("8") int maxTopicQuotas,
        @DefaultValue("20") int maxMapidRules) {
    public ExamStructureProperties {
        if (maxTopicQuotas < 1) {
            throw new IllegalArgumentException("maxTopicQuotas must be positive");
        }
        if (maxMapidRules < 1) {
            throw new IllegalArgumentException("maxMapidRules must be positive");
        }
    }
}
