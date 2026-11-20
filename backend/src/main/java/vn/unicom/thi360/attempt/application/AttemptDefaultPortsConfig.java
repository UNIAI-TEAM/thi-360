package vn.unicom.thi360.attempt.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Default no-op bean for temporary module-internal session port (plan B1); replaced by AT-3. */
@Configuration(proxyBeanMethods = false)
public class AttemptDefaultPortsConfig {

    @Bean
    @ConditionalOnMissingBean(AttemptSessionChecker.class)
    public AttemptSessionChecker noOpAttemptSessionChecker() {
        return new NoOpAttemptSessionChecker();
    }
}
