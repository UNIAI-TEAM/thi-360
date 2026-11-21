package vn.unicom.thi360.commerce.pricing;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a default no-op {@link PendingPaymentOrderLookup} when {@code commerce/order} is absent from a narrow test
 * slice.
 */
@Configuration(proxyBeanMethods = false)
public class PendingPaymentOrderLookupConfig {

    @Bean
    @ConditionalOnMissingBean(PendingPaymentOrderLookup.class)
    public PendingPaymentOrderLookup emptyPendingPaymentOrderLookup() {
        return (userId, productCodeNormalized) -> Optional.empty();
    }
}
