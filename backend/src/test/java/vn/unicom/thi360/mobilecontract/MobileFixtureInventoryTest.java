package vn.unicom.thi360.mobilecontract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.identity.MobileContractFixturesIT;

class MobileFixtureInventoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");
    private static final Pattern HEX_HMAC_PATTERN = Pattern.compile("(?i)vnp_SecureHash=[0-9a-f]{64,}");
    private static final Pattern BEARER_REAL_PATTERN = Pattern.compile("Bearer\\s+eyJ[A-Za-z0-9_-]+");
    private static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
            "password",
            "currentPassword",
            "oldPassword",
            "newPassword",
            "passwordHash",
            "secret",
            "secretKey",
            "clientSecret",
            "hashSecret",
            "otp",
            "otpCode",
            "verificationCode",
            "codeHash",
            "gradingError",
            "gradingGuide",
            "rubric",
            "sampleAnswer",
            "commonMistakes");

    private static final Map<String, String> NEW_PREFIX_TO_OPERATION = buildNewPrefixMap();

    private static final Set<String> EXPECTED_FIXTURES = buildExpectedFixtures();

    record FixtureIndexEntry(
            String file,
            String operationId,
            String method,
            String path,
            int status,
            String variant) {}

    @Test
    void expectedNamesPresent() throws IOException {
        Set<String> onDisk = listFixturesOnDisk();
        assertThat(onDisk)
                .as("committed fixtures in contracts/mobile/{api,auth} must match exact BE-1 inventory")
                .containsExactlyInAnyOrderElementsOf(EXPECTED_FIXTURES);
    }

    @Test
    void allFixturesCertifiedByItsAndFollowNamingRules() throws IOException {
        Set<String> certifiedUnion = new TreeSet<>();
        List<Set<String>> itSets = List.of(
                MobileContractFixturesIT.Fixtures.SET,
                MobileCatalogFixturesIT.Fixtures.SET,
                MobileCommerceFixturesIT.Fixtures.SET,
                MobileMyExamsFixturesIT.Fixtures.SET,
                MobileAttemptFixturesIT.Fixtures.SET,
                MobileAttemptContentFixturesIT.Fixtures.SET,
                MobileResultFixturesIT.Fixtures.SET,
                MobileProfileFixturesIT.Fixtures.SET);

        for (Set<String> set : itSets) {
            for (String file : set) {
                assertThat(certifiedUnion.add(file))
                        .as("duplicate fixture declared across ITs: %s", file)
                        .isTrue();
            }
        }

        Set<String> onDisk = listFixturesOnDisk();
        assertThat(onDisk)
                .as("every fixture on disk must be certified by exactly one IT")
                .containsExactlyInAnyOrderElementsOf(certifiedUnion);

        Set<String> phase1 = MobileContractFixturesIT.Fixtures.SET;
        for (String file : onDisk) {
            if (phase1.contains(file)) {
                continue;
            }
            assertThat(file).as("new fixture must live under api/: %s", file).startsWith("api/");
            assertMatchesPrefixRule(file);
        }
    }

    @Test
    void indexMatchesFolder() throws IOException {
        List<FixtureIndexEntry> entries = readIndex();
        Set<String> onDisk = listFixturesOnDisk();

        List<String> filesInOrder = entries.stream().map(FixtureIndexEntry::file).toList();
        List<String> sortedCopy = new ArrayList<>(filesInOrder);
        sortedCopy.sort(String::compareTo);
        assertThat(filesInOrder)
                .as("contracts/mobile/api/index.json must be sorted by file with no duplicates")
                .containsExactlyElementsOf(sortedCopy);

        Set<String> uniqueIndexed = new TreeSet<>(filesInOrder);
        assertThat(uniqueIndexed).hasSameSizeAs(filesInOrder);
        assertThat(uniqueIndexed)
                .as("index.json entries must match files on disk two-way")
                .containsExactlyInAnyOrderElementsOf(onDisk);
    }

    @Test
    void indexMatchesContract() throws IOException {
        List<FixtureIndexEntry> entries = readIndex();
        JsonNode spec = MAPPER.readTree(Files.readString(
                MobileFixtureCertifier.contractsMobileDir().resolve("openapi-mobile.json"),
                StandardCharsets.UTF_8));

        Map<String, OperationMeta> opsById = new HashMap<>();
        spec.path("paths").fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toUpperCase(Locale.ROOT);
                if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                    return;
                }
                JsonNode opNode = methodEntry.getValue();
                String operationId = opNode.path("operationId").asText();
                Set<Integer> statuses = new TreeSet<>();
                opNode.path("responses").fieldNames().forEachRemaining(code -> {
                    if (code.matches("\\d{3}")) {
                        statuses.add(Integer.parseInt(code));
                    }
                });
                opsById.put(operationId, new OperationMeta(operationId, method, path, statuses));
            });
        });

        Pattern statusInName = Pattern.compile("-(\\d{3})(?:-[a-z0-9-]+)?\\.json$");
        for (FixtureIndexEntry entry : entries) {
            assertThat(entry.file()).isNotBlank();
            assertThat(entry.operationId()).isNotBlank();
            assertThat(entry.method()).isNotBlank();
            assertThat(entry.path()).startsWith("/api/v1/");
            assertThat(entry.variant()).isNotNull();

            OperationMeta meta = opsById.get(entry.operationId());
            assertThat(meta)
                    .as("operationId %s in index.json for %s must exist in openapi-mobile.json", entry.operationId(), entry.file())
                    .isNotNull();
            assertThat(entry.method())
                    .as("method mismatch for %s", entry.file())
                    .isEqualTo(meta.method());
            assertThat(entry.path())
                    .as("path mismatch for %s", entry.file())
                    .isEqualTo(meta.path());
            assertThat(meta.statuses())
                    .as("status %d for %s must be declared in OpenAPI responses of %s",
                            entry.status(), entry.file(), entry.operationId())
                    .contains(entry.status());

            Matcher m = statusInName.matcher(entry.file());
            assertThat(m.find()).as("filename must carry HTTP status: %s", entry.file()).isTrue();
            assertThat(Integer.parseInt(m.group(1)))
                    .as("index.json status must equal HTTP status in filename %s", entry.file())
                    .isEqualTo(entry.status());
        }
    }

    @Test
    void noSecretsInFixtures() throws IOException {
        Path mobileDir = MobileFixtureCertifier.contractsMobileDir();
        for (String rel : listFixturesOnDisk()) {
            String raw = Files.readString(mobileDir.resolve(rel), StandardCharsets.UTF_8);
            assertThat(JWT_PATTERN.matcher(raw).find())
                    .as("%s must not contain real JWTs", rel)
                    .isFalse();
            assertThat(HEX_HMAC_PATTERN.matcher(raw).find())
                    .as("%s must not contain real 64+ hex VNPAY signatures", rel)
                    .isFalse();
            assertThat(BEARER_REAL_PATTERN.matcher(raw).find())
                    .as("%s must not contain real Bearer tokens", rel)
                    .isFalse();
            assertThat(raw)
                    .as("%s must not contain test password literal", rel)
                    .doesNotContain("Valid@1234")
                    .doesNotContain("Wrong@1234")
                    .doesNotContain("Wrong@9999");

            JsonNode root = MAPPER.readTree(raw);
            assertNoForbiddenKeys(rel, "$", root);
        }
    }

    private static void assertNoForbiddenKeys(String file, String path, JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode val = entry.getValue();
                assertThat(FORBIDDEN_SECRET_KEYS)
                        .as("forbidden secret/internal key %s at %s.%s in %s", key, path, key, file)
                        .doesNotContain(key);
                if (("accessToken".equals(key) || "refreshToken".equals(key)) && val.isTextual()) {
                    assertThat(val.asText())
                            .as("%s.%s in %s must be a static fixture placeholder", path, key, file)
                            .startsWith("fixture-");
                }
                assertNoForbiddenKeys(file, path + "." + key, val);
            });
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                assertNoForbiddenKeys(file, path + "[" + i + "]", node.get(i));
            }
        }
    }

    private static void assertMatchesPrefixRule(String rel) {
        String stem = rel.substring("api/".length(), rel.length() - ".json".length());
        for (String prefix : NEW_PREFIX_TO_OPERATION.keySet()) {
            if (stem.startsWith(prefix + "-")) {
                String rest = stem.substring(prefix.length() + 1);
                if (rest.matches("\\d{3}(-[a-z0-9]+(-[a-z0-9]+)*)?")) {
                    return;
                }
            }
        }
        throw new AssertionError("Fixture name does not match <prefix>-<status>[-<variant>].json: " + rel);
    }

    private static List<FixtureIndexEntry> readIndex() throws IOException {
        Path indexPath = MobileFixtureCertifier.contractsMobileDir().resolve("api/index.json");
        assertThat(indexPath).as("contracts/mobile/api/index.json exists").isRegularFile();
        return MAPPER.readValue(Files.readString(indexPath, StandardCharsets.UTF_8), new TypeReference<>() {});
    }

    private static Set<String> listFixturesOnDisk() throws IOException {
        Path mobileDir = MobileFixtureCertifier.contractsMobileDir();
        Set<String> found = new TreeSet<>();
        for (String sub : List.of("api", "auth")) {
            Path dir = mobileDir.resolve(sub);
            assertThat(dir).isDirectory();
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.endsWith(".json"))
                        .filter(name -> !("api".equals(sub) && "index.json".equals(name)))
                        .map(name -> sub + "/" + name)
                        .forEach(found::add);
            }
        }
        return found;
    }

    private record OperationMeta(String operationId, String method, String path, Set<Integer> statuses) {}

    private static Map<String, String> buildNewPrefixMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("storefront-filters", "getStorefrontFilters");
        m.put("storefront-products-list", "listStorefrontProducts");
        m.put("storefront-products-detail", "getStorefrontProduct");
        m.put("exam-access-query", "queryMyExamAccess");
        m.put("price-quote", "quoteMyPrice");
        m.put("orders-create", "createMyOrder");
        m.put("orders-list", "listMyOrders");
        m.put("orders-detail", "getMyOrder");
        m.put("orders-pay", "payMyOrder");
        m.put("orders-cancel", "cancelMyOrder");
        m.put("vnpay-return", "verifyVnpayReturn");
        m.put("my-exams-list", "listMyExams");
        m.put("attempts-list", "listHistory");
        m.put("catalog-options", "getOptions");
        m.put("entitlements-prepare", "prepare");
        m.put("attempts-start", "start");
        m.put("attempts-detail", "getState");
        m.put("attempts-session", "claimSession");
        m.put("attempts-heartbeat", "heartbeat");
        m.put("attempts-answers", "saveAnswers");
        m.put("attempts-finish", "finishPart");
        m.put("attempts-submit", "submit");
        m.put("attempts-choice", "confirmPartChoice");
        m.put("attempts-retry", "retryPartGeneration");
        m.put("attempts-asset", "downloadAttemptAsset");
        m.put("attempts-solution-asset", "downloadSolutionAsset");
        m.put("audio-plays-start", "openAudioPlay");
        m.put("audio-plays-progress", "updateAudioProgress");
        m.put("audio-plays-resume", "resumeAudioPlay");
        m.put("audio-plays-end", "endAudioPlay");
        m.put("attempts-result", "getResult");
        m.put("attempts-review", "getReview");
        m.put("me-profile-update", "updateMyProfile");
        m.put("me-profile", "getMyProfile");
        m.put("me-avatar-put", "replaceMyAvatar");
        m.put("me-email-verification", "sendVerification");
        m.put("me-email-verify", "verify");
        m.put("me-email-change-confirm", "confirmChange");
        m.put("me-email-change", "requestChange");
        return Map.copyOf(m);
    }

    private static Set<String> buildExpectedFixtures() {
        return Set.of(
                // Phase 1 auth (15)
                "auth/login-200.json",
                "auth/login-200-must-change-password.json",
                "auth/login-400-validation-failed.json",
                "auth/login-401-invalid-credentials.json",
                "auth/login-403-account-inactive.json",
                "auth/login-403-app-student-only.json",
                "auth/login-429-login-throttled.json",
                "auth/login-429-rate-limited.json",
                "auth/refresh-200.json",
                "auth/refresh-401-session-revoked.json",
                "auth/refresh-403-app-student-only.json",
                "auth/register-verify-201.json",
                "auth/register-verify-422-email-code-expired.json",
                "auth/register-verify-422-email-code-invalid.json",
                "auth/register-verify-429-rate-limited.json",
                // Phase 1 api (19)
                "api/change-password-400-password-weak.json",
                "api/change-password-400-wrong-old-password.json",
                "api/me-200.json",
                "api/me-401-session-revoked.json",
                "api/me-401-unauthenticated.json",
                "api/me-profile-200.json",
                "api/me-profile-200-no-email.json",
                "api/password-forgot-202.json",
                "api/password-forgot-400-validation-failed.json",
                "api/password-reset-400-password-weak.json",
                "api/password-reset-422-reset-token-invalid.json",
                "api/register-200.json",
                "api/register-400-validation-failed.json",
                "api/register-409-email-taken.json",
                "api/register-409-username-taken.json",
                "api/register-429-otp-rate-limited.json",
                "api/register-resend-200.json",
                "api/register-resend-422-email-code-expired.json",
                "api/register-resend-429-rate-limited.json",
                // 6.1 Catalog & Exam Access (11)
                "api/storefront-filters-200.json",
                "api/storefront-filters-429-rate-limited.json",
                "api/storefront-products-list-200.json",
                "api/storefront-products-list-200-empty.json",
                "api/storefront-products-list-429-rate-limited.json",
                "api/storefront-products-detail-200-exam.json",
                "api/storefront-products-detail-200-bundle.json",
                "api/storefront-products-detail-404-not-found.json",
                "api/storefront-products-detail-410-product-discontinued.json",
                "api/exam-access-query-200.json",
                "api/exam-access-query-400-validation-failed.json",
                // 6.2 Commerce & Payments (51; BE1-Q1 orders-create-201-*, BE1-Q3 omits vnpay-return-400-validation-failed)
                "api/price-quote-200.json",
                "api/price-quote-200-voucher.json",
                "api/price-quote-200-changed.json",
                "api/price-quote-404-product-not-found.json",
                "api/price-quote-410-product-discontinued.json",
                "api/price-quote-422-voucher-invalid.json",
                "api/price-quote-422-voucher-expired.json",
                "api/price-quote-422-voucher-exhausted.json",
                "api/price-quote-422-voucher-user-limit.json",
                "api/price-quote-422-voucher-not-applicable.json",
                "api/price-quote-422-voucher-not-eligible.json",
                "api/price-quote-429-rate-limited.json",
                "api/orders-create-201-redirect.json",
                "api/orders-create-201-reused.json",
                "api/orders-create-201-fulfilled.json",
                "api/orders-create-201-checking.json",
                "api/orders-create-409-price-changed.json",
                "api/orders-create-409-account-changed.json",
                "api/orders-create-409-product-unavailable.json",
                "api/orders-create-409-idempotency-key-reused.json",
                "api/orders-create-410-product-discontinued.json",
                "api/orders-create-422-voucher-invalid.json",
                "api/orders-create-422-payment-attempts-exceeded.json",
                "api/orders-list-200.json",
                "api/orders-list-200-empty.json",
                "api/orders-detail-200-pending-payment.json",
                "api/orders-detail-200-paid.json",
                "api/orders-detail-200-fulfilled.json",
                "api/orders-detail-200-replaced.json",
                "api/orders-detail-200-expired.json",
                "api/orders-detail-200-cancelled.json",
                "api/orders-detail-200-refunded.json",
                "api/orders-detail-200-payment-failed.json",
                "api/orders-detail-200-free-voucher.json",
                "api/orders-detail-200-duplicate-payment.json",
                "api/orders-detail-404-order-not-found.json",
                "api/orders-pay-200.json",
                "api/orders-pay-404-order-not-found.json",
                "api/orders-pay-404-product-not-found.json",
                "api/orders-pay-409-order-not-pending.json",
                "api/orders-pay-409-order-expired.json",
                "api/orders-pay-409-product-unavailable.json",
                "api/orders-pay-410-product-discontinued.json",
                "api/orders-pay-422-payment-attempts-exceeded.json",
                "api/orders-cancel-200.json",
                "api/orders-cancel-409-order-not-pending.json",
                "api/vnpay-return-200-succeeded.json",
                "api/vnpay-return-200-failed.json",
                "api/vnpay-return-200-pending.json",
                "api/vnpay-return-404-order-not-found.json",
                "api/vnpay-return-422-vnpay-signature-invalid.json",
                // 6.3 My Exams, History, Catalog Options (9)
                "api/my-exams-list-200.json",
                "api/my-exams-list-200-empty.json",
                "api/my-exams-list-403-forbidden.json",
                "api/attempts-list-200.json",
                "api/attempts-list-200-empty.json",
                "api/attempts-list-403-attempt-access-forbidden.json",
                "api/attempts-list-422-invalid-date-filter.json",
                "api/catalog-options-200-exam.json",
                "api/catalog-options-200-subject.json",
                // 6.4 Prepare, Start, State, Session, Heartbeat, Answers, Finish, Submit, Choice, Retry (66; BE1-Q4/Q5 omit 2)
                "api/entitlements-prepare-200-per-part.json",
                "api/entitlements-prepare-200-shared.json",
                "api/entitlements-prepare-200-free-navigation.json",
                "api/entitlements-prepare-200-pre-choice.json",
                "api/entitlements-prepare-200-exhausted-purchase.json",
                "api/entitlements-prepare-200-exhausted-compensation.json",
                "api/entitlements-prepare-200-exhausted-class.json",
                "api/entitlements-prepare-200-open-in-progress.json",
                "api/entitlements-prepare-200-open-starting.json",
                "api/entitlements-prepare-200-class.json",
                "api/entitlements-prepare-403-attempt-access-forbidden.json",
                "api/entitlements-prepare-404-not-found.json",
                "api/attempts-start-202-starting.json",
                "api/attempts-start-200-resumed.json",
                "api/attempts-start-200-aborted.json",
                "api/attempts-start-400-idempotency-key-required.json",
                "api/attempts-start-409-no-attempts-left.json",
                "api/attempts-start-409-no-serving-option.json",
                "api/attempts-start-422-choice-not-allowed.json",
                "api/attempts-start-422-subjects-invalid.json",
                "api/attempts-detail-200-starting.json",
                "api/attempts-detail-200-aborted.json",
                "api/attempts-detail-200-in-progress-shared.json",
                "api/attempts-detail-200-in-progress-per-part.json",
                "api/attempts-detail-200-awaiting-choice.json",
                "api/attempts-detail-200-generating.json",
                "api/attempts-detail-200-generation-failed.json",
                "api/attempts-detail-200-audio-playing.json",
                "api/attempts-detail-200-closed-submitted.json",
                "api/attempts-detail-200-closed-time-up.json",
                "api/attempts-detail-200-closed-choice-timeout.json",
                "api/attempts-detail-404-attempt-not-found.json",
                "api/attempts-session-200.json",
                "api/attempts-session-409-attempt-open-elsewhere.json",
                "api/attempts-session-409-attempt-not-open.json",
                "api/attempts-session-409-time-up.json",
                "api/attempts-heartbeat-200.json",
                "api/attempts-heartbeat-409-attempt-session-revoked.json",
                "api/attempts-answers-200-saved.json",
                "api/attempts-answers-200-stale.json",
                "api/attempts-answers-200-rejected-invalid-answer.json",
                "api/attempts-answers-200-rejected-not-in-scope.json",
                "api/attempts-answers-200-rejected-time-up.json",
                "api/attempts-answers-200-rejected-part-closed.json",
                "api/attempts-answers-409-attempt-session-revoked.json",
                "api/attempts-answers-429-rate-limited.json",
                "api/attempts-finish-200.json",
                "api/attempts-finish-409-time-up.json",
                "api/attempts-finish-409-part-closed.json",
                "api/attempts-finish-409-attempt-part-is-last.json",
                "api/attempts-finish-409-part-finish-not-allowed.json",
                "api/attempts-finish-409-attempt-session-revoked.json",
                "api/attempts-submit-200.json",
                "api/attempts-submit-409-part-not-last.json",
                "api/attempts-submit-409-attempt-session-revoked.json",
                "api/attempts-choice-202.json",
                "api/attempts-choice-200-replayed.json",
                "api/attempts-choice-409-choice-window-closed.json",
                "api/attempts-choice-409-choice-locked.json",
                "api/attempts-choice-409-part-not-awaiting.json",
                "api/attempts-choice-422-choice-not-allowed.json",
                "api/attempts-choice-422-subjects-invalid.json",
                "api/attempts-retry-202.json",
                "api/attempts-retry-200-replayed.json",
                "api/attempts-retry-409-part-not-awaiting.json",
                "api/attempts-retry-409-part-generating.json",
                // 6.5 Attempt Content & Audio (15)
                "api/attempts-asset-404-not-found.json",
                "api/attempts-asset-401-unauthenticated.json",
                "api/attempts-solution-asset-404-not-found.json",
                "api/attempts-solution-asset-401-unauthenticated.json",
                "api/audio-plays-start-200.json",
                "api/audio-plays-start-409-audio-play-in-progress.json",
                "api/audio-plays-start-409-audio-plays-exhausted.json",
                "api/audio-plays-progress-200.json",
                "api/audio-plays-progress-409-audio-seek-rejected.json",
                "api/audio-plays-progress-409-audio-play-stale.json",
                "api/audio-plays-progress-409-attempt-session-revoked.json",
                "api/audio-plays-resume-200.json",
                "api/audio-plays-resume-409-part-closed.json",
                "api/audio-plays-end-200.json",
                "api/audio-plays-end-200-closed.json",
                // 6.6 Result & Review (17; BE1-Q6 omits attempts-result-200-no-scale-max)
                "api/attempts-result-200-scored.json",
                "api/attempts-result-200-pending-review.json",
                "api/attempts-result-200-grading.json",
                "api/attempts-result-200-grading-error.json",
                "api/attempts-result-200-regrading.json",
                "api/attempts-result-200-vact.json",
                "api/attempts-result-200-system-fault.json",
                "api/attempts-result-200-compensated.json",
                "api/attempts-result-200-zero-score.json",
                "api/attempts-result-404-attempt-not-found.json",
                "api/attempts-result-409-attempt-not-finished.json",
                "api/attempts-review-200-score-and-answers.json",
                "api/attempts-review-200-full-solution.json",
                "api/attempts-review-200-pending-review.json",
                "api/attempts-review-403-review-not-included.json",
                "api/attempts-review-404-attempt-not-found.json",
                "api/attempts-review-409-review-not-available.json",
                // 6.7 Profile, Avatar, Email (17)
                "api/me-profile-200-avatar-pending-email.json",
                "api/me-profile-update-200.json",
                "api/me-profile-update-400-phone.json",
                "api/me-avatar-put-200.json",
                "api/me-avatar-put-413-avatar-too-large.json",
                "api/me-avatar-put-413-upload-too-large.json",
                "api/me-avatar-put-422-avatar-invalid-image.json",
                "api/me-avatar-put-422-avatar-dimensions-exceeded.json",
                "api/me-email-verification-429-rate-limited.json",
                "api/me-email-verify-422-email-code-invalid.json",
                "api/me-email-verify-422-email-code-expired.json",
                "api/me-email-change-400-wrong-old-password.json",
                "api/me-email-change-400-new-email.json",
                "api/me-email-change-409-email-taken.json",
                "api/me-email-change-429-rate-limited.json",
                "api/me-email-change-429-login-throttled.json",
                "api/me-email-change-confirm-422-email-code-invalid.json");
    }
}
