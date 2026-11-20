package vn.unicom.thi360.exam.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/** A normal test-profile startup never creates the DS fixtures. */
class PublishedExamSetDefaultProfileIT extends AbstractIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.pointAt(registry,
                PublishedExamSetSupport.createDatabase(POSTGRES, "published_exam_set_default"));
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void defaultTestProfileCreatesNoDsRows() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam WHERE code LIKE 'DS-%'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question WHERE source_code LIKE 'DS-%'",
                Long.class)).isZero();
    }
}
