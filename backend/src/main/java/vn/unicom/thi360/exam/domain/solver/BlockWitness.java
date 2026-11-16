package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Đề chứng minh của một khối: danh sách câu (và phiên bản) bộ giải đã chọn cho khối.
 *
 * @param blockId id khối
 * @param picks   các câu được chọn, theo thứ tự bộ giải chọn
 */
public record BlockWitness(String blockId, List<QuestionPick> picks) {

    public BlockWitness {
        picks = List.copyOf(picks);
    }
}
