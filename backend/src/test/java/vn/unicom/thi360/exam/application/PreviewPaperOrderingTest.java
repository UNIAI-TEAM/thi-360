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
                new PreviewItem("SET", null, "set", "SET", 1, false, "passage", List.of(child), null));
        var block = new PreviewBlock("g", "Group", "MATH", "SINGLE_CHOICE", false, BigDecimal.ONE,
                1, 3, 3, null, null, null, List.of(), items);
        var part = new PreviewPart("p", "Part", 1, 10, 1, 3, BigDecimal.valueOf(3), null, List.of(block));
        var paper = new PreviewPaper(3, BigDecimal.valueOf(3), 10, true, false, List.of(part), List.of(), List.of(), List.of());
        var document = new ExamStructureDocument(List.of());

        PreviewPaper ordered = PreviewPaperOrdering.order(paper, document, 41, mapper);
        assertThat(PreviewPaperOrdering.order(paper, document, 41, mapper)).isEqualTo(ordered);
        var questions = ordered.parts().getFirst().blocks().getFirst().items().stream()
                .flatMap(value -> value.question() == null ? value.children().stream() : java.util.stream.Stream.of(value.question()))
                .collect(java.util.stream.Collectors.toMap(PreviewQuestion::questionId, value -> value));
        assertThat(questions.get("q1").originalOptionOrder()).containsExactly("a", "b", "c", "d", "e", "f");
        assertThat(questions.get("q1").optionOrder()).isNotEqualTo(questions.get("q1").originalOptionOrder());
        assertThat(questions.get("q2").optionOrder()).isEqualTo(questions.get("q2").originalOptionOrder());
        assertThat(questions.get("q3").optionOrder()).isEqualTo(questions.get("q3").originalOptionOrder());
        assertThat(questions.get("q1").originalNumber()).isEqualTo(1);
        assertThat(questions.get("q3").assignedTopicQuotaIds()).isEmpty();
    }

    private PreviewQuestion question(String id, int number, boolean shuffle) throws Exception {
        var body = mapper.readTree("""
                {"type":"SINGLE_CHOICE","shuffleOptions":%s,"options":[
                 {"id":"a"},{"id":"b"},{"id":"c"},{"id":"d"},{"id":"e"},{"id":"f"}]}
                """.formatted(shuffle));
        var options = List.of("a", "b", "c", "d", "e", "f").stream()
                .map(value -> new PreviewOption(value, value, false)).toList();
        return new PreviewQuestion(number, id, id, 1, "SINGLE_CHOICE", false, id, null, null,
                null, BigDecimal.ONE, options, null, null, null, body, null, null, null, null);
    }

    private static PreviewItem item(PreviewQuestion question) {
        return new PreviewItem("QUESTION", question, null, null, null, false, null, null, null);
    }
}
