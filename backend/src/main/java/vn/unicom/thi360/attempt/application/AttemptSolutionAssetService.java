package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.commerce.spi.EntitlementPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Serves image/file assets embedded inside question solutions under strict BS §15 gates.
 *
 * <p>Returns 404 unless the caller owns the attempt, {@code resultView == FULL_SOLUTION}, the
 * attempt is {@code SCORED} (or {@code regrading == true}), and {@code assetId} appears inside
 * an issued item's {@code solution} HTML at its locked version. Object storage I/O runs strictly
 * outside any database transaction.
 */
@Service
public class AttemptSolutionAssetService {

    private static final Pattern ADMIN_ASSET_URL_PATTERN = Pattern.compile(
            "(?:https?://[^/\"'\\s<>]+)?/api/v1/admin/question-assets/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/content)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SOLUTION_ASSET_URL_PATTERN = Pattern.compile(
            "/solution-assets/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})",
            Pattern.CASE_INSENSITIVE);

    private final AttemptAccessGate accessGate;
    private final AttemptGuard attemptGuard;
    private final AttemptItemRepository attemptItemRepository;
    private final EntitlementPort entitlementPort;
    private final QuestionSourcePort questionSourcePort;
    private final QuestionAssetContentPort questionAssetContentPort;
    private final TransactionTemplate readOnlyTx;

    public AttemptSolutionAssetService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptItemRepository attemptItemRepository,
            EntitlementPort entitlementPort,
            QuestionSourcePort questionSourcePort,
            QuestionAssetContentPort questionAssetContentPort,
            PlatformTransactionManager transactionManager) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptGuard = Objects.requireNonNull(attemptGuard);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.entitlementPort = Objects.requireNonNull(entitlementPort);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.questionAssetContentPort = Objects.requireNonNull(questionAssetContentPort);
        this.readOnlyTx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.readOnlyTx.setReadOnly(true);
    }

    public AssetContent loadSolutionAsset(UUID attemptId, UUID assetId) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null || assetId == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        Attempt attempt = attemptGuard.requireOwnedAttempt(attemptId, userId);

        String effectiveResultView = AttemptReviewSupport.resolveEffectiveResultView(entitlementPort, attempt);
        if (!"FULL_SOLUTION".equals(effectiveResultView)) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        boolean scoredOrRegrading = attempt.getState() == AttemptState.SCORED
                || (attempt.getState() == AttemptState.PENDING_REVIEW && attempt.isRegrading());
        if (!scoredOrRegrading) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        boolean allowed = Boolean.TRUE.equals(
                readOnlyTx.execute(status -> isSolutionAssetAllowed(attemptId, assetId)));
        if (!allowed) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        // Load binary bytes from object storage strictly outside any DB transaction.
        return questionAssetContentPort
                .loadContent(assetId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
    }

    private boolean isSolutionAssetAllowed(UUID attemptId, UUID assetId) {
        List<AttemptItem> allItems = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
        List<AttemptItem> visibleItems = AttemptReviewSupport.filterStudentVisibleItems(allItems);
        if (visibleItems.isEmpty()) {
            return false;
        }
        Map<UUID, AttemptItem> allById = new HashMap<>(allItems.size() * 2);
        for (AttemptItem item : allItems) {
            allById.put(item.getId(), item);
        }

        Map<VersionKey, JsonNode> snapshots = AttemptReviewSupport.loadSnapshots(
                visibleItems, allById, attemptItemRepository, questionSourcePort);

        Set<UUID> solutionAssetIds = new HashSet<>();
        for (AttemptItem item : visibleItems) {
            JsonNode snapshot = snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
            if (snapshot == null) {
                continue;
            }
            String solutionHtml = snapshot.path("solution").asText(null);
            extractSolutionAssetIds(solutionHtml, solutionAssetIds);
            if (solutionAssetIds.contains(assetId)) {
                return true;
            }
        }
        return false;
    }

    static void extractSolutionAssetIds(String html, Set<UUID> out) {
        if (html == null || html.isBlank()) {
            return;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element el : doc.select("[data-asset-id]")) {
            addUuid(el.attr("data-asset-id"), out);
        }
        for (Element el : doc.body().getAllElements()) {
            for (Attribute attr : el.attributes()) {
                extractUrlAssetIds(attr.getValue(), out);
            }
        }
        extractUrlAssetIds(html, out);
    }

    private static void extractUrlAssetIds(String text, Set<UUID> out) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher adminMatcher = ADMIN_ASSET_URL_PATTERN.matcher(text);
        while (adminMatcher.find()) {
            addUuid(adminMatcher.group(1), out);
        }
        Matcher solutionMatcher = SOLUTION_ASSET_URL_PATTERN.matcher(text);
        while (solutionMatcher.find()) {
            addUuid(solutionMatcher.group(1), out);
        }
    }

    private static void addUuid(String raw, Set<UUID> out) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            out.add(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            // Ignore non-UUID values
        }
    }
}
