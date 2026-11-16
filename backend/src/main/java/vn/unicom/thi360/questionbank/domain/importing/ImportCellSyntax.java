package vn.unicom.thi360.questionbank.domain.importing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cú pháp ô của tệp nhập (spec 4.6; các hàm nhỏ mirror {@code validation.ts} của web):
 * danh sách một mục mỗi dòng, dấu phẩy thập phân chỉ ở cột số, nhãn {@code A.}/{@code 1.},
 * gán {@code b1 = …}, và quy tắc HTML của quyết định 8.4.
 */
public final class ImportCellSyntax {

    private ImportCellSyntax() {}

    /** Một mục ghép/nhãn đọc được từ một dòng của ô danh sách. */
    public record LabeledItem(String label, String content) {}

    /** Một phép gán {@code tên = giá trị} của cú pháp {@code b1 = đáp án | đáp án 2}. */
    public record Assignment(String key, String value) {}

    private static final Pattern TAG = Pattern.compile("<\\s*/?\\s*[a-zA-Z][^>]*>");
    private static final Pattern LETTERED_LINE = Pattern.compile("^([A-Za-z])\\s*[.)]\\s*(.*)$");
    private static final Pattern NUMBERED_LINE = Pattern.compile("^(\\d{1,3})\\s*[.)]\\s*(.*)$");
    private static final Pattern ASSIGNMENT = Pattern.compile("^([A-Za-z]\\w{0,15})\\s*=\\s*(.*)$");
    private static final Pattern OPTION_PREFIX = Pattern.compile("^[A-Ja-j]\\s*[.)].*$");

