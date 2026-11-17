package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Đề chứng minh của một nhóm: danh sách câu (và phiên bản) bộ giải đã chọn cho nhóm.
 * (Đổi tên từ {@code BlockWitness} của bản trước: khối → nhóm theo B9/D21.)
 *
 * @param groupId id nhóm
 * @param picks   các câu được chọn, theo thứ tự bộ giải chọn
 */
public record GroupWitness(String groupId, List<QuestionPick> picks) {

    public GroupWitness {
        picks = picks == null ? List.of() : List.copyOf(picks);
    }
}
