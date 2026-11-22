package vn.unicom.thi360.questionbank.spi;

import java.util.Optional;
import java.util.UUID;

/** Read-only SPI port exposing stored question asset bytes without leaking domain entities (AT-3). */
public interface QuestionAssetContentPort {

    enum AssetKind {
        IMAGE,
        ATTACHMENT
    }

    record AssetContent(UUID id, AssetKind kind, String fileName, String mime, byte[] content) {
    }

    Optional<AssetContent> loadContent(UUID assetId);
}
