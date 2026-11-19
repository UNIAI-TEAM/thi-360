package vn.unicom.thi360.commerce.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.exam.spi.ExamEventTopics;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.security.PermissionCatalog;

/** CM-1 AC-13 and AC-14: the admin rebuild of the storefront projection. */
class StorefrontRebuildIT extends AbstractStorefrontIT {

    private static final String REBUILD = "/api/v1/admin/storefront/rebuild";

    @Autowired PermissionCatalog permissionCatalog;

    @Test
    void rebuildRestoresMissingRows() throws Exception {
        TestExam onSale = exam();
        TestExam discontinued = exam().discontinued();
        TestExam lostEvent = exam();
        TestExam draft = exam();
        serve(onSale);
        serve(discontinued);
        serve(lostEvent);
        // The event of this exam failed for good (no handler was registered when it was relayed): no row came of it.
        port.failFor(lostEvent.examId);
        UUID dead = publishEvent(ExamEventTopics.PUBLISHED, lostEvent.examId, 1);
        drain();
        jdbc.update("UPDATE shared.outbox_event SET failed_at = now(), attempts = 5 WHERE id = ?", dead);
        port.recover(lostEvent.examId);
        // This exam fails during the rebuild itself: the rebuild must go on with the others, not stop at it.
        TestExam brokenAtRebuild = exam();
        serve(brokenAtRebuild);
        port.failFor(brokenAtRebuild.examId);
        // The draft is not served by the exam module: it has no row to restore.
        assertThat(hasRow(onSale.examId) || hasRow(discontinued.examId) || hasRow(lostEvent.examId)).isFalse();

        mockMvc.perform(post(REBUILD).with(asUser(Permissions.EXAM_PUBLISH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examined").value(4))
                .andExpect(jsonPath("$.written").value(3))
                .andExpect(jsonPath("$.failed").value(1));

        assertThat(row(onSale.examId)).containsEntry("sale_state", "ON_SALE");
        assertThat(row(discontinued.examId)).containsEntry("sale_state", "DISCONTINUED");
        assertThat(row(lostEvent.examId)).containsEntry("sale_state", "ON_SALE");
        assertThat(hasRow(draft.examId)).as("no row for a draft").isFalse();

        // Running it again changes nothing; the exam that still fails is counted again, the others are unchanged.
        String before = rowJson(onSale.examId) + rowJson(discontinued.examId) + rowJson(lostEvent.examId);
        mockMvc.perform(post(REBUILD).with(asUser(Permissions.EXAM_PUBLISH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.written").value(0))
                .andExpect(jsonPath("$.unchanged").value(3))
                .andExpect(jsonPath("$.failed").value(1));
        assertThat(rowJson(onSale.examId) + rowJson(discontinued.examId) + rowJson(lostEvent.examId)).isEqualTo(before);
        assertThat(hasRow(brokenAtRebuild.examId)).isFalse();
    }

    @Test
    void rebuildCountsAnExamThatFailsAndKeepsGoing() throws Exception {
        TestExam broken = exam();
        TestExam fine = exam();
        TestExam unsellable = exam().price(null);
        serve(broken);
        serve(fine);
        serve(unsellable);
        port.failFor(broken.examId);

        mockMvc.perform(post(REBUILD).with(asUser(Permissions.EXAM_PUBLISH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.written").value(1))
                .andExpect(jsonPath("$.notSellable").value(1));

        assertThat(hasRow(fine.examId)).isTrue();
        assertThat(hasRow(broken.examId)).isFalse();
        assertThat(hasRow(unsellable.examId)).as("no listing price, nothing to sell").isFalse();
    }

    @Test
    void rebuildRequiresExamPublish() throws Exception {
        TestExam exam = exam();
        serve(exam);

        mockMvc.perform(post(REBUILD)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        TreeSet<String> everythingButPublish = new TreeSet<>(permissionCatalog.allCodes());
        assertThat(everythingButPublish.remove(Permissions.EXAM_PUBLISH)).isTrue();
        mockMvc.perform(post(REBUILD).with(asUser(everythingButPublish.toArray(String[]::new))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(hasRow(exam.examId)).as("a refused call builds nothing").isFalse();

        UUID actor = UUID.randomUUID();
        mockMvc.perform(post(REBUILD).with(asUser(actor, "publisher.cm1", Permissions.EXAM_PUBLISH)))
                .andExpect(status().isOk());
        assertThat(hasRow(exam.examId)).isTrue();

        // The audit event is written through the outbox in the request's transaction; relay it to see it stored.
        drain();
        List<Map<String, Object>> audit = jdbc.queryForList(
                "SELECT module, actor_id, after::text AS after FROM audit.audit_event WHERE action = 'STOREFRONT_REBUILT' "
                        + "AND actor_id = ?", actor);
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().get("module")).isEqualTo("COMMERCE");
        assertThat((String) audit.getFirst().get("after")).contains("\"examined\"");
    }
}
