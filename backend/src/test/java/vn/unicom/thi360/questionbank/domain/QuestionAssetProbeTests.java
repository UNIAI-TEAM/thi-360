package vn.unicom.thi360.questionbank.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.unicom.thi360.questionbank.domain.QuestionAssetProbe.Dimensions;
import vn.unicom.thi360.questionbank.domain.QuestionAssetSignature.AttachmentSignature;

/**
 * Test đơn vị của dò chữ ký byte ({@link QuestionAssetSignature}) và đọc kích thước từ
 * header ({@link QuestionAssetProbe}) — spec 4.2 + quyết định lane 8.5/8.7.
 */
public class QuestionAssetProbeTests {

    private final QuestionAssetProbe probe = new QuestionAssetProbe();

    // ------------------------------------------------------------- chữ ký byte

    @ParameterizedTest
    @ValueSource(strings = {"PNG", "JPEG", "WEBP"})
    void imageSignaturesDetected(String type) throws IOException {
        byte[] bytes = imageBytes(type, 2, 3);
        assertThat(QuestionAssetSignature.detectImage(bytes)).isPresent();
        assertThat(QuestionAssetSignature.detectAttachment(bytes, "f.bin")).isPresent();
    }

    @Test
    void htmlDisguisedAsPngRejected() {
        byte[] html = "<html><body>not a png</body></html>".getBytes();
        assertThat(QuestionAssetSignature.detectImage(html)).isEmpty();
        assertThat(QuestionAssetSignature.detectAttachment(html, "fake.png")).isEmpty();
    }

    @Test
    void svgRejected() {
        byte[] svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
        assertThat(QuestionAssetSignature.detectImage(svg)).isEmpty();
        assertThat(QuestionAssetSignature.detectAttachment(svg, "icon.svg")).isEmpty();
    }

    @Test
    void attachmentSignaturesPerDecision85() {
        assertThat(QuestionAssetSignature.detectAttachment("%PDF-1.7 x".getBytes(), "a.pdf"))
                .contains(AttachmentSignature.PDF);
        assertThat(QuestionAssetSignature.detectAttachment("ID3\4\0\0\0\0\0".getBytes(), "a.mp3"))
                .contains(AttachmentSignature.MP3);
        assertThat(QuestionAssetSignature.detectAttachment(new byte[] {(byte) 0xFF, (byte) 0xFB, 0x10, 0x00}, "a.mp3"))
                .contains(AttachmentSignature.MP3);
        // ZIP chỉ nhận kèm đuôi .docx/.xlsx; mime theo đuôi, các đuôi khác bị từ chối.
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        assertThat(QuestionAssetSignature.detectAttachment(zip, "a.docx"))
                .contains(AttachmentSignature.DOCX);
        assertThat(QuestionAssetSignature.detectAttachment(zip, "a.xlsx"))
                .contains(AttachmentSignature.XLSX);
        assertThat(QuestionAssetSignature.detectAttachment(zip, "a.zip")).isEmpty();
        assertThat(QuestionAssetSignature.detectAttachment(zip, "a.bin")).isEmpty();
    }

    @Test
    void randomBytesRejected() {
        byte[] garbage = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        assertThat(QuestionAssetSignature.detectImage(garbage)).isEmpty();
        assertThat(QuestionAssetSignature.detectAttachment(garbage, "a.dat")).isEmpty();
    }

    // ------------------------------------------------------------- probe kích thước

    @ParameterizedTest
    @ValueSource(strings = {"PNG", "JPEG", "WEBP"})
    void dimensionsReadForAllImageKinds(String type) throws IOException {
        Optional<Dimensions> dims = probe.probe(imageBytes(type, 17, 9));
        assertThat(dims).contains(new Dimensions(17, 9));
    }

    @Test
    void oversizedDimensionsRejectedBeforeDecode() throws IOException {
        // File chỉ gồm signature + IHDR của PNG 10.000 x 10.000 — không có IDAT:
        // giải mã toàn bộ thất bại (trả null hoặc ném IIOException) nhưng probe vẫn
        // đọc được kích thước từ header.
        byte[] headerOnly = pngHeaderOnly(10_000, 10_000);
        boolean decodes;
        try {
            decodes = ImageIO.read(new java.io.ByteArrayInputStream(headerOnly)) != null;
        } catch (IOException | RuntimeException expected) {
            decodes = false;
        }
        assertThat(decodes).isFalse();
        Optional<Dimensions> dims = probe.probe(headerOnly);
        assertThat(dims).contains(new Dimensions(10_000, 10_000));
        assertThat(dims.get().exceeds(QuestionAssetProbe.MAX_SIDE_PX, QuestionAssetProbe.MAX_PIXELS)).isTrue();
    }

    @Test
    void probeRejectsUndecodableBytes() {
        assertThat(probe.probe("not an image".getBytes())).isEmpty();
        assertThat(probe.probe(new byte[] {})).isEmpty();
        // Chữ ký PNG hợp lệ nhưng header hỏng (IHDR cắt ngắn) → không đọc được kích thước.
        assertThat(probe.probe(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})).isEmpty();
    }

    @Test
    void exceedRuleCoversSideAndPixels() {
        assertThat(new Dimensions(8001, 10).exceeds(8000, 40_000_000L)).isTrue();
        assertThat(new Dimensions(8000, 5001).exceeds(8000, 40_000_000L)).isTrue(); // >40MP
        assertThat(new Dimensions(8000, 5000).exceeds(8000, 40_000_000L)).isFalse();
    }

    // ------------------------------------------------------------- dữ liệu mẫu

    /** Ảnh thật (encode đầy đủ) theo định dạng yêu cầu. */
    public static byte[] imageBytes(String format, int width, int height) throws IOException {
        if ("WEBP".equals(format)) {
            // imageio-webp chỉ đọc, không ghi: dựng tối thiểu RIFF + chunk VP8X
            // (10 byte payload) — đủ để probe đọc kích thước từ header.
            return webpVp8xHeader(width, height);
        }
        BufferedImage image = new BufferedImage(width, height,
                "JPEG".equals(format) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF336699);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "JPEG".equals(format) ? "jpeg" : "png", out)).isTrue();
        return out.toByteArray();
    }

    /** WebP VP8X header-only (extended): đủ đọc kích thước, không chứa dữ liệu ảnh con. */
    public static byte[] webpVp8xHeader(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes());
        writeLE32(out, 22); // "WEBP" + chunk header + payload
        out.write("WEBP".getBytes());
        out.write("VP8X".getBytes());
        writeLE32(out, 10);
        out.write(0); // flags
        out.write(new byte[] {0, 0, 0}); // reserved
        writeLE24(out, width - 1);
        writeLE24(out, height - 1);
        return out.toByteArray();
    }

    private static void writeLE32(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) {
            out.write((value >>> (8 * i)) & 0xFF);
        }
    }

    private static void writeLE24(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 3; i++) {
            out.write((value >>> (8 * i)) & 0xFF);
        }
    }

    /**
     * PNG header-only: signature + chunk IHDR (13 byte) + CRC, không có IDAT.
     * Dùng kiểm việc đọc kích thước không cần giải mã toàn bộ (spec 4.2).
     */
    public static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, width);
        writeInt(ihdr, height);
        ihdr.write(new byte[] {8, 6, 0, 0, 0}); // 8-bit RGBA, deflate, adaptive, no interlace
        byte[] data = ihdr.toByteArray();
        writeInt(out, data.length);
        out.write("IHDR".getBytes());
        out.write(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update("IHDR".getBytes());
        crc.update(data);
        writeInt(out, (int) crc.getValue());
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
