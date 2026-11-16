package vn.unicom.thi360.questionbank.domain.importing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class QuestionImportParserTests {
    private static final ImportDictionary DICTIONARY = ImportDictionary.standard();
    private static final ImportCatalog CATALOG = new ImportCatalog(
        List.of(option("ENGLISH"), option("MATH")), List.of(option("GRADE_11"), option("GRADE_12")),
        List.of(option("RECOGNITION")), List.of(), List.of(), List.of());

    private static ImportCatalogOption option(String code) {
        return new ImportCatalogOption(UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)), code, code, true);
    }

    private static ImportParseResult parse(List<List<String>> table) throws Exception {
        ImportSheet sheet = ImportSheets.fromTable(ImportFileType.CSV, null, table, ImportLimits.DEFAULT);
        return QuestionImportParser.parse(sheet, ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY),
            CATALOG, List.of(), DICTIONARY);
    }

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    void templateContainsEachType(QuestionType type) throws Exception {
        ImportSheet sheet = new XlsxImportSheetReader().read("template.xlsx", QuestionImportTemplate.create(), ImportLimits.DEFAULT);
        ImportParseResult result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), CATALOG, List.of(), DICTIONARY);
        assertFalse(result.hasErrors(), () -> result.rows().stream().flatMap(r -> r.issues().stream()).toList().toString());
        assertTrue(result.rows().stream().anyMatch(r -> r.draft().type() == type));
    }

    @Test
    void decimalComma() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "points", "correctAnswer"),
            List.of("SHORT_ANSWER", "Giá trị", "MATH", "GRADE_11", "RECOGNITION", "0,75", "0,5\n1/2")));
        var draft = result.rows().getFirst().draft();
        assertEquals(new BigDecimal("0.75"), draft.points());
        assertEquals(List.of("0,5", "1/2"), ((QuestionBody.ShortAnswerBody) draft.body()).answers());
    }

    @Test
    void legacyCodes() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "correctAnswer", "scoring", "options", "matchRight"),
            List.of("FILL_IN", "Giá trị [[b1]]", "MATH", "GRADE_11", "RECOGNITION", "b1 = a | b", "b1 = 0,5", "", ""),
            List.of("FILL_IN", "[[b1]] và [[b2]]", "MATH", "GRADE_11", "RECOGNITION", "b1 = a\nb2 = b", "", "", ""),
            List.of("DRAG_DROP", "Ghép", "MATH", "GRADE_11", "RECOGNITION", "1 = A", "", "1. trái", "A. phải")));
        assertFalse(result.hasErrors(), () -> result.rows().toString());
        var first = result.rows().get(0).draft();
        assertEquals(QuestionType.SHORT_ANSWER, first.type());
        assertEquals("Giá trị ____", first.content());
        assertEquals(new BigDecimal("0.5"), first.points());
        assertEquals(List.of("a", "b"), ((QuestionBody.ShortAnswerBody) first.body()).answers());
        assertEquals(QuestionType.CLOZE, result.rows().get(1).draft().type());
        assertEquals(QuestionType.MATCHING, result.rows().get(2).draft().type());
    }

    @Test
    void setWithBlockingChildDropped() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "setCode", "options", "correctAnswer"),
            List.of("QUESTION_SET", "Bộ", "MATH", "GRADE_11", "RECOGNITION", "S", "", ""),
            List.of("SINGLE_CHOICE", "Con", "", "", "", "S", "A. Một\nB. Hai", "Z"),
            List.of("SHORT_ANSWER", "Con khác", "", "", "", "S", "", "1")));
        assertTrue(result.rows().stream().allMatch(r -> r.status() == ImportRowStatus.ERROR));
        assertTrue(result.rows().getFirst().issues().stream().anyMatch(i -> i.code() == ImportIssueCode.SET_CHILD_INVALID));
    }

    @Test
    void missingAnswerIsWarningAndDuplicatesBlock() throws Exception {
        var result = parse(List.of(
            List.of("sourceCode", "type", "content", "subject", "grade", "difficulty"),
            List.of("x", "Trả lời ngắn", "Câu", "MATH", "GRADE_11", "RECOGNITION"),
            List.of(" X ", "SHORT_ANSWER", "Câu 2", "MATH", "GRADE_11", "RECOGNITION")));
        assertEquals(ImportRowStatus.VALID, result.rows().get(0).status());
        assertTrue(result.rows().get(0).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.ANSWER_MISSING && !i.blocking()));
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.DUPLICATE_IN_FILE && i.blocking()));
    }

    @Test
    void defaultVietnameseTypeAndLiteralShortAnswer() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "correctAnswer"),
            List.of("Trả lời ngắn", "Câu", "MATH", "GRADE_11", "RECOGNITION", "a | b")));
        assertEquals(List.of("a | b"),
            ((QuestionBody.ShortAnswerBody) result.rows().getFirst().draft().body()).answers());
    }

    @Test
    void matchingUsesLeftLabelsRatherThanRowPositions() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "options", "matchRight", "correctAnswer"),
            List.of("MATCHING", "Ghép", "MATH", "GRADE_11", "RECOGNITION",
                "2. Hai\n5. Năm", "A. Two\nB. Five", "2 = A\n5 = B")));
        assertFalse(result.hasErrors(), () -> result.rows().toString());
        var body = (QuestionBody.MatchingBody) result.rows().getFirst().draft().body();
        assertEquals(List.of("l2", "l5"), body.pairs().stream().map(QuestionBody.MatchingPair::leftId).toList());
    }

    @Test
    void plainTextEscapedButHtmlPreserved() {
        assertEquals("x &lt; 3<br>a &amp; b", ImportCellSyntax.processCell("x < 3\na & b"));
        assertEquals("<strong>x</strong>", ImportCellSyntax.processCell("<strong>x</strong>"));
    }
}
