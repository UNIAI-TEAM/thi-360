package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.PayRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.QueryDrResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundRequest;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.RefundResult;
import vn.unicom.thi360.commerce.payment.gateway.VnpayProperties;
import vn.unicom.thi360.commerce.payment.gateway.VnpaySigner;

/**
 * AC-2, AC-4, AC-24: Kiểm thử tích hợp HTTP của {@link RealVnpayGateway} với JDK {@link HttpServer}
 * cục bộ: định dạng {@code querydr} / {@code refund}, kiểm chữ ký phản hồi, xử lý lỗi truyền tải
 * và đảm bảo bí mật không xuất hiện trong log, {@code rawResponse} hay thông báo lỗi.
 */
class VnpayHttpGatewayIT {

    private static final String TEST_SECRET = "SUPER_SECRET_SENTINEL_AC24_987654";
    private static final String TEST_TMN = "TEST-TMN-IT";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-11-21T03:30:00Z");

    private HttpServer server;
    private VnpayRealSigner signer;
    private RealVnpayGateway gateway;
    private AtomicReference<Function<CapturedRequest, MockResponse>> handlerRef;

    private record CapturedRequest(String method, String contentType, Map<String, Object> body) {
    }

    private record MockResponse(int status, String contentType, String body, long delayMs) {
        static MockResponse json(int status, Map<String, Object> payload) {
            try {
                return new MockResponse(status, "application/json", MAPPER.writeValueAsString(payload), 0);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        handlerRef = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/merchant_webapi/api/transaction", exchange -> {
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            String reqStr = new String(reqBytes, StandardCharsets.UTF_8);
            Map<String, Object> parsedBody = MAPPER.readValue(reqStr, new TypeReference<>() {
            });
            CapturedRequest captured = new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    parsedBody);
            MockResponse resp = handlerRef.get().apply(captured);
            if (resp.delayMs() > 0) {
                try {
                    Thread.sleep(resp.delayMs());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = resp.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", resp.contentType());
            exchange.sendResponseHeaders(resp.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        String apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/merchant_webapi/api/transaction";
        signer = new VnpayRealSigner(new VnpaySigningProperties(null, null, null, null));
        gateway = createGateway(apiUrl, Duration.ofMillis(400));
    }

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.destroy();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void queryDrSendsSignedPostJsonWithDistinct32CharRequestIdsAndTransactionDate() {
        List<CapturedRequest> captured = new ArrayList<>();
        handlerRef.set(req -> {
            captured.add(req);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("vnp_ResponseId", "resp" + captured.size());
            resp.put("vnp_Command", "querydr");
            resp.put("vnp_ResponseCode", "00");
            resp.put("vnp_Message", "QueryDr Success");
            resp.put("vnp_TmnCode", TEST_TMN);
            resp.put("vnp_TxnRef", "DH261121QDR01");
            resp.put("vnp_Amount", "25000000");
            resp.put("vnp_BankCode", "NCB");
            resp.put("vnp_PayDate", "20261121100230");
            resp.put("vnp_TransactionNo", "90001234");
            resp.put("vnp_TransactionType", "01");
            resp.put("vnp_TransactionStatus", "00");
            resp.put("vnp_OrderInfo", "Thanh toan don DH261121QDR");
            resp.put("vnp_PromotionCode", "");
            resp.put("vnp_PromotionAmount", "");
            resp.put("vnp_SecureHash", signQueryDrResponse(resp));
            return MockResponse.json(200, resp);
        });

        QueryDrResult r1 = gateway.queryDr(new QueryDrRequest(
                null, "DH261121QDR01", "20261121100000", null, null, null, "90001234"));
        QueryDrResult r2 = gateway.queryDr(new QueryDrRequest(
                null, "DH261121QDR01", "20261121100000", null, null, "Check 2", null));

        assertThat(captured).hasSize(2);
        for (CapturedRequest req : captured) {
            assertThat(req.method()).isEqualTo("POST");
            assertThat(req.contentType()).isEqualTo("application/json");
            Map<String, Object> body = req.body();
            assertThat(body)
                    .containsEntry("vnp_Version", "2.1.0")
                    .containsEntry("vnp_Command", "querydr")
                    .containsEntry("vnp_TmnCode", TEST_TMN)
                    .containsEntry("vnp_TxnRef", "DH261121QDR01")
                    .containsEntry("vnp_TransactionDate", "20261121100000")
                    .containsEntry("vnp_CreateDate", "20261121103000")
                    .containsEntry("vnp_IpAddr", "10.0.0.5");
            String reqId = String.valueOf(body.get("vnp_RequestId"));
            assertThat(reqId).matches("[0-9a-f]{32}");

            Map<String, String> stringBody = toStringMap(body);
            assertThat(body.get("vnp_SecureHash")).isEqualTo(signer.signQueryDrRequest(stringBody, TEST_SECRET));
        }
        assertThat(captured.get(0).body()).containsEntry("vnp_TransactionNo", "90001234");
        assertThat(captured.get(1).body()).doesNotContainKey("vnp_TransactionNo");
        assertThat(captured.get(0).body().get("vnp_RequestId"))
                .isNotEqualTo(captured.get(1).body().get("vnp_RequestId"));

        assertThat(r1.signatureValid()).isTrue();
        assertThat(r1.responseCode()).isEqualTo("00");
        assertThat(r1.transactionStatus()).isEqualTo("00");
        assertThat(r1.amountVnd()).isEqualTo(250_000L);
        assertThat(r1.transactionNo()).isEqualTo("90001234");
        assertThat(r2.signatureValid()).isTrue();
    }

    @Test
    void invalidOrMissingChecksumInQueryDrAndRefundNullsOutBusinessFields() {
        handlerRef.set(req -> {
            String cmd = String.valueOf(req.body().get("vnp_Command"));
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("vnp_ResponseId", "resp_tampered");
            resp.put("vnp_Command", cmd);
            resp.put("vnp_ResponseCode", "00");
            resp.put("vnp_Message", "Tampered Success");
            resp.put("vnp_TmnCode", TEST_TMN);
            resp.put("vnp_TxnRef", "DH261121TAMPER01");
            resp.put("vnp_Amount", "50000000");
            resp.put("vnp_BankCode", "NCB");
            resp.put("vnp_PayDate", "20261121100500");
            resp.put("vnp_TransactionNo", "90009999");
            resp.put("vnp_TransactionType", "querydr".equals(cmd) ? "01" : "02");
            resp.put("vnp_TransactionStatus", "querydr".equals(cmd) ? "00" : "05");
            resp.put("vnp_OrderInfo", "Tampered");
            resp.put("vnp_SecureHash", "deadbeef".repeat(16));
            return MockResponse.json(200, resp);
        });

        QueryDrResult qTampered = gateway.queryDr(new QueryDrRequest(
                null, "DH261121TAMPER01", "20261121100000", null, null, null, null));
        assertThat(qTampered.signatureValid()).isFalse();
        assertThat(qTampered.responseId()).isNull();
        assertThat(qTampered.responseCode()).isNull();
        assertThat(qTampered.message()).isNull();
        assertThat(qTampered.txnRef()).isNull();
        assertThat(qTampered.amountVnd()).isNull();
        assertThat(qTampered.transactionNo()).isNull();
        assertThat(qTampered.transactionStatus()).isNull();
        assertThat(qTampered.rawResponse()).containsEntry("vnp_ResponseCode", "00");

        RefundResult rTampered = gateway.refund(new RefundRequest(
                null, "02", "DH261121TAMPER01", 500_000L, null, "20261121100000", "admin.ops", null, null, "Hoan"));
        assertThat(rTampered.signatureValid()).isFalse();
        assertThat(rTampered.responseId()).isNull();
        assertThat(rTampered.responseCode()).isNull();
        assertThat(rTampered.message()).isNull();
        assertThat(rTampered.txnRef()).isNull();
        assertThat(rTampered.amountVnd()).isNull();
        assertThat(rTampered.transactionNo()).isNull();
        assertThat(rTampered.transactionStatus()).isNull();
        assertThat(rTampered.rawResponse()).containsEntry("vnp_ResponseCode", "00");

        // Missing vnp_SecureHash
        handlerRef.set(req -> MockResponse.json(200, Map.of(
                "vnp_ResponseId", "resp_nohash",
                "vnp_Command", req.body().get("vnp_Command"),
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_Amount", "1000000")));
        assertThat(gateway.queryDr(new QueryDrRequest(
                null, "DH261121NOHASH", "20261121100000", null, null, null, null)).signatureValid()).isFalse();
        assertThat(gateway.refund(new RefundRequest(
                null, "02", "DH261121NOHASH", 10_000L, "", "20261121100000", "admin.ops", null, null, "Hoan"))
                .signatureValid()).isFalse();
    }

    @Test
    void refundSendsValidRequestIncludingEmptyTransactionNoAndParsesSignedResponse() {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        handlerRef.set(req -> {
            captured.set(req);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("vnp_ResponseId", "refund_resp_01");
            resp.put("vnp_Command", "refund");
            resp.put("vnp_ResponseCode", "00");
            resp.put("vnp_Message", "Refund Success");
            resp.put("vnp_TmnCode", TEST_TMN);
            resp.put("vnp_TxnRef", "DH261121REF01");
            resp.put("vnp_Amount", "15000000");
            resp.put("vnp_BankCode", "NCB");
            resp.put("vnp_PayDate", "20261121103500");
            resp.put("vnp_TransactionNo", "90005555");
            resp.put("vnp_TransactionType", "03");
            resp.put("vnp_TransactionStatus", "05");
            resp.put("vnp_OrderInfo", "Hoan tien mot phan");
            resp.put("vnp_SecureHash", signRefundResponse(resp));
            return MockResponse.json(200, resp);
        });

        RefundResult res = gateway.refund(new RefundRequest(
                null, "03", "DH261121REF01", 150_000L, null, "20261121100000", "admin.ops", null, null, "Hoan tien mot phan"));

        Map<String, Object> reqBody = captured.get().body();
        assertThat(reqBody)
                .containsEntry("vnp_Command", "refund")
                .containsEntry("vnp_TransactionType", "03")
                .containsEntry("vnp_Amount", "15000000")
                .containsEntry("vnp_TransactionNo", "")
                .containsEntry("vnp_CreateBy", "admin.ops")
                .containsEntry("vnp_IpAddr", "10.0.0.5");
        assertThat(reqBody.get("vnp_SecureHash"))
                .isEqualTo(signer.signRefundRequest(toStringMap(reqBody), TEST_SECRET));

        assertThat(res.signatureValid()).isTrue();
        assertThat(res.responseCode()).isEqualTo("00");
        assertThat(res.transactionStatus()).isEqualTo("05");
        assertThat(res.amountVnd()).isEqualTo(150_000L);
        assertThat(res.transactionNo()).isEqualTo("90005555");
    }

    @Test
    void transportFailuresThrowVnpayTransportExceptionAndSecretNeverLeaksAnywhere() throws IOException {
        Logger vnpayLogger = (Logger) LoggerFactory.getLogger("vn.unicom.thi360.commerce.payment.vnpay");
        Level previousLevel = vnpayLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        vnpayLogger.addAppender(appender);
        vnpayLogger.setLevel(Level.DEBUG);

        try {
            // 1. Hostile response echoing secret in vnp_Message & extra field
            handlerRef.set(req -> {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("vnp_ResponseId", "hostile1");
                resp.put("vnp_Command", String.valueOf(req.body().get("vnp_Command")));
                resp.put("vnp_ResponseCode", "99");
                resp.put("vnp_Message", "Echo secret " + TEST_SECRET);
                resp.put("vnp_TxnRef", "DH_HOSTILE");
                resp.put("extraLeak", Map.of("nested", TEST_SECRET));
                resp.put("vnp_SecureHash", "badhash");
                return MockResponse.json(200, resp);
            });

            QueryDrResult qHostile = gateway.queryDr(new QueryDrRequest(
                    null, "DH_HOSTILE", "20261121100000", null, null, null, null));
            assertThat(qHostile.signatureValid()).isFalse();
            assertThat(MAPPER.writeValueAsString(qHostile.rawResponse())).doesNotContain(TEST_SECRET);

            RefundResult rHostile = gateway.refund(new RefundRequest(
                    null, "02", "DH_HOSTILE", 50_000L, null, "20261121100000", "admin.ops", null, null, "Hoan"));
            assertThat(rHostile.signatureValid()).isFalse();
            assertThat(MAPPER.writeValueAsString(rHostile.rawResponse())).doesNotContain(TEST_SECRET);

            // 2. Pay & verify roundtrip
            String payUrl = gateway.pay(new PayRequest(
                    "DH_SECRET_CHK", "DH_SECRET", 25_000L, "Thanh toan", "vn",
                    "https://thi360.example/return", "127.0.0.1", null, null));
            assertThat(payUrl).doesNotContain(TEST_SECRET);

            // 3. HTTP 500 HTML echoing secret
            handlerRef.set(req -> new MockResponse(500, "text/html", "<html>" + TEST_SECRET + "</html>", 0));
            assertThatThrownBy(() -> gateway.queryDr(new QueryDrRequest(
                    null, "DH500", "20261121100000", null, null, null, null)))
                    .isInstanceOf(VnpayTransportException.class)
                    .satisfies(ex -> {
                        VnpayTransportException tex = (VnpayTransportException) ex;
                        assertThat(tex.isTimeout()).isFalse();
                        assertThat(tex.getHttpStatus()).isEqualTo(500);
                        assertThat(stackTraceOf(ex)).doesNotContain(TEST_SECRET);
                    });

            // 4. Non-JSON 200 echoing secret
            handlerRef.set(req -> new MockResponse(200, "text/plain", "not json " + TEST_SECRET, 0));
            assertThatThrownBy(() -> gateway.refund(new RefundRequest(
                    null, "02", "DH_NONJSON", 50_000L, null, "20261121100000", "admin.ops", null, null, "Hoan")))
                    .isInstanceOf(VnpayTransportException.class)
                    .satisfies(ex -> {
                        VnpayTransportException tex = (VnpayTransportException) ex;
                        assertThat(tex.isTimeout()).isFalse();
                        assertThat(tex.getHttpStatus()).isEqualTo(200);
                        assertThat(stackTraceOf(ex)).doesNotContain(TEST_SECRET);
                    });

            // 5. Read timeout
            handlerRef.set(req -> new MockResponse(200, "application/json", "{}", 1500));
            assertThatThrownBy(() -> gateway.queryDr(new QueryDrRequest(
                    null, "DH_TIMEOUT", "20261121100000", null, null, null, null)))
                    .isInstanceOf(VnpayTransportException.class)
                    .satisfies(ex -> {
                        VnpayTransportException tex = (VnpayTransportException) ex;
                        assertThat(tex.isTimeout()).isTrue();
                        assertThat(tex.getHttpStatus()).isNull();
                        assertThat(stackTraceOf(ex)).doesNotContain(TEST_SECRET);
                    });

            // 6. Connection refused on unused port
            int unusedPort;
            try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                unusedPort = socket.getLocalPort();
            }
            RealVnpayGateway closedGateway = createGateway(
                    "http://127.0.0.1:" + unusedPort + "/merchant_webapi/api/transaction",
                    Duration.ofSeconds(1));
            assertThatThrownBy(() -> closedGateway.queryDr(new QueryDrRequest(
                    null, "DH_CONNREFUSED", "20261121100000", null, null, null, null)))
                    .isInstanceOf(VnpayTransportException.class)
                    .satisfies(ex -> {
                        VnpayTransportException tex = (VnpayTransportException) ex;
                        assertThat(tex.isTimeout()).isFalse();
                        assertThat(tex.getHttpStatus()).isNull();
                        assertThat(stackTraceOf(ex)).doesNotContain(TEST_SECRET);
                    });

            assertThat(appender.list).isNotEmpty();
            for (ILoggingEvent event : appender.list) {
                assertThat(event.getFormattedMessage()).doesNotContain(TEST_SECRET);
                if (event.getThrowableProxy() != null) {
                    assertThat(event.getThrowableProxy().getMessage()).doesNotContain(TEST_SECRET);
                }
            }
        } finally {
            vnpayLogger.detachAppender(appender);
            vnpayLogger.setLevel(previousLevel);
        }
    }

    @Test
    void signed0000WithMalformedZeroOrBlankAmountYieldsNullAmountVnd() {
        for (String rawAmount : List.of("15000050", "0", "")) {
            handlerRef.set(req -> {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("vnp_ResponseId", "resp_amt");
                resp.put("vnp_Command", "querydr");
                resp.put("vnp_ResponseCode", "00");
                resp.put("vnp_Message", "Success");
                resp.put("vnp_TmnCode", TEST_TMN);
                resp.put("vnp_TxnRef", "DH261121BADAMT");
                resp.put("vnp_Amount", rawAmount);
                resp.put("vnp_BankCode", "NCB");
                resp.put("vnp_PayDate", "20261121100230");
                resp.put("vnp_TransactionNo", "90001234");
                resp.put("vnp_TransactionType", "01");
                resp.put("vnp_TransactionStatus", "00");
                resp.put("vnp_OrderInfo", "Info");
                resp.put("vnp_SecureHash", signQueryDrResponse(resp));
                return MockResponse.json(200, resp);
            });

            QueryDrResult res = gateway.queryDr(new QueryDrRequest(
                    null, "DH261121BADAMT", "20261121100000", null, null, null, null));
            assertThat(res.signatureValid()).isTrue();
            assertThat(res.responseCode()).isEqualTo("00");
            assertThat(res.transactionStatus()).isEqualTo("00");
            assertThat(res.amountVnd()).isNull();
        }
    }

    @Test
    void mismatchedCommandOrTmnCodeFailsEnvelopeVerificationWhileNon00AllowsMissingEnvelope() {
        // 1. Sai vnp_TmnCode hoặc hoán đổi vnp_Command trên querydr (dù chữ ký đúng) -> signatureValid=false, trường nghiệp vụ null.
        for (String[] cmdAndTmn : new String[][] {
            {"querydr", "OTHER-TMN"},
            {"refund", TEST_TMN},
            {null, TEST_TMN},
            {"querydr", null}
        }) {
            handlerRef.set(req -> {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("vnp_ResponseId", "resp_bad_env");
                if (cmdAndTmn[0] != null) {
                    resp.put("vnp_Command", cmdAndTmn[0]);
                }
                resp.put("vnp_ResponseCode", "00");
                resp.put("vnp_Message", "Success");
                if (cmdAndTmn[1] != null) {
                    resp.put("vnp_TmnCode", cmdAndTmn[1]);
                }
                resp.put("vnp_TxnRef", "DH261121ENV01");
                resp.put("vnp_Amount", "20000000");
                resp.put("vnp_TransactionStatus", "00");
                resp.put("vnp_SecureHash", signQueryDrResponse(resp));
                return MockResponse.json(200, resp);
            });

            QueryDrResult qRes = gateway.queryDr(new QueryDrRequest(
                    null, "DH261121ENV01", "20261121100000", null, null, null, null));
            assertThat(qRes.signatureValid()).isFalse();
            assertThat(qRes.responseCode()).isNull();
            assertThat(qRes.txnRef()).isNull();
            assertThat(qRes.amountVnd()).isNull();
            assertThat(qRes.transactionStatus()).isNull();
        }

        // 2. Sai vnp_TmnCode hoặc hoán đổi vnp_Command trên refund (dù chữ ký đúng) -> signatureValid=false, trường nghiệp vụ null.
        for (String[] cmdAndTmn : new String[][] {
            {"refund", "OTHER-TMN"},
            {"querydr", TEST_TMN},
            {null, TEST_TMN},
            {"refund", null}
        }) {
            handlerRef.set(req -> {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("vnp_ResponseId", "refund_bad_env");
                if (cmdAndTmn[0] != null) {
                    resp.put("vnp_Command", cmdAndTmn[0]);
                }
                resp.put("vnp_ResponseCode", "00");
                resp.put("vnp_Message", "Success");
                if (cmdAndTmn[1] != null) {
                    resp.put("vnp_TmnCode", cmdAndTmn[1]);
                }
                resp.put("vnp_TxnRef", "DH261121ENV02");
                resp.put("vnp_Amount", "20000000");
                resp.put("vnp_TransactionStatus", "05");
                resp.put("vnp_SecureHash", signRefundResponse(resp));
                return MockResponse.json(200, resp);
            });

            RefundResult rRes = gateway.refund(new RefundRequest(
                    null, "02", "DH261121ENV02", 200_000L, null, "20261121100000", "admin.ops", null, null, "Hoan"));
            assertThat(rRes.signatureValid()).isFalse();
            assertThat(rRes.responseCode()).isNull();
            assertThat(rRes.txnRef()).isNull();
            assertThat(rRes.amountVnd()).isNull();
            assertThat(rRes.transactionStatus()).isNull();
        }

        // 3. Phản hồi lỗi != "00" (vd "99") cho phép thiếu vnp_Command / vnp_TmnCode nếu chữ ký hợp lệ.
        handlerRef.set(req -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("vnp_ResponseId", "resp_err_99");
            resp.put("vnp_ResponseCode", "99");
            resp.put("vnp_Message", "System Error");
            resp.put("vnp_SecureHash", signQueryDrResponse(resp));
            return MockResponse.json(200, resp);
        });
        QueryDrResult err99 = gateway.queryDr(new QueryDrRequest(
                null, "DH261121ERR99", "20261121100000", null, null, null, null));
        assertThat(err99.signatureValid()).isTrue();
        assertThat(err99.responseCode()).isEqualTo("99");
    }

    @Test
    void oversizedResponseBodyThrowsTransportExceptionAndDestroyDoesNotCloseInjectedClient() throws Exception {
        String oversizedBody = "{\"vnp_ResponseCode\":\"00\",\"pad\":\""
                + TEST_SECRET + "x".repeat(65_600) + "\"}";
        handlerRef.set(req -> new MockResponse(200, "application/json", oversizedBody, 0));

        assertThatThrownBy(() -> gateway.queryDr(new QueryDrRequest(
                null, "DH_BIG_BODY", "20261121100000", null, null, null, null)))
                .isInstanceOf(VnpayTransportException.class)
                .satisfies(ex -> {
                    VnpayTransportException tex = (VnpayTransportException) ex;
                    assertThat(tex.isTimeout()).isFalse();
                    assertThat(tex.getHttpStatus()).isEqualTo(200);
                    assertThat(stackTraceOf(ex)).doesNotContain(TEST_SECRET);
                });

        // Kiểm tra destroy() không đóng HttpClient được tiêm từ bên ngoài.
        HttpClient externalClient = HttpClient.newHttpClient();
        String apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/merchant_webapi/api/transaction";
        RealVnpayGateway injectedGateway = new RealVnpayGateway(
                new VnpayProperties("SANDBOX", TEST_TMN, TEST_SECRET,
                        "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", apiUrl, List.of(), null, null, null),
                new VnpayClientProperties(Duration.ofSeconds(2), Duration.ofSeconds(2), "10.0.0.5"),
                signer,
                MAPPER,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                externalClient);
        injectedGateway.destroy();

        handlerRef.set(req -> {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("vnp_ResponseId", "ok_after_destroy");
            resp.put("vnp_Command", "querydr");
            resp.put("vnp_ResponseCode", "99");
            resp.put("vnp_TmnCode", TEST_TMN);
            resp.put("vnp_SecureHash", signQueryDrResponse(resp));
            return MockResponse.json(200, resp);
        });
        assertThat(injectedGateway.queryDr(new QueryDrRequest(
                null, "DH_EXT_CLIENT", "20261121100000", null, null, null, null)).signatureValid()).isTrue();
        HttpResponse<String> ping = externalClient.send(
                HttpRequest.newBuilder(java.net.URI.create(apiUrl))
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(ping.statusCode()).isEqualTo(200);
        externalClient.close();
    }

    @Test
    void jsonNullOptionalFieldsPassChecksumAndAreOmittedFromRawResponse() {
        handlerRef.set(req -> {
            String cmd = String.valueOf(req.body().get("vnp_Command"));
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("vnp_ResponseId", "resp_nulls");
            resp.put("vnp_Command", cmd);
            resp.put("vnp_ResponseCode", "00");
            resp.put("vnp_Message", "Success");
            resp.put("vnp_TmnCode", TEST_TMN);
            resp.put("vnp_TxnRef", "DH261121NULL01");
            resp.put("vnp_Amount", "20000000");
            resp.put("vnp_BankCode", null);
            resp.put("vnp_PayDate", "20261121100230");
            resp.put("vnp_TransactionNo", "90001234");
            resp.put("vnp_TransactionType", "querydr".equals(cmd) ? "01" : "02");
            resp.put("vnp_TransactionStatus", "querydr".equals(cmd) ? "00" : "05");
            resp.put("vnp_OrderInfo", "Info");
            if ("querydr".equals(cmd)) {
                resp.put("vnp_PromotionCode", null);
                resp.put("vnp_PromotionAmount", null);
                resp.put("vnp_SecureHash", signQueryDrResponse(resp));
            } else {
                resp.put("vnp_SecureHash", signRefundResponse(resp));
            }
            return MockResponse.json(200, resp);
        });

        QueryDrResult qRes = gateway.queryDr(new QueryDrRequest(
                null, "DH261121NULL01", "20261121100000", null, null, null, null));
        assertThat(qRes.signatureValid()).isTrue();
        assertThat(qRes.promotionCode()).isNull();
        assertThat(qRes.promotionAmount()).isNull();
        assertThat(qRes.bankCode()).isNull();
        assertThat(qRes.rawResponse())
                .containsEntry("vnp_ResponseCode", "00")
                .doesNotContainKeys("vnp_PromotionCode", "vnp_PromotionAmount", "vnp_BankCode");

        RefundResult rRes = gateway.refund(new RefundRequest(
                null, "02", "DH261121NULL01", 200_000L, "90001234", "20261121100000", "admin.ops", null, null, "Hoan"));
        assertThat(rRes.signatureValid()).isTrue();
        assertThat(rRes.bankCode()).isNull();
        assertThat(rRes.rawResponse())
                .containsEntry("vnp_ResponseCode", "00")
                .doesNotContainKey("vnp_BankCode");
    }

    private RealVnpayGateway createGateway(String apiUrl, Duration readTimeout) {
        VnpayProperties props = new VnpayProperties(
                "SANDBOX",
                TEST_TMN,
                TEST_SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                apiUrl,
                List.of(),
                null,
                null,
                null);
        VnpayClientProperties clientProps = new VnpayClientProperties(
                Duration.ofSeconds(2),
                readTimeout,
                "10.0.0.5");
        return new RealVnpayGateway(
                props,
                clientProps,
                signer,
                MAPPER,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    private String signQueryDrResponse(Map<String, Object> resp) {
        String data = signer.buildQueryDrResponseSignData(resp);
        return hmacSha512(data, TEST_SECRET);
    }

    private String signRefundResponse(Map<String, Object> resp) {
        String data = signer.buildRefundResponseSignData(resp);
        return hmacSha512(data, TEST_SECRET);
    }

    private static String hmacSha512(String data, String secret) {
        return VnpaySigner.hmacSha512Hex(data, secret);
    }

    private static String stackTraceOf(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static Map<String, String> toStringMap(Map<String, Object> map) {
        Map<String, String> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, String.valueOf(v));
            }
        });
        return out;
    }
}
