package vn.unicom.thi360.shared.testapi;

import org.springframework.test.context.ActiveProfiles;

/**
 * R1-12: cùng mọi ca của {@link TestApiAbsentWithoutE2eIT} với profile {@code prod} cộng
 * {@code api,worker,test} của lớp nền (có MVC, bảo mật và cơ sở dữ liệu thật qua Testcontainers).
 * Production chạy {@code api,prod} không có {@code e2e}: tiền tố test phải không có handler và trả 404
 * bất kể thông tin xác thực.
 */
@ActiveProfiles("prod")
class TestApiAbsentUnderProdIT extends TestApiAbsentWithoutE2eIT {
}
