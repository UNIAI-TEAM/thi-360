package vn.unicom.thi360.exam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import vn.unicom.thi360.exam.domain.solver.SolverLimits;

class ExamSolverPropertiesTests {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ExamSolverProperties.class)
    static class TestConfig { }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaultsMatchSolverLimits() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExamSolverProperties.class).toLimits()).isEqualTo(SolverLimits.defaults());
        });
    }

    @Test
    void bindsConfiguredLimits() {
        runner.withPropertyValues(
                "thi360.exam.solver.max-paths=12",
                "thi360.exam.solver.max-candidates-per-block=345",
                "thi360.exam.solver.timeout=2m",
                "thi360.exam.solver.max-choice-subjects=4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ExamSolverProperties.class).toLimits())
                            .isEqualTo(new SolverLimits(12, 345, Duration.ofMinutes(2), 4));
                });
    }

    @Test
    void rejectsInvalidLimitsAtStartup() {
        runner.withPropertyValues("thi360.exam.solver.max-paths=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
