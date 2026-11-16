package vn.unicom.thi360.questionbank.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.unicom.thi360.questionbank.domain.QuestionHtmlSanitizer;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionUsageProvider;

/** Bean của module câu hỏi: resolver tài nguyên (tạm {@code none()} tới BE-2b), bộ lọc HTML. */
@Configuration
public class QuestionbankBeans {

    /** Resolver tài nguyên mặc định: BE-2b cung cấp bản thật trên {@code question_asset}. */
    @Bean
    @ConditionalOnMissingBean(QuestionAssetResolver.class)
    public QuestionAssetResolver questionAssetResolver() {
        return QuestionAssetResolver.none();
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
