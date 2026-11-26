package vn.unicom.thi360.shared;

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

class BrandNameScanTest {

    private static final Pattern LEGACY_BRAND_PATTERN =
            Pattern.compile("thi" + "[ \\t\\u00A0]+" + "360", Pattern.CASE_INSENSITIVE);

    @Test
    void patternMatchesSpacedVariantsAndIgnoresTechnicalIdentifiers() {
        assertThat(LEGACY_BRAND_PATTERN.matcher("Thi" + " 360").find()).isTrue();
        assertThat(LEGACY_BRAND_PATTERN.matcher("THi" + " 360").find()).isTrue();
        assertThat(LEGACY_BRAND_PATTERN.matcher("THI" + " 360").find()).isTrue();
        assertThat(LEGACY_BRAND_PATTERN.matcher("Thi" + "\u00A0360").find()).isTrue();
        assertThat(LEGACY_BRAND_PATTERN.matcher("Thi" + "\t360").find()).isTrue();

        assertThat(LEGACY_BRAND_PATTERN.matcher("Thi360").find()).isFalse();
        assertThat(LEGACY_BRAND_PATTERN.matcher("thi360").find()).isFalse();
        assertThat(LEGACY_BRAND_PATTERN.matcher("thi-360").find()).isFalse();
        assertThat(LEGACY_BRAND_PATTERN.matcher("vn.unicom.thi360").find()).isFalse();
    }

    @Test
    void backendMainInfraAndEnvExampleContainNoSpacedBrandName() throws IOException {
        Path repoRoot = resolveRepoRoot();
        List<Path> targets = List.of(
                repoRoot.resolve("backend/src/main/resources"),
                repoRoot.resolve("backend/src/main/java"),
                repoRoot.resolve("infra"),
                repoRoot.resolve(".env.example"));

        List<String> violations = new ArrayList<>();
        int scannedFiles = 0;

        for (Path target : targets) {
            assertThat(Files.exists(target)).as("scan target %s", target).isTrue();
            if (Files.isRegularFile(target)) {
                scannedFiles += scanFile(repoRoot, target, violations);
            } else {
                try (Stream<Path> walk = Files.walk(target)) {
                    for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                        scannedFiles += scanFile(repoRoot, file, violations);
                    }
                }
            }
        }

        assertThat(scannedFiles).isGreaterThan(0);
        assertThat(violations)
                .as("Spaced brand name occurrences under backend/src/main, infra, .env.example")
                .isEmpty();
    }

    private static int scanFile(Path repoRoot, Path file, List<String> violations) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        for (byte b : bytes) {
            if (b == 0) {
                return 0;
            }
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\\R", -1);
        String relative = repoRoot.relativize(file).toString().replace('\\', '/');
        for (int i = 0; i < lines.length; i++) {
            if (LEGACY_BRAND_PATTERN.matcher(lines[i]).find()) {
                violations.add(relative + ":" + (i + 1) + ": " + lines[i].strip());
            }
        }
        return 1;
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(cwd.resolve("src/main/java"))) {
            return cwd.getParent();
        }
        if (Files.isDirectory(cwd.resolve("backend/src/main/java"))) {
            return cwd;
        }
        throw new IllegalStateException("Cannot resolve repository root from working directory: " + cwd);
    }
}
