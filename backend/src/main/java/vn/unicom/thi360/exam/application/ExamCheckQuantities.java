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
        int[] rows = rows(values, unit, total);
        if (rows == null) return null;
        // A PERCENT list is validated (sum 100) by rows; a COUNT list must add up to the total here.
        if (unit == MatrixUnit.COUNT && java.util.Arrays.stream(rows).asLongStream().sum() != total) return null;
        return rows;
    }

    /**
     * The per-row question counts the solver derives from a quota list: COUNT values as declared, PERCENT values through
     * the solver's own {@link LargestRemainderApportioner} (declaration-order ties). A null unit means COUNT, exactly as
     * {@code SolverInputBuilder} reads it. Null when a value is missing, fractional or negative, or a PERCENT list does
     * not add up to 100 (the matrix and answer-quota rules report those).
     */
    static int[] rows(List<BigDecimal> values, MatrixUnit unit, int total) {
        if (total < 0) return null;
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
        if (unit != MatrixUnit.PERCENT) return result;
        if (sum != 100) return null;
        return APPORTIONER.apportion(java.util.Arrays.stream(result).boxed().toList(), total);
    }
}
