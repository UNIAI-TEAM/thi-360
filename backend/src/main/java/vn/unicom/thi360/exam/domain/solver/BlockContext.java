package vn.unicom.thi360.exam.domain.solver;

import java.util.List;

/**
 * Khối đã chuẩn bị cho một đường: chỉ tiêu nội bộ, ứng viên đã lọc cùng vector đóng góp.
 *
 * <p>Mỗi phần tử {@code contrib[i]} là vector đóng góp của ứng viên {@code cands.get(i)} trên mảng
 * {@code reqs} (số đơn vị của ứng viên khớp từng chỉ tiêu). Ứng viên đã lọc = đủ điều kiện lọc của khối
 * (điểm, quy tắc chấm, kích thước bộ), mọi đơn vị khớp ít nhất một chỉ tiêu và không đóng góp vào chỉ tiêu
 * nào có quota 0.
 *
 * @param quota          khối gốc
 * @param reqs           mọi chỉ tiêu (ma trận trước, năng lực, nhóm MAPID)
 * @param primaryCount   số chỉ tiêu đầu thuộc ma trận (để kiểm tổng chỉ tiêu = số câu)
 * @param primaryDim     số chiều chính một đơn vị được phép khớp trong ma trận (COMBINED: 1; SEPARATE 2 chiều: 2;
 *                      SEPARATE 1 chiều: 1)
 * @param cands          ứng viên đã lọc, giữ thứ tự đầu vào (hoặc đã xáo)
 * @param contrib        {@code contrib[i][r]}: số đơn vị của ứng viên i khớp chỉ tiêu r
 * @param unitsProvided  {@code unitsProvided[i]}: số đơn vị của ứng viên i được tính vào khối
 *                      (đơn vị không khớp chỉ tiêu nào làm ứng viên mất giá trị)
 * @param keys           {@code keys[i]}: khóa duy nhất của ứng viên i (questionId + childIds)
 * @param usable         ứng viên i có thể được chọn không
 */
record BlockContext(BlockQuota quota,
        List<Requirement> reqs,
        int primaryCount,
        int primaryDim,
        List<PathCandidate> cands,
        int[][] contrib,
        int[] unitsProvided,
        String[][] keys,
        boolean[] usable) {
}
