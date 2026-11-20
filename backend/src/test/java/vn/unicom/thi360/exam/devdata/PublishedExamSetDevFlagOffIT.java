package vn.unicom.thi360.exam.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/** The disabled flag leaves the DS destination empty even when the dev profile starts. */
@ActiveProfiles("dev")
@TestPropertySource(properties = "thi360.devdata.published-exams.enabled=false")
class PublishedExamSetDevFlagOffIT extends AbstractKeycloakIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.pointAt(registry,
                PublishedExamSetSupport.createDatabase(POSTGRES, "published_exam_set_dev_off"));
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void disabledDevProfileCreatesNoDsRows() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam WHERE code LIKE 'DS-%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question WHERE source_code LIKE 'DS-%'",
                Long.class)).isZero();
    }
}
