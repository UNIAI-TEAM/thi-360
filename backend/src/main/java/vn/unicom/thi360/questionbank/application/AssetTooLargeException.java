package vn.unicom.thi360.questionbank.application;

/**
 * Tệp tải lên vượt cỡ theo loại của endpoint {@code POST /question-assets}
 * (ảnh 5 MB, đính kèm 20 MB — spec 4.2, plan B5). Tầng web trả 413
 * {@code ASSET_TOO_LARGE}; khác với {@code MaxUploadSizeExceededException} của
 * tầng multipart (giới hạn chung 21/22 MB) và với lỗi pixel (422).
 */
public class AssetTooLargeException extends RuntimeException {

    public static final String CODE = "ASSET_TOO_LARGE";

    public AssetTooLargeException(String message) {
        super(message);
    }
}
