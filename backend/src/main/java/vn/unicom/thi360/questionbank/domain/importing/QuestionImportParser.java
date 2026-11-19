package vn.unicom.thi360.questionbank.domain.importing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vn.unicom.thi360.questionbank.domain.QuestionBody;
import vn.unicom.thi360.questionbank.domain.QuestionBodyValidator;
import vn.unicom.thi360.questionbank.domain.QuestionInput;
import vn.unicom.thi360.questionbank.spi.AnswerFormat;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Đọc các dòng của một {@link ImportSheet} đã ánh xạ thành {@link ImportParseResult} (mirror
 * {@code validateImportRows} của web, mở rộng cho 8 dạng và cú pháp ô của spec 4.6). Thuần:
 * danh mục và câu trùng truyền vào sẵn (lớp ứng dụng nạp theo lô), không chạm Spring hay DB.
 *
 * <p>Hai loại vấn đề theo spec 4.5: lỗi chặn (dòng không nhập; một câu con lỗi chặn kéo cả bộ
 * đi xuống và báo lỗi trên dòng bộ) và thiếu dữ liệu (vẫn nhập ở Nháp, kèm cảnh báo). Mã cũ
 * theo D12: {@code FILL_IN} một dấu {@code [[bN]]} thành {@code SHORT_ANSWER} (dấu →
 * {@code ____}), từ hai dấu thành {@code CLOZE}; {@code DRAG_DROP} thành {@code MATCHING}.
 */
public final class QuestionImportParser {

    private QuestionImportParser() {}

    private static final Pattern BLANK_MARKER = Pattern.compile("\\[\\[([^\\[\\]]+)\\]\\]");
    private static final Pattern GRADE_DIGITS = Pattern.compile("^\\d+$");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D+");

    /** Trường dòng câu con có giá trị nhưng không chỗ lưu: bỏ qua kèm cảnh báo. */
    private static final Set<ImportField> CHILD_IGNORED_FIELDS =
        EnumSet.of(ImportField.SUBJECT, ImportField.TASK_TYPE, ImportField.TAGS,
            ImportField.COMMON_MISTAKES);

    /** Trường có thể chứa công thức LaTeX cần kiểm dấu ngoặc (mirror FORMULA_FIELDS). */
    private static final Set<ImportField> FORMULA_FIELDS =
        EnumSet.of(ImportField.CONTENT, ImportField.OPTIONS, ImportField.MATCH_RIGHT,
            ImportField.OPTION_A, ImportField.OPTION_B, ImportField.OPTION_C,
            ImportField.OPTION_D, ImportField.SOLUTION);

    private static final Set<ImportIssueCode> WARNING_CODES = EnumSet.of(
        ImportIssueCode.ANSWER_MISSING,
        ImportIssueCode.OPTIONS_MISSING,
        ImportIssueCode.GRADING_GUIDE_MISSING,
        ImportIssueCode.SET_NO_CHILDREN,
        ImportIssueCode.KNOWLEDGE_MISSING,
        ImportIssueCode.CHILD_FIELD_IGNORED,
        ImportIssueCode.CHILD_SOURCE_CODE_IGNORED,
        ImportIssueCode.KEEP_OPTION_ORDER_IGNORED);

    private static final Pattern ASSIGNMENT_LINE = Pattern.compile("^([A-Za-z]\\w{0,15})\\s*=\\s*(.*)$");

    // ------------------------------------------------------------ vào

    /**
     * Đọc toàn bộ bảng.
     *
     * @param sheet bảng đã đọc
     * @param mapping ánh xạ cột (phải {@link MappingCheck#valid()})
     * @param catalog ảnh chụp danh mục nạp theo lô
     * @param existingQuestions câu đã có trong ngân hàng (để nhận trùng Mã nguồn)
     * @param dictionary từ điển bí danh
     * @throws ImportFileException {@code IMPORT_MAPPING_INVALID} khi mapping không hợp lệ
     */
    public static ImportParseResult parse(
        ImportSheet sheet,
        ColumnMapping mapping,
        ImportCatalog catalog,
        List<ExistingQuestionRef> existingQuestions,
        ImportDictionary dictionary) throws ImportFileException {

        MappingCheck check = ImportColumnMapper.checkMapping(sheet.columns(), mapping);
        if (!check.valid()) {
            throw new ImportFileException(
                ImportIssueCode.IMPORT_MAPPING_INVALID,
                "Ánh xạ cột không hợp lệ: thiếu trường bắt buộc " + check.missingRequired()
                    + " hoặc trường trùng cột " + check.duplicateFields());
        }

        Context ctx = new Context(sheet, catalog, dictionary, check);

        // Lượt 1: giá trị thô theo trường, dạng câu và dòng ngữ liệu của từng Mã chùm.
        List<Raw> raws = ctx.raws();
        List<QuestionType> types = new ArrayList<>(raws.size());
        for (Raw raw : raws) {
            types.add(resolveType(raw.get(ImportField.TYPE), raw.get(ImportField.CONTENT), dictionary));
        }
        Map<String, Integer> passageIndexBySet = new LinkedHashMap<>();
        Map<Integer, Integer> repeatedPassage = new HashMap<>();
        for (int i = 0; i < raws.size(); i++) {
            String setCode = raws.get(i).get(ImportField.SET_CODE);
            if (types.get(i) != QuestionType.QUESTION_SET || setCode.isBlank()) {
                continue;
            }
            String key = ImportCellSyntax.normalizeCode(setCode);
            Integer first = passageIndexBySet.get(key);
            if (first == null) {
                passageIndexBySet.put(key, i);
            } else {
                repeatedPassage.put(i, first);
            }
        }
        // LinkedHashMap: thứ tự duyệt = thứ tự dòng tệp, giữ đúng vị trí câu con trong bộ.
        Map<Integer, Integer> parentIndex = new LinkedHashMap<>();
        for (int i = 0; i < raws.size(); i++) {
            String setCode = raws.get(i).get(ImportField.SET_CODE);
            if (types.get(i) == QuestionType.QUESTION_SET || setCode.isBlank()) {
                continue;
            }
            Integer parent = passageIndexBySet.get(ImportCellSyntax.normalizeCode(setCode));
            if (parent != null) {
                parentIndex.put(i, parent);
            }
        }

        // Lượt 2: đọc từng dòng (không ghi đè phân loại vào câu con; trường thừa → cảnh báo).
        List<RowParse> parsed = new ArrayList<>(raws.size());
        for (int i = 0; i < raws.size(); i++) {
            Integer parent = parentIndex.get(i);
            parsed.add(parseRow(ctx, raws.get(i), sheet.rows().get(i), types.get(i), parent != null));
        }

        // Lượt 3: trùng Mã nguồn trong tệp (dòng sau là lỗi), rồi trùng với ngân hàng.
        Map<String, Integer> firstRowBySource = new LinkedHashMap<>();

        // Lặp dòng bộ cùng Mã chùm: dòng bộ sau là lỗi (SET_CODE_REPEATED).
        for (Map.Entry<Integer, Integer> entry : repeatedPassage.entrySet()) {
            parsed.get(entry.getKey()).add(ImportIssueCode.SET_CODE_REPEATED, ImportField.SET_CODE,
                raws.get(entry.getKey()).get(ImportField.SET_CODE),
                sheet.rows().get(entry.getValue()).rowNumber(), null);
        }
        for (int i = 0; i < parsed.size(); i++) {
            RowParse row = parsed.get(i);
            if (parentIndex.containsKey(i) || row.sourceCode.isBlank()) {
                continue;
            }
            String key = ImportCellSyntax.normalizeCode(row.sourceCode);
            Integer first = firstRowBySource.get(key);
            if (first == null) {
                firstRowBySource.put(key, i);
            } else {
                row.add(ImportIssueCode.DUPLICATE_IN_FILE, ImportField.SOURCE_CODE, row.sourceCode,
                    sheet.rows().get(first).rowNumber(), null);
            }
        }
        Map<String, ExistingQuestionRef> existingBySource = new LinkedHashMap<>();
        List<ExistingQuestionRef> duplicateOf = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            duplicateOf.add(null);
        }
        for (ExistingQuestionRef question : existingQuestions == null ? List.<ExistingQuestionRef>of() : existingQuestions) {
            if (question.sourceCodeNormalized() != null) {
                existingBySource.putIfAbsent(question.sourceCodeNormalized(), question);
            }
        }
        for (int i = 0; i < parsed.size(); i++) {
            RowParse row = parsed.get(i);
            if (parentIndex.containsKey(i) || row.sourceCode.isBlank() || row.hasErrors()) {
                continue;
            }
            ExistingQuestionRef existing = existingBySource.get(ImportCellSyntax.normalizeCode(row.sourceCode));
            if (existing == null) {
                continue;
            }
            if (existing.type() != null && row.type != null && existing.type() != row.type) {
                row.add(ImportIssueCode.IMPORT_SOURCE_TYPE_MISMATCH, ImportField.SOURCE_CODE,
                    row.sourceCode, null, null);
            } else {
                duplicateOf.set(i, existing);
            }
        }

