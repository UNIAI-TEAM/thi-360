package vn.unicom.thi360.audit.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.unicom.thi360.audit.spi.AuditRecorder;

@Configuration(proxyBeanMethods = false)
public class NoopAuditConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditRecorder.class)
    public AuditRecorder noopAuditRecorder() {
        return new NoopAuditRecorder();
    }
}
