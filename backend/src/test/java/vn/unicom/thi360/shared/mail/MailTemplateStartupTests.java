package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

/**
 * AC-13: thiếu một tệp mẫu thì ứng dụng không khởi động và thông báo nêu tên tệp. Bộ mẫu thật được chép sang
 * thư mục tạm, bỏ đi tệp cần thử, rồi trỏ {@code thi360.mail.templates-location} tới đó.
 */
class MailTemplateStartupTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailConfiguration.class)
            .withBean(java.time.Clock.class, java.time.Clock::systemUTC)
            .withPropertyValues("thi360.web-url=https://thi360.example.test", "spring.mail.host=localhost", "thi360.mail.tls=none");

    @TempDir
    Path templates;

    @Test
    void completeTemplateSetStarts() throws IOException {
        copyAllTemplates();

        runner.withPropertyValues(location()).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shippedTemplatesAreComplete() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void missingFileStopsStartupAndIsNamed() throws IOException {
        for (String missing : MailTemplateRenderer.requiredFiles()) {
            copyAllTemplates();
            Files.delete(templates.resolve(missing));

            runner.withPropertyValues(location()).run(context -> {
                assertThat(context).as("startup without %s", missing).hasFailed();
                assertThat(rootMessage(context.getStartupFailure())).contains(missing);
            });

            Files.write(templates.resolve(missing), new byte[0]);
        }
    }

    @Test
    void everyMissingFileIsNamedAtOnce() throws IOException {
        copyAllTemplates();
        Files.delete(templates.resolve("password_reset.en.txt"));
        Files.delete(templates.resolve("layout.vi.html"));

        runner.withPropertyValues(location()).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootMessage(context.getStartupFailure()))
                    .contains("password_reset.en.txt")
                    .contains("layout.vi.html");
        });
    }

    @Test
    void requiredFilesCoverEveryKindLocaleAndFormat() {
        assertThat(MailTemplateRenderer.requiredFiles())
                .hasSize(2 * (1 + MailKind.values().length * 2))
                .contains("layout.vi.html", "layout.en.html", "email_verify.vi.html", "email_verify.en.txt",
                        "refund_done.vi.txt", "order_fulfilled.en.html");
    }

    private String location() {
        return "thi360.mail.templates-location=" + templates.toUri();
    }

    private void copyAllTemplates() throws IOException {
        for (String file : MailTemplateRenderer.requiredFiles()) {
            try (var in = new ClassPathResource("mail/templates/" + file).getInputStream()) {
                Files.copy(in, templates.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String rootMessage(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }
}
