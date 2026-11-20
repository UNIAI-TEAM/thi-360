package vn.unicom.thi360.exam.devdata;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Starts the exam half of the published exam set (DS) when the app boots, after the question half
 * ({@code questionbank/devdata}, order 3) that supplies its questions.
 */
@Component
@Profile({"dev", "e2e"})
@Order(4)
@ConditionalOnProperty(name = "thi360.devdata.published-exams.enabled", havingValue = "true")
class PublishedExamSetRunner implements ApplicationRunner {
    private final PublishedExamSetBuilder builder;

    PublishedExamSetRunner(PublishedExamSetBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void run(ApplicationArguments args) {
        builder.build();
    }
}
