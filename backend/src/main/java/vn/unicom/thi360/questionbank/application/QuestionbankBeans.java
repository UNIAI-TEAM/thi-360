package vn.unicom.thi360.questionbank.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;

/** Bean của module câu hỏi: resolver tài nguyên (bản thật BE-2b), bộ lọc HTML. */
@Configuration
public class QuestionbankBeans {

    /**
     * Resolver tài nguyên thật trên {@code question_asset} (BE-2b). Thay thế tường minh bean
     * mặc định {@code none()} của BE-2a — không dựa vào thứ tự {@code @ConditionalOnMissingBean}
     * trong {@code @Configuration} thường (model note BE-2b).
     */
    @Bean
    public QuestionAssetResolver questionAssetResolver(QuestionAssetRepository assetRepository) {
        return new DbQuestionAssetResolver(assetRepository);
    }

    /** SPI nguồn câu mặc định: module exam cài khi BE-6 cần tra "câu đang là nguồn của bài nào". */
    @Bean
    @ConditionalOnMissingBean(QuestionUsageProvider.class)
    public QuestionUsageProvider questionUsageProvider() {
        return new QuestionUsageProvider() {};
    }

    @Bean
    public QuestionHtmlSanitizer questionHtmlSanitizer(QuestionAssetResolver assetResolver) {
        return new QuestionHtmlSanitizer(assetResolver);
    }
}
