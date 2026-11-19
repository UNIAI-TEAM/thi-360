package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.storage.ObjectStorage;

/** EX-1 AC-33: duplicating an exam copies its sale listing and cover image. */
class ExamDuplicateListingIT extends AbstractExamPublishIT {
    @Autowired ObjectStorage storage;

    @Test
    void duplicateCopiesListing() throws Exception {
        UUID source = newExam("HSA", "[{\"id\":\"part\",\"options\":[{\"id\":\"choice\",\"sections\":[]}]}]");
        UUID cover = UUID.randomUUID();
        byte[] bytes = {1, 2, 3, 4, 5};
        storage.put("exam-covers/" + cover, bytes, "image/png");
        jdbc.update("INSERT INTO exam.exam_cover_asset (id, exam_id, object_key, content_type, size_bytes, width, height) "
                + "VALUES (?, ?, ?, 'image/png', 5, 600, 700)", cover, source, "exam-covers/" + cover);
        jdbc.update("UPDATE exam.exam SET cover_asset_id = ?, description = 'Mo ta goc' WHERE id = ?", cover, source);
        jdbc.update("UPDATE exam.exam_listing SET summary = 'Tom tat', objectives = 'Muc tieu', audience = 'Doi tuong', "
                + "list_price_vnd = 200000, attempts_granted = 7, benefit_ai_explanation = true, benefit_note = 'Ghi chu', "
                + "result_view = 'FULL_SOLUTION' WHERE exam_id = ?", source);
        entityManager.clear();

        JsonNode copy = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams/" + source + "/duplicate")
                        .with(asUser(Permissions.EXAM_MODIFY)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ban sao\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID copyId = UUID.fromString(copy.path("id").asText());
        assertThat(copyId).isNotEqualTo(source);
        entityManager.flush();
        assertThat(copy.path("revision").asLong()).as("the response carries the stored revision").isEqualTo(revision(copyId));
        var row = jdbc.queryForMap("SELECT summary, objectives, audience, list_price_vnd, attempts_granted, benefit_ai_explanation, "
                + "benefit_note, result_view FROM exam.exam_listing WHERE exam_id = ?", copyId);
        assertThat(row).containsEntry("summary", "Tom tat").containsEntry("objectives", "Muc tieu").containsEntry("audience", "Doi tuong")
                .containsEntry("list_price_vnd", 200000L).containsEntry("attempts_granted", 7)
                .containsEntry("benefit_ai_explanation", true).containsEntry("benefit_note", "Ghi chu")
                .containsEntry("result_view", "FULL_SOLUTION");
        assertThat(jdbc.queryForObject("SELECT description FROM exam.exam WHERE id = ?", String.class, copyId)).isEqualTo("Mo ta goc");
        // The cover is a new image of the copy, with its own object, holding the same bytes.
        UUID copiedCover = jdbc.queryForObject("SELECT cover_asset_id FROM exam.exam WHERE id = ?", UUID.class, copyId);
        assertThat(copiedCover).isNotNull().isNotEqualTo(cover);
        var asset = jdbc.queryForMap("SELECT exam_id, object_key, content_type, size_bytes, width, height FROM exam.exam_cover_asset WHERE id = ?", copiedCover);
        assertThat(asset.get("exam_id")).isEqualTo(copyId);
        assertThat(asset.get("object_key")).isNotEqualTo("exam-covers/" + cover);
        assertThat(asset).containsEntry("content_type", "image/png").containsEntry("size_bytes", 5L)
                .containsEntry("width", 600).containsEntry("height", 700);
        assertThat(storage.get((String) asset.get("object_key"))).contains(bytes);
        assertThat(storage.exists("exam-covers/" + cover)).as("the source cover is untouched").isTrue();
        // The copy has no published version.
        JsonNode listing = mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams/" + copyId + "/listing").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listing.path("published").isNull()).isTrue();
        assertThat(listing.path("listPriceVnd").asLong()).isEqualTo(200000);
        assertThat(listing.path("coverAssetId").asText()).isEqualTo(copiedCover.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, copyId)).isZero();
    }
}
