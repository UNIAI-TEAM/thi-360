package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

class ImportBatchProcessorWorkerTests {

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    MockJobConfig.class,
                    ImportBatchProcessorJob.ImportBatchProcessorWorker.class);

    @Test
    void schedulerBean_isEnabledInWorkerProfile_andDisabledInTestProfileOrWhenPropertyFalse() throws Exception {
        baseRunner
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).hasSingleBean(ImportBatchProcessorJob.ImportBatchProcessorWorker.class));

        baseRunner
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker", "test");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ImportBatchProcessorJob.ImportBatchProcessorWorker.class));

        baseRunner
                .withPropertyValues("thi360.identity.import-batch.scheduling.enabled=false")
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ImportBatchProcessorJob.ImportBatchProcessorWorker.class));

        Method scheduledMethod =
                ImportBatchProcessorJob.ImportBatchProcessorWorker.class.getMethod("runScheduledImportBatches");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${thi360.identity.import-batch.poll-interval:PT5S}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${thi360.identity.import-batch.initial-delay:PT10S}");
    }

    @Configuration
    static class MockJobConfig {
        @Bean
        ImportBatchProcessorJob importBatchProcessorJob() {
            return Mockito.mock(ImportBatchProcessorJob.class);
        }
    }
}
