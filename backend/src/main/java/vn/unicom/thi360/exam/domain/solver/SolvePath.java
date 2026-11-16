package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Một đường lựa chọn: các khối bắt buộc cộng một lựa chọn của mỗi phần CHOICE (spec 5.4, D15).
 *
 * <p>{@code blocks} là toàn bộ khối phải giải đồng thời trên đường này. Bộ giải không dựng sẵn danh sách
 * đường: các đường được liệt kê lần lượt theo kiểu lặp (odometer), {@code pathIndex} là chỉ số của đường.
 *
 * @param pathIndex     chỉ số đường trong phép liệt kê (từ 0)
 * @param chosenOptions lựa chọn của từng phần CHOICE, theo thứ tự phần
 * @param blocks        toàn bộ khối của đường
 */
public record SolvePath(long pathIndex, List<ChosenOption> chosenOptions, List<BlockQuota> blocks) {

    public SolvePath {
        chosenOptions = chosenOptions == null ? List.of() : List.copyOf(chosenOptions);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
