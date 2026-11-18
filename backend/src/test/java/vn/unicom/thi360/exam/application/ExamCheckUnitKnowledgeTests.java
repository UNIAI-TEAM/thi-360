package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

class ExamCheckUnitKnowledgeTests {
    @Test
    void usesCatalogParentsToDetectNestedQuotasBeforeSolving() {
        UUID parent = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        var node = mock(CatalogItemView.class);
        when(node.id()).thenReturn(child);
        when(node.parentId()).thenReturn(parent);
        var catalog = mock(CatalogLookup.class);
        when(catalog.listGroupItems("KNOWLEDGE")).thenReturn(List.of(node));
        var mapper = new ObjectMapper();
        var doc = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","name":"Part","questionCount":2,"sections":[
                  {"id":"s","subjectCode":"TOAN","questionCount":2,"topicQuotas":[
                    {"id":"a","name":"Parent","nodeIds":["%s"],"count":1,"basis":"QUESTION","enforcement":"REQUIRED"},
                    {"id":"b","name":"Child","nodeIds":["%s"],"count":1,"basis":"QUESTION","enforcement":"REQUIRED"}],
                   "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}
                """.formatted(parent, child));
        var exam = mock(Exam.class);
        when(exam.getName()).thenReturn("Test");
        when(exam.getCode()).thenReturn("TEST");
        var limits = SolverLimits.defaults();
        var clock = Clock.systemUTC();
        var source = mock(QuestionSourcePort.class);
        var checker = new DefaultExamChecker(new SolverInputBuilder(catalog, source, limits),
                limits, clock, mapper, catalog, source);
        var result = checker.check(new ExamCheckSnapshotReader.Snapshot(exam, doc, null),
                clock.instant().plusSeconds(30), Duration.ofSeconds(30)).result();
        assertThat(result.path("groups").findValuesAsText("code")).contains("TOPIC_QUOTA_NESTED");
    }
}
