package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Một lựa chọn của phần CHOICE: một môn, một mã tổ hợp hoặc một nhánh nội dung (mock {@code ExamPartOption}).
 *
 * @param code   mã lựa chọn (mã môn, mã tổ hợp, mã nhánh) để ghi vào nhãn đường
 * @param blocks các khối chỉ tiêu của lựa chọn này (đã trải phẳng từ các môn của lựa chọn)
 */
public record SolveOption(String code, List<BlockQuota> blocks) {

    public SolveOption {
        Objects.requireNonNull(code, "code must not be null");
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
