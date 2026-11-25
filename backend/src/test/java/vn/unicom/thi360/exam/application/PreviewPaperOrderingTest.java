package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.api.ExamPreviewController.*;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;

class PreviewPaperOrderingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void retainsOriginalAndRespectsKeepOrderIncludingSetChildren() throws Exception {
        PreviewQuestion shuffle = question("q1", 1, true);
        PreviewQuestion keep = question("q2", 2, false);
        PreviewQuestion child = question("q3", 3, false);
        List<PreviewItem> items = List.of(item(shuffle), item(keep),
                new PreviewItem("SET", null, "set", "SET", 1, false, "passage", List.of(child), null, List.of()));
        var block = new PreviewBlock("g", "Group", "MATH", "SINGLE_CHOICE", false, BigDecimal.ONE,
                1, 3, 3, null, null, null, List.of(), items);
        var part = new PreviewPart("p", "Part", 1, 10, 1, 3, BigDecimal.valueOf(3), null, List.of(block), List.of());
        var paper = new PreviewPaper(3, BigDecimal.valueOf(3), 10, true, false, List.of(part), List.of(), List.of(), List.of());
        var document = new ExamStructureDocument(List.of());

        PreviewPaper ordered = PreviewPaperOrdering.order(paper, document, 41, mapper, java.util.Map.of());
        assertThat(PreviewPaperOrdering.order(paper, document, 41, mapper, java.util.Map.of())).isEqualTo(ordered);
        var questions = ordered.parts().getFirst().blocks().getFirst().items().stream()
                .flatMap(value -> value.question() == null ? value.children().stream() : java.util.stream.Stream.of(value.question()))
                .collect(java.util.stream.Collectors.toMap(PreviewQuestion::questionId, value -> value));
        assertThat(questions.get("q1").originalOptionOrder()).containsExactly("a", "b", "c", "d", "e", "f");
        assertThat(questions.get("q1").optionOrder()).isNotEqualTo(questions.get("q1").originalOptionOrder());
        assertThat(questions.get("q2").optionOrder()).isEqualTo(questions.get("q2").originalOptionOrder());
        assertThat(questions.get("q3").optionOrder()).isEqualTo(questions.get("q3").originalOptionOrder());
        assertThat(questions.get("q1").originalNumber()).isEqualTo(1);
        assertThat(questions.get("q3").assignedTopicQuotaIds()).containsExactly("topic-q3");
    }

    @Test
    void shufflesCombinedSectionBlockWhenMixBlocksIsEnabled() throws Exception {
        List<PreviewItem> items = List.of(
                item(question("q1", 1, true)),
                item(question("q2", 2, true)),
                new PreviewItem("SET", null, "set1", "SET-1", 1, false, "passage",
                        List.of(question("c1", 3, true), question("c2", 4, true)), null, List.of()),
                item(question("q3", 5, true)));
        var combinedBlock = new PreviewBlock("sec-mixed", "", "MATH", null, false, BigDecimal.ONE,
                1, 5, 5, null, null, null, List.of(), items);
        var part = new PreviewPart("p", "Part", 1, 10, 1, 5, BigDecimal.valueOf(5), null, List.of(combinedBlock), List.of());
        var paper = new PreviewPaper(5, BigDecimal.valueOf(5), 10, true, false, List.of(part), List.of(), List.of(), List.of());
        var unshuffledBlock = new vn.unicom.thi360.exam.domain.QuestionBlock("b1", "G1",
                vn.unicom.thi360.questionbank.spi.QuestionType.SINGLE_CHOICE, 5, null, null, null, null,
                null, null, false, null, List.of(), null, false, false, List.of(), List.of(), null,
                List.of(), false, List.of(), null);
        var mixedSection = new vn.unicom.thi360.exam.domain.ExamSection("sec-mixed", "MATH", 5, null, null, null, null,
                List.of(unshuffledBlock), List.of(), null, null, null, true);
        var document = new ExamStructureDocument(List.of(new vn.unicom.thi360.exam.domain.ExamPart(
                "p", 1, "Part", 10, 5, null, null, null, null, List.of(mixedSection), List.of())));

        boolean reordered = false;
        for (long seed = 1; seed <= 30; seed++) {
            PreviewPaper ordered = PreviewPaperOrdering.order(paper, document, seed, mapper, java.util.Map.of());
            List<PreviewItem> outItems = ordered.parts().getFirst().blocks().getFirst().items();
            List<String> ids = outItems.stream()
                    .map(it -> it.question() == null ? it.setId() : it.question().questionId())
                    .toList();
            if (!ids.equals(List.of("q1", "q2", "set1", "q3"))) {
                reordered = true;
            }
            PreviewItem setItem = outItems.stream().filter(it -> "SET".equals(it.kind())).findFirst().orElseThrow();
            assertThat(setItem.children().get(1).number()).isEqualTo(setItem.children().get(0).number() + 1);
        }
        assertThat(reordered).isTrue();
    }

    private PreviewQuestion question(String id, int number, boolean shuffle) throws Exception {
        var body = mapper.readTree("""
                {"type":"SINGLE_CHOICE","shuffleOptions":%s,"options":[
                 {"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"},{"id":"e"},{"id":"f"}]}
                """.formatted(shuffle));
        var options = List.of("a", "b", "c", "d", "e", "f").stream()
                .map(value -> new PreviewOption(value, value, false)).toList();
        return new PreviewQuestion(number, id, id, 1, "SINGLE_CHOICE", false, id, null, null, null, BigDecimal.ONE, options, null, null, null, body, null, null, null, List.of("topic-" + id), List.of());
    }

    private static PreviewItem item(PreviewQuestion question) {
        return new PreviewItem("QUESTION", question, null, null, null, false, null, null, null, List.of());
    }
}
