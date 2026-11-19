package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Máy chủ kịch bản chỉ "chấp nhận" một thư khi đã đọc trọn thân thư tới dấu kết thúc (R2-03). Các test hủy
 * dựa vào bộ đếm {@code dataAccepted}, nên bộ đếm sai làm test hủy không chứng minh được gì.
 */
class ScriptedSmtpServerTests {

    private static void command(Writer out, String line) throws Exception {
        out.write(line + "\r\n");
        out.flush();
    }

    private static BufferedReader openAndReachData(Socket socket, Writer[] outHolder) throws Exception {
        socket.setSoTimeout(5000);
        BufferedReader in =
                new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1);
        outHolder[0] = out;
        assertThat(in.readLine()).startsWith("220");
        command(out, "EHLO probe.test");
        assertThat(in.readLine()).startsWith("250");
        command(out, "MAIL FROM:<sender@thi360.test>");
        assertThat(in.readLine()).startsWith("250");
        command(out, "RCPT TO:<rcpt@thi360.test>");
        assertThat(in.readLine()).startsWith("250");
        command(out, "DATA");
        assertThat(in.readLine()).startsWith("354");
        return in;
    }

    /**
     * Phía gửi đóng chiều ghi giữa thân thư (EOF trước dấu {@code .}) nhưng vẫn đọc được: máy chủ không được trả
     * lời {@code 250} và không được tính là đã nhận.
     */
    @Test
    void aBodyCutBeforeTheTerminatorIsNeverCountedAsAccepted() throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer();
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            Writer[] out = new Writer[1];
            BufferedReader in = openAndReachData(socket, out);
            out[0].write("Subject: cut\r\n\r\nhalf a body\r\n");
            out[0].flush();
            socket.shutdownOutput();

            assertThat(in.readLine()).as("no reply to a body that never ended").isNull();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(server.connectionsClosed.get()).isEqualTo(1));
            assertThat(server.dataAccepted.get()).as("EOF before the terminator is not an accepted DATA").isZero();
        }
    }

    /** Đối chứng: thân thư kết thúc bằng dấu {@code .} thì được nhận đúng một lần. */
    @Test
    void aBodyWithItsTerminatorIsCountedOnce() throws Exception {
        try (ScriptedSmtpServer server = new ScriptedSmtpServer();
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            Writer[] out = new Writer[1];
            BufferedReader in = openAndReachData(socket, out);
            out[0].write("Subject: whole\r\n\r\nthe whole body\r\n.\r\n");
            out[0].flush();

            assertThat(in.readLine()).startsWith("250");
            assertThat(server.dataAccepted.get()).isEqualTo(1);
        }
    }
}
