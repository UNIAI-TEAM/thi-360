package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.solver.GroupWitness;
import vn.unicom.thi360.exam.domain.solver.PathResult;
import vn.unicom.thi360.exam.domain.solver.QuestionPick;
import vn.unicom.thi360.exam.domain.solver.SolvePath;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.exam.domain.solver.SubjectWitness;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/** BE-20 rows 5 and 6 at the evidence level: what reaches the port and which facts come out. */
class ExamCheckUnitAttachmentFactsTests {
    private final ObjectMapper mapper = new ObjectMapper();

    private final QuestionSourcePort source = mock(QuestionSourcePort.class);
    private final QuestionAttachmentPort attachments = mock(QuestionAttachmentPort.class);

    private PathResult path(String group, QuestionPick... picks) {
        return new PathResult(SolvePath.of(0, List.of()), SolveStatus.VALID, null, null, null,
                List.of(new SubjectWitness("VAN", picks.length, List.of(new GroupWitness(group, List.of(picks))))),
                List.of(), null);
    }

    @Test
    void versionsWithoutAttachmentIdsNeverReachThePortSoTheStorageIsNotCalled() throws Exception {
        var document = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"VAN","questionCount":1,
                  "blocks":[{"id":"b","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        UUID id = UUID.randomUUID();
        var key = new QuestionSourcePort.VersionKey(id, 1);
        when(source.loadVersions(anyList())).thenReturn(List.of(new QuestionSourcePort.Version(key, mapper.readTree(
                "{\"attachmentIds\":[],\"body\":{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"correct\":true},{\"correct\":false}]}}"),
                "Q1", Map.of())));
        var facts = ExamCheckUnitEvidence.witnessFacts(source, attachments, document,
                List.of(path("b", new QuestionPick(id.toString(), 1))), Map.of()).get(0L);
        assertThat(facts).singleElement().satisfies(fact -> assertThat(fact.attachmentsReadable()).isNull());
        verifyNoInteractions(attachments);
    }

    @Test
    void oneBatchedCallForEveryVersionWithFilesAndOneRootFactPerSet() throws Exception {
        var document = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","sections":[{"id":"s","subjectCode":"VAN","questionCount":3,
                  "blocks":[{"id":"single","questionType":"SINGLE_CHOICE","questionCount":1},
                            {"id":"set","questionType":"QUESTION_SET","setCount":1,"setSize":2}]}]}]}
                """);
        UUID single = UUID.randomUUID(), set = UUID.randomUUID();
        var singleKey = new QuestionSourcePort.VersionKey(single, 1);
        var setKey = new QuestionSourcePort.VersionKey(set, 1);
        String choice = "{\"type\":\"SINGLE_CHOICE\",\"options\":[{\"correct\":true},{\"correct\":false}]}";
        when(source.loadVersions(anyList())).thenReturn(List.of(
                new QuestionSourcePort.Version(singleKey, mapper.readTree(
                        "{\"attachmentIds\":[\"" + UUID.randomUUID() + "\"],\"body\":" + choice + "}"), "SINGLE", Map.of()),
                new QuestionSourcePort.Version(setKey, mapper.readTree("{\"attachmentIds\":[\"" + UUID.randomUUID()
                        + "\",\"" + UUID.randomUUID() + "\"],\"body\":{\"type\":\"QUESTION_SET\",\"children\":[{\"body\":"
                        + choice + "},{\"body\":" + choice + "}]}}"), "SET", Map.of())));
        when(attachments.attachments(anyCollection(), eq(true))).thenReturn(Map.of(
                singleKey, List.of(new QuestionAttachmentPort.Attachment(UUID.randomUUID(), "a.mp3", "audio/mpeg", true)),
                setKey, List.of(new QuestionAttachmentPort.Attachment(UUID.randomUUID(), "b.mp3", "audio/mpeg", true),
                        new QuestionAttachmentPort.Attachment(UUID.randomUUID(), "c.mp3", "audio/mpeg", false))));
        var group = List.of(new GroupWitness("single", List.of(new QuestionPick(single.toString(), 1))),
                new GroupWitness("set", List.of(new QuestionPick(set.toString(), 1))));
        var path = new PathResult(SolvePath.of(0, List.of()), SolveStatus.VALID, null, null, null,
                List.of(new SubjectWitness("VAN", 3, group)), List.of(), null);
        var facts = ExamCheckUnitEvidence.witnessFacts(source, attachments, document, List.of(path), Map.of()).get(0L);
        verify(attachments).attachments(anyCollection(), eq(true));
        verify(attachments, org.mockito.Mockito.times(1)).attachments(anyCollection(), anyBoolean());
        assertThat(facts).filteredOn(fact -> "SINGLE".equals(fact.questionCode()))
                .singleElement().satisfies(fact -> assertThat(fact.attachmentsReadable()).isTrue());
        assertThat(facts).filteredOn(fact -> "SET".equals(fact.questionCode()) && fact.location().childPosition() == null)
                .singleElement().satisfies(fact -> {
                    assertThat(fact.attachmentsReadable()).isFalse();
                    assertThat(fact.location().childPosition()).isNull();
                });
        assertThat(facts).filteredOn(fact -> fact.location().childPosition() != null)
                .allSatisfy(fact -> assertThat(fact.attachmentsReadable()).isNull());
    }
}
