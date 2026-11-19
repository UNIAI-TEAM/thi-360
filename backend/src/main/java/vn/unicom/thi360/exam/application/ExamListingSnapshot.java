package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import vn.unicom.thi360.exam.api.ExamListingResponse;

/**
 * The listing as it is frozen into {@code exam_version.config.listing} at publish (TM 6.1) and as the same
 * representation of the working copy, so publish and {@code ExamVersionQueryService.currentConfig} compare like with like.
 */
final class ExamListingSnapshot {
    private ExamListingSnapshot() { }

    /** SQL jsonb of the working listing of the exam aliased {@code e}; an exam without a listing row gives null columns. */
    static final String WORKING_JSON_SQL = "(SELECT jsonb_build_object("
            + "'summary', l.summary, 'objectives', l.objectives, 'audience', l.audience, "
            + "'listPriceVnd', l.list_price_vnd, 'attemptsGranted', l.attempts_granted, "
            + "'benefitAiExplanation', COALESCE(l.benefit_ai_explanation, false), "
            + "'benefitStrengthWeakness', COALESCE(l.benefit_strength_weakness, false), "
            + "'benefitPractice', COALESCE(l.benefit_practice, false), 'benefitNote', l.benefit_note, "
            + "'resultView', COALESCE(l.result_view, 'SCORE_AND_ANSWERS'), "
            + "'name', e.name, 'description', e.description, 'coverAssetId', e.cover_asset_id) "
            + "FROM (SELECT 1) one LEFT JOIN exam.exam_listing l ON l.exam_id = e.id)";

    /**
     * SQL text[] of the keys to drop from a comparison so that a version saved before EX-1 (no {@code listing}) is
     * compared as if the field did not exist: {@code listing} when the version lacks it, nothing otherwise.
     */
    static String ignoredKeys(String versionAlias) {
        return "(CASE WHEN " + versionAlias + ".config->'listing' IS NULL THEN ARRAY['listing'] ELSE ARRAY[]::text[] END)";
    }

    static ExamListingResponse.Sale sale(UUID examId, JsonNode listing) {
        UUID cover = listing.hasNonNull("coverAssetId") ? UUID.fromString(listing.path("coverAssetId").asText()) : null;
        return new ExamListingResponse.Sale(text(listing, "name"), text(listing, "description"), cover,
                cover == null ? null : ExamCoverService.urlOf(examId, cover),
                text(listing, "summary"), text(listing, "objectives"), text(listing, "audience"),
                listing.hasNonNull("listPriceVnd") ? listing.path("listPriceVnd").asLong() : null,
                listing.hasNonNull("attemptsGranted") ? listing.path("attemptsGranted").asInt() : null,
                listing.path("benefitAiExplanation").asBoolean(false),
                listing.path("benefitStrengthWeakness").asBoolean(false),
                listing.path("benefitPractice").asBoolean(false),
                text(listing, "benefitNote"),
                listing.hasNonNull("resultView") ? listing.path("resultView").asText() : "SCORE_AND_ANSWERS");
    }

    private static String text(JsonNode node, String name) {
        return node.hasNonNull(name) ? node.path(name).asText() : null;
    }
}
