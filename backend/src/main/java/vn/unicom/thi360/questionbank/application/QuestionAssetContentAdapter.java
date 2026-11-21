package vn.unicom.thi360.questionbank.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.questionbank.domain.QuestionAsset;
import vn.unicom.thi360.questionbank.domain.QuestionAssetKind;
import vn.unicom.thi360.questionbank.infrastructure.QuestionAssetRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/**
 * Loads question asset metadata from DB and bytes from {@link ObjectStorage} without holding a DB
 * transaction across storage I/O (plan B2, AT-3).
 */
@Component
public class QuestionAssetContentAdapter implements QuestionAssetContentPort {

    private final QuestionAssetRepository assetRepository;
    private final ObjectStorage objectStorage;

    public QuestionAssetContentAdapter(
            QuestionAssetRepository assetRepository, ObjectStorage objectStorage) {
        this.assetRepository = Objects.requireNonNull(assetRepository);
        this.objectStorage = Objects.requireNonNull(objectStorage);
    }

    @Override
    public Optional<AssetContent> loadContent(UUID assetId) {
        if (assetId == null) {
            return Optional.empty();
        }
        Optional<QuestionAsset> metadata = assetRepository.findById(assetId);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        QuestionAsset asset = metadata.get();
        return objectStorage
                .get(asset.getObjectKey())
                .map(
                        bytes ->
                                new AssetContent(
                                        asset.getId(),
                                        mapKind(asset.getKind()),
                                        asset.getFileName(),
                                        asset.getMime(),
                                        bytes));
    }

    private static AssetKind mapKind(QuestionAssetKind kind) {
        return switch (kind) {
            case IMAGE -> AssetKind.IMAGE;
            case ATTACHMENT -> AssetKind.ATTACHMENT;
        };
    }
}
