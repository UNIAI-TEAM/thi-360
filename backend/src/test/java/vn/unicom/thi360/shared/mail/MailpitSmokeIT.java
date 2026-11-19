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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.support.MailOutbox;

/**
 * AC-21: thư đi tới Mailpit của compose dev. Chạy đúng image {@code axllent/mailpit:v1.27} của
 * {@code infra/docker-compose.yml}; cổng SMTP của container thay cho {@code THI360_SMTP_PORT}
 * ({@code spring.mail.port}), thư đi qua {@code MailQueue} và worker như ở dev, rồi đọc lại bằng API của Mailpit.
 */
class MailpitSmokeIT extends AbstractIntegrationTest {

    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mailpitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("thi360.mail.tls", () -> "none");
    }

    @Autowired
    private MailQueue queue;

    @Autowired
    private MailDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void mailReachesMailpit() throws Exception {
        String to = "u-" + UUID.randomUUID() + "@example.test";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> queue.enqueue(
                MailKind.EMAIL_VERIFY, to, "vi", MailSamples.model(MailKind.EMAIL_VERIFY, "246810"), null));
        MailOutbox.Mail queued = new MailOutbox(jdbc).require(to, MailKind.EMAIL_VERIFY);

        // Đồng hồ thật: lô gồm thư của ca này và có thể vài thư tồn đọng vô hại tới example.test.
        for (int round = 0; round < 5 && new MailOutbox(jdbc).require(to, MailKind.EMAIL_VERIFY).sentAt() == null; round++) {
            dispatcher.dispatchBatch();
        }

        JsonNode found = awaitMailpitMessage(to);
        assertThat(found.get("Subject").asText()).isEqualTo(queued.subject());
        assertThat(found.get("To").get(0).get("Address").asText()).isEqualTo(to);
        assertThat(new MailOutbox(jdbc).require(to, MailKind.EMAIL_VERIFY).sentAt()).isNotNull();
    }

    private JsonNode awaitMailpitMessage(String to) {
        HttpClient http = HttpClient.newHttpClient();
        URI search = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025)
                + "/api/v1/search?query=" + URLEncoder.encode("to:" + to, StandardCharsets.UTF_8));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode[] result = new JsonNode[1];
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
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
