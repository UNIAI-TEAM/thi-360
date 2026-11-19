package vn.unicom.thi360.shared.mail;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * Xếp thư vào hàng (TM 4). Chạy trong transaction của bên gọi: use case rollback thì không có thư.
 * Tiêu đề, HTML, văn bản được dựng ngay lúc xếp hàng, nên thiếu khóa model hay thiếu mẫu báo lỗi ở đây.
 */
@Service
public class MailQueue {

    private final EmailMessageRepository repository;
    private final MailTemplateRenderer renderer;
    private final Clock clock;

    MailQueue(EmailMessageRepository repository, MailTemplateRenderer renderer, Clock clock) {
        this.repository = repository;
        this.renderer = renderer;
        this.clock = clock;
    }

    /**
     * @param kind   loại thư
     * @param to     địa chỉ người nhận
     * @param locale {@code vi} hoặc {@code en}; rỗng hoặc ngôn ngữ khác thì dùng {@code vi}
     * @param model  giá trị cho mẫu; phải có đủ {@link MailKind#requiredModelKeys()}
     * @param userId tài khoản nhận thư, có thể {@code null}
     * @return id của thư vừa xếp hàng
     * @throws org.springframework.transaction.IllegalTransactionStateException khi không có transaction
     * @throws IllegalArgumentException khi địa chỉ sai hoặc model thiếu khóa
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(MailKind kind, String to, String locale, Map<String, Object> model, UUID userId) {
        requireAddress(to);
        String normalizedLocale = MailLocale.normalize(locale);
        RenderedMail rendered = renderer.render(kind, normalizedLocale, model);
        UUID id = Ids.newId();
        repository.save(new EmailMessage(id, kind, to.strip(), normalizedLocale, rendered, userId, Instant.now(clock)));
        return id;
    }

    private static void requireAddress(String to) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Mail recipient is required");
        }
        try {
            new InternetAddress(to.strip(), true).validate();
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Mail recipient is not a valid address", ex);
        }
    }
}
