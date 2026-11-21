package vn.unicom.thi360.commerce.payment.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import vn.unicom.thi360.commerce.payment.gateway.MockVnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayConfiguration;
import vn.unicom.thi360.commerce.payment.gateway.VnpayGateway;
import vn.unicom.thi360.commerce.payment.gateway.VnpayMode;

/**
 * AC-21 & AC-22: Kiểm tra khởi động {@code SANDBOX} / {@code PRODUCTION}: đủ 4 biến thì nối
 * {@link RealVnpayGateway}, thiếu từng biến hoặc sai chế độ/URL thì dừng khởi động, nêu tên biến
 * và tuyệt đối không in giá trị bí mật hay giá trị của biến khác.
 */
class VnpayRealModeStartupTest {

    private static final String TMN_PROP = "thi360.vnpay.tmn-code";
    private static final String SECRET_PROP = "thi360.vnpay.hash-secret";
    private static final String PAY_URL_PROP = "thi360.vnpay.pay-url";
    private static final String API_URL_PROP = "thi360.vnpay.api-url";

    private static final String SENTINEL_TMN = "SENTINEL_TMN_XYZ_111";
    private static final String SENTINEL_SECRET = "SENTINEL_SECRET_XYZ_999";
    private static final String SENTINEL_PAY_URL = "https://sentinel-pay-222.example/paymentv2/vpcpay.html";
    private static final String SENTINEL_API_URL = "https://sentinel-api-333.example/merchant_webapi/api/transaction";

    private record RequiredVarCase(String springProp, String envVarName) {
    }

    private static final List<RequiredVarCase> REQUIRED_VARS = List.of(
            new RequiredVarCase(TMN_PROP, "THI360_VNPAY_TMN_CODE"),
            new RequiredVarCase(SECRET_PROP, "THI360_VNPAY_HASH_SECRET"),
            new RequiredVarCase(PAY_URL_PROP, "THI360_VNPAY_PAY_URL"),
            new RequiredVarCase(API_URL_PROP, "THI360_VNPAY_API_URL")
    );

