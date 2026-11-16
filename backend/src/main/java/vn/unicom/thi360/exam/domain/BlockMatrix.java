package vn.unicom.thi360.exam.domain;

import java.util.List;

public record BlockMatrix(
        MatrixMode mode,
        MatrixUnit unit,
        List<MatrixCell> cells,
        List<MatrixDimensionValue> byGrade,
        List<MatrixDimensionValue> byDifficulty,
        List<MatrixDimensionValue> byCompetency) {
    public BlockMatrix {
        cells = cells == null ? List.of() : List.copyOf(cells);
        byGrade = byGrade == null ? List.of() : List.copyOf(byGrade);
        byDifficulty = byDifficulty == null ? List.of() : List.copyOf(byDifficulty);
        byCompetency = byCompetency == null ? List.of() : List.copyOf(byCompetency);
    }
}
