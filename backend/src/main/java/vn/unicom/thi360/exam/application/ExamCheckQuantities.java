package vn.unicom.thi360.exam.application;

import java.math.BigDecimal;
import java.util.List;
import vn.unicom.thi360.exam.domain.MatrixUnit;
import vn.unicom.thi360.exam.domain.solver.LargestRemainderApportioner;

/** Exact integer quota conversion, including percentages resolved for the selected subject count. */
final class ExamCheckQuantities {
    private static final LargestRemainderApportioner APPORTIONER = new LargestRemainderApportioner();

    private ExamCheckQuantities() { }

    static int[] resolve(List<BigDecimal> values, MatrixUnit unit, int total) {
        if (total < 0 || unit == null) return null;
        int[] result = new int[values.size()];
        long sum = 0;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal value = values.get(i);
            if (value == null) return null;
            try {
                result[i] = value.intValueExact();
            } catch (ArithmeticException ex) {
                return null;
            }
            if (result[i] < 0) return null;
            sum += result[i];
        }
        if (sum != (unit == MatrixUnit.PERCENT ? 100 : total)) return null;
        return unit == MatrixUnit.PERCENT
                ? APPORTIONER.apportion(java.util.Arrays.stream(result).boxed().toList(), total)
                : result;
    }
}
