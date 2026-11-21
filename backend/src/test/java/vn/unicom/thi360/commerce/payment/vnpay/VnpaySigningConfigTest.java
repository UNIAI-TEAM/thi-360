package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * AC-23: Đổi cấu hình quy tắc mã hóa URL và thuật toán băm cho {@code pay}, {@code querydr}, {@code refund}
 * thì chuỗi ký và chữ ký đổi theo, không cần sửa mã.
 */
class VnpaySigningConfigTest {

    private static final String TEST_SECRET = "TEST-CONFIG-SIGNING-SECRET-001";

    @Test
    void changingPayEncodingRuleChangesPaySignDataAndHash() {
        Map<String, String> params = Map.of(
                "vnp_OrderInfo", "Thanh toan a*b~c d",
                "vnp_TxnRef", "DH261121ENC01");

        String javaSignData = signDataWith("JAVA_URL_ENCODER", params);
        String pythonSignData = signDataWith("PYTHON_QUOTE_PLUS", params);
        String rfcSignData = signDataWith("RFC_3986", params);
        String rawSignData = signDataWith("RAW", params);

        assertThat(javaSignData).isEqualTo("vnp_OrderInfo=Thanh+toan+a*b%7Ec+d&vnp_TxnRef=DH261121ENC01");
        assertThat(pythonSignData).isEqualTo("vnp_OrderInfo=Thanh+toan+a%2Ab~c+d&vnp_TxnRef=DH261121ENC01");
        assertThat(rfcSignData).isEqualTo("vnp_OrderInfo=Thanh%20toan%20a%2Ab~c%20d&vnp_TxnRef=DH261121ENC01");
        assertThat(rawSignData).isEqualTo("vnp_OrderInfo=Thanh toan a*b~c d&vnp_TxnRef=DH261121ENC01");

        String javaHash = signPayWith("JAVA_URL_ENCODER", "HMAC_SHA512", params);
        String pythonHash = signPayWith("PYTHON_QUOTE_PLUS", "HMAC_SHA512", params);
        String rfcHash = signPayWith("RFC_3986", "HMAC_SHA512", params);
        String sha256Hash = signPayWith("JAVA_URL_ENCODER", "HMAC_SHA256", params);

        assertThat(javaHash).hasSize(128).isNotEqualTo(pythonHash).isNotEqualTo(rfcHash);
        assertThat(pythonHash).isNotEqualTo(rfcHash);
        assertThat(sha256Hash).hasSize(64).isNotEqualTo(javaHash);
    }

