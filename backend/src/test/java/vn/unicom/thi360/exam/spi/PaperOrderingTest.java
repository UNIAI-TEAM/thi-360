package vn.unicom.thi360.exam.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperOrderingTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) throws Exception { return MAPPER.readTree(text); }

    @Test
    void ordersUnitsAndOptionsWithoutChangingBodies() throws Exception {
        JsonNode choice = json("""
                {"type":"SINGLE_CHOICE","options":[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}]}
                """);
        JsonNode fixed = json("""
                {"type":"MULTIPLE_CHOICE","shuffleOptions":false,"options":[{"id":"a"},{"id":"b"},{"id":"c"}]}
                """);
        JsonNode matching = json("""
                {"type":"MATCHING","left":[{"id":"left"}],"right":[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}],"pairs":[{"leftId":"left","rightId":"a"}]}
                """);
        JsonNode trueFalse = json("""
                {"type":"TRUE_FALSE","statements":[{"id":"x"},{"id":"y"}]}
                """);
        JsonNode set = json("""
                {"type":"QUESTION_SET","children":[
                  {"id":"child1","body":{"type":"SINGLE_CHOICE","shuffleOptions":false,"options":[{"id":"x"},{"id":"y"}]}},
                  {"id":"child2","body":{"type":"MULTIPLE_CHOICE","options":[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}]}}]}
                """);
        var block1 = new PaperOrdering.Block("first", true, List.of(
                new PaperOrdering.Question("choice", choice), new PaperOrdering.Question("set", set),
                new PaperOrdering.Question("matching", matching), new PaperOrdering.Question("trueFalse", trueFalse)));
        var block2 = new PaperOrdering.Block("second", false, List.of(
                new PaperOrdering.Question("fixed", fixed), new PaperOrdering.Question("other", choice)));
        var paper = new PaperOrdering.Paper(List.of(new PaperOrdering.Part("p", List.of(
                new PaperOrdering.Section("s", List.of(block1, block2))))));
        var originalSet = set.deepCopy();
        boolean shuffledUnits = false, shuffledChoice = false, shuffledMultiple = false, shuffledMatching = false;
        for (long seed = 0; seed < 50; seed++) {
            var result = PaperOrdering.order(paper, seed);
            assertThat(result).isEqualTo(PaperOrdering.order(paper, seed));
            var blocks = result.paper().parts().getFirst().sections().getFirst().blocks();
            assertThat(blocks).extracting(PaperOrdering.Block::id).containsExactly("first", "second");
            assertThat(blocks.get(1).units()).extracting(PaperOrdering.Question::id).containsExactly("fixed", "other");
            shuffledUnits |= !blocks.getFirst().units().equals(block1.units());
            assertThat(result.optionOrder().get("fixed")).containsExactly("a", "b", "c");
            assertThat(result.optionOrder().get("child1")).containsExactly("x", "y");
            assertThat(result.optionOrder().get("child2")).containsExactlyInAnyOrder("a", "b", "c", "d");
            assertThat(result.optionOrder()).doesNotContainKey("trueFalse");
            assertThat(result.optionOrder().get("choice")).containsExactlyInAnyOrder("a", "b", "c", "d");
            assertThat(result.optionOrder().get("matching")).containsExactlyInAnyOrder("a", "b", "c", "d");
            shuffledChoice |= !result.optionOrder().get("choice").equals(List.of("a", "b", "c", "d"));
            shuffledMultiple |= !result.optionOrder().get("child2").equals(List.of("a", "b", "c", "d"));
            shuffledMatching |= !result.optionOrder().get("matching").equals(List.of("a", "b", "c", "d"));
        }
        assertThat(shuffledUnits && shuffledChoice && shuffledMultiple && shuffledMatching).isTrue();
        assertThat(set).isEqualTo(originalSet);
        assertThat(matching.path("pairs").get(0).path("rightId").asText()).isEqualTo("a");
        assertThat(trueFalse.path("statements").get(0).path("id").asText()).isEqualTo("x");
    }

    @Test
    void keepingOneOptionOrderDoesNotChangeLaterQuestionOrders() throws Exception {
        JsonNode shuffled = json("""
                {"type":"SINGLE_CHOICE","options":[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}]}
                """);
        JsonNode fixed = json("""
                {"type":"SINGLE_CHOICE","shuffleOptions":false,"options":[{"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"}]}
                """);
        for (long seed = 0; seed < 20; seed++) {
            var later = new PaperOrdering.Question("later", shuffled);
            var shuffledBlock = new PaperOrdering.Block("b", false,
                    List.of(new PaperOrdering.Question("first", shuffled), later));
            var fixedBlock = new PaperOrdering.Block("b", false,
                    List.of(new PaperOrdering.Question("first", fixed), later));
            var shuffledPaper = new PaperOrdering.Paper(List.of(new PaperOrdering.Part("p",
                    List.of(new PaperOrdering.Section("s", List.of(shuffledBlock))))));
            var fixedPaper = new PaperOrdering.Paper(List.of(new PaperOrdering.Part("p",
                    List.of(new PaperOrdering.Section("s", List.of(fixedBlock))))));
            assertThat(PaperOrdering.order(fixedPaper, seed).optionOrder().get("later"))
                    .isEqualTo(PaperOrdering.order(shuffledPaper, seed).optionOrder().get("later"));
        }
    }
}
