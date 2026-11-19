package vn.unicom.thi360.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import vn.unicom.thi360.exam.api.ExamListingResponse;

/**
 * The listing as it is frozen into {@code exam_version.config.listing} at publish (TM 6.1) and as the same
 * representation of the working copy, so publish and {@code ExamVersionQueryService.currentConfig} compare like with like.
 *
 * <p>The snapshot carries every listing column, including who saved it last and when ({@code updatedBy},
 * {@code updatedAt}). Those two are history, not sale terms: comparisons ignore them ({@link #comparable}), so a save
 * that changed nothing else is not a change to publish. The public port never exposes them.
 */
final class ExamListingSnapshot {
    private ExamListingSnapshot() { }

    /** The metadata keys of {@code listing} that are not sale terms. */
    static final java.util.Set<String> METADATA = java.util.Set.of("updatedBy", "updatedAt");

    /**
     * SQL jsonb of the working listing of the exam aliased {@code e}; SQL NULL (JSON null in a config) when the exam has
     * no listing row, so "never saved" stays distinguishable from "saved empty".
     */
    static final String WORKING_JSON_SQL = "(SELECT jsonb_build_object("
            + "'examId', l.exam_id, 'summary', l.summary, 'objectives', l.objectives, 'audience', l.audience, "
            + "'listPriceVnd', l.list_price_vnd, 'attemptsGranted', l.attempts_granted, "
            + "'benefitAiExplanation', l.benefit_ai_explanation, "
            + "'benefitStrengthWeakness', l.benefit_strength_weakness, "
            + "'benefitPractice', l.benefit_practice, 'benefitNote', l.benefit_note, "
            + "'resultView', l.result_view, 'updatedBy', l.updated_by, 'updatedAt', l.updated_at, "
            + "'name', e.name, 'description', e.description, 'coverAssetId', e.cover_asset_id) "
            + "FROM exam.exam_listing l WHERE l.exam_id = e.id)";

    /**
     * SQL jsonb of a version config (or a freshly built one) in the form versions are compared in: without the listing
     * metadata, without {@code kindCode} (a frozen copy of {@code kindItemId}, absent from older versions) and with a
     * missing or null {@code listing} dropped. A version saved before EX-1 has no {@code listing} and the working copy
     * of an exam never saved has none either, so those two are equal; a listing that was saved after such a
     * version is a real difference (R1-3). {@code jsonExpr} is evaluated once.
     */
    static String comparable(String jsonExpr) {
        return "(SELECT ((t.x - CASE WHEN jsonb_typeof(t.x->'listing') IS NULL OR jsonb_typeof(t.x->'listing') = 'null' "
                + "THEN 'listing' ELSE '$none' END) - 'kindCode') #- '{listing,updatedBy}' #- '{listing,updatedAt}' "
                + "FROM (SELECT " + jsonExpr + " AS x) t)";
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
