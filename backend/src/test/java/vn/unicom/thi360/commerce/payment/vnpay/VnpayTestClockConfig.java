package vn.unicom.thi360.commerce.payment.vnpay;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import vn.unicom.thi360.shared.testapi.MutableClock;

/**
 * Cấu hình đồng hồ {@link MutableClock} dùng chung cho kiểm thử tích hợp {@code querydr} và hết hạn đơn.
 * Giữ nền cố định theo thời điểm khởi tạo context để kiểm tra chính xác biên giây (14m59s / 15m00s / 24h00m00s)
 * mà không bị trôi mili-giây theo đồng hồ thật; gọi {@link MutableClock#reset()} trong {@code @AfterEach}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class VnpayTestClockConfig {

    @Bean
    @Primary
    public MutableClock vnpayTestMutableClock() {
        return new MutableClock(Clock.fixed(Instant.now(), ZoneOffset.UTC));
    }
}