    /** So khớp tên tiếng Việt: NFC, bỏ khoảng trắng đầu cuối, không phân biệt hoa thường. */
    public static boolean sameText(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFC)
            .trim().toLowerCase(Locale.ROOT);
    }

    /** So khớp mã: bỏ khoảng trắng đầu cuối, viết hoa. */
    public static boolean sameCode(String left, String right) {
        return normalizeCode(left).equals(normalizeCode(right));
    }

    public static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** Tách danh sách theo dòng, không tách nội dung tại dấu chấm phẩy (spec 4.6). */
    public static List<String> splitList(String value) {
        return lines(value);
    }

    /** Các dòng không trống của một ô nhiều dòng (Alt+Enter), đã trim từng dòng. */
    public static List<String> lines(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String line : value.split("\r\n|\r|\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Mỗi dòng một mục có nhãn {@code A. nội dung} / {@code a. mệnh đề} / {@code 1. mục trái}
     * (cũng nhận {@code )} và chữ thường). Dòng không nhãn được tự nhãn theo vị trí
     * ({@code A}, {@code B}… hoặc {@code 1}, {@code 2}…); chỉ báo lỗi khi ô trộn cả hai kiểu.
     *
     * @return các mục theo thứ tự dòng; {@code unlabeled} ghi {@code true} khi có dòng không nhãn
     */
    public static List<LabeledItem> parseLabeledLines(String value, boolean lettered, boolean[] unlabeled) {
        List<LabeledItem> items = new ArrayList<>();
        List<String> lines = lines(value);
        boolean sawLabeled = false;
        boolean sawPlain = false;
        Pattern pattern = lettered ? LETTERED_LINE : NUMBERED_LINE;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            var matcher = pattern.matcher(line);
            if (matcher.matches()) {
                sawLabeled = true;
                String label = lettered
                    ? matcher.group(1).toUpperCase(Locale.ROOT)
                    : Integer.toString(Integer.parseInt(matcher.group(1)));
                items.add(new LabeledItem(label, matcher.group(2).trim()));
            } else {
                sawPlain = true;
                String label = lettered ? String.valueOf((char) ('A' + i)) : Integer.toString(i + 1);
                items.add(new LabeledItem(label, line));
            }
        }
        if (unlabeled != null && unlabeled.length > 0) {
            unlabeled[0] = sawLabeled && sawPlain;
        }
        return items;
    }

    /** {@code true} khi dòng trông như đã mang nhãn phương án ({@code A. …}, {@code b) …}). */
    static boolean looksLettered(String line) {
        return OPTION_PREFIX.matcher(line).matches();
    }

    /**
     * Mỗi dòng một phép gán {@code b1 = a | b} hoặc {@code b1 = 0,5}: trả các cặp khóa/giá trị;
     * {@code invalid} nhận dòng đầu tiên không khớp cú pháp ({@code null} khi đọc hết).
     */
    public static List<Assignment> parseAssignments(String value, String[] invalid) {
        List<Assignment> result = new ArrayList<>();
        if (invalid != null && invalid.length > 0) {
            invalid[0] = null;
        }
        for (String line : lines(value)) {
            var matcher = ASSIGNMENT.matcher(line);
            if (!matcher.matches()) {
                if (invalid != null && invalid.length > 0) {
                    invalid[0] = line;
                }
                return result;
            }
            result.add(new Assignment(matcher.group(1), matcher.group(2).trim()));
        }
        return result;
    }

    /**
     * Số của cột số: dấu phẩy hoặc dấu chấm thập phân đều được ({@code 0,75} = {@code 0.75},
     * spec 4.6). {@code null} khi ô trống/không phải số. Không áp cho đáp án Trả lời ngắn.
     */
    public static BigDecimal parseNumber(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace(',', '.');
        if (normalized.isEmpty() || !normalized.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        return new BigDecimal(normalized);
    }

    /** Chữ cái phương án đơn: {@code A}..{@code J} (tối đa 10 phương án của spec 4.3). */
    public static boolean isOptionLetter(String token) {
        if (token == null || token.length() != 1) {
            return false;
        }
        char c = Character.toUpperCase(token.charAt(0));
        return c >= 'A' && c <= 'J';
    }

    /** {@code true} khi ô chứa ít nhất một thẻ HTML (quyết định 8.4). */
    public static boolean containsHtmlTag(String value) {
        return value != null && TAG.matcher(value).find();
    }

    /** Escape tối thiểu của chữ thuần trước khi đưa vào nội dung HTML. */
    public static String escapeText(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    /**
     * Nội dung ô theo quyết định 8.4: ô có thẻ giữ nguyên (lọc an toàn ở lộ trình lưu);
     * ô chữ thuần được escape và xuống dòng thành {@code <br>}.
     * Ô trống trả chuỗi rỗng.
     */
    public static String processCell(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (containsHtmlTag(trimmed)) {
            return trimmed;
        }
        return escapeText(trimmed)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", "<br>");
    }

    /**
     * Dấu ngoặc nhọn của công thức LaTeX có đóng mở đủ (bỏ qua {@code \{} / {@code \}};
     * mirror {@code bracesBalanced} của web).
     */
    public static boolean bracesBalanced(String text) {
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    /**
     * Chia đều {@code total} cho {@code count} phần theo phần dư lớn nhất, quy 4 chữ số thập
     * phân (thang của {@code points numeric(8,4)}): phần tử đầu nhận phần dư nên tổng khôi
     * phục đúng {@code total}. Dùng cho "điểm từng cặp chia đều" của Ghép đôi và mặc định
     * Đúng/Sai {@code PER_STATEMENT}.
     */
    public static List<BigDecimal> splitEvenly(BigDecimal total, int count) {
        List<BigDecimal> result = new ArrayList<>(count);
        if (count <= 0 || total == null) {
            return result;
        }
        BigDecimal unit = total.divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.DOWN);
        BigDecimal assigned = unit.multiply(BigDecimal.valueOf(count));
        BigDecimal first = unit.add(total.subtract(assigned));
        for (int i = 0; i < count; i++) {
            result.add(i == 0 ? first : unit);
        }
        return result;
    }
}
