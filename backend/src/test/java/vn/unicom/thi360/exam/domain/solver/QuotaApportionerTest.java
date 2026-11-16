package vn.unicom.thi360.exam.domain.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ca của {@link LargestRemainderApportioner}, chuyển nguyên từ test web
 * {@code apps/web-admin/src/features/exams/__tests__/matrix-logic.test.ts} ("quy đổi tỷ lệ theo
 * phần dư lớn nhất", PBI-04 7.3) — phần còn thiếu đi tới phần thập phân lớn nhất, hòa thì theo thứ
 * tự khai báo.
 */
class QuotaApportionerTest {

    private final QuotaApportioner apportioner = new LargestRemainderApportioner();

    private static List<Integer> percents(int... values) {
        return Arrays.stream(values).boxed().toList();
    }

    @Test
    @DisplayName("khối 20 câu của bản vẽ: phần nguyên 19, phần còn thiếu vào mục phần thập phân lớn nhất (0,6)")
    void artboardBlockOf20() {
        // ARTBOARD_PERCENTS.flat() của test web
        int[] p = {12, 5, 5, 0, 10, 10, 5, 5, 8, 20, 15, 5};
        // ARTBOARD_COUNTS.flat()
        int[] expected = {2, 1, 1, 0, 2, 2, 1, 1, 2, 4, 3, 1};
        assertThat(apportioner.apportion(percents(p), 20)).containsExactly(expected);
    }

    @Test
    @DisplayName("bằng phần thập phân thì ưu tiên theo thứ tự khai báo")
    void tiesBreakByDeclarationOrder() {
        assertThat(apportioner.apportion(percents(25, 25, 25, 25), 2)).containsExactly(1, 1, 0, 0);
    }

    @Test
    @DisplayName("R2-1: hòa phần dư 0,3 không biểu diễn chính xác nhị phân → theo thứ tự khai báo")
    void nonBinaryExactTieBreaksByDeclarationOrder() {
        // 3%, 13%, 23% × 10 câu đều cho phần dư lý thuyết 0,3 — ba số khác biểu diễn nhị phân
        assertThat(apportioner.apportion(percents(3, 13, 23, 61), 10))
                .containsExactly(1, 1, 2, 6);
    }

    @Test
    @DisplayName("chia đều 50 câu cho 3 môn: 17-17-16 (HSA Khoa học)")
    void splitFiftyThreeWays() {
        // splitEvenly(50, 3) của web = apportionByPercent([100/3]*3, 50)
        List<Integer> thirds = List.of(100 / 3, 100 / 3, 100 / 3);
        assertThat(apportioner.apportion(thirds, 50)).containsExactly(17, 17, 16);
        assertThat(apportioner.apportion(List.of(50, 50), 50)).containsExactly(25, 25);
    }

    @Test
    @DisplayName("tổng tỷ lệ khác 100% thì không bù đủ số câu của nhóm")
    void percentTotalOtherThan100() {
        assertThat(apportioner.apportion(percents(50, 0), 20)).containsExactly(10, 0);
    }
}
