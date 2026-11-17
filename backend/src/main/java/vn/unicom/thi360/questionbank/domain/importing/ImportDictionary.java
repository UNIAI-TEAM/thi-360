package vn.unicom.thi360.questionbank.domain.importing;

import java.util.List;
import java.util.Map;

import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * Từ điển đọc tệp: tên cột, tên dạng câu và ký hiệu Đúng/Sai bằng tiếng Việt. Dữ liệu giống
 * {@code features/question-import/mock/import-dictionary.json} của web; ở đây là hằng vì tệp
 * mẫu mới dùng tên cột của spec 4.6 (mã trường tự là bí danh).
 *
 * @param fieldAliases tên gọi khác của từng trường, theo trường
 * @param ignoreHeaders tên cột được gợi ý bỏ qua
 * @param typeAliases tên gọi khác của từng dạng câu
 * @param trueTokens ký hiệu "đúng" của câu Đúng/Sai
 * @param falseTokens ký hiệu "sai"
 */
public record ImportDictionary(
    Map<ImportField, List<String>> fieldAliases,
    List<String> ignoreHeaders,
    Map<QuestionType, List<String>> typeAliases,
    List<String> trueTokens,
    List<String> falseTokens) {

    /** Từ điển chuẩn, ngang hàng mock {@code import-dictionary.json} của web. */
    public static ImportDictionary standard() {
        Map<ImportField, List<String>> fieldAliases = Map.ofEntries(
            Map.entry(ImportField.SOURCE_CODE, List.of("Mã nguồn", "Mã câu nguồn", "Source code")),
            Map.entry(ImportField.TYPE, List.of("Dạng câu", "Dạng câu hỏi", "Loại câu", "Question type")),
            Map.entry(ImportField.CONTENT, List.of("Nội dung", "Đề bài", "Câu hỏi")),
            Map.entry(ImportField.OPTION_A, List.of("A", "Phương án A", "Option A")),
            Map.entry(ImportField.OPTION_B, List.of("B", "Phương án B", "Option B")),
            Map.entry(ImportField.OPTION_C, List.of("C", "Phương án C", "Option C")),
            Map.entry(ImportField.OPTION_D, List.of("D", "Phương án D", "Option D")),
            Map.entry(ImportField.CORRECT_ANSWER, List.of("Đáp án", "Đáp án đúng", "Answer", "Correct answer")),
            Map.entry(ImportField.SOLUTION, List.of("Lời giải", "Hướng dẫn giải")),
            Map.entry(ImportField.SUBJECT, List.of("Môn", "Môn học")),
            Map.entry(ImportField.GRADE, List.of("Lớp", "Khối", "Khối lớp")),
            Map.entry(ImportField.DIFFICULTY, List.of("Mức độ", "Độ khó")),
            Map.entry(ImportField.KNOWLEDGE, List.of("Kiến thức", "Kiến thức (MAPID)", "MAPID", "Mã kiến thức")),
            Map.entry(ImportField.TAGS, List.of("Nhãn")),
            Map.entry(ImportField.SET_CODE, List.of("Mã chùm", "Mã câu chùm", "Set code")),
            Map.entry(ImportField.RUBRIC, List.of("Tiêu chí chấm", "Thang chấm", "Rubric")),
            Map.entry(ImportField.POINTS, List.of("Điểm", "Points")),
            Map.entry(ImportField.OPTIONS, List.of("Lựa chọn", "Options")),
            Map.entry(ImportField.MATCH_RIGHT, List.of("Lựa chọn phải", "Right choices")),
            Map.entry(ImportField.INPUT_HINT, List.of("Hướng dẫn nhập", "Input hint")),
            Map.entry(ImportField.ANSWER_FORMAT, List.of("Định dạng đáp án", "Định dạng trả lời", "Answer format")),
            Map.entry(ImportField.GRADING_GUIDE, List.of("Hướng dẫn chấm", "Grading guide")),
            Map.entry(ImportField.COMMON_MISTAKES, List.of("Lỗi sai thường gặp", "Common mistakes")),
            Map.entry(ImportField.TASK_TYPE, List.of("Dạng bài", "Task type")),
            Map.entry(ImportField.KEEP_OPTION_ORDER, List.of("Giữ thứ tự phương án", "Keep option order")));
        List<String> ignoreHeaders = List.of("Ghi chú nội bộ", "Ghi chú", "STT");
        Map<QuestionType, List<String>> typeAliases = Map.ofEntries(
            Map.entry(QuestionType.SINGLE_CHOICE,
                List.of("Trắc nghiệm 1 đáp án", "Trắc nghiệm một đáp án", "Một đáp án", "Single choice")),
            Map.entry(QuestionType.MULTIPLE_CHOICE,
                List.of("Trắc nghiệm nhiều đáp án", "Nhiều đáp án", "Multiple choice")),
            Map.entry(QuestionType.SHORT_ANSWER,
                List.of("Trả lời ngắn", "Điền đáp án", "Điền", "Short answer", "Fill in")),
            Map.entry(QuestionType.CLOZE,
                List.of("Điền khuyết", "Cloze", "Fill in the blanks")),
            Map.entry(QuestionType.QUESTION_SET,
                List.of("Câu chùm", "Question set")),
            Map.entry(QuestionType.TRUE_FALSE,
                List.of("Đúng / Sai", "Đúng/Sai", "Đúng sai", "True / False", "True/False")),
            Map.entry(QuestionType.MATCHING,
                List.of("Ghép đôi", "Kéo thả", "Matching", "Drag and drop")),
            Map.entry(QuestionType.ESSAY,
                List.of("Tự luận", "Câu tự luận", "Tự luận (chấm tay)", "Essay")));
        return new ImportDictionary(
            fieldAliases,
            ignoreHeaders,
            typeAliases,
            List.of("Đ", "Đúng", "T", "True"),
            List.of("S", "Sai", "F", "False"));
    }

    public List<String> aliasesOf(ImportField field) {
        return fieldAliases.getOrDefault(field, List.of());
    }

    public List<String> aliasesOf(QuestionType type) {
        return typeAliases.getOrDefault(type, List.of());
    }
}
