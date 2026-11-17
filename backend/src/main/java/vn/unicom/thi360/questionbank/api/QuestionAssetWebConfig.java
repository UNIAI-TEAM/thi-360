package vn.unicom.thi360.questionbank.api;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.unicom.thi360.questionbank.application.QuestionAssetProperties;
import vn.unicom.thi360.shared.security.CurrentUserProvider;

/**
 * Đăng ký interceptor giới hạn tần suất tải lên cho đúng
 * {@code /api/v1/admin/question-assets} (POST). Interceptor chạy sau tầng bảo mật nên
 * {@link CurrentUserProvider} đã có principal; dọn kết nối Redis khi đóng context.
 */
@Configuration
public class QuestionAssetWebConfig implements WebMvcConfigurer {

    private final QuestionAssetUploadRateLimiter rateLimiter;

    public QuestionAssetWebConfig(
            QuestionAssetProperties properties,
            CurrentUserProvider currentUserProvider,
            @Lazy LettuceConnectionFactory lettuceConnectionFactory) {
        this.rateLimiter = new QuestionAssetUploadRateLimiter(
                properties, currentUserProvider, lettuceConnectionFactory);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimiter)
                .addPathPatterns("/api/v1/admin/question-assets")
                .order(0);
    }

    @PreDestroy
    public void cleanup() {
        rateLimiter.close();
    }
}
