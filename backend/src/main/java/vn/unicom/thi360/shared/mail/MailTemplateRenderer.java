package vn.unicom.thi360.shared.mail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Dựng thư từ mẫu Thymeleaf {@code <kind>.<vi|en>.html} và {@code <kind>.<vi|en>.txt}.
 *
 * <ul>
 *   <li>Tiêu đề là dòng đầu của tệp {@code .txt}; phần còn lại là thân văn bản.
 *   <li>HTML dùng {@code th:text}, nên giá trị trong model được thoát; mẫu không dùng {@code th:utext}.
 *   <li>Mẫu HTML nhúng layout chung {@code layout.<locale>.html} (màu thương hiệu theo DESIGN.md).
 * </ul>
 *
 * Biến có sẵn cho mẫu ngoài model: {@code webUrl}, {@code productName}, {@code mailSubject} (chỉ ở mẫu HTML).
 * Ba tên này luôn thắng khóa cùng tên trong model.
 */
public class MailTemplateRenderer {

    static final String LAYOUT = "layout";

    private final SpringTemplateEngine engine = new SpringTemplateEngine();
    private final String webUrl;
    private final String productName;

    public MailTemplateRenderer(
            ApplicationContext applicationContext, String templatesLocation, String webUrl, String productName) {
        this.webUrl = webUrl;
        this.productName = MailProperties.normalizeProductName(productName);
        engine.addTemplateResolver(resolver(applicationContext, templatesLocation, "*.html", TemplateMode.HTML, 1));
        engine.addTemplateResolver(resolver(applicationContext, templatesLocation, "*.txt", TemplateMode.TEXT, 2));
    }

    private static SpringResourceTemplateResolver resolver(
            ApplicationContext applicationContext,
            String location,
            String pattern,
            TemplateMode mode,
            int order) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix(location);
        resolver.setResolvablePatterns(Set.of(pattern));
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setOrder(order);
        return resolver;
    }

    /** Tên tệp mẫu: {@code <name>.<vi|en>.<html|txt>}. */
    static String templateFile(String name, String locale, String extension) {
        return name + "." + locale + "." + extension;
    }

    /** Mọi tệp mẫu mà ứng dụng cần: layout và sáu loại thư, mỗi ngôn ngữ một bộ. */
    static List<String> requiredFiles() {
        List<String> files = new ArrayList<>();
        for (String locale : MailLocale.SUPPORTED) {
            files.add(templateFile(LAYOUT, locale, "html"));
            for (MailKind kind : MailKind.values()) {
                files.add(templateFile(kind.templateName(), locale, "html"));
                files.add(templateFile(kind.templateName(), locale, "txt"));
            }
        }
        return files;
    }

    public RenderedMail render(MailKind kind, String locale, Map<String, Object> model) {
        requireModelKeys(kind, model);
        String normalized = MailLocale.normalize(locale);

        Map<String, Object> variables = new HashMap<>(model);
        variables.put("webUrl", webUrl);
        variables.put("productName", productName);
        Context context = new Context(Locale.forLanguageTag(normalized), variables);

        String rawText = engine.process(templateFile(kind.templateName(), normalized, "txt"), context);
        String normalizedText = rawText.replace("\r\n", "\n").replace('\r', '\n');
        int firstBreak = normalizedText.indexOf('\n');
        String subject = sanitizeSubject(firstBreak < 0 ? normalizedText : normalizedText.substring(0, firstBreak));
        if (subject.isEmpty()) {
            throw new IllegalStateException("Mail template " + templateFile(kind.templateName(), normalized, "txt")
                    + " has an empty first line (the subject)");
        }
        String body = firstBreak < 0 ? "" : normalizedText.substring(firstBreak + 1).strip() + "\n";

        context.setVariable("mailSubject", subject);
        String html = engine.process(templateFile(kind.templateName(), normalized, "html"), context);
        return new RenderedMail(subject, html, body);
    }

    private static void requireModelKeys(MailKind kind, Map<String, Object> model) {
        for (String key : kind.requiredModelKeys()) {
            if (model.get(key) == null) {
                throw new IllegalArgumentException(
                        "Mail kind " + kind + " requires model key '" + key + "'");
            }
        }
    }

    /** Tiêu đề một dòng: bỏ ký tự điều khiển để giá trị người dùng không chèn được header thư. */
    private static String sanitizeSubject(String line) {
        return line.replaceAll("\\p{Cntrl}", " ").strip();
    }
}
