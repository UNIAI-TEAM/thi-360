package vn.unicom.thi360.exam.domain.solver;

import java.util.List;
import java.util.Objects;

/**
 * Chỉ tiêu theo một nhóm nút MAPID: một đơn vị đếm được tính vào nhóm khi nó gắn ít nhất một nút thuộc nhóm.
 *
 * @param groupKey khóa định danh của nhóm (để báo lỗi), ví dụ mã nhóm chủ đề
 * @param nodeIds  id các nút MAPID thuộc nhóm
 * @param quota    số đơn vị đếm phải gắn ít nhất một nút của nhóm
 */
public record MapidGroupQuota(String groupKey, List<String> nodeIds, int quota) {

    public MapidGroupQuota {
        Objects.requireNonNull(groupKey, "groupKey must not be null");
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        if (quota < 0) {
            throw new IllegalArgumentException("quota must not be negative");
        }
    }
}
