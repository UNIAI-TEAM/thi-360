package vn.unicom.thi360.catalog.spi;

import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.catalog.domain.CombinationBranch;

/**
 * Mã tổ hợp đang hiệu lực của nhóm kiểu COMBINATION: giá trị danh mục + kỳ thi +
 * danh sách môn theo thứ tự lưu.
 */
public record CatalogCombinationView(
        UUID itemId,
        String itemCode,
        String itemNameVi,
        String itemNameEn,
        UUID examItemId,
        String examItemCode,
        String examItemNameVi,
        String examItemNameEn,
        CombinationBranch branch,
        int versionNo,
        List<CatalogCombinationSubjectView> subjects
) {
    public CatalogCombinationView {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
    }


    public List<String> subjectCodes() {
        return subjects.stream().map(CatalogCombinationSubjectView::code).toList();
    }
}
