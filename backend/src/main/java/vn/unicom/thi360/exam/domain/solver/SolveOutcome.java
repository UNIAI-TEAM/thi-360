package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Kết quả của một lời gọi {@link PaperSolver#solveAll} trên cả cấu trúc.
 *
 * <p>{@code status} là tổng hợp: UNCONFIRMED khi bản thân yêu cầu bị chặn (vượt giới hạn đường, hết hạn
 * trước khi liệt kê) — khi đó {@code reason} khác null và {@code results} có thể rỗng; còn lại là trạng
 * thái nặng nhất trong các đường: INVALID > UNCONFIRMED > VALID.
 *
 * @param status  trạng thái tổng hợp
 * @param reason  lý do mức yêu cầu (PATH_LIMIT_EXCEEDED, DEADLINE_EXCEEDED); null khi tổng hợp từ các đường
 * @param results kết quả từng đường, theo thứ tự liệt kê
 */
public record SolveOutcome(SolveStatus status, SolveReason reason, List<PathResult> results) {

    public SolveOutcome {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
