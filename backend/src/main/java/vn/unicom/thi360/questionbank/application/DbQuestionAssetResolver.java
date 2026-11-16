package vn.unicom.thi360.questionbank.application;

import java.util.Optional;
import java.util.UUID;

import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAssetResolver;

/**
 * {@link QuestionAssetResolver} thật của BE-2b (UNI-2614): tra {@code question_asset} theo id
 * và bắt buộc đúng {@code kind} (quyết định lane 8.6) — {@code IMAGE} chỉ phục vụ qua
 * {@code <img data-asset-id>}, {@code ATTACHMENT} chỉ qua {@code attachmentIds}; sai loại trả
 * rỗng như id không có (cùng lỗi 422).
 */
public class DbQuestionAssetResolver implements QuestionAssetResolver {

    /** Đường phục vụ qua proxy admin (spec 4.2); object key không bao giờ lộ ra ngoài. */
    public static final String SERVING_PREFIX = "/api/v1/admin/question-assets/";

    private final QuestionAssetRepository assetRepository;

    public DbQuestionAssetResolver(QuestionAssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public Optional<String> servingPath(UUID assetId) {
        return servingPath(assetId, QuestionAssetKind.IMAGE);
    }

    @Override
    public Optional<String> servingPath(UUID assetId, QuestionAssetKind kind) {
        if (assetId == null || kind == null) {
            return Optional.empty();
        }
        return assetRepository.findById(assetId)
                .filter(asset -> asset.getKind() == kind)
                .map(QuestionAsset::getId)
                .map(id -> SERVING_PREFIX + id);
    }
}