    @Test
    void changingQueryDrAndRefundAlgorithmsChangesSignaturesIndependently() {
        Map<String, String> queryDrFields = Map.of(
                "vnp_RequestId", "req001",
                "vnp_Version", "2.1.0",
                "vnp_Command", "querydr",
                "vnp_TmnCode", "TEST-TMN",
                "vnp_TxnRef", "DH261121ALG01",
                "vnp_TransactionDate", "20261121100000",
                "vnp_CreateDate", "20261121102000",
                "vnp_IpAddr", "127.0.0.1",
                "vnp_OrderInfo", "Kiem tra GD");

        Map<String, String> refundFields = Map.ofEntries(
                Map.entry("vnp_RequestId", "req002"),
                Map.entry("vnp_Version", "2.1.0"),
                Map.entry("vnp_Command", "refund"),
                Map.entry("vnp_TmnCode", "TEST-TMN"),
                Map.entry("vnp_TransactionType", "02"),
                Map.entry("vnp_TxnRef", "DH261121ALG01"),
                Map.entry("vnp_Amount", "1000000"),
                Map.entry("vnp_TransactionNo", "90000001"),
                Map.entry("vnp_TransactionDate", "20261121100000"),
                Map.entry("vnp_CreateBy", "admin.ops"),
                Map.entry("vnp_CreateDate", "20261121110000"),
                Map.entry("vnp_IpAddr", "127.0.0.1"),
                Map.entry("vnp_OrderInfo", "Hoan tien GD"));

        AtomicReference<VnpayRealSigner> defaultSigner = new AtomicReference<>();
        AtomicReference<VnpayRealSigner> customQueryDrSigner = new AtomicReference<>();
        AtomicReference<VnpayRealSigner> customRefundSigner = new AtomicReference<>();
        AtomicReference<VnpayRealSigner> shaDigestSigner = new AtomicReference<>();

        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class, VnpayRealSigner.class)
                .withPropertyValues("thi360.vnpay.mode=MOCK")
                .run(ctx -> defaultSigner.set(ctx.getBean(VnpayRealSigner.class)));

        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class, VnpayRealSigner.class)
                .withPropertyValues(
                        "thi360.vnpay.mode=MOCK",
                        "thi360.vnpay.signing.query-dr-hash-algorithm=HMAC_SHA256")
                .run(ctx -> customQueryDrSigner.set(ctx.getBean(VnpayRealSigner.class)));

        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class, VnpayRealSigner.class)
                .withPropertyValues(
                        "thi360.vnpay.mode=MOCK",
                        "thi360.vnpay.signing.refund-hash-algorithm=SHA256")
                .run(ctx -> customRefundSigner.set(ctx.getBean(VnpayRealSigner.class)));

        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class, VnpayRealSigner.class)
                .withPropertyValues(
                        "thi360.vnpay.mode=MOCK",
                        "thi360.vnpay.signing.query-dr-hash-algorithm=SHA512",
                        "thi360.vnpay.signing.refund-hash-algorithm=HMAC_SHA256")
                .run(ctx -> shaDigestSigner.set(ctx.getBean(VnpayRealSigner.class)));

        String q512 = defaultSigner.get().signQueryDrRequest(queryDrFields, TEST_SECRET);
        String q256 = customQueryDrSigner.get().signQueryDrRequest(queryDrFields, TEST_SECRET);
        String qSha512 = shaDigestSigner.get().signQueryDrRequest(queryDrFields, TEST_SECRET);

        String rDefault = defaultSigner.get().signRefundRequest(refundFields, TEST_SECRET);
        String rCustomQueryDr = customQueryDrSigner.get().signRefundRequest(refundFields, TEST_SECRET);
        String rSha256 = customRefundSigner.get().signRefundRequest(refundFields, TEST_SECRET);
        String rHmac256 = shaDigestSigner.get().signRefundRequest(refundFields, TEST_SECRET);

        assertThat(q512).hasSize(128).isNotEqualTo(qSha512);
        assertThat(q256).hasSize(64).isNotEqualTo(q512);
        assertThat(rCustomQueryDr).isEqualTo(rDefault);
        assertThat(rSha256).hasSize(64).isNotEqualTo(rDefault).isNotEqualTo(rHmac256);
        assertThat(rHmac256).hasSize(64).isNotEqualTo(rDefault);

        AtomicReference<VnpayRealSigner> querySha256RefundSha512Signer = new AtomicReference<>();
        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class, VnpayRealSigner.class)
                .withPropertyValues(
                        "thi360.vnpay.mode=MOCK",
                        "thi360.vnpay.signing.query-dr-hash-algorithm=SHA256",
                        "thi360.vnpay.signing.refund-hash-algorithm=SHA512")
                .run(ctx -> querySha256RefundSha512Signer.set(ctx.getBean(VnpayRealSigner.class)));

        Map<String, Object> queryRespBase = Map.ofEntries(
                Map.entry("vnp_ResponseId", "resp01"),
                Map.entry("vnp_Command", "querydr"),
                Map.entry("vnp_ResponseCode", "00"),
                Map.entry("vnp_Message", "OK"),
                Map.entry("vnp_TmnCode", "TEST-TMN"),
                Map.entry("vnp_TxnRef", "DH261121ALG01"),
                Map.entry("vnp_Amount", "1000000"),
                Map.entry("vnp_BankCode", "NCB"),
                Map.entry("vnp_PayDate", "20261121102100"),
                Map.entry("vnp_TransactionNo", "90000001"),
                Map.entry("vnp_TransactionType", "01"),
                Map.entry("vnp_TransactionStatus", "00"),
                Map.entry("vnp_OrderInfo", "Kiem tra GD"),
                Map.entry("vnp_PromotionCode", ""),
                Map.entry("vnp_PromotionAmount", ""));
        String queryRespData = defaultSigner.get().buildQueryDrResponseSignData(queryRespBase);
        String queryRespHmac512 = hmacHex("HmacSHA512", queryRespData, TEST_SECRET);

        assertResponseVerification(
                customQueryDrSigner.get(), queryRespBase, hmacHex("HmacSHA256", queryRespData, TEST_SECRET),
                queryRespHmac512, true);
        assertResponseVerification(
                querySha256RefundSha512Signer.get(), queryRespBase, digestHex("SHA-256", TEST_SECRET + queryRespData),
                queryRespHmac512, true);
        assertResponseVerification(
                shaDigestSigner.get(), queryRespBase, digestHex("SHA-512", TEST_SECRET + queryRespData),
                queryRespHmac512, true);

        Map<String, Object> refundRespBase = Map.ofEntries(
                Map.entry("vnp_ResponseId", "resp02"),
                Map.entry("vnp_Command", "refund"),
                Map.entry("vnp_ResponseCode", "00"),
                Map.entry("vnp_Message", "OK"),
                Map.entry("vnp_TmnCode", "TEST-TMN"),
                Map.entry("vnp_TxnRef", "DH261121ALG01"),
                Map.entry("vnp_Amount", "1000000"),
                Map.entry("vnp_BankCode", "NCB"),
                Map.entry("vnp_PayDate", "20261121110100"),
                Map.entry("vnp_TransactionNo", "90000001"),
                Map.entry("vnp_TransactionType", "02"),
                Map.entry("vnp_TransactionStatus", "05"),
                Map.entry("vnp_OrderInfo", "Hoan tien GD"));
        String refundRespData = defaultSigner.get().buildRefundResponseSignData(refundRespBase);
        String refundRespHmac512 = hmacHex("HmacSHA512", refundRespData, TEST_SECRET);

        assertResponseVerification(
                shaDigestSigner.get(), refundRespBase, hmacHex("HmacSHA256", refundRespData, TEST_SECRET),
                refundRespHmac512, false);
        assertResponseVerification(
                customRefundSigner.get(), refundRespBase, digestHex("SHA-256", TEST_SECRET + refundRespData),
                refundRespHmac512, false);
        assertResponseVerification(
                querySha256RefundSha512Signer.get(), refundRespBase, digestHex("SHA-512", TEST_SECRET + refundRespData),
                refundRespHmac512, false);
    }

    private static void assertResponseVerification(
            VnpayRealSigner signer,
            Map<String, Object> baseFields,
            String expectedAlgHash,
            String defaultHmac512Hash,
            boolean queryDr) {
        Map<String, Object> valid = new LinkedHashMap<>(baseFields);
        valid.put("vnp_SecureHash", expectedAlgHash);
        Map<String, Object> wrongAlg = new LinkedHashMap<>(baseFields);
        wrongAlg.put("vnp_SecureHash", defaultHmac512Hash);
        if (queryDr) {
            assertThat(signer.verifyQueryDrResponse(valid, TEST_SECRET)).isTrue();
            assertThat(signer.verifyQueryDrResponse(wrongAlg, TEST_SECRET)).isFalse();
        } else {
            assertThat(signer.verifyRefundResponse(valid, TEST_SECRET)).isTrue();
            assertThat(signer.verifyRefundResponse(wrongAlg, TEST_SECRET)).isFalse();
        }
    }

    private static String hmacHex(String jcaAlg, String data, String secret) {
        try {
            Mac mac = Mac.getInstance(jcaAlg);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), jcaAlg));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String digestHex(String jcaAlg, String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(jcaAlg).digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String signDataWith(String encodingRule, Map<String, String> params) {
        AtomicReference<String> out = new AtomicReference<>();
        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class, VnpayRealSigner.class)
                .withPropertyValues(
                        "thi360.vnpay.mode=MOCK",
                        "thi360.vnpay.signing.pay-encoding=" + encodingRule)
                .run(ctx -> out.set(ctx.getBean(VnpayRealSigner.class).buildPaySignData(params)));
        return out.get();
    }

    private static String signPayWith(String encodingRule, String hashAlg, Map<String, String> params) {
        AtomicReference<String> out = new AtomicReference<>();
        new ApplicationContextRunner()
                .withUserConfiguration(VnpayRealConfiguration.class, VnpayRealSigner.class)
                .withPropertyValues(
                        "thi360.vnpay.mode=MOCK",
                        "thi360.vnpay.signing.pay-encoding=" + encodingRule,
                        "thi360.vnpay.signing.pay-hash-algorithm=" + hashAlg)
                .run(ctx -> out.set(ctx.getBean(VnpayRealSigner.class).signPay(params, TEST_SECRET)));
        return out.get();
    }
}
