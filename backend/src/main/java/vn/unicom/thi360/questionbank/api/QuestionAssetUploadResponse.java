package vn.unicom.thi360.questionbank.api;

import java.util.UUID;

/** Kết quả tải lên tài nguyên câu hỏi (spec 4.5): {@code url} là đường phục vụ của asset. */
public record QuestionAssetUploadResponse(UUID id, String url, String fileName, long size) {
}
