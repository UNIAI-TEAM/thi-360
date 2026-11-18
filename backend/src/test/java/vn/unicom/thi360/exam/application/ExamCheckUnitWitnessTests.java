package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ExamCheckUnitWitnessTests {
    @Test
    void aProvedQuestionWithoutAnAnswerBlocksTheCheck() throws Exception {
        var mapper = new ObjectMapper();
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        UUID subject = UUID.randomUUID(), question = UUID.randomUUID();
        var item = mock(CatalogItemView.class);
        when(item.id()).thenReturn(subject);
        when(catalog.findActiveItem(eq("SUBJECT"), eq("MATH"))).thenReturn(Optional.of(item));
        var unit = new QuestionSourcePort.Unit(question, null, QuestionType.SINGLE_CHOICE,
                null, null, List.of(), List.of(), BigDecimal.ONE);
        when(source.findCandidates(any(), anyInt())).thenReturn(List.of(new QuestionSourcePort.Candidate(
                question, 1, null, List.of(unit), null, null)));
        var key = new QuestionSourcePort.VersionKey(question, 1);
        when(source.loadVersions(anyList())).thenReturn(List.of(new QuestionSourcePort.Version(key,
                mapper.readTree("{\"attachmentIds\":[],\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"correct\":false},{\"correct\":false}]}}"),
                "Q1", java.util.Map.of())));
        var document = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","name":"Part","questionCount":1,"sections":[
                  {"id":"s","subjectCode":"MATH","questionCount":1,"blocks":[
                    {"id":"b","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        var exam = mock(Exam.class);
        when(exam.getName()).thenReturn("Test");
        when(exam.getCode()).thenReturn("TEST");
        var limits = SolverLimits.defaults();
        var clock = Clock.systemUTC();
        var checker = new DefaultExamChecker(new SolverInputBuilder(catalog, source, limits), limits, clock, mapper, catalog, source);
        var result = checker.check(new ExamCheckSnapshotReader.Snapshot(exam, document, null),
                clock.instant().plusSeconds(30), Duration.ofSeconds(30)).result();
        assertThat(result.path("status").asText()).isEqualTo("INVALID");
        assertThat(result.path("groups").findValuesAsText("code")).contains("QUESTION_NOT_PLAYABLE");
    }
}
