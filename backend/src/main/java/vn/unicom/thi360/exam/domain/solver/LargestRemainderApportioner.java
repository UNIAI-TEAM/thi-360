package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Cài đặt mặc định của {@link QuotaApportioner}: phần dư lớn nhất (PBI-04 7.3), chuyển nguyên từ
 * web ({@code matrix.ts#apportionByPercent}). Tính số câu lý thuyết, lấy phần nguyên, phân phần
 * còn thiếu cho mục có phần thập phân lớn nhất; bằng nhau thì theo thứ tự khai báo. Khi tổng tỉ lệ
 * khác 100 thì mục tiêu là tổng lý thuyết làm tròn, không bù đủ {@code total}.
 */
public final class LargestRemainderApportioner implements QuotaApportioner {

    /** Nửa đơn vị của phần tỷ lệ mà web dùng làm epsilon (1e-9 trên thang câu). */
    private static final double EPSILON = 1e-9;

    /**
     * Làm tròn tới 1e-9 như {@code roundPrecision} của web ({@code matrix.ts}): hai giá trị lý
     * thuyết / phần thập phân chỉ lệch nhau ở sai số nhị phân được coi bằng nhau (R2-1: phần dư
     * 0,3 của [3,13,23,61] hòa, không phải 0,2999999… so với 0,3000000…04).
     */
    private static double roundPrecision(double value) {
        return Math.round(value * 1e9) / 1e9;
    }

    @Override
    public int[] apportion(List<Integer> percents, int total) {
        Objects.requireNonNull(percents, "percents must not be null");
        int n = percents.size();
        double[] theoretical = new double[n];
        int[] floors = new int[n];
        double theoreticalSum = 0;
        int floorTotal = 0;
        for (int i = 0; i < n; i++) {
            theoretical[i] = roundPrecision(percents.get(i) * (double) total / 100);
            floors[i] = (int) Math.floor(theoretical[i] + EPSILON);
            theoreticalSum += theoretical[i];
            floorTotal += floors[i];
        }
        int target = (int) Math.round(theoreticalSum);
        int missing = Math.max(0, target - floorTotal);
        // thứ tự phân phần còn thiếu: phần thập phân giảm dần, hòa thì theo thứ tự khai báo
        Integer[] ranked = new Integer[n];
        for (int i = 0; i < n; i++) {
            ranked[i] = i;
        }
        java.util.Arrays.sort(ranked, (a, b) -> {
            double fa = roundPrecision(theoretical[a] - floors[a]);
            double fb = roundPrecision(theoretical[b] - floors[b]);
            return fa != fb ? Double.compare(fb, fa) : Integer.compare(a, b);
        });
        int[] out = floors.clone();
        for (int k = 0; k < missing && k < n; k++) {
            out[ranked[k]]++;
        }
        return out;
    }
}
