package vn.unicom.thi360.identity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Thay bean {@code Clock} của ứng dụng bằng {@link ControlledClock} (chạy theo giờ hệ thống tới khi test đóng băng). */
@TestConfiguration(proxyBeanMethods = false)
public class ControlledClockConfig {

    @Bean
    @Primary
    ControlledClock controlledClock() {
        return new ControlledClock();
    }
}
