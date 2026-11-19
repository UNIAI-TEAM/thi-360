package vn.unicom.thi360.identity;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Ảnh sinh trong test bằng {@link BufferedImage} (plan ID-2, dữ liệu test); không có tệp ảnh nào nằm trong repo. */
public final class AvatarTestImages {

    /** WebP 1 × 1 điểm ảnh (nén không mất dữ liệu) dạng base64, dùng cho ca nhận WebP. */
    private static final String TINY_WEBP_BASE64 = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

    private AvatarTestImages() {
    }

    /** PNG thật {@code width × height}, ảnh xám một màu (nhỏ khi nén, ít bộ nhớ khi giải mã). */
    public static byte[] png(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY), "png");
    }

    /** JPEG thật {@code width × height}. */
    public static byte[] jpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, (x * 255 / Math.max(1, width)) << 16 | (y * 255 / Math.max(1, height)) << 8);
            }
        }
        return encode(image, "jpg");
    }

    public static byte[] webp() {
        return Base64.getDecoder().decode(TINY_WEBP_BASE64);
    }

    /**
     * Chỉ có chữ ký PNG và khối IHDR khai {@code width × height}, không có dữ liệu điểm ảnh. Giải mã đầy đủ sẽ lỗi,
     * nên bị từ chối vì kích thước (thay vì "không phải ảnh") chứng tỏ máy chủ chỉ đọc header.
     */
    public static byte[] pngHeaderOnly(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = ByteBuffer.allocate(13)
                .putInt(width)
                .putInt(height)
                .put((byte) 8)  // bit depth
                .put((byte) 0)  // grayscale
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0)
                .array();
        writeChunk(out, "IHDR", ihdr);
        return out.toByteArray();
    }

    /** Thêm byte 0 sau dữ liệu ảnh để đủ {@code totalBytes}; bộ đọc PNG bỏ qua phần sau khối IEND. */
    public static byte[] padded(byte[] image, int totalBytes) {
        if (image.length > totalBytes) {
            throw new IllegalArgumentException("image is larger than " + totalBytes);
        }
        return Arrays.copyOf(image, totalBytes);
    }

    public static byte[] text() {
        return "đây không phải ảnh, chỉ là văn bản đặt tên avatar.jpg".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No writer for " + format);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
