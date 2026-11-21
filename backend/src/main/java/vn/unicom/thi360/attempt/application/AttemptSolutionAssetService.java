package vn.unicom.thi360.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.attempt.domain.Attempt;
import vn.unicom.thi360.attempt.domain.AttemptItem;
import vn.unicom.thi360.attempt.domain.AttemptState;
import vn.unicom.thi360.attempt.infrastructure.AttemptItemRepository;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort;
import vn.unicom.thi360.questionbank.spi.QuestionAssetContentPort.AssetContent;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Version;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.shared.api.NotFoundException;

/**
 * Serves image/file assets embedded inside question solutions under strict BS §15 gates.
 *
 * <p>Returns 404 unless the caller owns the attempt, {@code resultView == FULL_SOLUTION}, the
 * attempt is {@code SCORED} (or {@code regrading == true}), and {@code assetId} appears inside
 * an issued item's {@code solution} HTML at its locked version.
 */
@Service
public class AttemptSolutionAssetService {

    private static final Pattern ADMIN_ASSET_URL_PATTERN =
            Pattern.compile(
                    "/api/v1/admin/question-assets/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SOLUTION_ASSET_URL_PATTERN =
            Pattern.compile(
                    "/solution-assets/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})",
                    Pattern.CASE_INSENSITIVE);

    private final AttemptAccessGate accessGate;
    private final AttemptGuard attemptGuard;
    private final AttemptItemRepository attemptItemRepository;
    private final QuestionSourcePort questionSourcePort;
    private final QuestionAssetContentPort questionAssetContentPort;

    public AttemptSolutionAssetService(
            AttemptAccessGate accessGate,
            AttemptGuard attemptGuard,
            AttemptItemRepository attemptItemRepository,
            QuestionSourcePort questionSourcePort,
            QuestionAssetContentPort questionAssetContentPort) {
        this.accessGate = Objects.requireNonNull(accessGate);
        this.attemptGuard = Objects.requireNonNull(attemptGuard);
        this.attemptItemRepository = Objects.requireNonNull(attemptItemRepository);
        this.questionSourcePort = Objects.requireNonNull(questionSourcePort);
        this.questionAssetContentPort = Objects.requireNonNull(questionAssetContentPort);
    }

    @Transactional
    public AssetContent loadSolutionAsset(UUID attemptId, UUID assetId) {
        UUID userId = accessGate.requireLearner();
        if (attemptId == null || assetId == null) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        Attempt attempt = attemptGuard.requireOwnedAttempt(attemptId, userId);

        if (!"FULL_SOLUTION".equals(attempt.getResultView())) {
            throw new NotFoundException("NOT_FOUND", null);
        }
        boolean scoredOrRegrading = attempt.getState() == AttemptState.SCORED
                || (attempt.getState() == AttemptState.PENDING_REVIEW && attempt.isRegrading());
        if (!scoredOrRegrading) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        List<AttemptItem> items = attemptItemRepository.findByAttemptIdOrderByPartIdAscOrdAsc(attemptId);
        List<AttemptItem> inScopeItems = items.stream().filter(AttemptItem::isInScope).toList();
        if (inScopeItems.isEmpty()) {
            throw new NotFoundException("NOT_FOUND", null);
        }

        Set<VersionKey> keys = new LinkedHashSet<>();
        for (AttemptItem item : inScopeItems) {
            keys.add(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
        }
        Map<VersionKey, JsonNode> snapshots = new HashMap<>(keys.size() * 2);
        for (Version version : questionSourcePort.loadVersions(List.copyOf(keys))) {
            if (version == null || version.snapshot() == null) {
                continue;
            }
            snapshots.put(version.key(), version.snapshot());
            JsonNode children = version.snapshot().path("body").path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    addUuidChildSnapshot(child, version.key().versionNo(), snapshots);
                }
            }
        }

        Set<UUID> solutionAssetIds = new HashSet<>();
        for (AttemptItem item : inScopeItems) {
            JsonNode snapshot = snapshots.get(new VersionKey(item.getQuestionId(), item.getQuestionVersionNo()));
            if (snapshot == null) {
                continue;
            }
            String solutionHtml = snapshot.path("solution").asText(null);
            extractSolutionAssetIds(solutionHtml, solutionAssetIds);
            if (solutionAssetIds.contains(assetId)) {
                return questionAssetContentPort.loadContent(assetId)
                        .orElseThrow(() -> new NotFoundException("NOT_FOUND", null));
            }
        }
        throw new NotFoundException("NOT_FOUND", null);
    }

    private static void addUuidChildSnapshot(
            JsonNode child, int versionNo, Map<VersionKey, JsonNode> snapshots) {
        String idText = child.path("id").asText(null);
        if (idText == null || idText.isBlank()) {
            return;
        }
        try {
            snapshots.putIfAbsent(new VersionKey(UUID.fromString(idText.trim()), versionNo), child);
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed child ID
        }
    }

    private static void extractSolutionAssetIds(String html, Set<UUID> out) {
        if (html == null || html.isBlank()) {
            return;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element el : doc.select("[data-asset-id]")) {
            addUuid(el.attr("data-asset-id"), out);
        }
        Matcher adminMatcher = ADMIN_ASSET_URL_PATTERN.matcher(html);
        while (adminMatcher.find()) {
            addUuid(adminMatcher.group(1), out);
        }
        Matcher solutionMatcher = SOLUTION_ASSET_URL_PATTERN.matcher(html);
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
