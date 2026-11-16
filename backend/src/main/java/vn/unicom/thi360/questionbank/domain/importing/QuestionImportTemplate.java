package vn.unicom.thi360.questionbank.domain.importing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Workbook containing examples of the eight import formats. */
public final class QuestionImportTemplate {
    private QuestionImportTemplate() {}

    public static byte[] create() {
        List<String> headers = List.of("sourceCode", "type", "content", "points", "options", "matchRight",
            "correctAnswer", "scoring", "inputHint", "gradingGuide", "rubric", "solution", "commonMistakes",
            "subject", "grade", "difficulty", "taskType", "knowledge", "tags", "setCode");
        List<Map<String, String>> examples = List.of(
            Map.of("sourceCode", "EX-1", "type", "SINGLE_CHOICE", "content", "Choose the correct word.",
                "points", "0,75", "options", "A. hello\nB. goodbye", "correctAnswer", "A", "subject", "ENGLISH", "grade", "GRADE_12", "difficulty", "RECOGNITION"),
            Map.of("sourceCode", "EX-2", "type", "MULTIPLE_CHOICE", "content", "Chọn đáp án đúng.",
                "options", "A. Một\nB. Hai\nC. Ba", "correctAnswer", "A, C", "scoring", "PARTIAL", "subject", "MATH", "grade", "GRADE_11", "difficulty", "RECOGNITION"),
            Map.of("sourceCode", "EX-3", "type", "SHORT_ANSWER", "content", "Một nửa là bao nhiêu?",
                "correctAnswer", "0,5\n1/2", "subject", "MATH", "grade", "GRADE_11", "difficulty", "RECOGNITION"),
            Map.of("sourceCode", "EX-4", "type", "CLOZE", "content", "Điền [[b1]] và [[b2]].",
                "correctAnswer", "b1 = một\nb2 = hai", "scoring", "b1 = 0,5\nb2 = 0,5", "subject", "MATH", "grade", "GRADE_11", "difficulty", "RECOGNITION"),
            Map.of("sourceCode", "EX-5", "type", "QUESTION_SET", "content", "Đọc ngữ liệu.",
                "setCode", "SET-1", "subject", "MATH", "grade", "GRADE_11", "difficulty", "RECOGNITION"),
            Map.of("type", "SHORT_ANSWER", "content", "Câu con thứ nhất.", "correctAnswer", "1", "setCode", "SET-1"),
            Map.of("type", "SHORT_ANSWER", "content", "Câu con thứ hai.", "correctAnswer", "2", "setCode", "SET-1"),
            Map.of("sourceCode", "EX-6", "type", "TRUE_FALSE", "content", "Chọn đúng hoặc sai.",
                "options", "a. Một\nb. Hai", "correctAnswer", "Đ, S", "scoring", "PER_STATEMENT: 0,5 / 0,5", "subject", "MATH", "grade", "GRADE_11", "difficulty", "RECOGNITION"),
            Map.of("sourceCode", "EX-7", "type", "MATCHING", "content", "Ghép các mục.",
                "options", "1. Một\n2. Hai", "matchRight", "A. One\nB. Two", "correctAnswer", "1 = A\n2 = B", "subject", "MATH", "grade", "GRADE_11", "difficulty", "RECOGNITION"),
            Map.of("sourceCode", "EX-8", "type", "ESSAY", "content", "Viết một đoạn văn.",
                "gradingGuide", "Nêu rõ luận điểm.", "subject", "MATH", "grade", "GRADE_11", "difficulty", "RECOGNITION"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Câu hỏi");
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                header.createCell(c).setCellValue(headers.get(c));
                sheet.setColumnWidth(c, 22 * 256);
            }
            for (int r = 0; r < examples.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < headers.size(); c++) {
                    row.createCell(c).setCellValue(examples.get(r).getOrDefault(headers.get(c), ""));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create import template", e);
        }
    }
}
