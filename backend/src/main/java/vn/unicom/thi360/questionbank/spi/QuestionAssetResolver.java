package vn.unicom.thi360.questionbank.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Tra cứu tài nguyên câu hỏi (ảnh, tệp đính kèm) theo id: trả đường phục vụ
 * ({@code /api/v1/admin/question-assets/{id}}) hoặc rỗng khi id không có.
 *
 * <p>Bản thật do BE-2b (UNI-2614) cài đặt trên bảng {@code question_asset}. Bản mặc định trả
 * rỗng, nên nội dung có {@code img} hay {@code attachmentIds} bị kiểm tra từ chối (422
 * {@code QUESTION_CONTENT_UNSAFE}) tới khi BE-2b merge (ranh giới theo plan D/BE-2a).
 */
@FunctionalInterface
public interface QuestionAssetResolver {

    /** @return đường phục vụ của tài nguyên; {@code Optional.empty()} khi id không tồn tại. */
    Optional<String> servingPath(UUID assetId);

    /** Resolver mặc định của BE-2a: mọi id đều không tồn tại. */
    static QuestionAssetResolver none() {
        return assetId -> Optional.empty();
    }
}
