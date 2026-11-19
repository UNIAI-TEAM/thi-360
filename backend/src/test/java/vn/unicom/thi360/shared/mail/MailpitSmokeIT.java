package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.support.MailOutbox;

/**
 * AC-21: thư đi tới Mailpit của compose dev. Chạy đúng image {@code axllent/mailpit:v1.27} của
 * {@code infra/docker-compose.yml}. Test đi đúng đường triển khai: máy chủ và cổng SMTP vào qua khóa
 * {@code THI360_SMTP_HOST}/{@code THI360_SMTP_PORT} ({@code application.yml} ánh xạ sang
 * {@code spring.mail.*}), lịch gửi thật của worker bật với đồng hồ đứng yên, và test không gọi
 * {@link MailDispatcher}: thư xếp qua {@link MailQueue}, rồi chờ Mailpit nhận và {@code sent_at} được ghi.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AbstractMailIT.MailTestConfig.class)
@TestPropertySource(
        properties = {
            "thi360.mail.scheduling.enabled=true",
            "thi360.mail.poll-delay=PT0.2S",
            "thi360.mail.tls=none"
        })
class MailpitSmokeIT extends AbstractIntegrationTest {

    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mailpitProperties(DynamicPropertyRegistry registry) {
        registry.add("THI360_SMTP_HOST", MAILPIT::getHost);
        registry.add("THI360_SMTP_PORT", () -> MAILPIT.getMappedPort(1025));
    }

    @Autowired
    private MailQueue queue;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JavaMailSenderImpl sender;

    @Test
    void mailReachesMailpit() throws Exception {
        assertThat(sender.getHost()).as("THI360_SMTP_HOST reaches the sender").isEqualTo(MAILPIT.getHost());
        assertThat(sender.getPort()).as("THI360_SMTP_PORT reaches the sender").isEqualTo(MAILPIT.getMappedPort(1025));
        String to = "u-" + UUID.randomUUID() + "@example.test";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> queue.enqueue(
                MailKind.EMAIL_VERIFY, to, "vi", MailSamples.model(MailKind.EMAIL_VERIFY, "246810"), null));
        MailOutbox outbox = new MailOutbox(jdbc);
        MailOutbox.Mail queued = outbox.require(to, MailKind.EMAIL_VERIFY);

        JsonNode found = awaitMailpitMessage(to);
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> outbox.require(to, MailKind.EMAIL_VERIFY).sentAt() != null);

        assertThat(found.get("Subject").asText()).isEqualTo(queued.subject());
        assertThat(found.get("To").get(0).get("Address").asText()).isEqualTo(to);
        assertThat(outbox.require(to, MailKind.EMAIL_VERIFY).sentAt())
                .as("sent_at follows the fixed injected clock")
                .isEqualTo(AbstractMailIT.START);
    }

    private JsonNode awaitMailpitMessage(String to) {
        HttpClient http = HttpClient.newHttpClient();
        URI search = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025)
                + "/api/v1/search?query=" + URLEncoder.encode("to:" + to, StandardCharsets.UTF_8));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode[] result = new JsonNode[1];
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            HttpResponse<String> response =
                    http.send(HttpRequest.newBuilder(search).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode messages = mapper.readTree(response.body()).get("messages");
            assertThat(messages).as("Mailpit received the mail").hasSize(1);
            result[0] = messages.get(0);
        });
        return result[0];
    }
}
