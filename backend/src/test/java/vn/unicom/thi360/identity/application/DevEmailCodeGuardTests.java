package vn.unicom.thi360.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
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

    private static String rootMessages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }
}
