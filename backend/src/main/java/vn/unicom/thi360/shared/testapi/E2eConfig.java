package vn.unicom.thi360.shared.testapi;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import vn.unicom.thi360.shared.security.KeycloakProperties;
import vn.unicom.thi360.shared.security.TwoIssuerJwtDecoder;

/**
 * Bean chỉ có ở profile {@code e2e}: chốt chặn khởi động, đồng hồ điều khiển được và bộ giải mã JWT
 * giữ giờ hệ thống. ML và CM-3a thêm bean hoặc controller của mình (xem {@link TestApiController}).
 */
@Configuration(proxyBeanMethods = false)
@Profile(TestApiPaths.E2E_PROFILE)
class E2eConfig {

    @Bean
    static E2eProfileGuard e2eProfileGuard() {
        return new E2eProfileGuard();
    }

    /** Thay bean {@link Clock} mặc định ({@code ClockConfig}); mọi nơi tiêm {@link Clock} nhận bean này. */
    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }

    /**
     * Token do Keycloak cấp theo giờ thật, nên kiểm hạn token phải theo giờ hệ thống: nếu theo
     * {@link MutableClock}, tua quá thời hạn token làm mọi JWT hết hạn. Hàm một tham số dùng
     * {@code Clock.systemUTC()}.
     */
    @Bean
    @Primary
    TwoIssuerJwtDecoder e2eTwoIssuerJwtDecoder(KeycloakProperties keycloakProperties) {
        return new TwoIssuerJwtDecoder(keycloakProperties);
    }
}
