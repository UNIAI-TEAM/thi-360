package vn.unicom.thi360.exam.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "thi360.exam.check")
public record ExamCheckExecutionProperties(@DefaultValue("2") int poolSize) {
    public ExamCheckExecutionProperties {
        if (poolSize <= 0) throw new IllegalArgumentException("poolSize must be positive");
    }
}
