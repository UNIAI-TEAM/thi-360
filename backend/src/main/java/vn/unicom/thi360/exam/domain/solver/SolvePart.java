package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Một phần của cấu trúc bài thi ở dạng đầu vào của bộ giải (mock {@code ExamPart}).
 *
 * <p>{@link Fixed}: mọi học viên làm các khối trong {@code blocks}. {@link Choice}: học viên làm các khối
 * chung {@code commonBlocks} (có thể rỗng) cộng với đúng một lựa chọn trong {@code options} — mỗi tổ hợp
 * lựa chọn trên các phần CHOICE tạo thành một đường lựa chọn.
 */
public sealed interface SolvePart {

    record Fixed(String partId, List<BlockQuota> blocks) implements SolvePart {

        public Fixed {
            Objects.requireNonNull(partId, "partId must not be null");
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    record Choice(String partId, List<BlockQuota> commonBlocks, List<SolveOption> options)
            implements SolvePart {

        public Choice {
            Objects.requireNonNull(partId, "partId must not be null");
            commonBlocks = commonBlocks == null ? List.of() : List.copyOf(commonBlocks);
            options = options == null ? List.of() : List.copyOf(options);
        }

        /** Phần tự chọn không có phần chung. */
        public static Choice of(String partId, List<SolveOption> options) {
            return new Choice(partId, List.of(), options);
        }
    }
}
