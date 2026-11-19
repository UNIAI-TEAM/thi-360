package vn.unicom.thi360.exam.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Recognises a cover image by its signature bytes (never by the extension or Content-Type) and reads its size. */
final class ExamCoverImages {
    private ExamCoverImages() { }

    record Probe(String contentType, int width, int height) { }

    /** Content type of a jpg, png or webp by signature; empty for anything else. */
    static Optional<String> contentType(byte[] head) {
        if (head == null) return Optional.empty();
        if (head.length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A) {
            return Optional.of("image/png");
        }
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    /** Signature and size; empty when the bytes are not a readable jpg, png or webp. Reads the header only. */
    static Optional<Probe> probe(byte[] content) {
        Optional<String> type = contentType(content);
        if (type.isEmpty()) return Optional.empty();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return Optional.empty();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return Optional.of(new Probe(type.get(), reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
