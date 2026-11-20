package vn.unicom.thi360.commerce.payment.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VnpayProperties.class)
public class VnpayConfiguration {

    @Bean
    static VnpayModeStartupGuard vnpayModeStartupGuard() {
        return new VnpayModeStartupGuard();
    }

    @Bean
    @ConditionalOnMissingBean(VnpayGateway.class)
    VnpayGateway unsupportedVnpayGateway() {
        return new UnsupportedVnpayGateway();
    }
}
