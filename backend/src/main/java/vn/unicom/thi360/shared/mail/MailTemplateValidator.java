package vn.unicom.thi360.shared.mail;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;

/**
 * Kiểm lúc khởi động: thiếu tệp mẫu nào thì ứng dụng không khởi động và thông báo nêu tên tệp,
 * thay vì để lỗi lộ ra khi người dùng bấm gửi mã.
 */
public class MailTemplateValidator implements InitializingBean {

    private final ApplicationContext applicationContext;
    private final String templatesLocation;

    public MailTemplateValidator(ApplicationContext applicationContext, String templatesLocation) {
        this.applicationContext = applicationContext;
        this.templatesLocation = templatesLocation;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> missing = MailTemplateRenderer.requiredFiles().stream()
                .filter(file -> !applicationContext.getResource(templatesLocation + file).exists())
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing mail template file(s) under " + templatesLocation + ": " + String.join(", ", missing));
        }
    }
}
