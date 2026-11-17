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
import vn.unicom.thi360.questionbank.spi.AnswerFormat;

import vn.unicom.thi360.questionbank.spi.QuestionType;
class QuestionImportParserTests {
    private static final ImportDictionary DICTIONARY = ImportDictionary.standard();
    private static final ImportCatalog CATALOG = new ImportCatalog(
        List.of(option("ANH"), option("ENGLISH"), option("MATH"), option("TOAN"), option("VAN")),
        List.of(option("G11"), option("G12"), option("GRADE_11"), option("GRADE_12")),
        List.of(option("NB"), option("RECOGNITION"), option("TH")), List.of(), List.of(), List.of());

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
        ImportSheet sheet = new XlsxImportSheetReader().read("template.xlsx", QuestionImportTemplate.create("Questions"), ImportLimits.DEFAULT);
        ImportParseResult result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), CATALOG, List.of(), DICTIONARY);
        assertFalse(result.hasErrors(), () -> result.rows().stream().flatMap(r -> r.issues().stream()).toList().toString());
        assertTrue(result.rows().stream().anyMatch(r -> r.draft().type() == type));
    }

    @Test
    void keepOptionOrderValuesAndIgnoredTypes() throws Exception {
        for (String value : List.of("Có", "x", "Yes", "TRUE", "1", "Không", "No", "FALSE", "0", "")) {
            var result = parse(List.of(
                List.of("type", "content", "subject", "grade", "difficulty", "options", "correctAnswer", "Giữ thứ tự phương án"),
                List.of("SINGLE_CHOICE", "Question", "MATH", "GRADE_11", "RECOGNITION", "A. one\nB. two", "A", value)));
            assertFalse(result.hasErrors(), value);
            boolean keep = List.of("Có", "x", "Yes", "TRUE", "1").contains(value);
            assertEquals(!keep, ((QuestionBody.SingleChoiceBody) result.rows().getFirst().draft().body()).shuffleOptions(), value);
        }
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "options", "correctAnswer", "keepOptionOrder"),
            List.of("MULTIPLE_CHOICE", "Question", "MATH", "GRADE_11", "RECOGNITION", "A. one\nB. two", "A,B", "yes"),
            List.of("SINGLE_CHOICE", "Question", "MATH", "GRADE_11", "RECOGNITION", "A. one\nB. two", "A", "bad"),
            List.of("SHORT_ANSWER", "Question", "MATH", "GRADE_11", "RECOGNITION", "", "1", "bad")));
        assertEquals(false, ((QuestionBody.MultipleChoiceBody) result.rows().get(0).draft().body()).shuffleOptions());
        assertTrue(result.rows().get(1).issues().stream().anyMatch(i -> i.blocking()
            && i.field() == ImportField.KEEP_OPTION_ORDER && i.column().equals("keepOptionOrder")));
        assertTrue(result.rows().get(2).issues().stream().anyMatch(i -> !i.blocking()
            && i.code() == ImportIssueCode.KEEP_OPTION_ORDER_IGNORED));
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
    void answerFormatIsOptionalAndParsed() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "correctAnswer", "answerFormat"),
            List.of("SHORT_ANSWER", "Giá trị", "MATH", "GRADE_11", "RECOGNITION", "1/2", "FRACTION"),
            List.of("SHORT_ANSWER", "Cũ", "MATH", "GRADE_11", "RECOGNITION", "1", "")));
        assertFalse(result.hasErrors(), () -> result.rows().stream().flatMap(r -> r.issues().stream()).toList().toString());
        assertEquals(AnswerFormat.FRACTION,
            ((QuestionBody.ShortAnswerBody) result.rows().get(0).draft().body()).answerFormat());
        assertNull(((QuestionBody.ShortAnswerBody) result.rows().get(1).draft().body()).answerFormat());
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
    void rubricKeepsSemicolonInDescriptionAndReadsNewlineItems() throws Exception {
        // Spec 4.6: một tiêu chí mỗi dòng; dấu ; thuộc nội dung mô tả (R1-4).
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "rubric"),
            List.of("ESSAY", "Bài 1", "MATH", "GRADE_11", "RECOGNITION", "Ý 1: nêu luận điểm: 0,5"),
            List.of("ESSAY", "Bài 2", "MATH", "GRADE_11", "RECOGNITION", "Ý 1: 0"),
            List.of("ESSAY", "Bài 3", "MATH", "GRADE_11", "RECOGNITION",
                "Mở bài; nêu vấn đề: 0,5\nThân bài (0,5)")));
        var body = (QuestionBody.EssayBody) result.rows().get(0).draft().body();
        assertEquals("Ý 1: nêu luận điểm", body.rubric().getFirst().description());
        assertEquals(new BigDecimal("0.5"), body.rubric().getFirst().points());
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.RUBRIC_INVALID && i.blocking()));
        var semicolonBody = (QuestionBody.EssayBody) result.rows().get(2).draft().body();
        assertFalse(result.rows().get(2).hasErrors(),
            () -> result.rows().get(2).issues().toString());
        assertEquals(List.of("Mở bài; nêu vấn đề", "Thân bài"),
            semicolonBody.rubric().stream().map(QuestionBody.EssayCriterion::description).toList());
        assertEquals(List.of(new BigDecimal("0.5"), new BigDecimal("0.5")),
            semicolonBody.rubric().stream().map(QuestionBody.EssayCriterion::points).toList());
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
            new ImportKnowledgeNode(UUID.randomUUID(), "1D4-3-1", "Phép cộng", List.of("MATH"), true, true));
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
    void setChildrenKeepFileOrderAcrossStandaloneRows() throws Exception {
        // Câu con ở dòng thô 1 và 16 (cách nhau bởi câu đơn): LinkedHashMap giữ đúng thứ tự
        // dòng tệp; HashMap duyệt theo bucket nên 16 đứng trước 1 (R1-3).
        var header = List.of("type", "content", "subject", "grade", "difficulty", "setCode", "correctAnswer");
        var rows = new java.util.ArrayList<>(List.of(header,
            List.of("QUESTION_SET", "Ngữ liệu", "MATH", "GRADE_11", "RECOGNITION", "S", ""),
            List.of("SHORT_ANSWER", "Câu con 01", "", "", "", "S", "1")));
        for (int i = 0; i < 14; i++) {
            rows.add(List.of("SHORT_ANSWER", "Câu đơn " + i, "MATH", "GRADE_11", "RECOGNITION", "", "1"));
        }
        rows.add(List.of("SHORT_ANSWER", "Câu con 16", "", "", "", "S", "1"));
        var result = parse(rows);
        var setDraft = result.rows().getFirst().draft();
        assertEquals(List.of("Câu con 01", "Câu con 16"),
            setDraft.children().stream().map(ImportQuestionDraft::content).toList());
    }

    @Test
    void knowledgeNodeMatchesAnyOfItsSubjects() throws Exception {
        var node = new ImportKnowledgeNode(
            UUID.randomUUID(), "0D1", "Tia số", List.of("TOAN", "ANH"), true, true);
        var catalog = new ImportCatalog(
            List.of(option("ANH"), option("TOAN")), CATALOG.grades(), CATALOG.difficulties(),
            CATALOG.taskTypes(), CATALOG.tags(), List.of(node));
        var sheet = ImportSheets.fromTable(ImportFileType.CSV, null, List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "knowledge", "correctAnswer"),
            List.of("SHORT_ANSWER", "Điền", "ANH", "GRADE_11", "RECOGNITION", "0D1", "x")), ImportLimits.DEFAULT);
        var result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), catalog, List.of(), DICTIONARY);
        assertFalse(result.hasErrors(), () -> result.rows().getFirst().issues().toString());
        assertEquals(List.of(new ImportKnowledgeRef(node.nodeId(), "0D1", true)),
            result.rows().getFirst().draft().knowledge());
    }

    @Test
    void knowledgeResolvesByNameAndAmbiguousNameBlocks() throws Exception {
        var node = new ImportKnowledgeNode(
            UUID.randomUUID(), "0D1", "Phân số thập phân", List.of("TOAN"), true, true);
        var twin = new ImportKnowledgeNode(
            UUID.randomUUID(), "0D2", "Tên trùng", List.of("TOAN"), true, true);
        var twin2 = new ImportKnowledgeNode(
            UUID.randomUUID(), "0D3", "Tên trùng", List.of("TOAN"), true, true);
        var catalog = new ImportCatalog(
            List.of(option("TOAN"), option("ANH")), CATALOG.grades(), CATALOG.difficulties(),
            CATALOG.taskTypes(), CATALOG.tags(), List.of(node, twin, twin2));
        var sheet = ImportSheets.fromTable(ImportFileType.CSV, null, List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "knowledge", "correctAnswer"),
            List.of("SHORT_ANSWER", "Theo mã", "TOAN", "GRADE_11", "RECOGNITION", "0D1", "x"),
            List.of("SHORT_ANSWER", "Theo tên", "TOAN", "GRADE_11", "RECOGNITION", " phân số thập phân ", "x"),
            List.of("SHORT_ANSWER", "Tên trùng", "TOAN", "GRADE_11", "RECOGNITION", "Tên trùng", "x"),
            List.of("SHORT_ANSWER", "Sai môn", "ANH", "GRADE_11", "RECOGNITION", "Phân số thập phân", "x")),
            ImportLimits.DEFAULT);
        var result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), catalog, List.of(), DICTIONARY);
        assertEquals(List.of(new ImportKnowledgeRef(node.nodeId(), "0D1", true)),
            result.rows().get(0).draft().knowledge());
        assertEquals(List.of(new ImportKnowledgeRef(node.nodeId(), "0D1", true)),
            result.rows().get(1).draft().knowledge());
        assertTrue(result.rows().get(2).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.KNOWLEDGE_AMBIGUOUS && i.blocking()),
            () -> result.rows().get(2).issues().toString());
        assertTrue(result.rows().get(3).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.KNOWLEDGE_UNKNOWN && i.blocking()));
    }

    @Test
    void largeCatalogIndexesOncePerParse() throws Exception {
        // Danh mục lớn + ô trống: chỉ mục dựng một lần trong Context, không dựng lại theo dòng
        // (R1-6). 2.000 dòng × 10.000 nút, gồm cả ô trống và ô có mã.
        var nodes = new java.util.ArrayList<ImportKnowledgeNode>(10_000);
        for (int i = 0; i < 10_000; i++) {
            nodes.add(new ImportKnowledgeNode(UUID.randomUUID(), "KN-" + i, "Nút " + i,
                List.of("TOAN"), true, true));
        }
        var catalog = new ImportCatalog(
            List.of(option("TOAN")), CATALOG.grades(), CATALOG.difficulties(),
            CATALOG.taskTypes(), CATALOG.tags(), nodes);
        var table = new java.util.ArrayList<>(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "knowledge", "correctAnswer")));
        for (int i = 0; i < 2_000; i++) {
            table.add(List.of("SHORT_ANSWER", "Câu " + i, "TOAN", "GRADE_11", "RECOGNITION",
                i % 100 == 0 ? "KN-1" : "", "x"));
        }
        var sheet = ImportSheets.fromTable(ImportFileType.CSV, null, table, ImportLimits.DEFAULT);
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long threadId = Thread.currentThread().threadId();
        long allocatedBefore = bean.getThreadAllocatedBytes(threadId);
        long start = System.nanoTime();
        var result = QuestionImportParser.parse(sheet,
            ImportColumnMapper.suggestMapping(sheet.columns(), DICTIONARY), catalog, List.of(), DICTIONARY);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        long allocatedBytes = bean.getThreadAllocatedBytes(threadId) - allocatedBefore;
        System.out.println("knowledge parse elapsedMs=" + elapsedMs + " allocatedBytes=" + allocatedBytes);
        assertFalse(result.hasErrors(), () -> result.rows().stream()
            .filter(ImportRowResult::hasErrors).flatMap(r -> r.issues().stream()).toList().toString());
        assertEquals(nodes.get(1).nodeId(), result.rows().getFirst().draft().knowledge().getFirst().nodeId());
        assertTrue(result.rows().get(1).draft().knowledge().isEmpty());
    }

    @Test
    void suggestMappingLeavesKnowledgeUnmappedAndIgnoresNotes() {
        var columns = List.of(
            new ImportColumn("c0", "Ghi chú nội bộ", ""), new ImportColumn("c1", "Dạng câu", ""),
            new ImportColumn("c2", "Nội dung", ""), new ImportColumn("c3", "Môn", ""),
            new ImportColumn("c4", "Lớp", ""), new ImportColumn("c5", "Mức độ", ""),
            new ImportColumn("c6", "Mã KT chưa ánh xạ", ""));
        var mapping = ImportColumnMapper.suggestMapping(columns, DICTIONARY);
        assertEquals("IGNORE", mapping.byColumnKey().get("c0"));
        assertEquals("type", mapping.byColumnKey().get("c1"));
        assertEquals(ColumnMapping.UNMAPPED, mapping.byColumnKey().get("c6"));
        var check = ImportColumnMapper.checkMapping(columns, mapping);
        assertTrue(check.valid());
        var noSubject = new java.util.HashMap<>(mapping.byColumnKey());
        noSubject.put("c3", "UNMAPPED");
        var checkMissing = ImportColumnMapper.checkMapping(columns, new ColumnMapping(noSubject));
        assertFalse(checkMissing.valid());
        assertTrue(checkMissing.missingRequired().contains(ImportField.SUBJECT));
    }

    @Test
    void vietnameseAliasesResolveTypesAndUnknownBlocks() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "options", "correctAnswer"),
            List.of("Trắc nghiệm 1 đáp án", "A", "MATH", "GRADE_11", "RECOGNITION", "1\n2", "A"),
            List.of("Đúng / Sai", "B", "MATH", "GRADE_11", "RECOGNITION", "a. x", "Đ"),
            List.of("Ghép đôi", "C", "MATH", "GRADE_11", "RECOGNITION", "1. l", "1 = A"),
            List.of("Không có", "D", "MATH", "GRADE_11", "RECOGNITION", "", "")));
        assertEquals(QuestionType.SINGLE_CHOICE, result.rows().get(0).draft().type());
        assertEquals(QuestionType.TRUE_FALSE, result.rows().get(1).draft().type());
        assertEquals(QuestionType.MATCHING, result.rows().get(2).draft().type());
        assertTrue(result.rows().get(3).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.TYPE_UNKNOWN && i.blocking()));
    }

    @Test
    void inFileDuplicatesAndRepeatedSetCodesBlock() throws Exception {
        var result = parse(List.of(
            List.of("sourceCode", "type", "content", "subject", "grade", "difficulty", "setCode", "correctAnswer"),
            List.of("A-1", "SHORT_ANSWER", "Một", "MATH", "GRADE_11", "RECOGNITION", "", "1"),
            List.of("a-1", "SHORT_ANSWER", "Hai", "MATH", "GRADE_11", "RECOGNITION", "", "2"),
            List.of("", "QUESTION_SET", "Bộ 1", "MATH", "GRADE_11", "RECOGNITION", "S", ""),
            List.of("", "QUESTION_SET", "Bộ 2", "MATH", "GRADE_11", "RECOGNITION", "S", "")));
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.DUPLICATE_IN_FILE && i.blocking()
                && Integer.valueOf(2).equals(i.relatedRow())));
        assertTrue(result.rows().get(3).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.SET_CODE_REPEATED && i.blocking()));
    }

    @Test
    void setCodeWithoutPassageAndClozeChildAreBlocked() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "setCode", "correctAnswer"),
            List.of("SHORT_ANSWER", "Mồ côi", "MATH", "GRADE_11", "RECOGNITION", "NOPE", "1"),
            List.of("QUESTION_SET", "Bộ", "MATH", "GRADE_11", "RECOGNITION", "S", ""),
            List.of("CLOZE", "Con [[b1]]", "", "", "", "S", "b1 = x")));
        assertTrue(result.rows().get(0).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.SET_PASSAGE_MISSING && i.blocking()));
        assertTrue(result.rows().get(2).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.CHILD_TYPE_NOT_ALLOWED && i.blocking()));
    }

    @Test
    void nonPositivePointsBlockAndSetOrClozePointsRejected() throws Exception {
        var result = parse(List.of(
            List.of("type", "content", "subject", "grade", "difficulty", "points", "correctAnswer"),
            List.of("SINGLE_CHOICE", "A", "MATH", "GRADE_11", "RECOGNITION", "0", "A"),
            List.of("CLOZE", "B [[b1]]", "MATH", "GRADE_11", "RECOGNITION", "1,5", "b1 = x"),
            List.of("QUESTION_SET", "Bộ", "MATH", "GRADE_11", "RECOGNITION", "2", "")));
        assertTrue(result.rows().get(0).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.POINTS_INVALID && i.blocking()));
        assertTrue(result.rows().get(1).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.POINTS_INVALID && i.blocking()));
        assertTrue(result.rows().get(2).issues().stream()
            .anyMatch(i -> i.code() == ImportIssueCode.POINTS_INVALID && i.blocking()));
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