    @Test
    void sandboxAndProductionStartCleanlyAndWireRealVnpayGateway() {
        for (String mode : List.of("SANDBOX", "PRODUCTION", " sandbox ")) {
            runner()
                    .withPropertyValues(
                            "spring.profiles.active=api,prod",
                            "thi360.vnpay.mode=" + mode,
                            TMN_PROP + "=" + SENTINEL_TMN,
                            SECRET_PROP + "=" + SENTINEL_SECRET,
                            PAY_URL_PROP + "=" + SENTINEL_PAY_URL,
                            API_URL_PROP + "=" + SENTINEL_API_URL)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(VnpayGateway.class);
                        assertThat(context.getBean(VnpayGateway.class)).isInstanceOf(RealVnpayGateway.class);
                        assertThat(context.getBean(VnpayGateway.class)).isNotInstanceOf(MockVnpayGateway.class);
                    });
        }
    }

    @Test
    void missingEachRequiredVarInSandboxOrProductionFailsWithoutLeakingOtherValues() {
        for (String mode : List.of("SANDBOX", "PRODUCTION")) {
            for (RequiredVarCase omitted : REQUIRED_VARS) {
                Map<String, String> allProps = new LinkedHashMap<>();
                allProps.put(TMN_PROP, SENTINEL_TMN);
                allProps.put(SECRET_PROP, SENTINEL_SECRET);
                allProps.put(PAY_URL_PROP, SENTINEL_PAY_URL);
                allProps.put(API_URL_PROP, SENTINEL_API_URL);
                allProps.remove(omitted.springProp());

                ApplicationContextRunner ctxRunner = runner()
                        .withPropertyValues("spring.profiles.active=api,prod", "thi360.vnpay.mode=" + mode);
                for (Map.Entry<String, String> entry : allProps.entrySet()) {
                    ctxRunner = ctxRunner.withPropertyValues(entry.getKey() + "=" + entry.getValue());
                }

                ctxRunner.run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    String fullTrace = stackTraceOf(failure);
                    assertThat(failure.getMessage()).contains(omitted.envVarName());
                    assertThat(fullTrace)
                            .doesNotContain(SENTINEL_TMN)
                            .doesNotContain(SENTINEL_SECRET)
                            .doesNotContain(SENTINEL_PAY_URL)
                            .doesNotContain(SENTINEL_API_URL);
                });

                MockEnvironment mockEnv = new MockEnvironment().withProperty("THI360_VNPAY_MODE", mode);
                allProps.forEach((k, v) -> mockEnv.setProperty(toEnvName(k), v));
                assertThatThrownBy(() -> VnpayRealModeStartupGuard.validate(mockEnv))
                        .isInstanceOf(IllegalStateException.class)
                        .satisfies(ex -> {
                            assertThat(ex.getMessage()).contains(omitted.envVarName());
                            String trace = stackTraceOf(ex);
                            assertThat(trace)
                                    .doesNotContain(SENTINEL_TMN)
                                    .doesNotContain(SENTINEL_SECRET)
                                    .doesNotContain(SENTINEL_PAY_URL)
                                    .doesNotContain(SENTINEL_API_URL);
                        });
            }
        }
    }

    @Test
    void invalidModeOrMalformedUrlsFailStartupWithoutPrintingSecrets() {
        runner()
                .withPropertyValues(
                        "spring.profiles.active=api,prod",
                        "thi360.vnpay.mode=BOGUS",
                        TMN_PROP + "=" + SENTINEL_TMN,
                        SECRET_PROP + "=" + SENTINEL_SECRET,
                        PAY_URL_PROP + "=" + SENTINEL_PAY_URL,
                        API_URL_PROP + "=" + SENTINEL_API_URL)
                .run(context -> {
                    assertThat(context).hasFailed();
                    String trace = stackTraceOf(context.getStartupFailure());
                    assertThat(trace)
                            .doesNotContain(SENTINEL_TMN)
                            .doesNotContain(SENTINEL_SECRET)
                            .doesNotContain(SENTINEL_PAY_URL)
                            .doesNotContain(SENTINEL_API_URL);
                });

        MockEnvironment invalidModeEnv = new MockEnvironment()
                .withProperty("THI360_VNPAY_MODE", "BOGUS")
                .withProperty("THI360_VNPAY_HASH_SECRET", SENTINEL_SECRET);
        assertThatThrownBy(() -> VnpayRealModeStartupGuard.validate(invalidModeEnv))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("THI360_VNPAY_MODE");
                    assertThat(stackTraceOf(ex)).doesNotContain(SENTINEL_SECRET);
                });

        String badPayUrl = "ftp://malformed-pay-sentinel.example/pay";
        MockEnvironment badPayUrlEnv = new MockEnvironment()
                .withProperty("THI360_VNPAY_MODE", "SANDBOX")
                .withProperty("THI360_VNPAY_TMN_CODE", SENTINEL_TMN)
                .withProperty("THI360_VNPAY_HASH_SECRET", SENTINEL_SECRET)
                .withProperty("THI360_VNPAY_PAY_URL", badPayUrl)
                .withProperty("THI360_VNPAY_API_URL", SENTINEL_API_URL);
        assertThatThrownBy(() -> VnpayRealModeStartupGuard.validate(badPayUrlEnv))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("THI360_VNPAY_PAY_URL");
                    assertThat(stackTraceOf(ex))
                            .doesNotContain(badPayUrl)
                            .doesNotContain(SENTINEL_TMN)
                            .doesNotContain(SENTINEL_SECRET)
                            .doesNotContain(SENTINEL_API_URL);
                });

        String syntaxBadApiUrl = "https://bad host/api?x=[1";
        MockEnvironment badApiUrlEnv = new MockEnvironment()
                .withProperty("THI360_VNPAY_MODE", "PRODUCTION")
                .withProperty("THI360_VNPAY_TMN_CODE", SENTINEL_TMN)
                .withProperty("THI360_VNPAY_HASH_SECRET", SENTINEL_SECRET)
                .withProperty("THI360_VNPAY_PAY_URL", SENTINEL_PAY_URL)
                .withProperty("THI360_VNPAY_API_URL", syntaxBadApiUrl);
        assertThatThrownBy(() -> VnpayRealModeStartupGuard.validate(badApiUrlEnv))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("THI360_VNPAY_API_URL");
                    assertThat(stackTraceOf(ex))
                            .doesNotContain(syntaxBadApiUrl)
                            .doesNotContain(SENTINEL_TMN)
                            .doesNotContain(SENTINEL_SECRET)
                            .doesNotContain(SENTINEL_PAY_URL);
                });

        // R1-26: từ chối #fragment, cổng ngoài [1..65535], và http:// ở PRODUCTION (SANDBOX vẫn cho http://).
        for (String mode : List.of("SANDBOX", "PRODUCTION")) {
            for (String invalidUrl : List.of(
                    "https://sentinel-fragment.example/pay#frag",
                    "https://sentinel-port.example:70000/pay",
                    "https://sentinel-zero-port.example:0/pay")) {
                MockEnvironment invalidUrlEnv = new MockEnvironment()
                        .withProperty("THI360_VNPAY_MODE", mode)
                        .withProperty("THI360_VNPAY_TMN_CODE", SENTINEL_TMN)
                        .withProperty("THI360_VNPAY_HASH_SECRET", SENTINEL_SECRET)
                        .withProperty("THI360_VNPAY_PAY_URL", invalidUrl)
                        .withProperty("THI360_VNPAY_API_URL", SENTINEL_API_URL);
                assertThatThrownBy(() -> VnpayRealModeStartupGuard.validate(invalidUrlEnv))
                        .isInstanceOf(IllegalStateException.class)
                        .satisfies(ex -> {
                            assertThat(ex.getMessage()).contains("THI360_VNPAY_PAY_URL");
                            assertThat(stackTraceOf(ex))
                                    .doesNotContain(invalidUrl)
                                    .doesNotContain(SENTINEL_TMN)
                                    .doesNotContain(SENTINEL_SECRET);
                        });
            }
        }

        String httpPayUrl = "http://sentinel-http.example:8080/pay";
        String httpApiUrl = "http://sentinel-http.example:8080/api";
        MockEnvironment prodHttpPayEnv = new MockEnvironment()
                .withProperty("THI360_VNPAY_MODE", "PRODUCTION")
                .withProperty("THI360_VNPAY_TMN_CODE", SENTINEL_TMN)
                .withProperty("THI360_VNPAY_HASH_SECRET", SENTINEL_SECRET)
                .withProperty("THI360_VNPAY_PAY_URL", httpPayUrl)
                .withProperty("THI360_VNPAY_API_URL", SENTINEL_API_URL);
        assertThatThrownBy(() -> VnpayRealModeStartupGuard.validate(prodHttpPayEnv))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("THI360_VNPAY_PAY_URL").contains("https");
                    assertThat(stackTraceOf(ex))
                            .doesNotContain(httpPayUrl)
                            .doesNotContain(SENTINEL_SECRET);
                });

        MockEnvironment prodHttpApiEnv = new MockEnvironment()
                .withProperty("THI360_VNPAY_MODE", "PRODUCTION")
                .withProperty("THI360_VNPAY_TMN_CODE", SENTINEL_TMN)
                .withProperty("THI360_VNPAY_HASH_SECRET", SENTINEL_SECRET)
                .withProperty("THI360_VNPAY_PAY_URL", SENTINEL_PAY_URL)
                .withProperty("THI360_VNPAY_API_URL", httpApiUrl);
        assertThatThrownBy(() -> VnpayRealModeStartupGuard.validate(prodHttpApiEnv))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("THI360_VNPAY_API_URL").contains("https");
                    assertThat(stackTraceOf(ex))
                            .doesNotContain(httpApiUrl)
                            .doesNotContain(SENTINEL_SECRET);
                });

        MockEnvironment sandboxHttpEnv = new MockEnvironment()
                .withProperty("THI360_VNPAY_MODE", "SANDBOX")
                .withProperty("THI360_VNPAY_TMN_CODE", SENTINEL_TMN)
                .withProperty("THI360_VNPAY_HASH_SECRET", SENTINEL_SECRET)
                .withProperty("THI360_VNPAY_PAY_URL", httpPayUrl)
                .withProperty("THI360_VNPAY_API_URL", httpApiUrl);
        assertThat(VnpayRealModeStartupGuard.validate(sandboxHttpEnv)).isEqualTo(VnpayMode.SANDBOX);

        MockEnvironment mockEnv = new MockEnvironment().withProperty("THI360_VNPAY_MODE", "MOCK");
        assertThat(VnpayRealModeStartupGuard.validate(mockEnv)).isEqualTo(VnpayMode.MOCK);
    }

    @Test
    void devProfileWithMockStillWiresMockVnpayGateway() {
        runner()
                .withPropertyValues("spring.profiles.active=api,dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VnpayGateway.class);
                    assertThat(context.getBean(VnpayGateway.class)).isInstanceOf(MockVnpayGateway.class);
                });
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(
                        VnpayConfiguration.class,
                        VnpayRealConfiguration.class,
                        RealVnpayGateway.class,
                        MockVnpayGateway.class,
                        VnpayRealSigner.class);
    }

    private static String toEnvName(String springProp) {
        return switch (springProp) {
            case TMN_PROP -> "THI360_VNPAY_TMN_CODE";
            case SECRET_PROP -> "THI360_VNPAY_HASH_SECRET";
            case PAY_URL_PROP -> "THI360_VNPAY_PAY_URL";
            case API_URL_PROP -> "THI360_VNPAY_API_URL";
            default -> throw new IllegalArgumentException(springProp);
        };
    }

    private static String stackTraceOf(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
