package vn.unicom.thi360.questionbank.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Dò loại tệp từ chữ ký byte đầu (spec 4.2): không tin {@code Content-Type} của client, không
 * mở nội dung tệp đính kèm. Ảnh nhận PNG, JPEG, WebP (D17 — không SVG). Tệp đính kèm nhận
 * PDF, PNG, JPEG, WebP, MP3, và DOCX/XLSX qua chữ ký ZIP kèm đuôi tệp đúng (quyết định
 * lane 8.5: {@code PK\x03\x04} chỉ nhận khi đuôi là {@code .docx} hoặc {@code .xlsx},
 * mime lưu theo đuôi; MP3 = {@code ID3} hoặc frame sync {@code 0xFFEx}).
 */
public final class QuestionAssetSignature {

    /** Ảnh cho phép và mime lưu trữ. */
    public enum ImageSignature {
        PNG("image/png"),
        JPEG("image/jpeg"),
        WEBP("image/webp");

        private final String mime;

        ImageSignature(String mime) {
            this.mime = mime;
        }

        public String mime() {
            return mime;
        }
    }

    /** Tệp đính kèm cho phép và mime lưu trữ (DOCX/XLSX quyết theo đuôi tệp). */
    public enum AttachmentSignature {
        PDF("application/pdf"),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PNG("image/png"),
        JPEG("image/jpeg"),
        WEBP("image/webp"),
        MP3("audio/mpeg");

        private final String mime;

        AttachmentSignature(String mime) {
            this.mime = mime;
        }

        public String mime() {
            return mime;
        }
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private QuestionAssetSignature() {
    }

    /** Chữ ký ảnh: PNG / JPEG (FF D8 FF) / WebP (RIFF....WEBP). Rỗng = không phải ảnh nhận. */
    public static Optional<ImageSignature> detectImage(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (startsWith(head, PNG_MAGIC)) {
            return Optional.of(ImageSignature.PNG);
        }
        if (head.length >= 3
                && head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF) {
            return Optional.of(ImageSignature.JPEG);
        }
        if (isWebp(head)) {
            return Optional.of(ImageSignature.WEBP);
        }
        return Optional.empty();
    }

    /**
     * Chữ ký tệp đính kèm: PDF, ảnh (PNG/JPEG/WebP), MP3 (ID3 hoặc frame sync 0xFFEx), và
     * {@code PK\x03\x04} chỉ khi {@code fileName} có đuôi {@code .docx}/{@code .xlsx}.
     * Rỗng = loại không nhận (422 {@code ASSET_TYPE_NOT_ALLOWED}).
     */
    public static Optional<AttachmentSignature> detectAttachment(byte[] head, String fileName) {
        if (head == null) {
            return Optional.empty();
        }
        Optional<ImageSignature> image = detectImage(head);
        if (image.isPresent()) {
            return Optional.of(AttachmentSignature.valueOf(image.get().name()));
        }
        if (startsWith(head, PDF_MAGIC)) {
            return Optional.of(AttachmentSignature.PDF);
        }
        if (isMp3(head)) {
            return Optional.of(AttachmentSignature.MP3);
        }
        if (startsWith(head, ZIP_MAGIC)) {
            String ext = extension(fileName);
            if ("docx".equals(ext)) {
                return Optional.of(AttachmentSignature.DOCX);
            }
            if ("xlsx".equals(ext)) {
                return Optional.of(AttachmentSignature.XLSX);
            }
        }
        return Optional.empty();
    }

    private static boolean isWebp(byte[] head) {
        return head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }

    private static boolean isMp3(byte[] head) {
        if (head.length >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
            return true;
        }
        // MPEG frame sync: 11 bit đầu của hai byte đầu tiên đều là 1.
        return head.length >= 2 && head[0] == (byte) 0xFF && (head[1] & 0xE0) == 0xE0;
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
