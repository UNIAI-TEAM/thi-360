package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.domain.ExamTemplate;
import vn.unicom.thi360.exam.infrastructure.ExamTemplateRepository;

class ExamTemplateServiceTest {
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
}
