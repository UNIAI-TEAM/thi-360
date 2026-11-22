package vn.unicom.thi360.commerce.order.expiry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderExpiryProperties.class)
public class OrderExpiryConfiguration {
}
