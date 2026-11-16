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
        cells = cells == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(cells));
        byGrade = byGrade == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(byGrade));
        byDifficulty = byDifficulty == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(byDifficulty));
        byCompetency = byCompetency == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(byCompetency));
    }
}
