package vn.unicom.thi360.identity.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.application.AvatarRejectedException.Reason;

/**
 * Kiểm và chuẩn hóa ảnh đại diện (TM E20): nhận {@code jpg}, {@code png}, {@code webp} tối đa 5 MB và
 * 4.096 × 4.096 px, cắt vuông giữa ảnh rồi thu về PNG 256 × 256 (ImageIO không ghi WebP nếu không thêm thư viện, plan B7).
 *
 * <p>Thứ tự kiểm: cỡ tệp (413), chữ ký byte (không tin đuôi tệp hay Content-Type), kích thước đọc từ <b>header</b>
 * trước khi giải mã toàn ảnh (ảnh khổng lồ không bao giờ được giải mã), rồi mới giải mã và thu nhỏ.
 */
@Component
public class AvatarImageProcessor {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_SIDE_PX = 4096;
    public static final int OUTPUT_SIDE_PX = 256;
    public static final String OUTPUT_CONTENT_TYPE = "image/png";

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** Kích thước đọc từ header ảnh. */
    record Dimensions(int width, int height) {
    }

    /**
     * @return PNG 256 × 256
     * @throws AvatarRejectedException khi quá 5 MB, không phải ảnh nhận, vượt 4.096 px hoặc không giải mã được
     */
    public byte[] process(byte[] content) {
        if (content == null || content.length == 0) {
            throw rejected(Reason.INVALID_IMAGE, "empty file");
        }
        if (content.length > MAX_BYTES) {
            throw rejected(Reason.TOO_LARGE, "file exceeds " + MAX_BYTES + " bytes");
        }
        if (!hasAcceptedSignature(content)) {
            throw rejected(Reason.INVALID_IMAGE, "unrecognised image signature");
        }
        Dimensions dimensions = probe(content)
                .orElseThrow(() -> rejected(Reason.INVALID_IMAGE, "image header cannot be read"));
        if (dimensions.width() <= 0 || dimensions.height() <= 0) {
            throw rejected(Reason.INVALID_IMAGE, "image has no pixels");
        }
        if (dimensions.width() > MAX_SIDE_PX || dimensions.height() > MAX_SIDE_PX) {
            throw rejected(Reason.DIMENSIONS_EXCEEDED, "image side exceeds " + MAX_SIDE_PX + " px");
        }
        return render(content, dimensions);
    }

    /**
     * Đọc (rộng, cao) từ header mà không giải mã điểm ảnh. Reader lấy qua {@link ImageIO}; WebP cần
     * {@code com.twelvemonkeys.imageio:imageio-webp}, đã có sẵn trong {@code pom.xml}. Bộ nhớ đệm trong RAM, không
     * tạo tệp tạm.
     */
    Optional<Dimensions> probe(byte[] content) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return Optional.of(new Dimensions(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    private byte[] render(byte[] content, Dimensions dimensions) {
        int side = Math.min(dimensions.width(), dimensions.height());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Thumbnails.of(new ByteArrayInputStream(content))
                    .sourceRegion(Positions.CENTER, side, side)
                    .forceSize(OUTPUT_SIDE_PX, OUTPUT_SIDE_PX)
                    .outputFormat("png")
                    .toOutputStream(out);
        } catch (IOException | RuntimeException ex) {
            AvatarRejectedException rejected = rejected(Reason.INVALID_IMAGE, "image cannot be decoded");
            rejected.initCause(ex);
            throw rejected;
        }
        return out.toByteArray();
    }

    private static boolean hasAcceptedSignature(byte[] head) {
        if (startsWith(head, PNG_MAGIC)) {
            return true;
        }
        if (head.length >= 3 && head[0] == (byte) 0xFF && head[1] == (byte) 0xD8 && head[2] == (byte) 0xFF) {
            return true;
        }
        // WebP: "RIFF" <4 byte cỡ> "WEBP"
        return head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static AvatarRejectedException rejected(Reason reason, String detail) {
        return new AvatarRejectedException(reason, detail);
    }
}
