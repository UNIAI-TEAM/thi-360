package vn.unicom.thi360.questionbank.devdata;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Starts the question half of the published exam set (DS) when the app boots, after the dev accounts
 * ({@code DevDataInitializer}, order 1) and before the exam half (order 4).
 */
@Component
@Profile({"dev", "e2e"})
@Order(3)
@ConditionalOnProperty(name = "thi360.devdata.published-exams.enabled", havingValue = "true")
class PublishedQuestionPoolRunner implements ApplicationRunner {
    private final PublishedQuestionPoolBuilder builder;

    PublishedQuestionPoolRunner(PublishedQuestionPoolBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void run(ApplicationArguments args) {
        builder.build();
    }
}
