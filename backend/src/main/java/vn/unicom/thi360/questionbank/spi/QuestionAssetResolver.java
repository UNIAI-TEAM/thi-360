package vn.unicom.thi360.questionbank.spi;

import java.util.Optional;
import java.util.UUID;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;

/**
 * Tra cứu tài nguyên câu hỏi (ảnh, tệp đính kèm) theo id: trả đường phục vụ
 * ({@code /api/v1/admin/question-assets/{id}}) hoặc rỗng khi id không có hoặc sai loại.
 *
 * <p>Bản thật do BE-2b (UNI-2614) cài đặt trên bảng {@code question_asset}; loại tài nguyên
 * nghiêm ngặt (quyết định lane 8.6): {@link #servingPath(UUID)} chỉ tra {@code IMAGE} (cho
 * {@code <img data-asset-id>}), {@link #servingPath(UUID, QuestionAssetKind)} cho phép chọn
 * loại ({@code attachmentIds} bắt {@code ATTACHMENT}). Id tồn tại nhưng sai loại trả rỗng
 * như id không có — cùng lỗi 422. Bản mặc định {@link #none()} trả rỗng cho mọi id.
 */
@FunctionalInterface
public interface QuestionAssetResolver {

    /** @return đường phục vụ; {@code Optional.empty()} khi id không có hoặc không phải {@code IMAGE}. */
    Optional<String> servingPath(UUID assetId);

    /**
     * Tra theo id và loại bắt buộc. Mặc định: {@code IMAGE} uỷ về {@link #servingPath(UUID)},
     * loại khác trả rỗng — để resolver giả lập trong các test cũ giữ ngữ nghĩa "chỉ ảnh".
     */
    default Optional<String> servingPath(UUID assetId, QuestionAssetKind kind) {
        return kind == QuestionAssetKind.IMAGE ? servingPath(assetId) : Optional.empty();
    }

    /** Resolver mặc định của BE-2a: mọi id đều không tồn tại. */
    static QuestionAssetResolver none() {
        return assetId -> Optional.empty();
    }
}
