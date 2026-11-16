package vn.unicom.thi360.questionbank.domain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Đọc kích thước ảnh từ header mà không giải mã toàn bộ (spec 4.2). Reader lấy qua
 * {@link ImageIO}; WebP cần {@code com.twelvemonkeys.imageio:imageio-webp} (plan B6).
 * Trả {@link Optional#empty()} khi không reader nào nhận hoặc header không đọc được —
 * lời gọi coi là tệp không hợp lệ, không giải mã tiếp.
 */
public class QuestionAssetProbe {

    /** Kích thước đọc từ header. */
    public record Dimensions(int width, int height) {

        /** Vượt {@code 8000 px} một cạnh hoặc {@code 40 MP} → 422 {@code ASSET_TOO_LARGE}. */
        public boolean exceeds(int maxSide, long maxPixels) {
            return width > maxSide || height > maxSide || (long) width * (long) height > maxPixels;
        }
    }

    public static final int MAX_SIDE_PX = 8000;
    public static final long MAX_PIXELS = 40_000_000L;

    /** Đọc (width, height) từ header; {@code empty()} khi ảnh không nhận diện được. */
    public Optional<Dimensions> probe(byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
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
}
