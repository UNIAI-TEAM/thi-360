package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ExamVersionChangesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void identifiesBlockInsertionRemovalAndReorder() throws Exception {
        var a = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"groupOrder\":\"FIXED\",\"blocks\":[{\"id\":\"a\",\"name\":\"A\"}]}]}]}");
        var ba = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"groupOrder\":\"FIXED\",\"blocks\":[{\"id\":\"b\",\"name\":\"B\"},{\"id\":\"a\",\"name\":\"A\"}]}]}]}");
        var ab = mapper.readTree("{\"parts\":[{\"id\":\"p\",\"sections\":[{\"id\":\"s\",\"groupOrder\":\"FIXED\",\"blocks\":[{\"id\":\"a\",\"name\":\"A\"},{\"id\":\"b\",\"name\":\"B\"}]}]}]}");
        var added = ExamVersionChanges.between(null, a, null, ba);
        assertThat(added).hasSize(1);
        assertThat(added.getFirst().kind()).isEqualTo("BLOCK_ADDED");
        assertThat(added.getFirst().blockId()).isEqualTo("b");
        var removed = ExamVersionChanges.between(null, ba, null, a);
        assertThat(removed).hasSize(1);
        assertThat(removed.getFirst().kind()).isEqualTo("BLOCK_REMOVED");
        assertThat(removed.getFirst().blockId()).isEqualTo("b");
        var reordered = ExamVersionChanges.between(null, ba, null, ab);
        assertThat(reordered).hasSize(1);
        assertThat(reordered.getFirst().kind()).isEqualTo("ORDER_CHANGED");
        assertThat(reordered.getFirst().where()).isEqualTo("parts/parts/p/sections/s/blocks");
    }

    @Test
    void detectsReordersAtEveryCollectionLevelAndAlongsideInsertions() throws Exception {
        for (String collection : new String[] {"parts", "sections", "blocks", "options"}) {
            var before = mapper.readTree("{\"" + collection + "\":[{\"id\":\"a\"},{\"id\":\"b\"}]}");
            var after = mapper.readTree("{\"" + collection + "\":[{\"id\":\"b\"},{\"id\":\"c\"},{\"id\":\"a\"}]}");
            var changes = ExamVersionChanges.between(null, before, null, after);
            assertThat(changes.stream().filter(change -> change.kind().equals("ORDER_CHANGED")).toList())
                    .singleElement().satisfies(change -> assertThat(change.where()).isEqualTo("parts/" + collection));
            assertThat(changes).hasSize(2);
        }
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

    @Test
    void listingChangeIsReported() throws Exception {
        var before = mapper.readTree("{\"listing\":{\"listPriceVnd\":100000,\"summary\":\"Same\",\"coverAssetId\":null}}");
        var after = mapper.readTree("{\"listing\":{\"listPriceVnd\":120000,\"summary\":\"Same\",\"coverAssetId\":null}}");
        var changes = ExamVersionChanges.between(before, null, after, null);
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().where()).isEqualTo("config/listing/listPriceVnd");
        assertThat(changes.getFirst().before()).isEqualTo("100000");
        assertThat(changes.getFirst().after()).isEqualTo("120000");
        // A listing that did not change is not a change.
        assertThat(ExamVersionChanges.between(before, null, before, null)).isEmpty();
    }
}
