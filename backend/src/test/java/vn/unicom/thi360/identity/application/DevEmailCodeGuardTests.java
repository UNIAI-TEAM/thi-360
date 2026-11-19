package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** AC-26, AC-27: TM 5.3 mã OTP cố định chỉ ở profile dev. */
class DevEmailCodeGuardTests {

    @Configuration
    @EnableConfigurationProperties(DevEmailCodeProperties.class)
    @Import({DevEmailCodeGuard.class, EmailCodeGenerator.class})
    static class GuardConfig {
    }

    private ApplicationContextRunner runner(String... profiles) {
        return new ApplicationContextRunner()
                .withUserConfiguration(GuardConfig.class)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles));
    }

    /** AC-26: dev có biến thì mã cố định; không đặt biến thì mã ngẫu nhiên 6 số. */
    @Test
    void devProfileUsesFixedCode() {
        runner("dev")
                .withPropertyValues("THI360_DEV_EMAIL_CODE=123456", "thi360.identity.email-code.dev-code=123456")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EmailCodeGenerator generator = context.getBean(EmailCodeGenerator.class);
                    assertThat(generator.next()).isEqualTo("123456");
                    assertThat(generator.next()).isEqualTo("123456");
                });
        runner("dev").run(context -> {
            assertThat(context).hasNotFailed();
            EmailCodeGenerator generator = context.getBean(EmailCodeGenerator.class);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                String code = generator.next();
                assertThat(code).matches("\\d{6}");
                seen.add(code);
            }
            assertThat(seen.size()).as("random codes").isGreaterThan(1);
        });
        // Biến để trống (như .env.example) cũng là "không đặt".
        runner("dev")
                .withPropertyValues("THI360_DEV_EMAIL_CODE=", "thi360.identity.email-code.dev-code=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    /** AC-27: đặt biến ở profile khác dev thì context không khởi động, thông báo không lộ mã. */
    @Test
    void otherProfilesFailStartup() {
        for (String profile : new String[] {"test", "e2e", "api", "worker", "prod"}) {
            runner(profile)
                    .withPropertyValues("THI360_DEV_EMAIL_CODE=654321")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(rootMessages(context.getStartupFailure()))
                                .contains("THI360_DEV_EMAIL_CODE")
                                .doesNotContain("654321");
                    });
        }
        new ApplicationContextRunner()
                .withUserConfiguration(GuardConfig.class)
                .withPropertyValues("THI360_DEV_EMAIL_CODE=654321")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void devProfileRejectsMalformedCode() {
        runner("dev")
                .withPropertyValues("thi360.identity.email-code.dev-code=12ab")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void generatorIgnoresFixedCodeOutsideDev() {
        runner("test")
                .withPropertyValues("thi360.identity.email-code.dev-code=123456")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmailCodeGenerator.class).next()).matches("\\d{6}");
                });
    }

    /** R1-08: dev chung với một profile môi trường khác (prod, e2e, test) bị từ chối, bất kể thứ tự profile. */
    @Test
    void devCombinedWithAnotherEnvironmentProfileFailsStartup() {
        for (String other : new String[] {"prod", "e2e", "test"}) {
            for (String[] profiles : new String[][] {{"dev", other}, {other, "dev"}, {"api", "worker", "dev", other}}) {
                runner(profiles)
                        .withPropertyValues("THI360_DEV_EMAIL_CODE=654321")
                        .run(context -> {
                            assertThat(context).as(String.join(",", profiles)).hasFailed();
                            assertThat(rootMessages(context.getStartupFailure()))
                                    .contains("THI360_DEV_EMAIL_CODE")
                                    .contains(other)
                                    .doesNotContain("654321");
                        });
            }
        }
    }

    /** Tổ hợp dev thông thường {@code api,worker,dev} vẫn dùng mã cố định; trộn với profile khác thì mã khóa bị bỏ qua. */
    @Test
    void devCompanionProfilesKeepFixedCodeAndMixedProfilesIgnoreBoundKey() {
        runner("api", "worker", "dev")
                .withPropertyValues("THI360_DEV_EMAIL_CODE=123456", "thi360.identity.email-code.dev-code=123456")
                .run(context -> assertThat(context.getBean(EmailCodeGenerator.class).next()).isEqualTo("123456"));

        // Không có biến môi trường (không qua chốt chặn) nhưng khóa đã nối: không bao giờ dùng mã cố định khi trộn profile.
        runner("dev", "prod")
                .withPropertyValues("thi360.identity.email-code.dev-code=123456")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EmailCodeGenerator generator = context.getBean(EmailCodeGenerator.class);
                    Set<String> seen = new HashSet<>();
                    for (int i = 0; i < 20; i++) {
                        seen.add(generator.next());
                    }
                    assertThat(seen.size()).as("random codes").isGreaterThan(1);
                });
    }

    /**
     * Khối {@code dev} của {@code application.yml} nối khóa từ đúng biến {@code THI360_DEV_EMAIL_CODE}: chỉ đặt biến đó
     * (không đặt khóa đã nối) là đủ cho mã cố định ở dev, và không đặt thì mã ngẫu nhiên.
     */
    @Test
    void devYamlBindsTheKeyFromTheNamedVariableOnly() {
        runner("api", "worker", "dev")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("THI360_DEV_EMAIL_CODE=246810")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DevEmailCodeProperties.class).devCode()).isEqualTo("246810");
                    assertThat(context.getBean(EmailCodeGenerator.class).next()).isEqualTo("246810");
                });
        runner("api", "worker", "dev")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Set<String> seen = new HashSet<>();
                    for (int i = 0; i < 20; i++) {
                        seen.add(context.getBean(EmailCodeGenerator.class).next());
                    }
                    assertThat(seen.size()).as("random codes").isGreaterThan(1);
                });
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }
}
