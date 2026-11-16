package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ExamVersionChangesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void identifiesBlockInsertionRemovalAndReorder() throws Exception {
        var a = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"blocks\":[{\"id\":\"a\",\"name\":\"A\"}]}]}]}");
        var ba = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"blocks\":[{\"id\":\"b\",\"name\":\"B\"},{\"id\":\"a\",\"name\":\"A\"}]}]}]}");
        var ab = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"blocks\":[{\"id\":\"a\",\"name\":\"A\"},{\"id\":\"b\",\"name\":\"B\"}]}]}]}");
        var added = ExamVersionChanges.between(null, a, null, ba);
        assertThat(added).hasSize(1);
        assertThat(added.getFirst().kind()).isEqualTo("BLOCK_ADDED");
        assertThat(added.getFirst().blockId()).isEqualTo("b");
        var removed = ExamVersionChanges.between(null, ba, null, a);
        assertThat(removed).hasSize(1);
        assertThat(removed.getFirst().kind()).isEqualTo("BLOCK_REMOVED");
        assertThat(removed.getFirst().blockId()).isEqualTo("b");
        assertThat(ExamVersionChanges.between(null, ba, null, ab)).isEmpty();
    }

    @Test
    void insertedBlockInsideOptionIsNotAnOptionAddition() throws Exception {
        var before = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"options\":[{\"id\":\"o\",\"blocks\":[]}]}]}");
        var after = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"options\":[{\"id\":\"o\",\"blocks\":[{\"id\":\"b\"}]}]}]}");
        var changes = ExamVersionChanges.between(null, before, null, after);
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().kind()).isEqualTo("BLOCK_ADDED");
        assertThat(changes.getFirst().blockId()).isEqualTo("b");
    }
}
