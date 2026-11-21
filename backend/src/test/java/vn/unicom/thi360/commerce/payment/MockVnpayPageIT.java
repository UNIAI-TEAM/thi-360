package vn.unicom.thi360.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.HtmlUtils;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.classroom.application.DevClassSeeder;
import vn.unicom.thi360.commerce.order.domain.OrderState;
import vn.unicom.thi360.commerce.order.domain.PaymentMethod;
import vn.unicom.thi360.commerce.payment.domain.PaymentState;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway.PayRequest;
import vn.unicom.thi360.commerce.promotion.infrastructure.PromotionRepository;
import vn.unicom.thi360.commerce.support.CommerceTestFixtures;
import vn.unicom.thi360.identity.application.DevDataInitializer;
import vn.unicom.thi360.shared.security.AuthCookieService;

/**
 * AC-17 & AC-18: kiểm tra trang {@code /mock-vnpay}, cookie hết hạn, loại trừ khỏi OpenAPI, và 404 khi ở chế độ {@code SANDBOX}.
 */
class MockVnpayPageIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private CatalogLookup catalogLookup;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private VnpayGateway vnpayGateway;

    private CommerceTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CommerceTestFixtures(jdbc, mapper, catalogLookup, promotionRepository, testSecuritySupport);
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void rendersMockPageEvenWithExpiredCookieAndHandlesNoResponseAndRejectsInvalidSignature() throws Exception {
        assertThat(vnpayGateway).isInstanceOf(MockVnpayGateway.class);

        var buyer = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 3);
        var order = fixtures.insertStubOrder(
                buyer, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var payment = fixtures.insertStubPayment(order, 1, 200_000L, PaymentState.CREATED);

        String payUrl = vnpayGateway.pay(new PayRequest(
                payment.txnRef(), order.code(), 200_000L,
                "Thanh toan don " + order.code(), "vn",
                "http://localhost:3000/checkout/result",
                "127.0.0.1", "20261121100000", "20261121101500"));

        // Gọi /mock-vnpay khi trình duyệt còn cookie hết hạn vẫn trả 200 HTML đủ 3 nút hành động.
        String html = mockMvc.perform(getDecodedUrl(payUrl)
                        .cookie(new Cookie(AuthCookieService.ACCESS_TOKEN_COOKIE, "expired.invalid.jwt")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("data-mock-action=\"success\"")
                .contains("data-mock-action=\"failed\"")
                .contains("data-mock-action=\"no_response\"")
                .contains("data-ipn-url=");

        // Chữ ký sai bị từ chối 422 VNPAY_SIGNATURE_INVALID.
        String tamperedUrl = payUrl.replace("vnp_Amount=20000000", "vnp_Amount=100");
        mockMvc.perform(getDecodedUrl(tamperedUrl))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VNPAY_SIGNATURE_INVALID"));

        // "Không trả lời": 302 tới vnp_ReturnUrl kèm chữ ký hợp lệ, payment giữ CREATED.
        String noResponseAction = payUrl.replace("/mock-vnpay?", "/mock-vnpay/action?outcome=no_response&");
        String location = mockMvc.perform(getDecodedUrl(noResponseAction))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.startsWith("http://localhost:3000/checkout/result?")))
                .andReturn().getResponse().getHeader("Location");

        assertThat(vnpayGateway.verify(parseQueryParams(location))).isTrue();
        String paymentState = jdbc.queryForObject(
                "SELECT state FROM commerce.payment WHERE id = ?", String.class, payment.id());
        assertThat(paymentState).isEqualTo("CREATED");

        // "Thất bại": IPN chạy với mã 24 -> payment thành FAILED, order vẫn PENDING_PAYMENT, chưa cấp quyền.
        String failedAction = payUrl.replace("/mock-vnpay?", "/mock-vnpay/action?outcome=failed&");
        String failedRedirect = mockMvc.perform(getDecodedUrl(failedAction))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.startsWith("http://localhost:3000/checkout/result?")))
                .andReturn().getResponse().getHeader("Location");
        assertThat(vnpayGateway.verify(parseQueryParams(failedRedirect))).isTrue();
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, payment.id()))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PENDING_PAYMENT");

        // "Thành công" trên lần thanh toán mới -> IPN chạy thật -> đơn thành FULFILLED và cấp đủ lượt quyền.
        var secondPayment = fixtures.insertStubPayment(order, 2, 200_000L, PaymentState.CREATED);
        String secondPayUrl = vnpayGateway.pay(new PayRequest(
                secondPayment.txnRef(), order.code(), 200_000L,
                "Thanh toan don " + order.code(), "vn",
                "http://localhost:3000/checkout/result",
                "127.0.0.1", "20261121100500", "20261121102000"));
        String successAction = secondPayUrl.replace("/mock-vnpay?", "/mock-vnpay/action?outcome=success&");
        String successRedirect = mockMvc.perform(getDecodedUrl(successAction))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.startsWith("http://localhost:3000/checkout/result?")))
                .andReturn().getResponse().getHeader("Location");
        assertThat(vnpayGateway.verify(parseQueryParams(successRedirect))).isTrue();
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.payment WHERE id = ?", String.class, secondPayment.id()))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("FULFILLED");
        Integer grantedAttempts = jdbc.queryForObject(
                "SELECT attempts_total FROM commerce.entitlement WHERE user_id = ? AND exam_id = ?",
                Integer.class, buyer.id(), product.examId());
        assertThat(grantedAttempts).isEqualTo(3);
    }

    @Test
    void emitsDistinctTransactionNosInDataIpnUrlsAndRejectsInvalidReturnUrls() throws Exception {
        var buyer1 = fixtures.createLearner();
        var buyer2 = fixtures.createLearner();
        var product = fixtures.insertOnSaleProduct("HSA", 200_000L, 2);
        var o1 = fixtures.insertStubOrder(
                buyer1, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p1 = fixtures.insertStubPayment(o1, 1, 200_000L, PaymentState.CREATED);
        var o2 = fixtures.insertStubOrder(
                buyer2, product, OrderState.PENDING_PAYMENT, PaymentMethod.VNPAY,
                200_000L, Instant.now().plusSeconds(900), null);
        var p2 = fixtures.insertStubPayment(o2, 1, 200_000L, PaymentState.CREATED);

        String payUrl1 = vnpayGateway.pay(new PayRequest(
                p1.txnRef(), o1.code(), 200_000L,
                "Thanh toan don " + o1.code(), "vn",
                "/checkout/result", "127.0.0.1", "20261121100000", "20261121101500"));
        String payUrl2 = vnpayGateway.pay(new PayRequest(
                p2.txnRef(), o2.code(), 200_000L,
                "Thanh toan don " + o2.code(), "vn",
                "/checkout/result", "127.0.0.1", "20261121100000", "20261121101500"));

        String html1 = mockMvc.perform(getDecodedUrl(payUrl1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String html2 = mockMvc.perform(getDecodedUrl(payUrl2))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String ipnUrl1 = extractSuccessIpnUrl(html1);
        String ipnUrl2 = extractSuccessIpnUrl(html2);
        String txnNo1 = parseQueryParams(ipnUrl1).get("vnp_TransactionNo");
        String txnNo2 = parseQueryParams(ipnUrl2).get("vnp_TransactionNo");
        assertThat(txnNo1).isNotBlank().isNotEqualTo("MOCK000001");
        assertThat(txnNo2).isNotBlank().isNotEqualTo("MOCK000001").isNotEqualTo(txnNo1);

        mockMvc.perform(getDecodedUrl(ipnUrl1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        mockMvc.perform(getDecodedUrl(ipnUrl2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o1.id()))
                .isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT state FROM commerce.purchase_order WHERE id = ?", String.class, o2.id()))
                .isEqualTo("FULFILLED");

        // Từ chối path traversal và #fragment trên vnp_ReturnUrl.
        for (String badReturnUrl : new String[] {
            "/checkout/result/../admin",
            "/checkout/result/%2e%2e/admin",
            "/checkout/result#frag",
            "http://localhost:3000/checkout/result#frag"
        }) {
            String badPayUrl = vnpayGateway.pay(new PayRequest(
                    p1.txnRef(), o1.code(), 200_000L,
                    "Thanh toan don " + o1.code(), "vn",
                    badReturnUrl, "127.0.0.1", "20261121100000", "20261121101500"));
            mockMvc.perform(getDecodedUrl(badPayUrl))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
            String badActionUrl = badPayUrl.replace("/mock-vnpay?", "/mock-vnpay/action?outcome=no_response&");
            mockMvc.perform(getDecodedUrl(badActionUrl))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void mockVnpayIsExcludedFromOpenApi() throws Exception {
        String raw = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode paths = mapper.readTree(raw).path("paths");
        assertThat(paths.fieldNames()).toIterable().noneMatch(p -> p.contains("mock-vnpay"));
    }

    @Nested
    @ActiveProfiles(profiles = {"api", "dev"}, inheritProfiles = false)
    @TestPropertySource(properties = {
        "thi360.vnpay.mode=SANDBOX",
        "thi360.devdata.published-exams.enabled=false",
        "thi360.s3.mode=memory",
        "spring.datasource.hikari.maximum-pool-size=4"
    })
    class SandboxModeInDevProfile {

        // Identity và classroom seed cần Keycloak; test chỉ kiểm tra condition của /mock-vnpay trong profile dev.
        @MockitoBean
        private DevDataInitializer devDataInitializer;

        @MockitoBean
        private DevClassSeeder devClassSeeder;

        @Autowired
        private MockMvc sandboxMockMvc;

        @Test
        void mockVnpayReturns404WhenModeIsSandboxEvenInDevProfile() throws Exception {
            sandboxMockMvc.perform(get("/mock-vnpay"))
                    .andExpect(status().isNotFound());
            sandboxMockMvc.perform(get("/mock-vnpay/action").param("outcome", "success"))
                    .andExpect(status().isNotFound());
        }
    }

    /**
     * Tái lập cách servlet container giải mã query {@code application/x-www-form-urlencoded}
     * ({@code +} -> khoảng trắng, {@code %XX} -> UTF-8) trước khi đưa vào {@code @RequestParam}.
     */
    private static MockHttpServletRequestBuilder getDecodedUrl(String fullUrl) {
        URI uri = URI.create(fullUrl);
        MockHttpServletRequestBuilder builder = get(uri.getRawPath());
        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                builder.param(k, v);
            }
        }
        return builder;
    }

    private static Map<String, String> parseQueryParams(String url) {
        String query = URI.create(url).getRawQuery();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }

    private static String extractSuccessIpnUrl(String html) {
        Matcher matcher = Pattern.compile("data-mock-action=\"success\"\\s+data-ipn-url=\"([^\"]+)\"")
                .matcher(html);
        assertThat(matcher.find()).as("success data-ipn-url present in HTML").isTrue();
        return HtmlUtils.htmlUnescape(matcher.group(1));
    }
}
