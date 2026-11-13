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

class ProvisioningReconcilerWorkerTests {
    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    MockJobConfig.class,
                    ProvisioningReconcilerJob.ProvisioningReconcilerWorker.class);

    @Test
    void schedulerBean_isEnabledInWorkerProfile_andDisabledInTestProfileOrWhenPropertyFalse() throws Exception {
        baseRunner
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).hasSingleBean(ProvisioningReconcilerJob.ProvisioningReconcilerWorker.class));

        baseRunner
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker", "test");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ProvisioningReconcilerJob.ProvisioningReconcilerWorker.class));

        baseRunner
                .withPropertyValues("thi360.identity.provisioning-reconcile.scheduling.enabled=false")
                .withInitializer(ctx -> {
                    ctx.getEnvironment().setActiveProfiles("worker");
                    ConfigDataEnvironmentPostProcessor.applyTo(ctx.getEnvironment());
                })
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ProvisioningReconcilerJob.ProvisioningReconcilerWorker.class));

        Method scheduledMethod =
                ProvisioningReconcilerJob.ProvisioningReconcilerWorker.class.getMethod("runScheduledReconcile");
        Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.initialDelayString()).isEqualTo(scheduled.fixedDelayString());
        assertThat(scheduled.fixedDelayString()).isEqualTo("${thi360.identity.provisioning-reconcile-interval:PT5M}");
    }

    @Configuration
    static class MockJobConfig {
        @Bean
        ProvisioningReconcilerJob provisioningReconcilerJob() {
            return Mockito.mock(ProvisioningReconcilerJob.class);
        }
    }
}
