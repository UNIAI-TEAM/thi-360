package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructure;
import vn.unicom.thi360.exam.infrastructure.ExamBlockRefRepository;
import vn.unicom.thi360.exam.infrastructure.ExamKindProfileRepository;
import vn.unicom.thi360.exam.infrastructure.ExamRepository;
import vn.unicom.thi360.exam.infrastructure.ExamStructureRepository;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

class ExamCheckUnitSnapshotTests {
    @Test
    void includesExamQuotasInSnapshotAndFingerprint() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID id = UUID.randomUUID();
        var exams = mock(ExamRepository.class);
        var structures = mock(ExamStructureRepository.class);
        var references = mock(ExamBlockRefRepository.class);
        var source = mock(QuestionSourcePort.class);
        var structure = mock(ExamStructure.class);
        when(exams.findById(id)).thenReturn(Optional.of(mock(Exam.class)));
        when(structures.findById(id)).thenReturn(Optional.of(structure));
        when(structure.getParts()).thenReturn(mapper.readTree("{\"parts\":[]}"));
        when(structure.getExamQuotas()).thenReturn(mapper.readTree("[]"), mapper.readTree("""
                [{"id":"q","name":"Math","dimension":"GRADE",
                  "buckets":[{"codes":["GRADE_12"],"count":2}],"basis":"QUESTION",
                  "enforcement":"REQUIRED","subjectCodes":["TOAN"]}]
                """));
        when(references.findByExamIdAndVersionNo(any(), isNull())).thenReturn(List.of());
        when(source.sourceRevisions(any())).thenReturn(Map.of());
        var reader = new ExamCheckSnapshotReader(exams, structures, mock(ExamKindProfileRepository.class),
                references, source, mock(CatalogLookup.class), new ExamCheckFingerprint(mapper), mapper,
                mock(org.springframework.jdbc.core.JdbcTemplate.class), mock(ExamCheckValidity.class));

        var before = reader.read(id);
        var after = reader.read(id);

        assertThat(before.structure().examQuotas()).isEmpty();
        assertThat(after.structure().examQuotas()).singleElement()
                .satisfies(quota -> assertThat(quota.id()).isEqualTo("q"));
        assertThat(after.persistenceInput().fingerprint()).isNotEqualTo(before.persistenceInput().fingerprint());
    }
}
