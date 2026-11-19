package vn.unicom.thi360.shared.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Máy chủ SMTP tối thiểu cho test, trả lời theo kịch bản. GreenMail luôn nhận mọi người nhận nên không
 * giả lập được 5xx ở {@code RCPT TO} hay lỗi tạm; lớp này làm được hai việc đó và đếm số lệnh nhận.
 */
final class ScriptedSmtpServer implements AutoCloseable {

    private final ServerSocket server;
    private volatile String rcptReply = "250 OK";
    private volatile String dataReply = "250 OK queued";
    private volatile boolean echoDataInReply;
    private volatile boolean silentOnQuit;

    final AtomicInteger rcptCommands = new AtomicInteger();
    final AtomicInteger dataCommands = new AtomicInteger();
    private final AtomicReference<String> lastData = new AtomicReference<>("");

    ScriptedSmtpServer() throws IOException {
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "scripted-smtp-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return server.getLocalPort();
    }

    /** Trả lời {@code RCPT TO} bằng {@code reply}, ví dụ {@code 550 5.1.1 User unknown}. */
    ScriptedSmtpServer rejectRecipientWith(String reply) {
        this.rcptReply = reply;
        return this;
    }

    /** Trả lời sau {@code DATA} bằng {@code reply}. */
    ScriptedSmtpServer answerDataWith(String reply) {
        this.dataReply = reply;
        return this;
    }

    /** Trả lời sau {@code DATA} bằng {@code <prefix> <toàn bộ thư nhận được>}, như máy chủ lặp lại nội dung. */
    ScriptedSmtpServer echoDataAfter(String prefix) {
        this.dataReply = prefix;
        this.echoDataInReply = true;
        return this;
    }

    /** Không trả lời {@code QUIT}: máy chủ đã nhận thư nhưng việc đóng kết nối không bao giờ xong. */
    ScriptedSmtpServer silentOnQuit() {
        this.silentOnQuit = true;
        return this;
    }

    String lastData() {
        return lastData.get();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread handler = new Thread(() -> handle(socket), "scripted-smtp-conn");
                handler.setDaemon(true);
                handler.start();
            } catch (IOException ex) {
                return;
            }
        }
    }

    private void handle(Socket socket) {
        try (socket;
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1)) {
            reply(out, "220 scripted ESMTP");
            String line;
            while ((line = in.readLine()) != null) {
                String command = line.toUpperCase(Locale.ROOT);
                if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                    reply(out, "250 scripted");
                } else if (command.startsWith("MAIL FROM")) {
                    reply(out, "250 OK");
                } else if (command.startsWith("RCPT TO")) {
                    rcptCommands.incrementAndGet();
                    reply(out, rcptReply);
                } else if (command.equals("DATA")) {
                    dataCommands.incrementAndGet();
                    reply(out, "354 go ahead");
                    StringBuilder data = new StringBuilder();
                    while ((line = in.readLine()) != null && !line.equals(".")) {
                        data.append(line).append('\n');
                    }
                    lastData.set(data.toString());
                    reply(out, echoDataInReply ? dataReply + " " + data.toString().replace('\n', ' ') : dataReply);
                } else if (command.startsWith("QUIT")) {
                    if (silentOnQuit) {
                        continue; // giữ kết nối mở, chờ phía gửi tự đóng khi hết thời gian chờ
                    }
                    reply(out, "221 bye");
                    return;
                } else if (command.startsWith("RSET") || command.startsWith("NOOP")) {
                    reply(out, "250 OK");
                } else {
                    reply(out, "502 not implemented");
                }
            }
        } catch (IOException ignored) {
            // Kết nối đóng giữa chừng: đủ cho test.
        }
    }

    private static void reply(Writer out, String text) throws IOException {
        out.write(text.replace("\r", "").replace("\n", " ") + "\r\n");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
