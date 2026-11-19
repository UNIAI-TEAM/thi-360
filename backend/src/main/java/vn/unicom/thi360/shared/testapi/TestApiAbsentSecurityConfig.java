package vn.unicom.thi360.shared.testapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Khi profile {@code e2e} tắt, {@code /api/v1/test/**} không có handler và phải nhận 404 bất kể
 * thông tin xác thực kèm theo (plan A6, B6). Chuỗi bộ lọc của tiền tố này chạy trước chuỗi chính
 * và không có bộ lọc xác thực nào: không cookie, không Bearer (nên token sai hay hết hạn không bị
 * đổi thành 401 trước khi tới MVC), không CSRF. Yêu cầu đi thẳng tới MVC và nhận 404 như mọi đường
 * không tồn tại. Chuỗi chỉ khớp đúng {@link TestApiPaths#PATTERN}; đường kề (ví dụ
 * {@code /api/v1/testing}) vẫn do chuỗi chính xử lý.
 *
 * <p>Khi bật {@code e2e}, chuỗi này không đăng ký; chuỗi chính cho phép ẩn danh (xem
 * {@code SecurityConfig}) và {@link TestApiController} tự trả lời.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!" + TestApiPaths.E2E_PROFILE)
class TestApiAbsentSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain testApiAbsentFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(TestApiPaths.PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
