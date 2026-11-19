package vn.unicom.thi360.exam.devdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckOrchestrator;
import vn.unicom.thi360.exam.application.ExamDiscontinueService;
import vn.unicom.thi360.exam.application.ExamListingService;
import vn.unicom.thi360.exam.application.ExamPublishService;
import vn.unicom.thi360.exam.application.ExamService;
import vn.unicom.thi360.exam.application.ExamTemplateService;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.application.DevQuestionAssetSeeder;
import vn.unicom.thi360.questionbank.application.QuestionAssetService;
import vn.unicom.thi360.questionbank.application.QuestionService;
import vn.unicom.thi360.questionbank.devdata.PublishedQuestionPoolBuilder;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * Where the published exam set (DS) runs. The {@code e2e} profile (with the Keycloak container of the base class, so that
 * {@code DevDataInitializer} makes the real accounts) builds the set when the application starts, on a new database.
 */
@ActiveProfiles("e2e")
class PublishedExamSetProfileIT extends AbstractKeycloakIntegrationTest {
    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) {
        String url = PublishedExamSetSupport.createDatabase(POSTGRES, "published_exam_set_e2e");
        registry.add("spring.datasource.url", () -> url);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ExamService exams;
    @Autowired ExamListingService listings;
    @Autowired ExamCheckOrchestrator checks;
    @Autowired ExamPublishService publisher;
    @Autowired ExamDiscontinueService discontinuer;
    @Autowired ExamTemplateService templates;
    @Autowired QuestionService questions;
    @Autowired QuestionAssetService assets;
    @Autowired DevQuestionAssetSeeder seeder;
    @Autowired ObjectStorage storage;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void e2eProfileBuildsTheSet() {
        PublishedExamSetDefinition definition = PublishedExamSetDefinition.load(mapper);
        for (var exam : definition.exams()) {
            String expected = exam.code().equals(definition.discontinuedExam()) ? "DISCONTINUED" : "PUBLISHED";
            assertThat(jdbc.queryForList("SELECT status FROM exam.exam WHERE code = ?", String.class, exam.code()))
                    .as(exam.code()).containsExactly(expected);
        }
        for (var account : definition.accounts()) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.user_account WHERE username = ? "
                    + "AND status = 2", Integer.class, account.username())).as(account.username()).isEqualTo(1);
        }
    }

    @Test
    void disabledFlagBuildsNothing() throws Exception {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(MockBuilder.class, PublishedExamSetRunner.class);
        // Flag off: no runner in the dev profile, so nothing is built at start.
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withPropertyValues("thi360.devdata.published-exams.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PublishedExamSetRunner.class));
        // The test profile never runs it, even with the flag on; dev and e2e run it with the flag on.
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues("thi360.devdata.published-exams.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(PublishedExamSetRunner.class));
        for (String profile : new String[] {"dev", "e2e"}) {
            runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                    .withPropertyValues("thi360.devdata.published-exams.enabled=true")
                    .run(context -> assertThat(context).hasSingleBean(PublishedExamSetRunner.class));
        }
        // The question half follows the same two rules and runs before the exam half.
        Class<?> questionRunner = Class.forName("vn.unicom.thi360.questionbank.devdata.PublishedQuestionPoolRunner");
        assertThat(questionRunner.getAnnotation(Profile.class).value()).containsExactlyInAnyOrder("dev", "e2e");
        assertThat(questionRunner.getAnnotation(ConditionalOnProperty.class).name())
                .containsExactly("thi360.devdata.published-exams.enabled");
        assertThat(questionRunner.getAnnotation(Order.class).value()).isEqualTo(3);
        assertThat(PublishedExamSetRunner.class.getAnnotation(Order.class).value()).isEqualTo(4);
        // And the shipped defaults: the flag is on in dev and e2e only.
        String yaml = new String(new org.springframework.core.io.ClassPathResource("application.yml").getInputStream()
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(yaml.split("devdata.published-exams.enabled")).hasSize(3);
    }

    /** The builder as a mock bean: only the runner's conditions are under test. */
    static class MockBuilder {
        @org.springframework.context.annotation.Bean
        PublishedExamSetBuilder builder() {
            return mock(PublishedExamSetBuilder.class);
        }
    }

    @Test
    void missingActorStopsCleanly() {
        long questionsBefore = jdbc.queryForObject("SELECT count(*) FROM questionbank.question", Long.class);
        long examsBefore = jdbc.queryForObject("SELECT count(*) FROM exam.exam", Long.class);
        UserDirectory nobody = mock(UserDirectory.class);
        var questionBuilder = new PublishedQuestionPoolBuilder(questions, assets, seeder, storage, nobody, jdbc, mapper, transactionManager);
        var examBuilder = new PublishedExamSetBuilder(exams, listings, checks, publisher, discontinuer, templates, nobody,
                jdbc, mapper, transactionManager, entityManager);
        assertThatThrownBy(questionBuilder::build).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sys.admin");
        assertThatThrownBy(examBuilder::build).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sys.admin");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question", Long.class)).isEqualTo(questionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam", Long.class)).isEqualTo(examsBefore);
    }
}
