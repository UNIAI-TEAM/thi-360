package vn.unicom.thi360.exam.devdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.application.ExamCheckOrchestrator;
import vn.unicom.thi360.exam.application.ExamDiscontinueService;
import vn.unicom.thi360.exam.application.ExamListingService;
import vn.unicom.thi360.exam.application.ExamPublishService;
import vn.unicom.thi360.exam.application.ExamService;
import vn.unicom.thi360.exam.application.ExamTemplateService;
import vn.unicom.thi360.identity.application.DevDataInitializer;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.questionbank.application.DevQuestionAssetSeeder;
import vn.unicom.thi360.questionbank.application.QuestionAssetService;
import vn.unicom.thi360.questionbank.application.QuestionService;
import vn.unicom.thi360.questionbank.devdata.PublishedQuestionPoolBuilder;
import vn.unicom.thi360.shared.storage.ObjectStorage;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/**
 * AC-16 and AC-17 on a real context: an empty database under the e2e profile with
 * {@code thi360.devdata.published-exams.enabled=false} starts with no runner, no account initializer and no
 * {@code DS-} row (R1-7); and on the same empty destination a builder without {@code sys.admin} writes nothing —
 * question, asset (the audio seeder included), exam, version and listing rows stay untouched.
 */
@ActiveProfiles("e2e")
@TestPropertySource(properties = "thi360.devdata.published-exams.enabled=false")
class PublishedExamSetFlagOffIT extends AbstractIntegrationTest {
    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) {
        String url = PublishedExamSetSupport.createDatabase(POSTGRES, "published_exam_set_off");
        PublishedExamSetSupport.pointAt(registry, url);
    }

    @Autowired ApplicationContext context;
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
    @Autowired EntityManager entityManager;

    @Test
    void flagOffLeavesNoDsRows() throws ClassNotFoundException {
        assertThat(context.getBeanNamesForType(
                Class.forName("vn.unicom.thi360.questionbank.devdata.PublishedQuestionPoolRunner"))).isEmpty();
        assertThat(context.getBeanNamesForType(PublishedExamSetRunner.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DevDataInitializer.class)).isEmpty();
        assertThat(count("SELECT count(*) FROM exam.exam WHERE code LIKE 'DS-%'")).isZero();
        assertThat(count("SELECT count(*) FROM questionbank.question WHERE source_code LIKE 'DS-%'")).isZero();
    }

    @Test
    void missingActorWritesNothingOnAnEmptyDestination() {
        long questionsBefore = count("SELECT count(*) FROM questionbank.question");
        long assetsBefore = count("SELECT count(*) FROM questionbank.question_asset");
        long examsBefore = count("SELECT count(*) FROM exam.exam");
        long versionsBefore = count("SELECT count(*) FROM exam.exam_version");
        long listingsBefore = count("SELECT count(*) FROM exam.exam_listing");
        UserDirectory nobody = mock(UserDirectory.class);
        var audioSeeder = org.mockito.Mockito.spy(seeder);
        var questionBuilder = new PublishedQuestionPoolBuilder(questions, assets, audioSeeder, storage, nobody, jdbc, mapper,
                transactionManager);
        var examBuilder = new PublishedExamSetBuilder(exams, listings, checks, publisher, discontinuer, templates,
                nobody, jdbc, mapper, transactionManager, entityManager);
        assertThatThrownBy(questionBuilder::build).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sys.admin");
        org.mockito.Mockito.verify(audioSeeder, org.mockito.Mockito.never()).seed();
        assertThatThrownBy(examBuilder::build).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sys.admin");
        assertThat(count("SELECT count(*) FROM questionbank.question")).isEqualTo(questionsBefore);
        // The 2B audio seeder runs inside the builder but after the actor check: nothing lands in storage either.
        assertThat(count("SELECT count(*) FROM questionbank.question_asset")).isEqualTo(assetsBefore);
        assertThat(count("SELECT count(*) FROM exam.exam")).isEqualTo(examsBefore);
        assertThat(count("SELECT count(*) FROM exam.exam_version")).isEqualTo(versionsBefore);
        assertThat(count("SELECT count(*) FROM exam.exam_listing")).isEqualTo(listingsBefore);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