        // Lượt 4: bộ câu hỏi — ngữ liệu lỗi kéo câu con lỗi; một câu con lỗi chặn kéo cả bộ;
        // ngữ liệu trùng thì câu con trùng theo; câu con ghép vào bản nháp của bộ.
        Set<Integer> dropped = new LinkedHashSet<>();
        Map<Integer, List<Integer>> childrenByParent = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : parentIndex.entrySet()) {
            childrenByParent.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        for (Map.Entry<Integer, List<Integer>> entry : childrenByParent.entrySet()) {
            int parentIdx = entry.getKey();
            List<Integer> childIdx = entry.getValue();
            RowParse passage = parsed.get(parentIdx);
            if (childIdx.size() > 20) {
                passage.add(ImportIssueCode.CELL_SYNTAX, ImportField.SET_CODE,
                    passage.rawSetCode(), null, passage.type);
            }
            if (passage.hasErrors()) {
                for (int idx : childIdx) {
                    parsed.get(idx).add(ImportIssueCode.SET_PASSAGE_INVALID, ImportField.SET_CODE,
                        passage.rawSetCode(), sheet.rows().get(parentIdx).rowNumber(), null);
                    dropped.add(idx);
                }
                continue;
            }
            for (int idx : childIdx) {
                RowParse child = parsed.get(idx);
                child.knowledge = resolveKnowledge(ctx, child, child.rawKnowledge,
                    passage.subjectCode);
            }
            List<Integer> blocking = new ArrayList<>();
            for (int idx : childIdx) {
                if (parsed.get(idx).hasErrors()) {
                    blocking.add(idx);
                }
            }
            if (!blocking.isEmpty()) {
                // Một câu con lỗi chặn: cả bộ bị bỏ qua và báo lỗi cho dòng bộ (spec 4.5).
                int firstBad = sheet.rows().get(blocking.get(0)).rowNumber();
                passage.add(ImportIssueCode.SET_CHILD_INVALID, ImportField.SET_CODE,
                    passage.rawSetCode(), firstBad, null);
                dropped.addAll(childIdx);
                continue;
            }
            if (childIdx.isEmpty()) {
                continue;
            }
            if (duplicateOf.get(parentIdx) != null) {
                for (int idx : childIdx) {
                    duplicateOf.set(idx, duplicateOf.get(parentIdx));
                }
            }
        }
        for (int i = 0; i < parsed.size(); i++) {
            RowParse row = parsed.get(i);
            if (row.type == QuestionType.QUESTION_SET && !parentIndex.containsKey(i)
                && !childrenByParent.containsKey(i)) {
                // Dòng bộ không có câu con nào tham chiếu: thiếu dữ liệu, nhập ở Nháp.
                if (!row.hasErrors()) {
                    row.add(ImportIssueCode.SET_NO_CHILDREN, ImportField.SET_CODE,
                        row.rawSetCode(), null, null);
                }
            }
        }

        // Kết quả: ghép câu con vào bản nháp bộ; trạng thái cuối.
        List<ImportRowResult> results = new ArrayList<>(parsed.size());
        for (int i = 0; i < parsed.size(); i++) {
            RowParse row = parsed.get(i);
            List<Integer> children = childrenByParent.getOrDefault(i, List.of());
            List<ImportQuestionDraft> childDrafts = new ArrayList<>(children.size());
            for (int idx : children) {
                childDrafts.add(parsed.get(idx).draftWithChildren(List.of()));
            }
            ImportQuestionDraft draft = row.draftWithChildren(childDrafts);
            boolean isError = row.hasErrors() || dropped.contains(i);
            ImportRowStatus status = isError
                ? ImportRowStatus.ERROR
                : duplicateOf.get(i) != null ? ImportRowStatus.DUPLICATE : ImportRowStatus.VALID;
            results.add(new ImportRowResult(
                sheet.rows().get(i).rowNumber(),
                row.sourceCode.isBlank() ? null : row.sourceCode,
                status,
                row.issues,
                draft,
                duplicateOf.get(i),
                row.type == QuestionType.QUESTION_SET,
                parentIndex.containsKey(i) ? sheet.rows().get(parentIndex.get(i)).rowNumber() : null,
                dropped.contains(i)));
        }
        return new ImportParseResult(results, summarize(results));
    }

    // ------------------------------------------------------------ dạng câu

    /**
     * Dạng của một dòng: mã 8 dạng (B6), tên tiếng Việt qua từ điển, mã cũ D12
     * ({@code FILL_IN} theo số dấu {@code [[bN]]} trong nội dung, {@code DRAG_DROP}).
     * {@code null} khi ô trống hoặc không nhận ra.
     */
    static QuestionType resolveType(String rawType, String rawContent, ImportDictionary dictionary) {
        String value = rawType == null ? "" : rawType.trim();
        if (value.isEmpty()) {
            return null;
        }
        String upper = ImportCellSyntax.normalizeCode(value);
        if ("FILL_IN".equals(upper)) {
            Matcher matcher = BLANK_MARKER.matcher(rawContent == null ? "" : rawContent);
            int markers = 0;
            while (matcher.find()) {
                markers++;
            }
            return markers <= 1 ? QuestionType.SHORT_ANSWER : QuestionType.CLOZE;
        }
        if ("DRAG_DROP".equals(upper)) {
            return QuestionType.MATCHING;
        }
        for (QuestionType type : QuestionType.values()) {
            if (ImportCellSyntax.sameCode(type.name(), value)
                || ImportCellSyntax.sameText(type.defaultNameVi(), value)) {
                return type;
            }
        }
        for (QuestionType type : QuestionType.values()) {
            for (String alias : dictionary.aliasesOf(type)) {
                if (ImportCellSyntax.sameText(alias, value)) {
                    return type;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ một dòng

    private static RowParse parseRow(
        Context ctx, Raw raw, ImportSheetRow sheetRow, QuestionType type, boolean isChild) {

        RowParse row = new RowParse(sheetRow.rowNumber(), ctx, type, raw.get(ImportField.SET_CODE));

        String rawType = raw.get(ImportField.TYPE);
        if (rawType.isBlank()) {
            row.add(ImportIssueCode.REQUIRED, ImportField.TYPE, null, null, null);
        } else if (type == null) {
            row.add(ImportIssueCode.TYPE_UNKNOWN, ImportField.TYPE, rawType, null, null);
        }
        if (isChild && type == QuestionType.QUESTION_SET || isChild && type == QuestionType.CLOZE) {
            row.add(ImportIssueCode.CHILD_TYPE_NOT_ALLOWED, ImportField.TYPE, rawType, null, type);
        }
        if (raw.get(ImportField.CONTENT).isBlank()) {
            row.add(ImportIssueCode.REQUIRED, ImportField.CONTENT, null, null, type);
        }
        if (type != null && type != QuestionType.SINGLE_CHOICE && type != QuestionType.MULTIPLE_CHOICE
                && !raw.get(ImportField.KEEP_OPTION_ORDER).isBlank()) {
            row.add(ImportIssueCode.KEEP_OPTION_ORDER_IGNORED, ImportField.KEEP_OPTION_ORDER,
                raw.get(ImportField.KEEP_OPTION_ORDER), null, type);
        }

        // Mã nguồn của câu con bị bỏ qua kèm cảnh báo (quyết định 8.8).
        if (isChild && !raw.get(ImportField.SOURCE_CODE).isBlank()) {
            row.add(ImportIssueCode.CHILD_SOURCE_CODE_IGNORED, ImportField.SOURCE_CODE,
                raw.get(ImportField.SOURCE_CODE), null, type);
        }
        if (!isChild) {
            row.sourceCode = raw.get(ImportField.SOURCE_CODE).trim();
        }

        // Phân loại: câu đơn/dòng bộ bắt buộc môn/khối/mức độ; câu con chỉ ghi đè.
        boolean classificationRequired = !isChild;
        if (isChild) {
            for (ImportField field : CHILD_IGNORED_FIELDS) {
                if (!raw.get(field).isBlank()) {
                    row.add(ImportIssueCode.CHILD_FIELD_IGNORED, field, raw.get(field), null, type);
                }
            }
        }
        row.subjectCode = isChild
            ? null
            : resolveOption(ctx, row, ImportField.SUBJECT, raw.get(ImportField.SUBJECT),
                ctx.catalog.subjects(), ImportIssueCode.SUBJECT_UNKNOWN, classificationRequired);
        row.gradeCode = resolveGrade(ctx, row, raw.get(ImportField.GRADE), ctx.catalog.grades(),
            classificationRequired);
        row.difficultyCode = resolveOption(ctx, row, ImportField.DIFFICULTY,
            raw.get(ImportField.DIFFICULTY), ctx.catalog.difficulties(),
            ImportIssueCode.DIFFICULTY_UNKNOWN, classificationRequired);
        row.taskTypeCode = isChild
            ? null
            : resolveOption(ctx, row, ImportField.TASK_TYPE, raw.get(ImportField.TASK_TYPE),
                ctx.catalog.taskTypes(), ImportIssueCode.TASK_TYPE_UNKNOWN, false);

        // Kiến thức: tra mã trong cây của môn; câu đơn thiếu là cảnh báo, mã lạ/không lá là lỗi.
        // Câu con để trống thì kế thừa của bộ; có ghi thì so môn của dòng bộ (lượt 4).
        if (isChild) {
            row.rawKnowledge = raw.get(ImportField.KNOWLEDGE);
        } else {
            row.knowledge = resolveKnowledge(ctx, row, raw.get(ImportField.KNOWLEDGE),
                row.subjectCode);
            if (raw.get(ImportField.KNOWLEDGE).isBlank()) {
                row.add(ImportIssueCode.KNOWLEDGE_MISSING, ImportField.KNOWLEDGE, null, null, type);
            }
        }
        if (!isChild) {
            for (String tag : ImportCellSyntax.splitList(raw.get(ImportField.TAGS))) {
                ImportCatalogOption found = findOption(ctx.catalog.tags(), tag);
                if (found == null) {
                    row.add(ImportIssueCode.TAG_UNKNOWN, ImportField.TAGS, tag, null, type);
                } else if (!row.tagCodes.contains(found.code())) {
                    row.tagCodes.add(found.code());
                }
            }
        }

        // Phần thân theo dạng.
        boolean legacyFillInSingle = type == QuestionType.SHORT_ANSWER
            && "FILL_IN".equals(ImportCellSyntax.normalizeCode(rawType));
        String processedContent = ImportCellSyntax.processCell(raw.get(ImportField.CONTENT));
        if (legacyFillInSingle) {
            // D12: một ô nhập thành Trả lời ngắn — dấu [[b1]] trở thành ____.
            processedContent = BLANK_MARKER.matcher(processedContent).replaceAll("____");
        }
        row.content = processedContent;
        row.solution = raw.get(ImportField.SOLUTION).isBlank()
            ? null : ImportCellSyntax.processCell(raw.get(ImportField.SOLUTION));
        row.commonMistakes = isChild
            ? List.of()
            : ImportCellSyntax.lines(raw.get(ImportField.COMMON_MISTAKES)).stream()
                .map(ImportCellSyntax::processCell).toList();

        // Điểm: cột số nhận dấu phẩy thập phân; bộ/điền khuyết bắt buộc trống.
        String rawPoints = raw.get(ImportField.POINTS);
        if (!rawPoints.isBlank()) {
            if (type == QuestionType.QUESTION_SET || type == QuestionType.CLOZE) {
                row.add(ImportIssueCode.POINTS_INVALID, ImportField.POINTS, rawPoints, null, type);
            } else {
                BigDecimal points = ImportCellSyntax.parseNumber(rawPoints);
                if (points == null || points.signum() <= 0) {
                    row.add(ImportIssueCode.POINTS_INVALID, ImportField.POINTS, rawPoints, null, type);
                } else {
                    row.points = points;
                }
            }
        }
        if (legacyFillInSingle && !raw.get(ImportField.SCORING).isBlank()) {
            // D12: "b1 = x" trong cột Cách chấm trở thành điểm của câu Trả lời ngắn.
            String[] invalidScoring = {null};
            for (var assignment
                : ImportCellSyntax.parseAssignments(raw.get(ImportField.SCORING), invalidScoring)) {
                BigDecimal points = ImportCellSyntax.parseNumber(assignment.value());
                if (points == null || points.signum() <= 0) {
                    row.add(ImportIssueCode.POINTS_INVALID, ImportField.SCORING,
                        assignment.key() + " = " + assignment.value(), null, type);
                } else {
                    row.points = points;
                }
            }
            if (invalidScoring[0] != null) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.SCORING, invalidScoring[0], null, type);
            }
        }
        row.body = parseBody(ctx, row, raw, type);
        if (type != null && row.body != null) {
            var input = new QuestionInput(type, row.body, row.points, row.content,
                row.solution, row.commonMistakes, List.of());
            for (var error : QuestionBodyValidator.validateSave(input, null)) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.CONTENT,
                    error.path() + ": " + error.code(), null, type);
            }
        }


        // Dấu ngoặc công thức trên các ô chữ.
        for (ImportField field : FORMULA_FIELDS) {
            String value = raw.get(field);
            if (!value.isBlank() && !ImportCellSyntax.bracesBalanced(value)) {
                row.add(ImportIssueCode.FORMULA_INVALID, field, null, null, type);
            }
        }

        // Mã chùm: dòng bộ bắt buộc; câu con trỏ tới bộ không có là lỗi; lặp dòng bộ là lỗi.
        String setCode = raw.get(ImportField.SET_CODE);
        if (type == QuestionType.QUESTION_SET && setCode.isBlank()) {
            row.add(ImportIssueCode.REQUIRED, ImportField.SET_CODE, null, null, type);
        }
        if (type != QuestionType.QUESTION_SET && !setCode.isBlank() && !isChild) {
            row.add(ImportIssueCode.SET_PASSAGE_MISSING, ImportField.SET_CODE, setCode, null, type);
        }
        row.setCode = setCode.isBlank() ? null : setCode;
        return row;
    }

    private static ImportCatalogOption findOption(List<ImportCatalogOption> options, String value) {
        for (ImportCatalogOption option : options) {
            if (!option.active()) {
                continue;
            }
            if (ImportCellSyntax.sameCode(option.code(), value) || ImportCellSyntax.sameText(option.name(), value)) {
                return option;
            }
        }
        return null;
    }

    private static String resolveOption(
        Context ctx, RowParse row, ImportField field, String raw,
        List<ImportCatalogOption> options, ImportIssueCode unknown, boolean required) {
        if (raw.isBlank()) {
            if (required) {
                row.add(ImportIssueCode.REQUIRED, field, null, null, row.type);
            }
            return null;
        }
        ImportCatalogOption found = findOption(options, raw);
        if (found == null) {
            row.add(unknown, field, raw, null, row.type);
            return null;
        }
        return found.code();
    }

    /** Khối: khớp mã/tên, hoặc chỉ số lớp ("11" khớp "Lớp 11"; mirror {@code findGrade}). */
    private static String resolveGrade(
        Context ctx, RowParse row, String raw, List<ImportCatalogOption> grades, boolean required) {
        if (raw.isBlank()) {
            if (required) {
                row.add(ImportIssueCode.REQUIRED, ImportField.GRADE, null, null, row.type);
            }
            return null;
        }
        ImportCatalogOption found = findOption(grades, raw);
        if (found == null && GRADE_DIGITS.matcher(raw.trim()).matches()) {
            for (ImportCatalogOption option : grades) {
                if (option.active()
                    && NON_DIGITS.matcher(option.name()).replaceAll("").equals(raw.trim())) {
                    found = option;
                    break;
                }
            }
        }
        if (found == null) {
            row.add(ImportIssueCode.GRADE_UNKNOWN, ImportField.GRADE, raw, null, row.type);
            return null;
        }
        return found.code();
    }

    /**
     * Ô knowledge: mã MAPID hoặc tên danh mục tiếng Việt, một mục mỗi dòng (spec 4.6). Ô trống
     * trả ngay (R1-6); chỉ mục danh mục đã dựng sẵn trong {@link Context}. Tên khớp nhiều nút
     * là lỗi chặn {@code KNOWLEDGE_AMBIGUOUS} (R1-5).
     */
    private static List<ImportKnowledgeRef> resolveKnowledge(
        Context ctx, RowParse row, String raw, String subjectCode) {
        List<ImportKnowledgeRef> result = new ArrayList<>();
        if (raw.isBlank()) {
            return result;
        }
        for (String code : ImportCellSyntax.splitList(raw)) {
            ImportKnowledgeNode found = ctx.knowledgeByCode.get(ImportCellSyntax.normalizeCode(code));
            if (found == null) {
                List<ImportKnowledgeNode> byName =
                    ctx.knowledgeByName.getOrDefault(ImportCellSyntax.normalizeText(code), List.of());
                if (byName.size() > 1) {
                    row.add(ImportIssueCode.KNOWLEDGE_AMBIGUOUS, ImportField.KNOWLEDGE, code,
                        null, row.type);
                    continue;
                }
                found = byName.isEmpty() ? null : byName.getFirst();
            }
            if (found == null
                || (subjectCode != null && found.subjectCodes().stream()
                    .noneMatch(s -> ImportCellSyntax.sameCode(s, subjectCode)))) {
                row.add(ImportIssueCode.KNOWLEDGE_UNKNOWN, ImportField.KNOWLEDGE, code, null, row.type);
                row.lastSubjectCode = subjectCode;
            } else if (!found.leaf()) {
                row.add(ImportIssueCode.KNOWLEDGE_NOT_LEAF, ImportField.KNOWLEDGE, code, null, row.type);
            } else {
                ImportKnowledgeNode resolved = found;
                if (result.stream().noneMatch(ref -> ref.nodeId().equals(resolved.nodeId()))) {
                    result.add(new ImportKnowledgeRef(resolved.nodeId(), resolved.code(), result.isEmpty()));
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------ phần thân

    private static QuestionBody parseBody(Context ctx, RowParse row, Raw raw, QuestionType type) {
        if (type == null || type == QuestionType.QUESTION_SET) {
            return type == null ? null
                : new QuestionBody.QuestionSetBody(
                    QuestionType.QUESTION_SET, row.content, null, List.of());
        }
        return switch (type) {
            case SINGLE_CHOICE -> parseChoice(row, raw, false);
            case MULTIPLE_CHOICE -> parseChoice(row, raw, true);
            case SHORT_ANSWER -> parseShortAnswer(row, raw);
            case CLOZE -> parseCloze(row, raw);
            case TRUE_FALSE -> parseTrueFalse(row, raw);
            case MATCHING -> parseMatching(row, raw);
            case ESSAY -> parseEssay(row, raw);
            case QUESTION_SET -> throw new IllegalStateException();
        };
    }

    /** Phương án của câu trắc nghiệm/Đúng-Sai: ô {@code options} nhiều dòng, hoặc bốn cột A–D của tệp cũ. */
    private static List<ImportCellSyntax.LabeledItem> optionItems(RowParse row, Raw raw, boolean[] mixed) {
        String optionsCell = raw.get(ImportField.OPTIONS);
        if (!optionsCell.isBlank()) {
            return ImportCellSyntax.parseLabeledLines(optionsCell, true, mixed);
        }
        List<ImportCellSyntax.LabeledItem> items = new ArrayList<>();
        String[] letters = {"A", "B", "C", "D"};
        ImportField[] fields = {
            ImportField.OPTION_A, ImportField.OPTION_B, ImportField.OPTION_C, ImportField.OPTION_D};
        for (int i = 0; i < fields.length; i++) {
            String content = raw.get(fields[i]);
            if (!content.isBlank()) {
                items.add(new ImportCellSyntax.LabeledItem(letters[i], content.trim()));
            }
        }
        return items;
    }

    private static QuestionBody parseChoice(RowParse row, Raw raw, boolean multiple) {
        String keepValue = raw.get(ImportField.KEEP_OPTION_ORDER).trim().toLowerCase(Locale.ROOT);
        boolean keepOrder = switch (keepValue) {
            case "có", "x", "yes", "true", "1" -> true;
            case "", "không", "no", "false", "0" -> false;
            default -> {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.KEEP_OPTION_ORDER, keepValue, null, row.type);
                yield false;
            }
        };
        boolean[] mixed = {false};
        List<ImportCellSyntax.LabeledItem> items = optionItems(row, raw, mixed);
        if (mixed[0]) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS, null, null, row.type);
        }
        if (items.isEmpty()) {
            row.add(ImportIssueCode.OPTIONS_MISSING, ImportField.OPTIONS, null, null, row.type);
        }
        if (items.size() > 10) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS, raw.get(ImportField.OPTIONS), null, row.type);
        }
        Set<String> known = new LinkedHashSet<>();
        for (var item : items) {
            if (!ImportCellSyntax.isOptionLetter(item.label())) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS, item.label(), null, row.type);
                continue;
            }
            if (!known.add(item.label())) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS, item.label(), null, row.type);
            }
        }

        List<String> letters = new ArrayList<>();
        String answerCell = raw.get(ImportField.CORRECT_ANSWER);
        if (answerCell.isBlank()) {
            row.add(ImportIssueCode.ANSWER_MISSING, ImportField.CORRECT_ANSWER, null, null, row.type);
        } else {
            boolean malformed = false;
            for (String token : answerCell.split("[\\s,;]+")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }
                String upper = t.toUpperCase(Locale.ROOT);
                if (!ImportCellSyntax.isOptionLetter(upper) || letters.contains(upper)) {
                    malformed = true;
                } else {
                    letters.add(upper);
                }
            }
            if (malformed || letters.isEmpty()
                || !known.containsAll(letters)
                || (!multiple && letters.size() != 1)) {
                row.add(ImportIssueCode.ANSWER_INVALID, ImportField.CORRECT_ANSWER,
                    answerCell, null, row.type);
            }
        }

        List<QuestionBody.ChoiceOption> options = new ArrayList<>();
        for (var item : items) {
            options.add(new QuestionBody.ChoiceOption(
                "opt-" + item.label(),
                ImportCellSyntax.processCell(item.content()),
                letters.contains(item.label())));
        }
        if (multiple) {
            QuestionBody.MultipleChoiceRule rule = QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING;
            String scoring = raw.get(ImportField.SCORING);
            if (!scoring.isBlank()) {
                if (ImportCellSyntax.sameCode(scoring, "ALL_OR_NOTHING")) {
                    rule = QuestionBody.MultipleChoiceRule.ALL_OR_NOTHING;
                } else if (ImportCellSyntax.sameCode(scoring, "PARTIAL")) {
                    rule = QuestionBody.MultipleChoiceRule.PARTIAL;
                } else {
                    row.add(ImportIssueCode.CELL_SYNTAX, ImportField.SCORING, scoring, null, row.type);
                }
            }
            return new QuestionBody.MultipleChoiceBody(
                QuestionType.MULTIPLE_CHOICE, options, rule, !keepOrder);
        }
        return new QuestionBody.SingleChoiceBody(QuestionType.SINGLE_CHOICE, options, !keepOrder);
    }

    private static QuestionBody parseShortAnswer(RowParse row, Raw raw) {
        List<String> answers = new ArrayList<>();
        String answerCell = raw.get(ImportField.CORRECT_ANSWER);
        // D12: dòng mã cũ FILL_IN đọc cú pháp "b1 = đáp án | đáp án" — bỏ phần gán bN.
        boolean legacyFillIn = "FILL_IN".equals(
            ImportCellSyntax.normalizeCode(raw.get(ImportField.TYPE)));
        if (answerCell.isBlank()) {
            row.add(ImportIssueCode.ANSWER_MISSING, ImportField.CORRECT_ANSWER, null, null, row.type);
        } else {
            // Ordinary short answers are one per line; only legacy FILL_IN uses '|'.
            for (String line : answerCell.split(legacyFillIn ? "\r\n|\r|\n|\\|" : "\r\n|\r|\n")) {
                String trimmed = line.trim();
                if (legacyFillIn) {
                    Matcher assignment = ASSIGNMENT_LINE.matcher(trimmed);
                    if (assignment.matches()) {
                        trimmed = assignment.group(2).trim();
                    }
                }
                if (!trimmed.isEmpty()) {
                    answers.add(trimmed);
                }
            }
        }
        if (answers.size() > 20) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.CORRECT_ANSWER, answerCell, null, row.type);
        }
        String inputHint = raw.get(ImportField.INPUT_HINT).isBlank()
            ? null : raw.get(ImportField.INPUT_HINT).trim();
        AnswerFormat answerFormat = null;
        String answerFormatCell = raw.get(ImportField.ANSWER_FORMAT);
        if (!answerFormatCell.isBlank()) {
            try {
                answerFormat = AnswerFormat.valueOf(ImportCellSyntax.normalizeCode(answerFormatCell));
            } catch (IllegalArgumentException e) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.ANSWER_FORMAT,
                    answerFormatCell, null, row.type);
            }
        }
        return new QuestionBody.ShortAnswerBody(QuestionType.SHORT_ANSWER, answers, inputHint, answerFormat);
    }

    private static QuestionBody parseCloze(RowParse row, Raw raw) {
        List<String> markerIds = new ArrayList<>();
        Matcher matcher = BLANK_MARKER.matcher(raw.get(ImportField.CONTENT));
        while (matcher.find()) {
            String id = matcher.group(1).trim();
            if (!markerIds.contains(id)) {
                markerIds.add(id);
            }
        }
        if (markerIds.isEmpty()) {
            row.add(ImportIssueCode.CLOZE_MARKER_MISMATCH, ImportField.CONTENT, null, null, row.type);
        }

        Map<String, List<String>> answersByBlank = new LinkedHashMap<>();
        String[] invalid = {null};
        for (var assignment : ImportCellSyntax.parseAssignments(raw.get(ImportField.CORRECT_ANSWER), invalid)) {
            List<String> answers = new ArrayList<>();
            for (String part : assignment.value().split("\\|")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    answers.add(trimmed);
                }
            }
            String key = assignment.key();
            if (!markerIds.contains(key)) {
                row.add(ImportIssueCode.BLANK_UNKNOWN, ImportField.CORRECT_ANSWER,
                    assignment.key() + " = " + assignment.value(), null, row.type);
            } else if (answersByBlank.putIfAbsent(key, answers) != null) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.CORRECT_ANSWER,
                    assignment.key(), null, row.type);
            }
        }
        if (invalid[0] != null) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.CORRECT_ANSWER, invalid[0], null, row.type);
        }

        Map<String, BigDecimal> pointsByBlank = new LinkedHashMap<>();
        String[] invalidScoring = {null};
        for (var assignment : ImportCellSyntax.parseAssignments(raw.get(ImportField.SCORING), invalidScoring)) {
            BigDecimal points = ImportCellSyntax.parseNumber(assignment.value());
            String key = assignment.key();
            if (points == null || points.signum() <= 0) {
                row.add(ImportIssueCode.POINTS_INVALID, ImportField.SCORING,
                    assignment.key() + " = " + assignment.value(), null, row.type);
            } else if (!markerIds.contains(key)) {
                row.add(ImportIssueCode.BLANK_UNKNOWN, ImportField.SCORING, key, null, row.type);
            } else {
                pointsByBlank.putIfAbsent(key, points);
            }
        }
        if (invalidScoring[0] != null) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.SCORING, invalidScoring[0], null, row.type);
        }

        List<QuestionBody.ClozeBlank> blanks = new ArrayList<>();
        for (String id : markerIds) {
            List<String> answers = answersByBlank.getOrDefault(id, List.of());
            if (answers.isEmpty()) {
                row.add(ImportIssueCode.ANSWER_MISSING, ImportField.CORRECT_ANSWER, id, null, row.type);
            }
            blanks.add(new QuestionBody.ClozeBlank(
                id, answers, pointsByBlank.getOrDefault(id, BigDecimal.ONE)));
        }
        if (markerIds.size() > 30) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.CONTENT, null, null, row.type);
        }
        return new QuestionBody.ClozeBody(QuestionType.CLOZE, row.content, blanks);
    }

    private static QuestionBody parseTrueFalse(RowParse row, Raw raw) {
        boolean[] mixed = {false};
        List<ImportCellSyntax.LabeledItem> items = optionItems(row, raw, mixed);
        if (mixed[0]) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS, null, null, row.type);
        }
        if (items.isEmpty()) {
            row.add(ImportIssueCode.OPTIONS_MISSING, ImportField.OPTIONS, null, null, row.type);
        }
        if (items.size() > 8) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS, null, null, row.type);
        }

        String answerCell = raw.get(ImportField.CORRECT_ANSWER);
        List<Boolean> answers = new ArrayList<>();
        boolean malformed = false;
        if (answerCell.isBlank()) {
            row.add(ImportIssueCode.ANSWER_MISSING, ImportField.CORRECT_ANSWER, null, null, row.type);
            answers = List.of();
        } else {
            List<String> tokens = new ArrayList<>();
            for (String token : answerCell.split("[\\s,;]+")) {
                if (!token.isBlank()) {
                    tokens.add(token.trim());
                }
            }
            for (String token : tokens) {
                Boolean value = null;
                for (String candidate : row.ctx.dictionary.trueTokens()) {
                    if (ImportCellSyntax.sameText(candidate, token)) {
                        value = Boolean.TRUE;
                        break;
                    }
                }
                if (value == null) {
                    for (String candidate : row.ctx.dictionary.falseTokens()) {
                        if (ImportCellSyntax.sameText(candidate, token)) {
                            value = Boolean.FALSE;
                            break;
                        }
                    }
                }
                answers.add(value);
            }
            if (answers.size() != items.size() || answers.stream().anyMatch(a -> a == null)) {
                malformed = true;
            }
            if (malformed) {
                row.add(ImportIssueCode.ANSWER_INVALID, ImportField.CORRECT_ANSWER,
                    answerCell, null, row.type);
            }
        }

        // Cách chấm: PER_STATEMENT (điểm từng mệnh đề, chia đều khi không ghi) hoặc
        // COUNT_TABLE: bảng điểm theo số mệnh đề đúng.
        QuestionBody.TrueFalseScoringMethod method = QuestionBody.TrueFalseScoringMethod.PER_STATEMENT;
        List<BigDecimal> statementPoints = new ArrayList<>();
        List<BigDecimal> scoreTable = List.of();
        String scoring = raw.get(ImportField.SCORING);
        if (!scoring.isBlank()) {
            String trimmed = scoring.trim();
            int colon = trimmed.indexOf(':');
            String head = colon < 0 ? trimmed : trimmed.substring(0, colon).trim();
            String tail = colon < 0 ? "" : trimmed.substring(colon + 1).trim();
            if (ImportCellSyntax.sameCode(head, "PER_STATEMENT")) {
                List<BigDecimal> values = parseScoringValues(tail);
                if (values == null || values.size() != items.size()) {
                    row.add(ImportIssueCode.CELL_SYNTAX, ImportField.SCORING, scoring, null, row.type);
                } else {
                    for (BigDecimal value : values) {
                        if (value.signum() <= 0) {
                            row.add(ImportIssueCode.POINTS_INVALID, ImportField.SCORING,
                                scoring, null, row.type);
                            break;
                        }
                    }
                    statementPoints.addAll(values);
                }
            } else if (ImportCellSyntax.sameCode(head, "COUNT_TABLE")) {
                method = QuestionBody.TrueFalseScoringMethod.COUNT_TABLE;
                List<BigDecimal> values = parseScoringValues(tail);
                if (values == null || (values.size() != items.size())) {
                    row.add(ImportIssueCode.CELL_SYNTAX, ImportField.SCORING, scoring, null, row.type);
                } else {
                    for (BigDecimal value : values) {
                        if (value.signum() < 0) {
                            row.add(ImportIssueCode.POINTS_INVALID, ImportField.SCORING,
                                scoring, null, row.type);
                            break;
                        }
                    }
                    scoreTable = values;
                }
            } else {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.SCORING, scoring, null, row.type);
            }
        }
        if (method == QuestionBody.TrueFalseScoringMethod.PER_STATEMENT && statementPoints.isEmpty()
            && !items.isEmpty()) {
            statementPoints = ImportCellSyntax.splitEvenly(
                row.points != null ? row.points : BigDecimal.ONE, items.size());
        }

        List<QuestionBody.TrueFalseStatement> statements = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            statements.add(new QuestionBody.TrueFalseStatement(
                "st-" + i,
                ImportCellSyntax.processCell(item.content()),
                i < answers.size() ? answers.get(i) : null,
                method == QuestionBody.TrueFalseScoringMethod.PER_STATEMENT && i < statementPoints.size()
                    ? statementPoints.get(i) : null));
        }
        return new QuestionBody.TrueFalseBody(
            QuestionType.TRUE_FALSE, statements, method, scoreTable);
    }

    /** "0,25 / 0,5 / 1" → danh sách số; {@code null} khi có phần tử không phải số. */
    private static List<BigDecimal> parseScoringValues(String value) {
        List<BigDecimal> values = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return null;
        }
        for (String part : value.split("/")) {
            BigDecimal number = ImportCellSyntax.parseNumber(part);
            if (number == null) {
                return null;
            }
            values.add(number);
        }
        return values;
    }

    private static QuestionBody parseMatching(RowParse row, Raw raw) {
        boolean[] mixedLeft = {false};
        List<ImportCellSyntax.LabeledItem> left =
            ImportCellSyntax.parseLabeledLines(raw.get(ImportField.OPTIONS), false, mixedLeft);
        if (mixedLeft[0]) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS, null, null, row.type);
        }
        boolean[] mixedRight = {false};
        List<ImportCellSyntax.LabeledItem> right =
            ImportCellSyntax.parseLabeledLines(raw.get(ImportField.MATCH_RIGHT), true, mixedRight);
        if (mixedRight[0]) {
            row.add(ImportIssueCode.CELL_SYNTAX, ImportField.MATCH_RIGHT, null, null, row.type);
        }
        if (left.isEmpty()) {
            row.add(ImportIssueCode.OPTIONS_MISSING, ImportField.OPTIONS, null, null, row.type);
        }
        if (right.isEmpty()) {
            row.add(ImportIssueCode.OPTIONS_MISSING, ImportField.MATCH_RIGHT, null, null, row.type);
        }
        Map<Integer, Integer> leftPositions = new LinkedHashMap<>();
        for (int i = 0; i < left.size(); i++) {
            int label = Integer.parseInt(left.get(i).label());
            if (label < 1 || leftPositions.putIfAbsent(label, i) != null) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.OPTIONS,
                    left.get(i).label(), null, row.type);
            }
        }

        Set<String> rightLabels = new LinkedHashSet<>();
        for (var item : right) {
            if (!ImportCellSyntax.isOptionLetter(item.label()) || !rightLabels.add(item.label())) {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.MATCH_RIGHT, item.label(), null, row.type);
            }
        }

        // "1 = A": mục trái thứ N khớp lựa chọn chữ cái.
        Map<Integer, String> pairByLeft = new LinkedHashMap<>();
        String answerCell = raw.get(ImportField.CORRECT_ANSWER);
        if (answerCell.isBlank()) {
            row.add(ImportIssueCode.ANSWER_MISSING, ImportField.CORRECT_ANSWER, null, null, row.type);
        } else {
            for (String line : ImportCellSyntax.lines(answerCell)) {
                var matcher = Pattern.compile("^(\\d{1,3})\\s*=\\s*([A-Za-z])\\s*$").matcher(line);
                if (!matcher.matches()) {
                    row.add(ImportIssueCode.CELL_SYNTAX, ImportField.CORRECT_ANSWER, line, null, row.type);
                    continue;
                }
                int leftIndex = Integer.parseInt(matcher.group(1));
                String rightLetter = matcher.group(2).toUpperCase(Locale.ROOT);
                if (!leftPositions.containsKey(leftIndex)) {
                    row.add(ImportIssueCode.ANSWER_INVALID, ImportField.CORRECT_ANSWER,
                        line, null, row.type);
                } else if (!rightLabels.contains(rightLetter)) {
                    row.add(ImportIssueCode.ANSWER_INVALID, ImportField.CORRECT_ANSWER,
                        line, null, row.type);
                } else if (pairByLeft.putIfAbsent(leftIndex, rightLetter) != null) {
                    row.add(ImportIssueCode.ANSWER_INVALID, ImportField.CORRECT_ANSWER,
                        line, null, row.type);
                }
            }
        }
        for (int label : leftPositions.keySet()) {
            if (!pairByLeft.containsKey(label) && !answerCell.isBlank()) {
                row.add(ImportIssueCode.ANSWER_MISSING, ImportField.CORRECT_ANSWER,
                    Integer.toString(label), null, row.type);
            }
        }

        Boolean reuseRight = Boolean.FALSE;
        String scoring = raw.get(ImportField.SCORING);
        if (!scoring.isBlank()) {
            if (ImportCellSyntax.sameCode(scoring, "REUSE")) {
                reuseRight = Boolean.TRUE;
            } else if (ImportCellSyntax.sameCode(scoring, "ONCE")) {
                reuseRight = Boolean.FALSE;
            } else {
                row.add(ImportIssueCode.CELL_SYNTAX, ImportField.SCORING, scoring, null, row.type);
            }
        }

        List<QuestionBody.MatchingSide> leftSides = new ArrayList<>();
        for (var item : left) {
            leftSides.add(new QuestionBody.MatchingSide(
                "l" + item.label(), ImportCellSyntax.processCell(item.content())));
        }
        List<QuestionBody.MatchingSide> rightSides = new ArrayList<>();
        for (var item : right) {
            rightSides.add(new QuestionBody.MatchingSide(
                "r" + item.label(), ImportCellSyntax.processCell(item.content())));
        }
        List<BigDecimal> pairPoints =
            ImportCellSyntax.splitEvenly(row.points != null ? row.points : BigDecimal.ONE, left.size());
        List<QuestionBody.MatchingPair> pairs = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : pairByLeft.entrySet()) {
            pairs.add(new QuestionBody.MatchingPair(
                "l" + entry.getKey(), "r" + entry.getValue(),
                pairPoints.isEmpty() ? null : pairPoints.get(leftPositions.get(entry.getKey()))));
        }
        return new QuestionBody.MatchingBody(
            QuestionType.MATCHING, leftSides, rightSides, pairs, reuseRight);
    }

    private static QuestionBody parseEssay(RowParse row, Raw raw) {
        String guideCell = raw.get(ImportField.GRADING_GUIDE);
        if (guideCell.isBlank()) {
            row.add(ImportIssueCode.GRADING_GUIDE_MISSING, ImportField.GRADING_GUIDE, null, null, row.type);
        }
        List<QuestionBody.EssayCriterion> rubric = new ArrayList<>();
        String rubricCell = raw.get(ImportField.RUBRIC);
        if (!rubricCell.isBlank()) {
            var parsed = parseRubric(rubricCell);
            if (parsed.invalid != null) {
                row.add(ImportIssueCode.RUBRIC_INVALID, ImportField.RUBRIC, parsed.invalid, null, row.type);
            }
            rubric.addAll(parsed.criteria);
        }
        String sample = raw.get(ImportField.CORRECT_ANSWER).isBlank()
            ? null : ImportCellSyntax.processCell(raw.get(ImportField.CORRECT_ANSWER));
        return new QuestionBody.EssayBody(
            QuestionType.ESSAY,
            guideCell.isBlank() ? null : ImportCellSyntax.processCell(guideCell),
            rubric,
            sample,
            null);
    }

    /** Kết quả đọc cột Tiêu chí chấm: các tiêu chí và phần đầu tiên không đọc được. */
    private record RubricParse(List<QuestionBody.EssayCriterion> criteria, String invalid) {}

    private static final Pattern CRITERION_RE =
        Pattern.compile("^(.+?)\\s*(?:[:=]\\s*(\\d+(?:[.,]\\d+)?)|\\(\\s*(\\d+(?:[.,]\\d+)?)\\s*\\))\\s*$");

    /**
     * Cột Tiêu chí chấm: mỗi tiêu chí "Mô tả: 0,5" / "Mô tả = 0,5" / "Mô tả (0,5)", một tiêu
     * chí mỗi dòng — không tách tại {@code ;} vì mô tả có thể chứa dấu chấm phẩy (spec 4.6).
     * Phần thiếu điểm hoặc điểm không lớn hơn 0 là phần đầu tiên bị trả về trong
     * {@code invalid}.
     */
    static RubricParse parseRubric(String value) {
        List<QuestionBody.EssayCriterion> criteria = new ArrayList<>();
        List<String> parts = ImportCellSyntax.lines(value);
        int i = 0;
        for (String part : parts) {
            Matcher matcher = CRITERION_RE.matcher(part);
            String number = matcher.matches() && matcher.group(2) != null
                ? matcher.group(2)
                : matcher.matches() ? matcher.group(3) : null;
            BigDecimal points = ImportCellSyntax.parseNumber(number);
            if (!matcher.matches() || points == null || points.signum() <= 0) {
                return new RubricParse(criteria, part);
            }
            criteria.add(new QuestionBody.EssayCriterion(
                "c" + (++i), ImportCellSyntax.processCell(matcher.group(1).trim()), points));
        }
        return new RubricParse(criteria, null);
    }

    // ------------------------------------------------------------ hỗ trợ

    private static ImportSummary summarize(List<ImportRowResult> results) {
        int valid = 0;
        int error = 0;
        int duplicate = 0;
        int validSets = 0;
        int validSetChildren = 0;
        for (ImportRowResult row : results) {
            switch (row.status()) {
                case VALID -> {
                    valid++;
                    if (row.isSetPassage()) {
                        validSets++;
                    } else if (row.parentRowNumber() != null) {
                        validSetChildren++;
                    }
                }
                case ERROR -> error++;
                case DUPLICATE -> duplicate++;
            }
        }
        return new ImportSummary(results.size(), valid, error, duplicate, validSets, validSetChildren);
    }

    /** Giá trị thô của một dòng theo trường đã ánh xạ (trim). */
    private static final class Raw {
        private final Map<ImportField, String> values;

        Raw(Map<ImportField, String> values) {
            this.values = values;
        }

        String get(ImportField field) {
            return values.getOrDefault(field, "");
        }
    }

    /** Ngữ cảnh dùng chung của một lần đọc. */
    private static final class Context {
        private final ImportSheet sheet;
        private final ImportCatalog catalog;
        private final ImportDictionary dictionary;
        private final Map<ImportField, String> columnByField;
        private final Map<String, String> headerByKey;
        // Lập chỉ mục danh mục kiến thức MỘT lần mỗi lượt đọc, không theo dòng (R1-6):
        // tra theo mã MAPID hoặc tên tiếng Việt (spec 4.6), tên trùng là lỗi chặn.
        private final Map<String, ImportKnowledgeNode> knowledgeByCode;
        private final Map<String, List<ImportKnowledgeNode>> knowledgeByName;

        Context(ImportSheet sheet, ImportCatalog catalog,
            ImportDictionary dictionary, MappingCheck check) {
            this.sheet = sheet;
            this.catalog = catalog;
            this.dictionary = dictionary;
            this.columnByField = check.columnByField();
            this.headerByKey = new HashMap<>();
            for (ImportColumn column : sheet.columns()) {
                headerByKey.put(column.key(), column.header());
            }
            this.knowledgeByCode = new HashMap<>();
            this.knowledgeByName = new HashMap<>();
            for (ImportKnowledgeNode node : catalog.knowledgeNodes()) {
                if (!node.active()) {
                    continue;
                }
                knowledgeByCode.putIfAbsent(ImportCellSyntax.normalizeCode(node.code()), node);
                knowledgeByName
                    .computeIfAbsent(ImportCellSyntax.normalizeText(node.name()), key -> new ArrayList<>())
                    .add(node);
            }
        }

        String columnHeader(ImportField field) {
            String key = columnByField.get(field);
            return key == null ? null : headerByKey.get(key);
        }

        List<Raw> raws() {
            List<Raw> raws = new ArrayList<>(sheet.rows().size());
            for (ImportSheetRow row : sheet.rows()) {
                Map<ImportField, String> values = new LinkedHashMap<>();
                for (ImportField field : ImportField.values()) {
                    String key = columnByField.get(field);
                    values.put(field, key == null ? "" : row.cell(key).trim());
                }
                raws.add(new Raw(values));
            }
            return raws;
        }
    }

    /** Bản đọc của một dòng: vấn đề, mã nguồn và các trường của bản nháp. */
    private static final class RowParse {
        private final int rowNumber;
        private final Context ctx;
        private final QuestionType type;
        private final String setCodeRaw;
        private final List<ImportIssue> issues = new ArrayList<>();
        private String sourceCode = "";
        private String setCode;
        private String content = "";
        private String solution;
        private List<String> commonMistakes = List.of();
        private BigDecimal points;
        private QuestionBody body;
        private String subjectCode;
        private String gradeCode;
        private String difficultyCode;
        private String taskTypeCode;
        private List<ImportKnowledgeRef> knowledge = List.of();
        private String rawKnowledge = "";
        private final List<String> tagCodes = new ArrayList<>();
        private String lastSubjectCode;

        RowParse(int rowNumber, Context ctx, QuestionType type, String setCodeRaw) {
            this.rowNumber = rowNumber;
            this.ctx = ctx;
            this.type = type;
            this.setCodeRaw = setCodeRaw;
        }

        String rawSetCode() {
            return setCodeRaw.isBlank() ? null : setCodeRaw;
        }

        void add(ImportIssueCode code, ImportField field, String value,
            Integer relatedRow, QuestionType questionType) {
            issues.add(new ImportIssue(
                rowNumber, code,
                WARNING_CODES.contains(code) ? ImportIssueSeverity.WARNING : ImportIssueSeverity.ERROR,
                field, field == null ? null : ctx.columnHeader(field), value, relatedRow,
                questionType == null ? type : questionType, lastSubjectCode));
        }

        boolean hasErrors() {
            return issues.stream().anyMatch(ImportIssue::blocking);
        }

        ImportQuestionDraft draftWithChildren(List<ImportQuestionDraft> children) {
            return new ImportQuestionDraft(
                type, sourceCode.isBlank() ? null : sourceCode, content, solution, commonMistakes,
                points, body, subjectCode, gradeCode, difficultyCode, taskTypeCode,
                knowledge, tagCodes, setCode, children);
        }
    }
}
