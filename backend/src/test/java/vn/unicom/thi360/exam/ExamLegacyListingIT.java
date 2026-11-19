package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.identity.spi.Permissions;

/** EX-1 AC-36: exams and versions that predate the listing keep working. */
class ExamLegacyListingIT extends AbstractExamPublishIT {
    private static final String PLAIN = "[{\"id\":\"part\",\"options\":[{\"id\":\"choice\",\"countsByPick\":[17,17,16],\"sections\":[]}]}]";
    @org.springframework.beans.factory.annotation.Autowired vn.unicom.thi360.exam.spi.PublishedExamPort port;

    /** An exam published before EX-1: no exam_listing row, a version whose config has no `listing`. */
    private UUID legacyExam() throws Exception {
        UUID exam = newExam("HSA", PLAIN);
        saveCheck(exam, paths("default=VALID"));
        publish(exam, "default").andExpect(status().isOk());
        jdbc.update("UPDATE exam.exam_version SET config = config - 'listing' WHERE exam_id = ?", exam);
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", exam);
        entityManager.clear();
        return exam;
    }

    @Test
    void versionsWithoutListingStayReadable() throws Exception {
        UUID exam = legacyExam();
        String base = "/api/v1/admin/exams/" + exam;
        // The working copy reads as empty and is refused at publish by the price rule alone.
        JsonNode listing = mapper.readTree(mockMvc.perform(get(base + "/listing").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listing.path("listPriceVnd").isNull()).isTrue();
        assertThat(listing.path("published").isNull()).as("a version without a listing snapshot has nothing to show").isTrue();
        assertThat(listing.path("checkIssues").findValuesAsText("code")).contains("LISTING_PRICE_MISSING", "LISTING_ATTEMPTS_MISSING");
        // The port reads the same legacy version: no listing, everything else as published.
        var serving = port.findServing(exam).orElseThrow();
        assertThat(serving.version().listing()).isNull();
        assertThat(serving.version().versionNo()).isEqualTo(1);
        assertThat(serving.version().name()).isEqualTo("E24 test");
        assertThat(port.loadVersion(exam, 1).orElseThrow().listing()).isNull();
        // The version is readable and the listing is not reported as a change because it is missing from the snapshot.
        mockMvc.perform(get(base + "/versions/1").with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.row.version").value(1));
        jdbc.update("UPDATE exam.exam SET has_unpublished_changes = true WHERE id = ?", exam);
        entityManager.clear();
        JsonNode versions = mapper.readTree(mockMvc.perform(get(base + "/versions").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(versions.path("items")).hasSize(1);
        assertThat(versions.path("changes").findValuesAsText("where")).noneMatch(where -> where.startsWith("config/listing"));
        assertThat(versions.path("workingCopy").path("unpublishedPaths")).as("the serving path is not reported unpublished").isEmpty();
        // Nothing changed except the missing field: publishing again is "nothing to publish", not a new version.
        publish(exam, "default").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTHING_TO_PUBLISH"));
        // A real change is still a change: once the listing is complete the exam publishes as version 2.
        ExamListingFixture.fill(jdbc, exam);
        jdbc.update("UPDATE exam.exam SET name = 'Renamed' WHERE id = ?", exam);
        entityManager.clear();
        saveCheck(exam, paths("default=VALID"));
        publish(exam, "default").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    }

    /**
     * R1-3: on a version published before EX-1 a deliberate listing edit (same name, description and cover; only the
     * sale fields) is a change: it is shown as unpublished and it publishes, with the price that was saved.
     */
    @Test
    void listingOnlyEditOfALegacyVersionIsPublishable() throws Exception {
        UUID exam = legacyExam();
        String base = "/api/v1/admin/exams/" + exam;
        // Untouched: still "nothing to publish".
        publish(exam, "default").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTHING_TO_PUBLISH"));
        String description = jdbc.queryForObject("SELECT description FROM exam.exam WHERE id = ?", String.class, exam);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(base + "/listing")
                        .with(asUser(Permissions.EXAM_MODIFY)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"listPriceVnd\":123000,\"attemptsGranted\":2,\"description\":\"" + description
                                + "\",\"expectedRevision\":" + revision(exam) + "}"))
                .andExpect(status().isOk());
        entityManager.clear();
        // The check is still fresh (listing is outside its fingerprint) and the serving path is unpublished again.
        mockMvc.perform(get(base + "/versions").with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andExpect(jsonPath("$.workingCopy.unpublishedPaths[0].pathId").value("default"))
                .andExpect(jsonPath("$.changes[?(@.where=='config/listing')]").isNotEmpty());
        publish(exam, "default").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        assertThat(jdbc.queryForObject("SELECT (config->'listing'->>'listPriceVnd')::bigint FROM exam.exam_version "
                + "WHERE exam_id = ? AND version_no = 2", Long.class, exam)).isEqualTo(123000L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.outbox_event WHERE topic = 'exam.published' "
                + "AND payload->>'examId' = ? AND payload->>'versionNo' = '2'", Long.class, exam.toString())).isEqualTo(1L);
    }
}
