package vn.unicom.thi360.exam.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "thi360.exam.structure")
public record ExamStructureProperties(@DefaultValue("8") int maxTopicQuotas) {
    public ExamStructureProperties {
        if (maxTopicQuotas < 1) {
            throw new IllegalArgumentException("maxTopicQuotas must be positive");
        }
    }
}
