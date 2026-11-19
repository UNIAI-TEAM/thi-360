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
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Máy chủ SMTP tối thiểu cho test, trả lời theo kịch bản. GreenMail luôn nhận mọi người nhận nên không
 * giả lập được 5xx ở {@code RCPT TO} hay lỗi tạm; lớp này làm được hai việc đó, cộng thêm bắt AUTH,
 * treo ở {@code DATA} và đếm kết nối đã đóng. Kịch bản không bao giờ ghi mật khẩu ra đâu.
 */
final class ScriptedSmtpServer implements AutoCloseable {

    private final ServerSocket server;
    private volatile String rcptReply = "250 OK";
    private volatile String dataReply = "250 OK queued";
    private volatile boolean echoDataInReply;
    private volatile boolean silentOnQuit;
    private volatile boolean silentOnRset;
    private volatile boolean stallBeforeData;
    private final CountDownLatch releaseData = new CountDownLatch(1);
    private volatile String authUser;
    private volatile String authPassword;

    final AtomicInteger rcptCommands = new AtomicInteger();
    final AtomicInteger dataCommands = new AtomicInteger();
    /** Số lần máy chủ đã trả lời 250 sau khi nhận trọn thân thư ({@code DATA} được chấp nhận). */
    final AtomicInteger dataAccepted = new AtomicInteger();
    /** Số kết nối đã đóng (phía nào đóng cũng đếm). */
    final AtomicInteger connectionsClosed = new AtomicInteger();
    /** Số lệnh {@code RSET} đã nhận. */
    final AtomicInteger rsetCommands = new AtomicInteger();
    private final AtomicReference<String> lastData = new AtomicReference<>("");
    private final AtomicReference<String> authenticatedAs = new AtomicReference<>();

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

    /** Đọc {@code RSET} nhưng không trả lời: máy chủ đã từ chối người nhận, còn việc đặt lại không bao giờ xong. */
    ScriptedSmtpServer silentOnRset() {
        this.silentOnRset = true;
        return this;
    }

    /**
     * Nhận lệnh {@code DATA} nhưng không trả lời {@code 354}: thư chưa được chấp nhận. Gọi
     * {@link #releaseStalledData()} để máy chủ đi tiếp (lúc đó kết nối có thể đã chết).
     */
    ScriptedSmtpServer stallBeforeDataReply() {
        this.stallBeforeData = true;
        return this;
    }

    ScriptedSmtpServer releaseStalledData() {
        releaseData.countDown();
        return this;
    }

    /** Bắt {@code AUTH PLAIN}/{@code AUTH LOGIN} với đúng tài khoản này trước khi nhận {@code MAIL FROM}. */
    ScriptedSmtpServer requireAuth(String user, String password) {
        this.authUser = user;
        this.authPassword = password;
        return this;
    }

    /** Tài khoản đã xác thực trên kết nối gần nhất (không giữ mật khẩu). */
    String authenticatedAs() {
        return authenticatedAs.get();
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
            boolean authenticated = false;
            reply(out, "220 scripted ESMTP");
            String line;
            while ((line = in.readLine()) != null) {
                String command = line.toUpperCase(Locale.ROOT);
                if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                    if (authUser != null) {
                        reply(out, "250-scripted");
                        reply(out, "250 AUTH PLAIN LOGIN");
                    } else {
                        reply(out, "250 scripted");
                    }
                } else if (command.startsWith("AUTH")) {
                    authenticated = authenticate(command, line, in, out);
                } else if (command.startsWith("MAIL FROM")) {
                    if (authUser != null && !authenticated) {
                        reply(out, "530 5.7.0 Authentication required");
                    } else {
                        reply(out, "250 OK");
                    }
                } else if (command.startsWith("RCPT TO")) {
                    rcptCommands.incrementAndGet();
                    reply(out, rcptReply);
                } else if (command.equals("DATA")) {
                    dataCommands.incrementAndGet();
                    if (stallBeforeData) {
                        releaseData.await(60, TimeUnit.SECONDS);
                    }
                    reply(out, "354 go ahead");
                    StringBuilder data = new StringBuilder();
                    while ((line = in.readLine()) != null && !line.equals(".")) {
                        data.append(line).append('\n');
                    }
                    lastData.set(data.toString());
                    reply(out, echoDataInReply ? dataReply + " " + data.toString().replace('\n', ' ') : dataReply);
                    if (dataReply.startsWith("250")) {
                        dataAccepted.incrementAndGet();
                    }
                } else if (command.startsWith("QUIT")) {
                    if (silentOnQuit) {
                        continue; // giữ kết nối mở, chờ phía gửi tự đóng khi hết thời gian chờ
                    }
                    reply(out, "221 bye");
                    return;
                } else if (command.startsWith("RSET")) {
                    rsetCommands.incrementAndGet();
                    if (silentOnRset) {
                        continue; // đọc lệnh nhưng không trả lời: phía gửi phải không chờ câu trả lời này
                    }
                    reply(out, "250 OK");
                } else if (command.startsWith("NOOP")) {
                    reply(out, "250 OK");
                } else {
                    reply(out, "502 not implemented");
                }
            }
        } catch (IOException | InterruptedException ignored) {
            // Kết nối đóng giữa chừng hoặc bị ngắt khi chờ: đủ cho test.
        } finally {
            connectionsClosed.incrementAndGet();
        }
    }

    /**
     * {@code AUTH PLAIN <b64>} (hoặc trên dòng tiếp) và {@code AUTH LOGIN}: so khớp tài khoản, nhớ lại
     * chỉ tên người dùng, không ghi mật khẩu đâu cả.
     */
    private boolean authenticate(String command, String line, BufferedReader in, Writer out) throws IOException {
        String user;
        String password;
        if (command.startsWith("AUTH PLAIN")) {
            String encoded;
            if (line.length() > "AUTH PLAIN".length()) {
                encoded = line.substring("AUTH PLAIN".length()).strip();
            } else {
                reply(out, "334 ");
                encoded = in.readLine();
            }
            String[] parts = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8).split("\0", -1);
            if (parts.length != 3) {
                reply(out, "501 5.5.4 Malformed auth");
                return false;
            }
            user = parts[1];
            password = parts[2];
        } else if (command.startsWith("AUTH LOGIN")) {
            String encodedUser = line.length() > "AUTH LOGIN".length()
                    ? line.substring("AUTH LOGIN".length()).strip()
                    : null;
            if (encodedUser == null) {
                reply(out, "334 " + Base64.getEncoder().encodeToString("Username:".getBytes(StandardCharsets.UTF_8)));
                encodedUser = in.readLine();
            }
            reply(out, "334 " + Base64.getEncoder().encodeToString("Password:".getBytes(StandardCharsets.UTF_8)));
            String encodedPassword = in.readLine();
            user = new String(Base64.getDecoder().decode(encodedUser), StandardCharsets.UTF_8);
            password = new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
        } else {
            reply(out, "504 5.5.4 Unrecognized authentication type");
            return false;
        }
        if (user.equals(authUser) && password.equals(authPassword)) {
            authenticatedAs.set(user);
            reply(out, "235 2.7.0 Authentication successful");
            return true;
        }
        reply(out, "535 5.7.8 Authentication credentials invalid");
        return false;
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
