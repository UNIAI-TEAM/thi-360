package vn.unicom.thi360.attempt.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Default no-op beans for temporary module-internal ports (plan B1); replaced automatically by AT-2a/AT-3 beans. */
@Configuration(proxyBeanMethods = false)
public class AttemptDefaultPortsConfig {

    @Bean
    @ConditionalOnMissingBean(OverdueAttemptCloser.class)
    public OverdueAttemptCloser noOpOverdueAttemptCloser() {
        return new NoOpOverdueAttemptCloser();
    }

    @Bean
    @ConditionalOnMissingBean(AttemptSessionChecker.class)
    public AttemptSessionChecker noOpAttemptSessionChecker() {
        return new NoOpAttemptSessionChecker();
    }
}
