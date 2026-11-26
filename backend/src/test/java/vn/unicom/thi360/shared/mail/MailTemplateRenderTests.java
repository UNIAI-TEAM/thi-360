package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;

class MailTemplateRenderTests {

    private static final String WEB_URL = "https://thi360.example.test";
    private static final String HOSTILE = "<script>alert('x')</script> & \"quoted\"";

    private static GenericApplicationContext context;
    private static MailTemplateRenderer renderer;

    @BeforeAll
    static void startContext() {
        context = new GenericApplicationContext();
        context.refresh();
        renderer = new MailTemplateRenderer(
                context, "classpath:mail/templates/", WEB_URL, MailProperties.DEFAULT_PRODUCT_NAME);
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    void modelValuesAreHtmlEscaped() {
        for (MailKind kind : MailKind.values()) {
            for (String locale : MailLocale.SUPPORTED) {
                Map<String, Object> model = new HashMap<>(MailSamples.model(kind));
                model.put("fullName", HOSTILE);
                // Mọi giá trị chữ khác trong model cũng phải được thoát, không chỉ tên.
                for (String key : kind.requiredModelKeys()) {
                    if (model.get(key) instanceof String && !key.equals("resetUrl")) {
                        model.put(key, HOSTILE);
                    }
                }

                RenderedMail mail = renderer.render(kind, locale, model);

                String where = kind + "/" + locale;
                assertThat(mail.html()).as("html of %s", where)
                        .doesNotContain("<script>")
                        .doesNotContain("alert('x')")
                        .contains("&lt;script&gt;");
            }
        }
    }

    @Test
    void escapingCoversTheItemsOfAnOrderToo() {
        Map<String, Object> model = new HashMap<>(MailSamples.model(MailKind.ORDER_FULFILLED));
        model.put("items", List.of(Map.<String, Object>of("name", HOSTILE)));

        RenderedMail mail = renderer.render(MailKind.ORDER_FULFILLED, "vi", model);

        assertThat(mail.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void orderFulfilledListsEveryItemInBothFormats() {
        for (String locale : MailLocale.SUPPORTED) {
            RenderedMail mail = renderer.render(MailKind.ORDER_FULFILLED, locale, MailSamples.model(MailKind.ORDER_FULFILLED));

            for (String item : MailSamples.ORDER_ITEMS) {
                assertThat(mail.html()).as("html lists '%s' (%s)", item, locale).contains(item);
                assertThat(mail.text()).as("text lists '%s' (%s)", item, locale).contains("- " + item);
            }
        }
    }

    @Test
    void orderFulfilledWithOneItemListsJustThatItem() {
        Map<String, Object> model = new HashMap<>(MailSamples.model(MailKind.ORDER_FULFILLED));
        model.put("items", List.of(Map.<String, Object>of("name", "Đề lẻ duy nhất")));

        RenderedMail mail = renderer.render(MailKind.ORDER_FULFILLED, "vi", model);

        assertThat(mail.text()).contains("- Đề lẻ duy nhất");
        assertThat(mail.text()).doesNotContain(MailSamples.ORDER_ITEMS.getFirst());
    }

    @Test
    void subjectIsFirstLineOfText() throws IOException {
        for (MailKind kind : MailKind.values()) {
            for (String locale : MailLocale.SUPPORTED) {
                String where = kind + "/" + locale;
                String firstLine = new ClassPathResource("mail/templates/" + kind.templateName() + "." + locale + ".txt")
                        .getContentAsString(StandardCharsets.UTF_8)
                        .lines()
                        .findFirst()
                        .orElseThrow()
                        .strip()
                        .replace("[(${productName})]", MailProperties.DEFAULT_PRODUCT_NAME);

                RenderedMail mail = renderer.render(kind, locale, MailSamples.model(kind));

                assertThat(mail.subject()).as("subject of %s", where).isEqualTo(firstLine).isNotBlank();
                assertThat(mail.text()).as("text of %s", where).doesNotContain(mail.subject());
                assertThat(mail.text().lines()).as("text of %s", where).doesNotContain(mail.subject());
                assertThat(mail.text()).as("text of %s starts with its greeting, not a blank line", where)
                        .isNotEmpty()
                        .satisfies(text -> assertThat(text.charAt(0)).isNotEqualTo('\n'));
                assertThat(mail.html()).as("html title of %s", where).contains("<title>" + mail.subject() + "</title>");
            }
        }
    }

    @Test
    void subjectCannotBeUsedToInjectHeaders(@TempDir Path templates) throws IOException {
        // Mẫu đưa giá trị người dùng vào dòng tiêu đề; giá trị chứa xuống dòng không được tạo thêm dòng tiêu đề.
        for (String file : MailTemplateRenderer.requiredFiles()) {
            try (var in = new ClassPathResource("mail/templates/" + file).getInputStream()) {
                Files.copy(in, templates.resolve(file));
            }
        }
        Files.writeString(templates.resolve("email_verify.en.txt"), "Code for [(${fullName})]\nbody [(${code})]\n");
        MailTemplateRenderer custom = new MailTemplateRenderer(
                context, templates.toUri().toString(), WEB_URL, MailProperties.DEFAULT_PRODUCT_NAME);
        Map<String, Object> model = new HashMap<>(MailSamples.model(MailKind.EMAIL_VERIFY));
        model.put("fullName", "Alex\r\nBcc: attacker@example.test");

        RenderedMail mail = custom.render(MailKind.EMAIL_VERIFY, "en", model);

        assertThat(mail.subject()).doesNotContain("\n").doesNotContain("\r");
        assertThat(mail.subject()).startsWith("Code for Alex");
        assertThat(mail.text()).contains("482913");
    }

    @Test
    void unknownOrMissingLocaleFallsBackToVietnamese() {
        RenderedMail fallback = renderer.render(MailKind.EMAIL_VERIFY, "fr", MailSamples.model(MailKind.EMAIL_VERIFY));
        RenderedMail vietnamese = renderer.render(MailKind.EMAIL_VERIFY, "vi", MailSamples.model(MailKind.EMAIL_VERIFY));
        RenderedMail english = renderer.render(MailKind.EMAIL_VERIFY, "en-US", MailSamples.model(MailKind.EMAIL_VERIFY));

        assertThat(fallback.subject()).isEqualTo(vietnamese.subject());
        assertThat(renderer.render(MailKind.EMAIL_VERIFY, null, MailSamples.model(MailKind.EMAIL_VERIFY)).subject())
                .isEqualTo(vietnamese.subject());
        assertThat(english.subject()).isNotEqualTo(vietnamese.subject());
    }

    @Test
    void layoutUsesBrandColoursAndFooterLink() {
        RenderedMail mail = renderer.render(MailKind.PASSWORD_RESET, "en", MailSamples.model(MailKind.PASSWORD_RESET));

        // DESIGN.md: primary-800 cho thanh đầu thư, primary-fill cho nút.
        assertThat(mail.html()).contains("#065F46").contains("#047857").contains("href=\"" + WEB_URL + "\"");
        assertThat(mail.html()).contains("href=\"" + MailSamples.RESET_URL + "\"");
    }

    @Test
    void missingModelKeyIsRejectedByTheRenderer() {
        for (MailKind kind : MailKind.values()) {
            for (String missing : kind.requiredModelKeys()) {
                Map<String, Object> model = new HashMap<>(MailSamples.model(kind));
                model.remove(missing);

                assertThatThrownBy(() -> renderer.render(kind, "vi", model))
                        .as("%s without '%s'", kind, missing)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(missing);
            }
        }
    }

    @Test
    void defaultProductNameEverywhere() {
        assertProductNameEverywhere(renderer, MailProperties.DEFAULT_PRODUCT_NAME, false);
    }

    @Test
    void configuredProductNameEverywhere() {
        MailTemplateRenderer custom =
                new MailTemplateRenderer(context, "classpath:mail/templates/", WEB_URL, "Acme Học");
        assertProductNameEverywhere(custom, "Acme Học", true);
    }

    @Test
    void productNameIsHtmlEscaped() {
        MailTemplateRenderer custom =
                new MailTemplateRenderer(context, "classpath:mail/templates/", WEB_URL, HOSTILE);
        RenderedMail mail = custom.render(MailKind.PASSWORD_RESET, "en", MailSamples.model(MailKind.PASSWORD_RESET));

        assertThat(mail.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    private static void assertProductNameEverywhere(
            MailTemplateRenderer target, String productName, boolean forbidDefaultBrand) {
        String spacedLegacy = "Thi" + " 360";
        String headerPattern = "#065F46[^>]*>\\s*" + java.util.regex.Pattern.quote(productName) + "\\s*</td>";

        for (MailKind kind : MailKind.values()) {
            for (String locale : MailLocale.SUPPORTED) {
                String where = kind + "/" + locale;
                RenderedMail mail = target.render(kind, locale, MailSamples.model(kind));

                assertThat(mail.subject()).as("subject of %s", where)
                        .contains(productName)
                        .doesNotContain(spacedLegacy)
                        .doesNotContain("productName");

                List<String> nonBlankLines = mail.text().lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .toList();
                assertThat(nonBlankLines).as("text lines of %s", where).isNotEmpty();
                assertThat(nonBlankLines.getLast()).as("text signature of %s", where).isEqualTo(productName);
                assertThat(mail.text()).as("text of %s", where)
                        .doesNotContain(spacedLegacy)
                        .doesNotContain("productName");

                String footerPhrase = locale.equals("vi")
                        ? "Đây là thư tự động từ " + productName + "."
                        : "This is an automated message from " + productName + ".";
                assertThat(mail.html()).as("html of %s", where)
                        .contains("<title>" + mail.subject() + "</title>")
                        .containsPattern(headerPattern)
                        .contains(footerPhrase)
                        .doesNotContain(spacedLegacy)
                        .doesNotContain("productName");

                if (kind == MailKind.EMAIL_CHANGED_NOTICE || kind == MailKind.PASSWORD_RESET) {
                    String bodyBeforeSignature = String.join("\n", nonBlankLines.subList(0, nonBlankLines.size() - 1));
                    assertThat(bodyBeforeSignature).as("text body of %s", where).contains(productName);
                    String bodyPhrase = locale.equals("vi")
                            ? "tài khoản " + productName
                            : "your " + productName + " account";
                    assertThat(mail.html()).as("html body of %s", where).contains(bodyPhrase);
                }

                if (forbidDefaultBrand) {
                    assertThat(mail.subject()).as("subject of %s has no default brand", where)
                            .doesNotContain(MailProperties.DEFAULT_PRODUCT_NAME);
                    assertThat(mail.text()).as("text of %s has no default brand", where)
                            .doesNotContain(MailProperties.DEFAULT_PRODUCT_NAME);
                    assertThat(mail.html()).as("html of %s has no default brand", where)
                            .doesNotContain(MailProperties.DEFAULT_PRODUCT_NAME);
                }
            }
        }
    }
}
