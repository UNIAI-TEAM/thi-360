package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class VactThptSampleDataIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired QuestionSourcePort source;

    private static final List<String> D9_TAGS = List.of(
            "ANH_PHAT_HIEN_LOI", "ANH_DIEN_DAT_TUONG_DUONG", "TD_SAP_XEP_DIEU_KIEN",
            "TD_XEP_HANG", "TD_BANG_SO_LIEU", "TD_BIEU_DO");

    @Test
    void provisionalTagsHaveBothNames() {
        for (String code : D9_TAGS) {
            assertThat(jdbc.queryForObject("""
                    select count(*) from catalog.catalog_item
                    where group_id = '00000000-0000-7000-8100-000000000008'
                      and code = ? and status = 1 and nullif(trim(name_vi), '') is not null
                      and nullif(trim(name_en), '') is not null
                    """, Integer.class, code)).as(code).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void thptEnglishHasDistinctPassagesAndArrangementQuestions() {
        assertSets("ANH", "ANH_DOC_DIEN", null, "0.25", 6, 2);
        assertSets("ANH", "ANH_DOC_DIEN_CAU", null, "0.25", 5, 1);
        assertSets("ANH", "ANH_DOC_HIEU", null, "0.25", 8, 1);
        assertSets("ANH", "ANH_DOC_HIEU", null, "0.25", 10, 1);
        var filter = filter("ANH", QuestionType.SINGLE_CHOICE,
                List.of("ANH_SAP_XEP_HOI_THOAI", "ANH_SAP_XEP_DOAN"), null, "0.25", null);
        assertThat(source.count(filter).total()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void vactSourcesMatchCompetenciesAndPoints() {
        assertSingles("VIETNAMESE", null, "VACT-NN-TV", 30);
        for (String tag : List.of("ANH_HOAN_THANH_CAU", "ANH_PHAT_HIEN_LOI", "ANH_DIEN_DAT_TUONG_DUONG")) {
            assertSingles("ANH", tag, "VACT-NN-TA", 5);
        }
        assertSets("ANH", "ANH_DOC_HIEU", "VACT-NN-TA", "10", 7, 1);
        assertSets("ANH", "ANH_DOC_HIEU", "VACT-NN-TA", "10", 8, 1);
        assertSingles("TOAN", null, "VACT-TOAN", 12);
        var math = candidates("TOAN", null, "VACT-TOAN", "10");
        Set<Integer> sums = new HashSet<>(Set.of(0));
        for (Candidate set : math) {
            Set<Integer> next = new HashSet<>(sums);
            for (int sum : sums) {
                if (sum + set.units().size() <= 18) next.add(sum + set.units().size());
            }
            sums = next;
        }
        assertThat(sums).as("whole math sets can form 18 children").contains(18);
        assertSets("SCIENTIFIC_THINKING", null, "VACT-TD-LOGIC", "10", 3, 4);
        assertSets("SCIENTIFIC_THINKING", null, "VACT-TD-SL", "10", 3, 6);
    }

    @Test
    void eachLogicDataTagHasAThreeChildSet() {
        for (String tag : D9_TAGS.subList(2, 6)) {
            assertSets("SCIENTIFIC_THINKING", tag, "VACT-TD-LOGIC", "10", 3, 1);
        }
    }

    @Test
    void thptTrueFalseSubjectsHaveFourStatementQuestions() {
        for (String subject : List.of("TOAN", "LY", "HOA", "SINH", "DIA", "SU",
                "ECON_LAW", "TECH_INDUSTRY", "TECH_AGRICULTURE")) {
            var filter = filter(subject, QuestionType.TRUE_FALSE, List.of(), null, "1",
                    new QuestionSourcePort.ItemShape(4, null, null));
            assertThat(source.count(filter).total()).as(subject).isGreaterThanOrEqualTo(4);
        }
    }

    private void assertSingles(String subject, String tag, String competency, int minimum) {
        var filter = filter(subject, QuestionType.SINGLE_CHOICE, tag == null ? List.of() : List.of(tag),
                competency, "10", null);
        assertThat(source.count(filter).total()).as(subject + " " + tag + " " + competency)
                .isGreaterThanOrEqualTo(minimum);
    }

    private void assertSets(String subject, String tag, String competency, String points, int size, int minimum) {
        assertThat(candidates(subject, tag, competency, points).stream()
                .filter(c -> c.units().size() == size).map(Candidate::questionId).distinct().count())
                .as(subject + " " + tag + " " + competency + " size " + size)
                .isGreaterThanOrEqualTo(minimum);
    }

    private List<Candidate> candidates(String subject, String tag, String competency, String points) {
        return source.findCandidates(filter(subject, QuestionType.QUESTION_SET,
                tag == null ? List.of() : List.of(tag), competency, points, null), 1000);
    }

    private QuestionSourcePort.Filter filter(String subject, QuestionType type, List<String> tags,
            String competency, String points, QuestionSourcePort.ItemShape shape) {
        UUID id = jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = ?
                """, UUID.class, subject);
        return new QuestionSourcePort.Filter(id, List.of(), List.of(), type, null,
                type == QuestionType.QUESTION_SET ? QuestionType.SINGLE_CHOICE : null,
                tags, competency == null ? List.of() : List.of(competency), new BigDecimal(points),
                shape, List.of(), List.of());
    }
}
