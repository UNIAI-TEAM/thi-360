package vn.unicom.thi360.catalog.spi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CatalogLookup {

    /**
     * Số revision tăng đơn điệu của toàn danh mục: mọi lệnh ghi vào bảng danh mục (kể cả liên kết
     * và tổ hợp) tăng bộ đếm này. Dùng cho dấu vân tay đầu vào và cache chéo module.
     */
    long revision();

    /**
     * Tất cả giá trị đang có hiệu lực của một nhóm đang hoạt động, sắp theo display_order rồi mã.
     * Trả rỗng khi nhóm không có hoặc đã ngừng.
     */
    List<CatalogItemView> listActiveGroupItems(String groupCode);

    /**
     * Mọi giá trị của một nhóm đang hoạt động kể cả đã ngừng, sắp theo display_order rồi mã;
     * {@code effectiveStatus} là ACTIVE chỉ khi giá trị và cả tổ tiên đều hoạt động. Trả rỗng
     * khi nhóm không có hoặc đã ngừng.
     */
    List<CatalogItemView> listGroupItems(String groupCode);

    /**
     * Liên kết hiệu lực của các giá trị nhóm cây: liên kết riêng cộng kế thừa từ tổ tiên,
     * đã bỏ trùng theo {@code refItemId}.
     */
    Map<UUID, List<CatalogItemRefView>> findEffectiveItemRefs(Collection<UUID> itemIds);

    /**
     * Phiên bản tổ hợp đang hiệu lực của mọi giá trị đang hoạt động trong nhóm COMBINATION
     * ký hiệu bởi {@code groupCode}. Trả rỗng khi nhóm không có, đã ngừng, hoặc không phải
     * COMBINATION.
     */
    List<CatalogCombinationView> listCurrentCombinations(String groupCode);

    /**
     * Các mã tổ hợp đang hoạt động của một kỳ thi, giữ nhánh và thứ tự môn.
     */
    List<CatalogCombinationView> listCombinations(UUID examItemId);

    Optional<CatalogItemView> findActiveItem(String groupCode, String itemCode);

    Optional<CatalogItemView> findItem(UUID id);

    List<UUID> findLeafIdsUnder(UUID nodeId);

    List<UUID> findLeafIds(String groupCode, Collection<UUID> refItemIds);
}
