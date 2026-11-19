package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * EX-1 AC-11 and AC-12. The deep check runs on its own thread and transaction, so the exam is committed and removed
 * again after each test.
 */
class ExamListingCheckIT extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private UUID exam;
    private String base;

    @BeforeEach
    void createExam() {
        exam = UUID.randomUUID();
        base = "/api/v1/admin/exams/" + exam;
        jdbc.update("INSERT INTO exam.exam (id,code,code_normalized,name,scope,timing) VALUES (?,?,?,'Listing check','FULL','SHARED')",
                exam, exam.toString(), exam.toString());
        jdbc.update("INSERT INTO exam.exam_structure (exam_id,parts,structure_hash) VALUES (?,'{\"parts\":[]}'::jsonb,'empty')", exam);
    }

    @AfterEach
    void removeExam() {
        jdbc.update("DELETE FROM exam.exam_listing WHERE exam_id = ?", exam);
        jdbc.update("DELETE FROM exam.exam WHERE id = ?", exam);
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status) throws Exception {
        return mapper.readTree(mockMvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }

    private String storedResult() {
        return jdbc.queryForObject("SELECT result::text FROM exam.exam_check_run WHERE exam_id = ?", String.class, exam);
    }

    @Test
    void listingCheckIsComputedOnReadOnEveryRoute() throws Exception {
        JsonNode checked = json(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
        assertThat(checked.path("listingCheck").path("group").asText()).isEqualTo("LISTING");
        assertThat(checked.path("listingCheck").path("status").asText()).isEqualTo("FAIL");
        assertThat(checked.path("listingCheck").path("issues").findValuesAsText("code"))
                .containsExactly("LISTING_PRICE_MISSING", "LISTING_ATTEMPTS_MISSING", "LISTING_DESCRIPTION_MISSING", "LISTING_COVER_MISSING");
        assertThat(checked.path("groups").findValuesAsText("group")).as("the eight configuration groups only").doesNotContain("LISTING");
        String stored = storedResult();
        assertThat(mapper.readTree(stored).has("listingCheck")).as("never stored with the check").isFalse();

        JsonNode latest = json(get(base + "/check").with(asUser(Permissions.EXAM_VIEW)), 200);
        assertThat(latest.path("listingCheck")).isEqualTo(checked.path("listingCheck"));
        JsonNode started = json(post(base + "/check").param("deep", "true").with(asUser(Permissions.EXAM_MODIFY)), 202);
        String run = started.path("runId").asText();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
                json(get(base + "/check/runs/" + run).with(asUser(Permissions.EXAM_VIEW)), 200).path("state").asText()).isEqualTo("DONE"));
        JsonNode deep = json(get(base + "/check/runs/" + run).with(asUser(Permissions.EXAM_VIEW)), 200);
        assertThat(deep.path("result").path("listingCheck").path("group").asText()).isEqualTo("LISTING");
        assertThat(deep.path("result").path("listingCheck").path("issues")).hasSize(4);

        // Complete the listing. The configuration result does not move; only the listing group is recomputed.
        String afterDeep = storedResult();
        long revision = jdbc.queryForObject("SELECT revision FROM exam.exam WHERE id = ?", Long.class, exam);
        json(put(base + "/listing").with(asUser(Permissions.EXAM_MODIFY)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"listPriceVnd\":80000,\"attemptsGranted\":2,\"description\":\"Mo ta\",\"expectedRevision\":" + revision + "}"), 200);
        assertThat(storedResult()).isEqualTo(afterDeep);
        JsonNode after = json(get(base + "/check").with(asUser(Permissions.EXAM_VIEW)), 200);
        assertThat(after.path("stale").asBoolean()).isFalse();
        assertThat(after.path("status")).isEqualTo(checked.path("status"));
        assertThat(after.path("issueCount")).isEqualTo(checked.path("issueCount"));
        assertThat(after.path("groups")).isEqualTo(checked.path("groups"));
        assertThat(after.path("listingCheck").path("status").asText()).isEqualTo("WARN");
        assertThat(after.path("listingCheck").path("issues").findValuesAsText("code")).containsExactly("LISTING_COVER_MISSING");
        JsonNode rechecked = json(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY)), 200);
        assertThat(rechecked.path("listingCheck").path("status").asText()).isEqualTo("WARN");
        assertThat(rechecked.path("status")).isEqualTo(checked.path("status"));
    }

    @Test
    void uncheckedExamStillReportsListingIssues() throws Exception {
        assertThat(mockMvc.perform(get(base + "/check").with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo("null");
        JsonNode listing = json(get(base + "/listing").with(asUser(Permissions.EXAM_VIEW)), 200);
        assertThat(listing.path("checkIssues").findValuesAsText("code"))
                .containsExactly("LISTING_PRICE_MISSING", "LISTING_ATTEMPTS_MISSING", "LISTING_DESCRIPTION_MISSING", "LISTING_COVER_MISSING");
    }
}
