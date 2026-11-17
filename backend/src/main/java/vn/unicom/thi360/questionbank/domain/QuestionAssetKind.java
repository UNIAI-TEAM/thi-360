package vn.unicom.thi360.questionbank.domain;

/**
 * Loại tài nguyên câu hỏi (spec 4.2): {@code IMAGE} dùng trong {@code <img data-asset-id>},
 * {@code ATTACHMENT} dùng qua {@code attachmentIds}. Hai loại không đổi chỗ cho nhau
 * (quyết định lane 8.6).
 */
public enum QuestionAssetKind {
    IMAGE,
    ATTACHMENT
}
