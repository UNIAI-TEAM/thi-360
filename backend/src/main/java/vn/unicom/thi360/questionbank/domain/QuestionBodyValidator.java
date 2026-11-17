package vn.unicom.thi360.questionbank.domain;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Kiểm tra phần thân câu hỏi ở hai mức (spec mục 4.3): <b>lưu</b> chỉ chặn dữ liệu không an toàn
 * hoặc sai kiểu (vượt giới hạn tối đa, id trùng, tham chiếu tới phần tử không có, điểm &le; 0);
 * <b>Sẵn sàng</b> kiểm đủ điều kiện của từng dạng cộng nội dung và phân loại. Câu Nháp được thiếu
 * phương án, đáp án, câu con.
 *
 * <p>Hàm thuần: trả danh sách {@link QuestionFieldError} (mã lỗi + đường dẫn JSON); rỗng là hợp lệ.
 * Lớp ứng dụng gọi {@link #validateSave} trước khi lưu và {@link #validateReady} khi đánh dấu
 * Sẵn sàng, rồi gói lỗi trong {@code BusinessRuleException} 422. Kiểm tra tồn tại của giá trị
 * danh mục (môn, khối, MAPID, nhãn, năng lực) là việc của lớp ứng dụng qua {@code CatalogLookup};
 * ở đây chỉ kiểm tra sự có mặt.
 *
 * <p>HTML không nằm trong lớp này: {@code content} và các trường HTML trong thân phải được lọc
 * trước bởi {@link QuestionHtmlSanitizer}; {@code attachmentIds} được tra qua
 * {@link QuestionAssetResolver} truyền vào — id không tra được (hoặc sai loại tài nguyên) là
 * lỗi lưu.
 */
public final class QuestionBodyValidator {

    private QuestionBodyValidator() {}

    // Giới hạn theo cột "Kiểm tra khi lưu" của spec 4.3.
    static final int MAX_OPTIONS = 10;
    static final int MAX_ANSWERS = 20;
    static final int MAX_BLANKS = 30;
    static final int MAX_CHILDREN = 20;
    static final int MAX_STATEMENTS = 8;
    static final int MIN_OPTIONS = 2;
    static final int MIN_STATEMENTS = 2;
    static final int MIN_CHILDREN = 2;
    /** Thang của cột {@code points numeric(8,4)}: giá trị nhiều hơn 4 số thập phân không ghi được. */
    static final int POINTS_SCALE = 4;
    private static final BigDecimal MAX_POINTS = new BigDecimal("9999.9999");

    /** Sai số so điểm tổng (bảng điểm, tiêu chí, cặp) so với điểm câu. */
    private static final BigDecimal SCORE_TOLERANCE = new BigDecimal("0.0005");

    private static final Pattern BLANK_MARKER = Pattern.compile("\\[\\[([^\\[\\]]+)\\]\\]");
    private static final Pattern OPTION_REFERENCE = Pattern.compile(
        "\\b(?:[a-j]\\s*(?:,\\s*[a-j]\\s*)*(?:va|hoac|and|or)\\s+[a-j]"
        + "|(?:ca|both|phuong an|dap an|cau|option)\\s+[a-j]"
        + "|tat ca\\s+(?:(?:cac\\s+)?(?:phuong an|dap an)\\s+tren|deu\\s+(?:dung|sai))"
        + "|ca\\s+(?:hai|ba)|khong co\\s+(?:phuong an|dap an)\\s+nao"
        + "|(?:dap an|phuong an)\\s+khac|cac\\s+(?:phuong an|dap an)\\s+tren"
        + "|(?:all|none|both)\\s+of\\s+the\\s+above|neither)\\b");

    /** Mã của phân loại tối thiểu khi Sẵn sàng (spec 4.3: nội dung, môn, khối lớp, độ khó). */
    public record ClassificationRequirement(boolean subjectPresent, boolean gradePresent, boolean levelPresent) {}

    /** Kết quả của kiểm tra Sẵn sàng: lỗi chặn và cảnh báo (không chặn). */
    public record ReadinessResult(List<QuestionFieldError> errors, List<QuestionFieldError> warnings) {

        public ReadinessResult {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean ready() {
            return errors.isEmpty();
        }
    }

    /**
     * Kiểm tra mức lưu (mọi trạng thái, kể cả Nháp). Không nhận phân loại: lưu Nháp không bắt
     * buộc môn/khối/độ khó.
     */
    public static List<QuestionFieldError> validateSave(QuestionInput input, QuestionAssetResolver assets) {
        List<QuestionFieldError> errors = new ArrayList<>();
        if (input == null) {
            errors.add(QuestionFieldError.of("QUESTION_BODY_INVALID", "body"));
            return errors;
        }
        boolean emptyCompositePoints = input.points() != null && input.points().signum() == 0
                && (input.type() == QuestionType.QUESTION_SET || input.type() == QuestionType.CLOZE);
        if (!emptyCompositePoints) {
            validatePointsField(input.points(), "points", errors);
        }
        if (input.type() == null) {
            errors.add(QuestionFieldError.of("QUESTION_TYPE_MISSING", "type"));
            return errors;
        }
        if (input.body() == null) {
            errors.add(QuestionFieldError.of("QUESTION_BODY_INVALID", "body"));
            return errors;
        }
        if (input.body().type() != input.type()) {
            errors.add(QuestionFieldError.of("QUESTION_BODY_TYPE_MISMATCH", "body.type"));
            return errors;
        }
        validateBodySave(input.body(), "body", errors);
        validateAttachments(input.attachmentIds(), assets, errors);
        return errors;
    }

    /**
     * Kiểm tra mức Sẵn sàng = mọi lỗi mức lưu + điều kiện đủ của từng dạng + nội dung và
     * phân loại tối thiểu. MAPID không bắt buộc (D20); thiếu kiến thức chính chỉ là cảnh báo
     * (spec 9.2).
     *
     * @param input câu đã qua lọc HTML
     * @param classification môn/khối/độ khó đã có chưa (đọc từ bản ghi câu)
     * @param hasPrimaryKnowledge câu đã có nút kiến thức chính chưa ({@code question_knowledge.is_primary})
     * @param assets resolver tài nguyên cho {@code attachmentIds}
     */
    public static ReadinessResult validateReady(
        QuestionInput input,
        ClassificationRequirement classification,
        boolean hasPrimaryKnowledge,
        QuestionAssetResolver assets) {

        List<QuestionFieldError> errors = validateSave(input, assets);
        if (input == null || input.type() == null || input.body() == null) {
            return new ReadinessResult(errors, List.of());
        }
        List<QuestionFieldError> warnings = new ArrayList<>();
        if (errors.stream().noneMatch(e -> e.path().equals("body.type"))) {
            validateBodyReady(input, errors, warnings);
        }
        if (isBlankHtml(input.content()) && !contentCarrierIsBody(input.body())) {
            errors.add(QuestionFieldError.of("CONTENT_EMPTY", "content"));
        }
        if (classification == null || !classification.subjectPresent()) {
            errors.add(QuestionFieldError.of("SUBJECT_MISSING", "subjectItemId"));
        }
        if (classification == null || !classification.gradePresent()) {
            errors.add(QuestionFieldError.of("GRADE_MISSING", "gradeItemId"));
        }
        if (classification == null || !classification.levelPresent()) {
            errors.add(QuestionFieldError.of("DIFFICULTY_MISSING", "levelItemId"));
        }
        if (!hasPrimaryKnowledge) {
            warnings.add(QuestionFieldError.of("PRIMARY_KNOWLEDGE_MISSING", "knowledge"));
        }
        return new ReadinessResult(errors, warnings);
    }

    // ---------------------------------------------------------------- mức lưu

    private static void validateBodySave(QuestionBody body, String prefix, List<QuestionFieldError> errors) {
        switch (body) {
            case QuestionBody.SingleChoiceBody b -> {
                if (b.options().size() > MAX_OPTIONS) {
                    errors.add(QuestionFieldError.of("QUESTION_OPTIONS_TOO_MANY", prefix + ".options"));
                }
                checkDuplicateIds(idsOfOptions(b.options()), prefix + ".options", errors);
            }
            case QuestionBody.MultipleChoiceBody b -> {
                if (b.options().size() > MAX_OPTIONS) {
                    errors.add(QuestionFieldError.of("QUESTION_OPTIONS_TOO_MANY", prefix + ".options"));
                }
                checkDuplicateIds(idsOfOptions(b.options()), prefix + ".options", errors);
                if (b.scoringRule() == null) {
                    errors.add(QuestionFieldError.of("QUESTION_SCORING_RULE_MISSING", prefix + ".scoringRule"));
                }
            }
            case QuestionBody.ShortAnswerBody b -> {
                if (b.answers().size() > MAX_ANSWERS) {
                    errors.add(QuestionFieldError.of("QUESTION_ANSWERS_TOO_MANY", prefix + ".answers"));
                }
            }
            case QuestionBody.ClozeBody b -> {
                if (b.blanks().size() > MAX_BLANKS) {
                    errors.add(QuestionFieldError.of("QUESTION_BLANKS_TOO_MANY", prefix + ".blanks"));
                }
                Set<String> blankIds = new HashSet<>();
                for (int i = 0; i < b.blanks().size(); i++) {
                    QuestionBody.ClozeBlank blank = b.blanks().get(i);
                    String path = prefix + ".blanks[" + i + "]";
                    if (blank.id() == null || blank.id().isBlank() || !blankIds.add(blank.id())) {
                        errors.add(QuestionFieldError.of("QUESTION_DUPLICATE_ID", path + ".id"));
                    }
                    validatePointsField(blank.points(), path + ".points", errors);
                }
                validateClozeMarkers(b.passage(), blankIds, prefix, errors);
            }
            case QuestionBody.QuestionSetBody b -> {
                // Spec 3.4 (UNI-2638): số bắt đầu đánh chỗ trống 1–200, mặc định 1 (constructor
                // đã đọc null ra 1). Ngoài khoảng → mã trường số sẵn có của body.
                if (b.blankNumberStart() != null && (b.blankNumberStart() < 1 || b.blankNumberStart() > 200)) {
                    errors.add(QuestionFieldError.of("QUESTION_WORD_LIMIT_INVALID", prefix + ".blankNumberStart"));
                }
                if (b.children().size() > MAX_CHILDREN) {
                    errors.add(QuestionFieldError.of("QUESTION_CHILDREN_TOO_MANY", prefix + ".children"));
                }
                for (int i = 0; i < b.children().size(); i++) {
                    validateChildSave(b.children().get(i), prefix + ".children[" + i + "]", errors);
                }
            }
            case QuestionBody.TrueFalseBody b -> {
                if (b.statements().size() > MAX_STATEMENTS) {
                    errors.add(QuestionFieldError.of("QUESTION_STATEMENTS_TOO_MANY", prefix + ".statements"));
                }
                checkDuplicateIds(idsOfStatements(b.statements()), prefix + ".statements", errors);
                if (b.scoringMethod() == null) {
                    errors.add(QuestionFieldError.of("QUESTION_SCORING_METHOD_MISSING", prefix + ".scoringMethod"));
                } else if (b.scoringMethod() == QuestionBody.TrueFalseScoringMethod.PER_STATEMENT) {
                    for (int i = 0; i < b.statements().size(); i++) {
                        validatePointsField(
                            b.statements().get(i).points(),
                            prefix + ".statements[" + i + "].points",
                            errors);
                    }
                } else {
                    // COUNT_TABLE: độ dài bảng phải bằng số mệnh đề (thuộc kiểu dữ liệu bảng).
                    if (!b.scoreTable().isEmpty() && b.scoreTable().size() != b.statements().size()) {
                        errors.add(QuestionFieldError.of("SCORE_TABLE_INVALID", prefix + ".scoreTable"));
                    }
                    for (int i = 0; i < b.scoreTable().size(); i++) {
                        BigDecimal cell = b.scoreTable().get(i);
                        if (cell == null || cell.stripTrailingZeros().scale() > POINTS_SCALE
                            || cell.abs().compareTo(MAX_POINTS) > 0) {
                            errors.add(QuestionFieldError.of("SCORE_TABLE_INVALID", prefix + ".scoreTable[" + i + "]"));
                        }
                    }
                }
            }
            case QuestionBody.MatchingBody b -> {
                Set<String> leftIds = new HashSet<>();
                Set<String> rightIds = new HashSet<>();
                collectSideIds(b.left(), leftIds, prefix + ".left", errors);
                collectSideIds(b.right(), rightIds, prefix + ".right", errors);
                Set<String> pairedLeft = new HashSet<>();
                for (int i = 0; i < b.pairs().size(); i++) {
                    QuestionBody.MatchingPair pair = b.pairs().get(i);
                    String path = prefix + ".pairs[" + i + "]";
                    if (pair.leftId() == null || !leftIds.contains(pair.leftId())) {
                        errors.add(QuestionFieldError.of("QUESTION_PAIR_UNKNOWN_LEFT", path + ".leftId"));
                    } else if (!pairedLeft.add(pair.leftId())) {
                        // Một mục trái trỏ tới đúng một lựa chọn (spec 4.3): cặp thứ hai là tham chiếu sai.
                        errors.add(QuestionFieldError.of("QUESTION_PAIR_DUPLICATE_LEFT", path + ".leftId"));
                    }
                    if (pair.rightId() == null || !rightIds.contains(pair.rightId())) {
                        errors.add(QuestionFieldError.of("QUESTION_PAIR_UNKNOWN_RIGHT", path + ".rightId"));
                    }
                    validatePointsField(pair.points(), path + ".points", errors);
                }
            }
            case QuestionBody.EssayBody b -> {
                if (b.wordLimit() != null && b.wordLimit() <= 0) {
                    errors.add(QuestionFieldError.of("QUESTION_WORD_LIMIT_INVALID", prefix + ".wordLimit"));
                }
                checkDuplicateIds(idsOfCriteria(b.rubric()), prefix + ".rubric", errors);
                for (int i = 0; i < b.rubric().size(); i++) {
                    validatePointsField(
                        b.rubric().get(i).points(), prefix + ".rubric[" + i + "].points", errors);
                }
            }
        }
    }

    private static void validateChildSave(QuestionBody.ChildQuestion child, String path, List<QuestionFieldError> errors) {
        if (child.type() == null) {
            errors.add(QuestionFieldError.of("QUESTION_TYPE_MISSING", path + ".type"));
            return;
        }
        // D19: không cho Bộ câu hỏi hay Điền khuyết làm câu con.
        if (child.type() == QuestionType.QUESTION_SET || child.type() == QuestionType.CLOZE) {
            errors.add(QuestionFieldError.of("QUESTION_CHILD_TYPE_NOT_ALLOWED", path + ".type"));
            return;
        }
        if (child.body() == null) {
            errors.add(QuestionFieldError.of("QUESTION_BODY_INVALID", path + ".body"));
            return;
        }
        if (child.body().type() != child.type()) {
            errors.add(QuestionFieldError.of("QUESTION_BODY_TYPE_MISMATCH", path + ".body.type"));
            return;
        }
        validatePointsField(child.points(), path + ".points", errors);
        validateBodySave(child.body(), path + ".body", errors);
    }

    private static void validateClozeMarkers(String passage, Set<String> blankIds, String prefix, List<QuestionFieldError> errors) {
        // Dấu vị trí trong ngữ liệu: [[id]]. Mỗi dấu vị trí có đúng một ô và ngược lại.
        Map<String, Integer> markerCounts = new LinkedHashMap<>();
        if (passage != null) {
            Matcher matcher = BLANK_MARKER.matcher(QuestionHtmlSanitizer.textOf(passage));
            while (matcher.find()) {
                markerCounts.merge(matcher.group(1).trim(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> marker : markerCounts.entrySet()) {
            if (!blankIds.contains(marker.getKey()) || marker.getValue() != 1) {
                errors.add(QuestionFieldError.of("QUESTION_BLANK_MARKER_MISMATCH", prefix + ".passage"));
            }
        }
    }

    private static void validateAttachments(List<java.util.UUID> attachmentIds, QuestionAssetResolver assets, List<QuestionFieldError> errors) {
        if (assets == null) {
            assets = QuestionAssetResolver.none();
        }
        for (int i = 0; i < attachmentIds.size(); i++) {
            java.util.UUID id = attachmentIds.get(i);
            if (id == null || assets.servingPath(id, QuestionAssetKind.ATTACHMENT).isEmpty()) {
                errors.add(QuestionFieldError.of("QUESTION_ATTACHMENT_UNKNOWN", "attachmentIds[" + i + "]"));
            }
        }
    }

    /** Điểm ở mức lưu: {@code null} được phép (Nháp); giá trị có phải &gt; 0 và vừa numeric(8,4). */
    private static void validatePointsField(BigDecimal points, String path, List<QuestionFieldError> errors) {
        if (points == null) {
            return;
        }
        if (points.signum() <= 0) {
            errors.add(QuestionFieldError.of("POINTS_NOT_POSITIVE", path));
        } else if (points.stripTrailingZeros().scale() > POINTS_SCALE) {
            errors.add(QuestionFieldError.of("POINTS_TOO_MANY_DECIMALS", path));
        } else if (points.compareTo(MAX_POINTS) > 0) {
            errors.add(QuestionFieldError.of("POINTS_TOO_LARGE", path));
        }
    }

    // ---------------------------------------------------------------- mức Sẵn sàng

    private static void validateBodyReady(QuestionInput input, List<QuestionFieldError> errors,
            List<QuestionFieldError> warnings) {
        BigDecimal points = input.points() == null ? BigDecimal.ONE : input.points();
        switch (input.body()) {
            case QuestionBody.SingleChoiceBody b -> {
                if (b.options().size() < MIN_OPTIONS || b.options().size() > MAX_OPTIONS) {
                    errors.add(QuestionFieldError.of("TOO_FEW_OPTIONS", "body.options"));
                }
                for (int i = 0; i < b.options().size(); i++) {
                    if (b.shuffleOptions() && refersToOtherOptions(b.options().get(i).content())) {
                        warnings.add(QuestionFieldError.of("OPTION_REFERS_TO_OTHERS", "body.options[" + i + "].content"));
                    }
                    if (isBlankHtml(b.options().get(i).content())) {
                        errors.add(QuestionFieldError.of("OPTION_EMPTY", "body.options[" + i + "].content"));
                    }
                }
                long correct = b.options().stream().filter(o -> Boolean.TRUE.equals(o.correct())).count();
                if (correct == 0) {
                    errors.add(QuestionFieldError.of("NO_CORRECT_OPTION", "body.options"));
                } else if (correct > 1) {
                    errors.add(QuestionFieldError.of("SINGLE_CHOICE_MANY_CORRECT", "body.options"));
                }
            }
            case QuestionBody.MultipleChoiceBody b -> {
                if (b.options().size() < MIN_OPTIONS || b.options().size() > MAX_OPTIONS) {
                    errors.add(QuestionFieldError.of("TOO_FEW_OPTIONS", "body.options"));
                }
                for (int i = 0; i < b.options().size(); i++) {
                    if (b.shuffleOptions() && refersToOtherOptions(b.options().get(i).content())) {
                        warnings.add(QuestionFieldError.of("OPTION_REFERS_TO_OTHERS", "body.options[" + i + "].content"));
                    }
                    if (isBlankHtml(b.options().get(i).content())) {
                        errors.add(QuestionFieldError.of("OPTION_EMPTY", "body.options[" + i + "].content"));
                    }
                }
                long correct = b.options().stream().filter(o -> Boolean.TRUE.equals(o.correct())).count();
                if (correct < 2) {
                    errors.add(QuestionFieldError.of("TOO_FEW_CORRECT_OPTIONS", "body.options"));
                }
            }
            case QuestionBody.ShortAnswerBody b -> {
                boolean any = b.answers().stream().anyMatch(a -> a != null && !a.isBlank());
                if (!any) {
                    errors.add(QuestionFieldError.of("BLANK_WITHOUT_ANSWER", "body.answers"));
                }
            }
            case QuestionBody.ClozeBody b -> {
                if (isBlankHtml(b.passage()) && isBlankHtml(input.content())) {
                    errors.add(QuestionFieldError.of("PASSAGE_EMPTY", "body.passage"));
                }
                if (b.blanks().isEmpty()) {
                    errors.add(QuestionFieldError.of("NO_BLANKS", "body.blanks"));
                }
                Set<String> markers = new HashSet<>();
                Matcher matcher = BLANK_MARKER.matcher(QuestionHtmlSanitizer.textOf(b.passage()));
                while (matcher.find()) {
                    markers.add(matcher.group(1).trim());
                }
                for (QuestionBody.ClozeBlank blank : b.blanks()) {
                    if (!markers.contains(blank.id())) {
                        errors.add(QuestionFieldError.of("QUESTION_BLANK_MARKER_MISMATCH", "body.blanks"));
                    }
                }
                for (int i = 0; i < b.blanks().size(); i++) {
                    QuestionBody.ClozeBlank blank = b.blanks().get(i);
                    boolean any = blank.answers().stream().anyMatch(a -> a != null && !a.isBlank());
                    if (!any) {
                        errors.add(QuestionFieldError.of("BLANK_WITHOUT_ANSWER", "body.blanks[" + i + "].answers"));
                    }
                }
            }
            case QuestionBody.QuestionSetBody b -> {
                if (isBlankHtml(b.passage()) && isBlankHtml(input.content())) {
                    errors.add(QuestionFieldError.of("PASSAGE_EMPTY", "body.passage"));
                }
                if (b.children().size() < MIN_CHILDREN) {
                    errors.add(QuestionFieldError.of("SET_WITHOUT_CHILDREN", "body.children"));
                }
                for (int i = 0; i < b.children().size(); i++) {
                    validateChildReady(b.children().get(i), "body.children[" + i + "]", errors, warnings);
                }
            }
            case QuestionBody.TrueFalseBody b -> {
                if (b.statements().size() < MIN_STATEMENTS) {
                    errors.add(QuestionFieldError.of("NO_STATEMENTS", "body.statements"));
                }
                for (int i = 0; i < b.statements().size(); i++) {
                    QuestionBody.TrueFalseStatement statement = b.statements().get(i);
                    if (isBlankHtml(statement.content())) {
                        errors.add(QuestionFieldError.of("STATEMENT_EMPTY", "body.statements[" + i + "].content"));
                    }
                    if (statement.answer() == null) {
                        errors.add(QuestionFieldError.of("STATEMENT_ANSWER_REQUIRED", "body.statements[" + i + "].answer"));
                    }
                }
                if (b.scoringMethod() == QuestionBody.TrueFalseScoringMethod.PER_STATEMENT) {
                    for (int i = 0; i < b.statements().size(); i++) {
                        if (b.statements().get(i).points() == null) {
                            errors.add(QuestionFieldError.of("POINTS_REQUIRED", "body.statements[" + i + "].points"));
                        }
                    }
                    boolean allPoints = b.statements().stream().allMatch(s -> s.points() != null);
                    if (allPoints && !b.statements().isEmpty()) {
                        BigDecimal total = b.statements().stream()
                            .map(QuestionBody.TrueFalseStatement::points)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (points != null && total.subtract(points).abs().compareTo(SCORE_TOLERANCE) >= 0) {
                            errors.add(QuestionFieldError.of("SCORE_TABLE_INVALID", "body.statements"));
                        }
                    }
                } else if (b.scoringMethod() == QuestionBody.TrueFalseScoringMethod.COUNT_TABLE) {
                    if (!isValidScoreTable(b.scoreTable(), b.statements().size(), points)) {
                        errors.add(QuestionFieldError.of("SCORE_TABLE_INVALID", "body.scoreTable"));
                    }
                }
            }
            case QuestionBody.MatchingBody b -> {
                Set<String> leftIds = idsOfSides(b.left());
                Set<String> rightIds = idsOfSides(b.right());
                Map<String, Integer> rightUse = new HashMap<>();
                Set<String> pairedLeft = new HashSet<>();
                for (int i = 0; i < b.pairs().size(); i++) {
                    if (b.pairs().get(i).points() == null) {
                        errors.add(QuestionFieldError.of("POINTS_REQUIRED", "body.pairs[" + i + "].points"));
                    }
                }
                for (QuestionBody.MatchingPair pair : b.pairs()) {
                    if (pair.leftId() != null && leftIds.contains(pair.leftId())) {
                        pairedLeft.add(pair.leftId());
                    }
                    if (pair.rightId() != null && rightIds.contains(pair.rightId())) {
                        rightUse.merge(pair.rightId(), 1, Integer::sum);
                    }
                }
                for (int i = 0; i < b.left().size(); i++) {
                    if (isBlankHtml(b.left().get(i).content())) {
                        errors.add(QuestionFieldError.of("MATCHING_SIDE_EMPTY", "body.left[" + i + "].content"));
                    }
                }
                for (int i = 0; i < b.right().size(); i++) {
                    if (isBlankHtml(b.right().get(i).content())) {
                        errors.add(QuestionFieldError.of("MATCHING_SIDE_EMPTY", "body.right[" + i + "].content"));
                    }
                }
                for (int i = 0; i < b.left().size(); i++) {
                    if (!pairedLeft.contains(b.left().get(i).id())) {
                        errors.add(QuestionFieldError.of("QUESTION_LEFT_UNPAIRED", "body.left[" + i + "]"));
                    }
                }
                if (!Boolean.TRUE.equals(b.reuseRight())) {
                    for (int i = 0; i < b.right().size(); i++) {
                        Integer used = rightUse.get(b.right().get(i).id());
                        if (used != null && used > 1) {
                            errors.add(QuestionFieldError.of("QUESTION_RIGHT_REUSED", "body.right[" + i + "]"));
                        }
                    }
                }
                BigDecimal total = b.pairs().stream()
                    .map(QuestionBody.MatchingPair::points)
                    .filter(p -> p != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (points != null && total.subtract(points).abs().compareTo(SCORE_TOLERANCE) >= 0) {
                    errors.add(QuestionFieldError.of("QUESTION_PAIR_POINTS_MISMATCH", "body.pairs"));
                }
            }
            case QuestionBody.EssayBody b -> {
                // D20: hướng dẫn chấm bắt buộc; tiêu chí chấm tùy chọn, có thì tổng = điểm câu.
                if (isBlankHtml(b.gradingGuide())) {
                    errors.add(QuestionFieldError.of("NO_GRADING_GUIDE", "body.gradingGuide"));
                }
                for (int i = 0; i < b.rubric().size(); i++) {
                    if (isBlankHtml(b.rubric().get(i).description())) {
                        errors.add(QuestionFieldError.of("CRITERION_EMPTY", "body.rubric[" + i + "].description"));
                    }
                    if (b.rubric().get(i).points() == null) {
                        errors.add(QuestionFieldError.of("POINTS_REQUIRED", "body.rubric[" + i + "].points"));
                    }
                }
                if (!b.rubric().isEmpty() && points != null) {
                    boolean allPoints = b.rubric().stream().allMatch(c -> c.points() != null);
                    if (allPoints) {
                        BigDecimal total = b.rubric().stream()
                            .map(QuestionBody.EssayCriterion::points)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (total.subtract(points).abs().compareTo(SCORE_TOLERANCE) >= 0) {
                            errors.add(QuestionFieldError.of("RUBRIC_TOTAL_MISMATCH", "body.rubric"));
                        }
                    }
                }
            }
        }
        // Trường được nhận nhãn gạch chân: chỉ đề bài (content) của câu Trắc nghiệm 1 đáp án
        // (spec 3.3). Trường nhận chỗ trống: chỉ ngữ liệu câu chùm (spec 6).
        // Câu chùm: trình soạn web gửi content = body.passage (cùng một HTML, hợp đồng từ #60).
        // Khi hai trường trùng nhau thì chỉ kiểm ở body.passage (luật chỗ trống của ngữ liệu),
        // để chỗ trống hợp lệ không bị báo BLANK_OUTSIDE_SET_PASSAGE ở content.
        boolean contentIsSetPassage = input.body() instanceof QuestionBody.QuestionSetBody set
                && input.content() != null && set.passage() != null
                && input.content().strip().equals(set.passage().strip());
        if (!contentIsSetPassage) {
            checkHtml(input.content(), "content",
                    input.type() == QuestionType.SINGLE_CHOICE ? FieldKind.STEM : FieldKind.PLAIN,
                    errors, warnings);
        }
        checkHtml(input.solution(), "solution", FieldKind.PLAIN, errors, warnings);
        for (int i = 0; i < input.commonMistakes().size(); i++) {
            checkHtml(input.commonMistakes().get(i), "commonMistakes[" + i + "]",
                    FieldKind.PLAIN, errors, warnings);
        }
        switch (input.body()) {
            case QuestionBody.SingleChoiceBody b -> {
                for (int i = 0; i < b.options().size(); i++) {
                    checkHtml(b.options().get(i).content(), "body.options[" + i + "].content",
                            FieldKind.PLAIN, errors, warnings);
                }
            }
            case QuestionBody.MultipleChoiceBody b -> {
                for (int i = 0; i < b.options().size(); i++) {
                    checkHtml(b.options().get(i).content(), "body.options[" + i + "].content",
                            FieldKind.PLAIN, errors, warnings);
                }
            }
            case QuestionBody.TrueFalseBody b -> {
                for (int i = 0; i < b.statements().size(); i++) {
                    checkHtml(b.statements().get(i).content(), "body.statements[" + i + "].content",
                            FieldKind.PLAIN, errors, warnings);
                }
            }
            case QuestionBody.MatchingBody b -> {
                for (int i = 0; i < b.left().size(); i++) {
                    checkHtml(b.left().get(i).content(), "body.left[" + i + "].content",
                            FieldKind.PLAIN, errors, warnings);
                }
                for (int i = 0; i < b.right().size(); i++) {
                    checkHtml(b.right().get(i).content(), "body.right[" + i + "].content",
                            FieldKind.PLAIN, errors, warnings);
                }
            }
            case QuestionBody.EssayBody b -> {
                checkHtml(b.gradingGuide(), "body.gradingGuide", FieldKind.PLAIN, errors, warnings);
                checkHtml(b.sampleAnswer(), "body.sampleAnswer", FieldKind.PLAIN, errors, warnings);
                for (int i = 0; i < b.rubric().size(); i++) {
                    checkHtml(b.rubric().get(i).description(), "body.rubric[" + i + "].description",
                            FieldKind.PLAIN, errors, warnings);
                }
            }
            case QuestionBody.ClozeBody b ->
                checkHtml(b.passage(), "body.passage", FieldKind.PLAIN, errors, warnings);
            case QuestionBody.QuestionSetBody b ->
                checkHtml(b.passage(), "body.passage",
                        FieldKind.setPassage(b.children().size()), errors, warnings);
            case QuestionBody.ShortAnswerBody b -> { }
        }
    }

    /**
     * Vai trò của một trường HTML trong luật theo ngữ cảnh (spec 3.3, 6):
     * {@code STEM} đề bài câu Trắc nghiệm 1 đáp án (nơi duy nhất cho {@code u[data-label]});
     * {@code SET_PASSAGE} ngữ liệu câu chùm (nơi duy nhất cho {@code span.blank}, so số với
     * số câu con); {@code PLAIN} mọi trường khác.
     */
    private record FieldKind(boolean labelAllowed, boolean blanksAllowed, int childCount) {
        static final FieldKind PLAIN = new FieldKind(false, false, 0);
        static final FieldKind STEM = new FieldKind(true, false, 0);
        static FieldKind setPassage(int childCount) {
            return new FieldKind(false, true, childCount);
        }
    }

    /** Kiểm một trường HTML: cặp dấu công thức + luật theo ngữ cảnh của spec 6. */
    private static void checkHtml(String html, String path, FieldKind kind,
            List<QuestionFieldError> errors, List<QuestionFieldError> warnings) {
        checkMathPairs(html, path, errors);
        checkRichContent(html, path, kind, errors, warnings);
    }

    /**
     * Luật theo ngữ cảnh của spec 6 trên HTML đã lọc: {@code span.blank} chỉ ở ngữ liệu câu
     * chùm và mỗi số chỗ trống trỏ đúng một câu con; {@code u[data-label]} chỉ ở đề bài
     * Trắc nghiệm 1 đáp án và không trùng nhãn; {@code img} thiếu {@code alt} là cảnh báo.
     */
    private static void checkRichContent(String html, String path, FieldKind kind,
            List<QuestionFieldError> errors, List<QuestionFieldError> warnings) {
        if (html == null || html.isEmpty()) {
            return;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        Set<Integer> blankNumbers = new HashSet<>();
        for (Element blank : doc.select("span.blank")) {
            if (!kind.blanksAllowed()) {
                errors.add(QuestionFieldError.of("BLANK_OUTSIDE_SET_PASSAGE", path));
                continue;
            }
            int number;
            try {
                number = Integer.parseInt(blank.attr("data-blank").trim());
            } catch (NumberFormatException e) {
                number = -1;
            }
            if (number < 1 || number > kind.childCount() || !blankNumbers.add(number)) {
                errors.add(QuestionFieldError.of("BLANK_CHILD_MISMATCH", path));
            }
        }
        Set<String> labels = new HashSet<>();
        for (Element u : doc.select("u[data-label]")) {
            if (!kind.labelAllowed()) {
                errors.add(QuestionFieldError.of("UNDERLINE_LABEL_MISPLACED", path));
            }
            if (!labels.add(u.attr("data-label").trim())) {
                errors.add(QuestionFieldError.of("UNDERLINE_LABEL_DUPLICATE", path));
            }
        }
        for (Element img : doc.select("img")) {
            if (img.attr("alt").isBlank()) {
                warnings.add(QuestionFieldError.of("IMAGE_ALT_MISSING", path));
            }
        }
    }

    private static boolean refersToOtherOptions(String html) {
        String text = QuestionHtmlSanitizer.textOf(html).toLowerCase(Locale.ROOT).replace('đ', 'd');
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return OPTION_REFERENCE.matcher(text).find();
    }

    /** Câu con đạt điều kiện Sẵn sàng của dạng mình (spec 4.3); phân loại kế thừa của bộ. */
    private static void validateChildReady(QuestionBody.ChildQuestion child, String path,
            List<QuestionFieldError> errors, List<QuestionFieldError> warnings) {
        if (child.type() == null || child.body() == null || child.body().type() != child.type()) {
            // Lỗi cấu trúc đã báo ở mức lưu; không lặp.
            return;
        }
        if (isBlankHtml(child.content()) && !contentCarrierIsBody(child.body())) {
            errors.add(QuestionFieldError.of("CONTENT_EMPTY", path + ".content"));
        }
        QuestionInput childInput = new QuestionInput(
            child.type(), child.body(), child.points() != null ? child.points() : BigDecimal.ONE,
            child.content(), null, List.of(), List.of());
        List<QuestionFieldError> childErrors = new ArrayList<>();
        List<QuestionFieldError> childWarnings = new ArrayList<>();
        validateBodyReady(childInput, childErrors, childWarnings);
        for (QuestionFieldError error : childErrors) {
            // Đường dẫn con nằm trong body của câu con: children[i].body.xxx.
            errors.add(new QuestionFieldError(error.code(), path + "." + error.path(), error.message()));
        }
        for (QuestionFieldError warning : childWarnings) {
            warnings.add(new QuestionFieldError(warning.code(), path + "." + warning.path(), warning.message()));
        }
    }


    /**
     * Với CLOZE/QUESTION_SET ngữ liệu nằm trong {@code body.passage}, không phải {@code content}:
     * trường nội dung của câu không bắt buộc khi ngữ liệu đã mang phần chữ.
     */
    private static boolean contentCarrierIsBody(QuestionBody body) {
        return body instanceof QuestionBody.ClozeBody || body instanceof QuestionBody.QuestionSetBody;
    }

    /** Bảng điểm COUNT_TABLE: đủ số ý, không âm, không giảm, phần tử cuối bằng điểm câu (mirror web). */
    static boolean isValidScoreTable(List<BigDecimal> table, int statementCount, BigDecimal points) {
        if (table == null || table.size() != statementCount || statementCount == 0) {
            return false;
        }
        for (int i = 0; i < table.size(); i++) {
            BigDecimal value = table.get(i);
            if (value == null || value.signum() < 0) {
                return false;
            }
            if (i > 0 && value.compareTo(table.get(i - 1)) < 0) {
                return false;
            }
        }
        if (points == null) {
            points = BigDecimal.ONE;
        }
        return table.get(table.size() - 1).subtract(points).abs().compareTo(SCORE_TOLERANCE) < 0;
    }

    /**
     * Kiểm tra cặp dấu công thức {@code \( … \)} và {@code \[ … \]} khớp nhau trong chữ thuần
     * (spec 4.2: máy chủ chỉ kiểm tra cặp mở/đóng — quy tắc NO_GAPS của kiểm tra sẵn sàng).
     */
    private static void checkMathPairs(String html, String path, List<QuestionFieldError> errors) {
        String text = QuestionHtmlSanitizer.textOf(html);
        if (text.isEmpty()) {
            return;
        }
        if (!balanced(text)) {
            errors.add(QuestionFieldError.of("MATH_UNBALANCED", path));
        }
    }

    private static boolean balanced(String text) {
        Deque<Character> delimiters = new ArrayDeque<>();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '\\') {
                continue;
            }
            char delimiter = text.charAt(i + 1);
            if (delimiter == '(' || delimiter == '[') {
                delimiters.push(delimiter);
                i++;
            } else if (delimiter == ')' || delimiter == ']') {
                if (delimiters.isEmpty()
                    || delimiters.pop() != (delimiter == ')' ? '(' : '[')) {
                    return false;
                }
                i++;
            }
        }
        return delimiters.isEmpty();
    }


    // ---------------------------------------------------------------- tiện ích

    /**
     * Rỗng = không có chữ <b>và</b> không có {@code img} (P1-22, quyết định lane 8.8): một
     * {@code img} mang {@code data-asset-id} hợp lệ làm nội dung không rỗng ở mọi trường HTML
     * của danh sách "Áp cho" (spec 4.2) — đầu vào luôn là HTML đã qua lọc nên img còn sót là
     * img có id tra được. Tệp đính kèm không tính là nội dung (quyết định 8.3).
     */
    static boolean isBlankHtml(String html) {
        return html == null
                || (QuestionHtmlSanitizer.textOf(html).isBlank() && !QuestionHtmlSanitizer.containsImg(html));
    }

    private static void checkDuplicateIds(List<String> ids, String path, List<QuestionFieldError> errors) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || id.isBlank() || !seen.add(id)) {
                errors.add(QuestionFieldError.of("QUESTION_DUPLICATE_ID", path + "[" + i + "].id"));
            }
        }
    }

    private static void collectSideIds(List<QuestionBody.MatchingSide> sides, Set<String> ids, String path, List<QuestionFieldError> errors) {
        for (int i = 0; i < sides.size(); i++) {
            String id = sides.get(i).id();
            if (id == null || id.isBlank() || !ids.add(id)) {
                errors.add(QuestionFieldError.of("QUESTION_DUPLICATE_ID", path + "[" + i + "].id"));
            }
        }
    }

    private static List<String> idsOfOptions(List<QuestionBody.ChoiceOption> options) {
        return options.stream().map(QuestionBody.ChoiceOption::id).toList();
    }

    private static List<String> idsOfStatements(List<QuestionBody.TrueFalseStatement> statements) {
        return statements.stream().map(QuestionBody.TrueFalseStatement::id).toList();
    }

    private static List<String> idsOfCriteria(List<QuestionBody.EssayCriterion> rubric) {
        return rubric.stream().map(QuestionBody.EssayCriterion::id).toList();
    }

    private static Set<String> idsOfSides(List<QuestionBody.MatchingSide> sides) {
        Set<String> ids = new HashSet<>();
        sides.forEach(s -> ids.add(s.id()));
        return ids;
    }

}
