package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamTemplateResponse;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.infrastructure.ExamTemplateRepository;

class ExamTemplateServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultTemplateTakesPrecedenceOverNewerYear() {
        ExamTemplateRepository repository = mock(ExamTemplateRepository.class);
        ExamTemplate oldDefault = mock(ExamTemplate.class);
        ExamTemplate newer = mock(ExamTemplate.class);
        when(oldDefault.getBuildMode()).thenReturn("STANDARD");
        when(newer.getBuildMode()).thenReturn("STANDARD");
        when(oldDefault.isDefaultTemplate()).thenReturn(true);
        when(oldDefault.getStructureYear()).thenReturn(2025);
        when(oldDefault.getCode()).thenReturn("OLD");
        when(newer.getStructureYear()).thenReturn(2026);
        when(newer.getCode()).thenReturn("NEW");
        when(repository.findByKindCodeAndScopeAndActiveTrue("HSA", "FULL"))
                .thenReturn(List.of(newer, oldDefault));

        assertThat(new ExamTemplateService(repository).findBestMatch("HSA", "FULL", null, null, null))
                .isSameAs(oldDefault);
    }

    @Test
    void partSummariesMatchBlockCountsAndSkipSubjectPoolOrSubjectChoice() throws Exception {
        var summaries = ExamTemplateService.partSummaries(mapper.readTree("""
                {"parts":[
                  {"id":"p-set","order":1,"name":"Set + Remainder","questionCount":12,
                   "sections":[{"id":"s1","subjectCode":"TOAN","questionCount":12,"blocks":[
                     {"id":"b1","questionType":"QUESTION_SET","questionCount":10,"setCount":2,"setSize":3},
                     {"id":"b2","questionType":"SINGLE_CHOICE"},
                     {"id":"b3","questionType":"ESSAY","questionCount":5,"omitted":true}]}]},
                  {"id":"p-pool","order":2,"name":"Pool","questionCount":15,
                   "codeSource":{"source":"GLOBAL_LIST"},"sections":[],"options":[],
                   "subjectPool":[
                     {"id":"ly","subjectCode":"LY","questionCount":15,"blocks":[{"id":"b4","questionType":"SINGLE_CHOICE","questionCount":15}]},
                     {"id":"hoa","subjectCode":"HOA","questionCount":15,"blocks":[{"id":"b5","questionType":"SINGLE_CHOICE","questionCount":15}]}]},
                  {"id":"p-choice-sci","order":3,"name":"Subject Choice","questionCount":50,
                   "participation":{"mode":"CHOICE","choose":1},"sections":[],
                   "options":[
                     {"id":"sci","kind":"SUBJECT_CHOICE","chooseSubjects":3,"countsByPick":[17,17,16],
                      "sections":[
                        {"id":"s-ly","subjectCode":"LY","questionCount":17,"blocks":[{"id":"b6","questionType":"SINGLE_CHOICE","questionCount":17}]},
                        {"id":"s-hoa","subjectCode":"HOA","questionCount":17,"blocks":[{"id":"b7","questionType":"SINGLE_CHOICE","questionCount":17}]}]}]},
                  {"id":"p-choice-comb","order":4,"name":"Combination Choice","questionCount":15,
                   "participation":{"mode":"CHOICE","choose":1},"sections":[],
                   "options":[
                     {"id":"o1","kind":"COMBINATION","code":"CA1",
                      "sections":[{"id":"s-c1","subjectCode":"LY","questionCount":10,"blocks":[{"id":"b8","questionType":"SINGLE_CHOICE","questionCount":10}]}],
                      "blocks":[{"id":"b9","questionType":"SHORT_ANSWER","questionCount":5}]},
                     {"id":"o2","kind":"COMBINATION","code":"CA2",
                      "sections":[{"id":"s-c2","subjectCode":"HOA","questionCount":15,"blocks":[{"id":"b10","questionType":"SINGLE_CHOICE","questionCount":15}]}]}]},
                  {"id":"p-review","order":5,"name":"Review Placeholder",
                   "sections":[{"id":"s-rev","blocks":[{"id":"b-rev"}]}],"options":[]}
                ]}
                """));

        assertThat(summaries).hasSize(5);
        assertThat(summaries.get(0).questionTypes()).containsExactly(
                new ExamTemplateResponse.QuestionTypeCount("QUESTION_SET", 6),
                new ExamTemplateResponse.QuestionTypeCount("SINGLE_CHOICE", 6));
        assertThat(summaries.get(1).questionTypes()).isEmpty();
        assertThat(summaries.get(2).questionTypes()).isEmpty();
        assertThat(summaries.get(3).questionTypes()).containsExactly(
                new ExamTemplateResponse.QuestionTypeCount("SINGLE_CHOICE", 10),
                new ExamTemplateResponse.QuestionTypeCount("SHORT_ANSWER", 5));
        assertThat(summaries.get(4).questionTypes()).isEmpty();
    }
}
