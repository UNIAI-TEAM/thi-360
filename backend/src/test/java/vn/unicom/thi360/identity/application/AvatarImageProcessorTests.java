package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.identity.AvatarTestImages;
import vn.unicom.thi360.identity.application.AvatarRejectedException.Reason;

/**
 * AC-31 (plan ID-2): ảnh quá lớn bị từ chối chỉ từ thông tin kích thước ở header, và biên 4.096 / 4.097 px; cộng các
 * ca xử lý của {@link AvatarImageProcessor} (cắt vuông, 256 px, PNG, cỡ tệp, chữ ký).
 */
class AvatarImageProcessorTests {

    private final AvatarImageProcessor processor = new AvatarImageProcessor();

    private static BufferedImage decode(byte[] png) throws IOException {
        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static Reason reasonOf(Throwable thrown) {
        assertThat(thrown).isInstanceOf(AvatarRejectedException.class);
        return ((AvatarRejectedException) thrown).reason();
    }

    @Test
    void rejectsFromHeaderWithoutDecoding() {
        // Tệp chỉ có IHDR 8.000 × 8.000: giải mã sẽ lỗi "không phải ảnh". Báo kích thước vượt (không phải ảnh lỗi)
        // chứng tỏ chỉ đọc header; một lần giải mã 8.000 × 8.000 còn cần ~64 MB.
        byte[] header = AvatarTestImages.pngHeaderOnly(8000, 8000);
        assertThatThrownBy(() -> processor.process(header))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.DIMENSIONS_EXCEEDED));
        // Chính tệp đó không có điểm ảnh: nếu đi qua kiểm kích thước thì giải mã phải lỗi.
        byte[] small = AvatarTestImages.pngHeaderOnly(64, 64);
        assertThatThrownBy(() -> processor.process(small))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.INVALID_IMAGE));
    }

    @Test
    void dimensionBoundary4096And4097() throws IOException {
        byte[] atLimit = AvatarTestImages.png(4096, 4096);
        BufferedImage out = decode(processor.process(atLimit));
        assertThat(out.getWidth()).isEqualTo(256);
        assertThat(out.getHeight()).isEqualTo(256);

        for (byte[] over : new byte[][] {
            AvatarTestImages.pngHeaderOnly(4097, 4096),
            AvatarTestImages.pngHeaderOnly(4096, 4097),
            AvatarTestImages.pngHeaderOnly(4097, 4097)}) {
            assertThatThrownBy(() -> processor.process(over))
                    .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.DIMENSIONS_EXCEEDED));
        }
    }

    @Test
    void cropsSquareAndOutputs256PngForEveryAcceptedFormat() throws IOException {
        for (byte[] source : new byte[][] {
            AvatarTestImages.png(300, 200),
            AvatarTestImages.png(200, 300),
            AvatarTestImages.png(1000, 1000),
            AvatarTestImages.jpeg(640, 360),
            AvatarTestImages.png(10, 10),
            AvatarTestImages.webp()}) {
            BufferedImage out = decode(processor.process(source));
            assertThat(out.getWidth()).isEqualTo(256);
            assertThat(out.getHeight()).isEqualTo(256);
        }
    }

    @Test
    void sizeBoundaryExactly5MbAndOneByteMore() throws IOException {
        byte[] png = AvatarTestImages.png(64, 64);
        int fiveMb = 5 * 1024 * 1024;

        assertThat(decode(processor.process(AvatarTestImages.padded(png, fiveMb))).getWidth()).isEqualTo(256);
        assertThatThrownBy(() -> processor.process(AvatarTestImages.padded(png, fiveMb + 1)))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.TOO_LARGE));
        // Cỡ được kiểm trước chữ ký: tệp rác quá 5 MB là 413, không phải 422.
        assertThatThrownBy(() -> processor.process(new byte[fiveMb + 1]))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.TOO_LARGE));
    }

    @Test
    void refusesNonImagesAndBrokenImages() {
        assertThatThrownBy(() -> processor.process(AvatarTestImages.text()))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.INVALID_IMAGE));
        assertThatThrownBy(() -> processor.process(new byte[0]))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.INVALID_IMAGE));
        assertThatThrownBy(() -> processor.process(null))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.INVALID_IMAGE));
        // Chữ ký JPEG nhưng nội dung hỏng.
        byte[] fakeJpeg = new byte[64];
        fakeJpeg[0] = (byte) 0xFF;
        fakeJpeg[1] = (byte) 0xD8;
        fakeJpeg[2] = (byte) 0xFF;
        assertThatThrownBy(() -> processor.process(fakeJpeg))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.INVALID_IMAGE));
        // GIF không nằm trong danh sách nhận.
        byte[] gif = "GIF89a\u0001\u0000\u0001\u0000".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> processor.process(gif))
                .satisfies(ex -> assertThat(reasonOf(ex)).isEqualTo(Reason.INVALID_IMAGE));
    }
}
