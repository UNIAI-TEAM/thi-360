package vn.unicom.thi360.commerce.payment.vnpay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    VnpaySigningProperties.class,
    VnpayClientProperties.class,
    QueryDrProperties.class
})
public class VnpayRealConfiguration {

    @Bean
    static VnpayRealModeStartupGuard vnpayRealModeStartupGuard() {
        return new VnpayRealModeStartupGuard();
    }
}
