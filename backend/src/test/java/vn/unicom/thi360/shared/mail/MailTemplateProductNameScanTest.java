package vn.unicom.thi360.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MailTemplateProductNameScanTest {

    // Case-sensitive so lowercase prototype domain text like "thi360.vn" (replaced by th:text at render time) is ignored.
    private static final Pattern HARDCODED_PRODUCT_NAME = Pattern.compile("Thi" + "[\\s\\u00A0]*" + "360");

    @Test
    void patternMatchesHardcodedBrandAndIgnoresLowercaseDomainFallback() {
        assertThat(HARDCODED_PRODUCT_NAME.matcher("Thi" + "360").find()).isTrue();
        assertThat(HARDCODED_PRODUCT_NAME.matcher("Thi" + " 360").find()).isTrue();
        assertThat(HARDCODED_PRODUCT_NAME.matcher("Thi" + "\u00A0360").find()).isTrue();
        assertThat(HARDCODED_PRODUCT_NAME.matcher("thi360.vn").find()).isFalse();
        assertThat(HARDCODED_PRODUCT_NAME.matcher("https://thi360.vn/reset-password").find()).isFalse();
    }

    @Test
    void mailTemplatesContainNoHardcodedProductName() throws IOException {
        Path templatesDir = resolveTemplatesDir();
        assertThat(Files.isDirectory(templatesDir)).as("mail templates directory %s", templatesDir).isTrue();

        List<String> violations = new ArrayList<>();
        int scannedFiles = 0;

        try (Stream<Path> walk = Files.walk(templatesDir)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                scannedFiles += 1;
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String[] lines = content.split("\\R", -1);
                String relative = templatesDir.relativize(file).toString().replace('\\', '/');
                for (int i = 0; i < lines.length; i++) {
                    if (HARDCODED_PRODUCT_NAME.matcher(lines[i]).find()) {
                        violations.add("mail/templates/" + relative + ":" + (i + 1) + ": " + lines[i].strip());
                    }
                }
            }
        }

        assertThat(scannedFiles).isGreaterThanOrEqualTo(MailTemplateRenderer.requiredFiles().size());
        assertThat(violations)
                .as("Hardcoded product name occurrences under backend/src/main/resources/mail/templates/")
                .isEmpty();
    }

    private static Path resolveTemplatesDir() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path fromBackend = cwd.resolve("src/main/resources/mail/templates");
        if (Files.isDirectory(fromBackend)) {
            return fromBackend;
        }
        Path fromRoot = cwd.resolve("backend/src/main/resources/mail/templates");
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        throw new IllegalStateException("Cannot resolve mail templates directory from working directory: " + cwd);
    }
}
