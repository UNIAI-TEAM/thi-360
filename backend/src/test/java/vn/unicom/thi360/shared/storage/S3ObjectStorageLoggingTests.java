package vn.unicom.thi360.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.shared.api.GlobalExceptionHandler;

class S3ObjectStorageLoggingTests {

    @Test
    void s3FailureOnHttpPath_logsSingleExceptionAtBoundary_andStructuredExternalMetadataWithoutThrowable()
            throws Exception {
        int closedPort;
        try (ServerSocket ss = new ServerSocket(0)) {
            closedPort = ss.getLocalPort();
        }
        S3Properties props = new S3Properties(
                "s3",
                "http://127.0.0.1:" + closedPort,
                "thi360-test-bucket",
                "access",
                "secret",
                "us-east-1",
                false);
        S3ObjectStorage storage = new S3ObjectStorage(props);

        Logger externalLogger = (Logger) LoggerFactory.getLogger("thi360.external");
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> externalAppender = new ListAppender<>();
        ListAppender<ILoggingEvent> handlerAppender = new ListAppender<>();
        externalAppender.start();
        handlerAppender.start();
        externalLogger.addAppender(externalAppender);
        handlerLogger.addAppender(handlerAppender);

        String secretPayload = "SuperSecretPayload@123";
        try {
            StaticMessageSource messageSource = new StaticMessageSource();
            MockMvc mockMvc = MockMvcBuilders
                    .standaloneSetup(new TestS3Controller(storage, secretPayload))
                    .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                    .build();

            mockMvc.perform(get("/test/s3-fail"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

            List<ILoggingEvent> extEvents = externalAppender.list;
            assertThat(extEvents).hasSize(1);
            ILoggingEvent extEvent = extEvents.getFirst();
            assertThat(extEvent.getThrowableProxy()).isNull();
            Map<String, Object> kv = extEvent.getKeyValuePairs().stream()
                    .collect(Collectors.toMap(kvp -> kvp.key, kvp -> kvp.value));
            assertThat(kv)
                    .containsEntry("target", "s3")
                    .containsEntry("operation", "put")
                    .containsEntry("bucket", "thi360-test-bucket")
                    .containsEntry("key", "import-batches/test/secret.xlsx")
                    .containsEntry("outcome", "error")
                    .containsKey("durationMs");
            assertThat(extEvent.getFormattedMessage()).doesNotContain(secretPayload);

            List<ILoggingEvent> handlerEvents = handlerAppender.list;
            assertThat(handlerEvents).hasSize(1);
            assertThat(handlerEvents.getFirst().getThrowableProxy()).isNotNull();
            assertThat(handlerEvents.getFirst().getFormattedMessage()).doesNotContain(secretPayload);
        } finally {
            externalLogger.detachAppender(externalAppender);
            handlerLogger.detachAppender(handlerAppender);
            storage.close();
        }
    }

    @RestController
    static class TestS3Controller {
        private final ObjectStorage storage;
        private final String secret;

        TestS3Controller(ObjectStorage storage, String secret) {
            this.storage = storage;
            this.secret = secret;
        }

        @GetMapping("/test/s3-fail")
        String trigger() {
            storage.put("import-batches/test/secret.xlsx", secret.getBytes(StandardCharsets.UTF_8), "application/octet-stream");
            return "ok";
        }
    }
}
