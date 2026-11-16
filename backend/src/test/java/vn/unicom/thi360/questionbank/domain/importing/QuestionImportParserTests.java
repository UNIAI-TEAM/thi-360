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
    void childSourceCodeIsIgnoredAndReported() throws Exception {
        var result = parse(List.of(
            List.of("sourceCode", "type", "content", "subject", "grade", "difficulty", "setCode", "correctAnswer"),
            List.of("SET", "Bộ câu hỏi", "Bộ", "MATH", "GRADE_11", "RECOGNITION", "S", ""),
            List.of("SET", "SHORT_ANSWER", "Con", "", "", "", "S", "1")));
        assertFalse(result.hasErrors());
        assertNull(result.rows().get(1).draft().sourceCode());
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.CHILD_SOURCE_CODE_IGNORED && !i.blocking()));
    }

    @Test
    void duplicateSetChildrenAreNotCountedAsValid() throws Exception {
        var sheet = ImportSheets.fromTable(ImportFileType.CSV, null, List.of(
            List.of("sourceCode", "type", "content", "subject", "grade", "difficulty", "setCode", "correctAnswer"),
            List.of("SET", "QUESTION_SET", "Bộ", "MATH", "GRADE_11", "RECOGNITION", "S", ""),
            List.of("", "SHORT_ANSWER", "Con", "", "", "", "S", "1")), ImportLimits.DEFAULT);
        var existing = new ExistingQuestionRef(UUID.randomUUID(), "CH-1", QuestionType.QUESTION_SET, "set");
        var result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), CATALOG, List.of(existing), DICTIONARY);
        assertEquals(2, result.summary().duplicate());
        assertEquals(0, result.summary().validSetChildren());
    }

    @Test
    void mappingDetectsDuplicateFieldsAndNormalizesHeaders() {
        assertEquals("ma nguon", ImportColumnMapper.normalizeHeader("Mã nguồn"));
        assertEquals("kien thuc mapid", ImportColumnMapper.normalizeHeader("Kiến thức (MAPID)"));
        var columns = List.of(new ImportColumn("c0", "Dạng câu", ""),
            new ImportColumn("c1", "Nội dung", ""), new ImportColumn("c2", "Môn", ""),
            new ImportColumn("c3", "Lớp", ""), new ImportColumn("c4", "Mức độ", ""),
            new ImportColumn("c5", "Lời giải", ""));
        var mapping = ImportColumnMapper.suggestMapping(columns, DICTIONARY);
        var changed = new java.util.HashMap<>(mapping.byColumnKey());
        changed.put("c5", "content");
        var check = ImportColumnMapper.checkMapping(columns, new ColumnMapping(changed));
        assertFalse(check.valid());
        assertEquals(List.of(ImportField.CONTENT), check.duplicateFields());
        assertEquals(java.util.Set.of("c1", "c5"), check.duplicateColumnKeys());
    }

    @Test
    void singleChoiceRejectsTwoAnswersAndTrueFalseNeedsMatchingCount() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "options", "correctAnswer"),
            List.of("SINGLE_CHOICE", "Hai đáp án", "MATH", "GRADE_11", "RECOGNITION", "1\n2", "A, B"),
            List.of("TRUE_FALSE", "Thiếu ý", "MATH", "GRADE_11", "RECOGNITION", "a. x\nb. y", "Đ")));
        assertTrue(result.rows().get(0).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.ANSWER_INVALID && i.blocking()));
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.ANSWER_INVALID && i.blocking()));
    }

    @Test
    void rubricReadsColonDescriptionAndRejectsZeroPoints() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "rubric"),
            List.of("ESSAY", "Bài 1", "MATH", "GRADE_11", "RECOGNITION", "Ý 1: nêu luận điểm: 0,5"),
            List.of("ESSAY", "Bài 2", "MATH", "GRADE_11", "RECOGNITION", "Ý 1: 0"),
            List.of("ESSAY", "Bài 3", "MATH", "GRADE_11", "RECOGNITION", "Mở bài; Thân bài: 1")));
        var body = (QuestionBody.EssayBody) result.rows().get(0).draft().body();
        assertEquals("Ý 1: nêu luận điểm", body.rubric().getFirst().description());
        assertEquals(new BigDecimal("0.5"), body.rubric().getFirst().points());
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.RUBRIC_INVALID && i.blocking()));
        assertTrue(result.rows().get(2).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.RUBRIC_INVALID && i.blocking()));
    }

    @Test
    void bracesBalanceChecks() {
        assertTrue(ImportCellSyntax.bracesBalanced("$\\frac{1}{2}$"));
        assertFalse(ImportCellSyntax.bracesBalanced("$\\frac{1}{x$"));
        assertFalse(ImportCellSyntax.bracesBalanced("}{"));
        assertTrue(ImportCellSyntax.bracesBalanced("\\{ a"));
        assertTrue(ImportCellSyntax.bracesBalanced(""));
    }

    @Test
    void essayReadsAnswerCellAsSample() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "correctAnswer"),
            List.of("ESSAY", "Viết bài", "MATH", "GRADE_11", "RECOGNITION", "Xét f(x) = x^2")));
        var body = (QuestionBody.EssayBody) result.rows().getFirst().draft().body();
        assertEquals("Xét f(x) = x^2", body.sampleAnswer());
        assertTrue(result.rows().getFirst().issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.GRADING_GUIDE_MISSING && !i.blocking()));
    }

    @Test
    void badPassageDropsChildrenAndDuplicatePassageDuplicatesChildren() throws Exception {
        var header = List.of("sourceCode", "type", "content", "subject", "grade", "difficulty", "setCode", "options", "correctAnswer");
        var invalidPassage = parse(List.of(header,
            List.of("S-1", "QUESTION_SET", "Ngữ liệu", "NOPE", "GRADE_11", "RECOGNITION", "S", "", ""),
            List.of("", "SINGLE_CHOICE", "Câu con", "", "", "", "S", "1\n2", "A"),
            List.of("T-1", "QUESTION_SET", "Ngữ liệu 2", "MATH", "GRADE_11", "RECOGNITION", "T", "", ""),
            List.of("", "SINGLE_CHOICE", "Câu con lỗi", "", "", "", "T", "1\n2", "Z")));
        var rows = invalidPassage.rows();
        assertTrue(rows.get(0).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.SUBJECT_UNKNOWN));
        assertEquals(ImportRowStatus.ERROR, rows.get(1).status());
        assertTrue(rows.get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.SET_PASSAGE_INVALID));
        assertTrue(rows.get(1).dropped());
        assertTrue(rows.get(2).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.SET_CHILD_INVALID));
        assertEquals(ImportRowStatus.ERROR, rows.get(3).status());
        assertTrue(rows.get(3).dropped());

        var sheet2 = ImportSheets.fromTable(ImportFileType.CSV, null, List.of(header,
            List.of("OLD-SET", "QUESTION_SET", "Ngữ liệu", "MATH", "GRADE_11", "RECOGNITION", "S", "", ""),
            List.of("", "SINGLE_CHOICE", "Câu con", "", "", "", "S", "1\n2", "A")), ImportLimits.DEFAULT);
        var existing = new ExistingQuestionRef(UUID.randomUUID(), "CH-9", QuestionType.QUESTION_SET, "old-set");
        var dupResult = QuestionImportParser.parse(sheet2,
            ImportColumnMapper.suggestMapping(sheet2.columns(), DICTIONARY), CATALOG, List.of(existing), DICTIONARY);
        assertEquals(List.of(ImportRowStatus.DUPLICATE, ImportRowStatus.DUPLICATE),
            dupResult.rows().stream().map(ImportRowResult::status).toList());
    }

    @Test
    void sourceDuplicateMarksRowAndTypeMismatchBlocks() throws Exception {
        var header = List.of("sourceCode", "type", "content", "subject", "grade", "difficulty", "options", "correctAnswer");
        var sheet = ImportSheets.fromTable(ImportFileType.CSV, null, List.of(header,
            List.of("dup", "SINGLE_CHOICE", "Một", "MATH", "GRADE_11", "RECOGNITION", "1\n2", "A"),
            List.of("dup2", "SINGLE_CHOICE", "Hai", "MATH", "GRADE_11", "RECOGNITION", "1\n2", "B")), ImportLimits.DEFAULT);
        var existing = List.of(
            new ExistingQuestionRef(UUID.randomUUID(), "CH-1", QuestionType.SINGLE_CHOICE, " DUP "),
            new ExistingQuestionRef(UUID.randomUUID(), "CH-2", QuestionType.ESSAY, "dup2"));
        var result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), CATALOG, existing, DICTIONARY);
        assertEquals(ImportRowStatus.DUPLICATE, result.rows().get(0).status());
        assertEquals("CH-1", result.rows().get(0).duplicateOf().code());
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.IMPORT_SOURCE_TYPE_MISMATCH && i.blocking()));
    }

    @Test
    void childLeavesEmptyClassificationNullForInheritance() throws Exception {
        var nodes = List.of(
            new ImportKnowledgeNode(UUID.randomUUID(), "1D4-3-1", "MATH", true, true));
        var catalog = new ImportCatalog(CATALOG.subjects(), CATALOG.grades(), CATALOG.difficulties(),
            CATALOG.taskTypes(), CATALOG.tags(), nodes);
        var sheet = ImportSheets.fromTable(ImportFileType.CSV, null, List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "setCode", "knowledge", "options", "correctAnswer"),
            List.of("QUESTION_SET", "Ngữ liệu", "MATH", "GRADE_11", "RECOGNITION", "S", "1D4-3-1", "", ""),
            List.of("SINGLE_CHOICE", "Câu con", "", "", "", "S", "", "1\n2", "A")), ImportLimits.DEFAULT);
        var result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), catalog, List.of(), DICTIONARY);
        var child = result.rows().get(1).draft();
        assertNull(child.subjectCode());
        assertNull(child.gradeCode());
        assertNull(child.difficultyCode());
        assertTrue(child.knowledge().isEmpty());
        var setDraft = result.rows().get(0).draft();
        assertEquals(1, setDraft.children().size());
        assertEquals(child, setDraft.children().getFirst());
    }

    @Test
    void unknownCatalogValuesBlockWhileMissingGuideWarns() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "tags", "rubric"),
            List.of("ESSAY", "Viết bài", "MATH", "GRADE_11", "RECOGNITION", "", "Mở bài: 0,25\nThân bài: 0,75"),
            List.of("ESSAY", "Viết bài", "MATH", "GRADE_11", "RECOGNITION", "UNKNOWN", "")));
        assertTrue(result.rows().get(0).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.GRADING_GUIDE_MISSING && !i.blocking()));
        var body = (QuestionBody.EssayBody) result.rows().get(0).draft().body();
        assertEquals(List.of(new BigDecimal("0.25"), new BigDecimal("0.75")),
            body.rubric().stream().map(QuestionBody.EssayCriterion::points).toList());
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.TAG_UNKNOWN && i.blocking()));
    }

    @Test
    void plainTextEscapedButHtmlPreserved() {
        assertEquals("x &lt; 3<br>a &amp; b", ImportCellSyntax.processCell("x < 3\na & b"));
        assertEquals("<strong>x</strong>", ImportCellSyntax.processCell("<strong>x</strong>"));
    }
}
