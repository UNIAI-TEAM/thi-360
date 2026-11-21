package vn.unicom.thi360.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.unicom.thi360.attempt.api.CompensateAttemptRequest;
import vn.unicom.thi360.attempt.api.FaultAttemptRequest;
import vn.unicom.thi360.attempt.api.FinalizeEssayRequest;
import vn.unicom.thi360.attempt.api.ReopenEssayRequest;
import vn.unicom.thi360.attempt.api.SaveEssayDraftRequest;
import vn.unicom.thi360.identity.spi.Permissions;

/**
 * Verifies single-permission isolation, missing-permission 403, student 403, and anonymous 401
 * across every {@code /api/v1/admin/grading/**} and {@code /api/v1/admin/attempts/**} endpoint (AC-16).
 */
class AdminAttemptPermissionsIT extends AbstractAttemptDsIT {

    @Test
    void gradingViewAloneAllowsReadAndDeniesWriteAndAttempts() throws Exception {
        RequestPostProcessor user = asUser(Permissions.GRADING_VIEW);
        UUID id = UUID.randomUUID();

        assertThat(call(get("/api/v1/admin/grading/items"), user)).isEqualTo(200);
        assertThat(call(get("/api/v1/admin/grading/items/counts"), user)).isEqualTo(200);
        assertThat(call(get("/api/v1/admin/grading/items/{id}", id), user)).isEqualTo(404);

        assertThat(call(post("/api/v1/admin/grading/items/{id}/claim", id), user)).isEqualTo(403);
        assertThat(call(putDraft(id), user)).isEqualTo(403);
        assertThat(call(postFinalize(id), user)).isEqualTo(403);
        assertThat(call(postReopen(id), user)).isEqualTo(403);

        assertThat(call(get("/api/v1/admin/attempts"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/attempts/{id}", id), user)).isEqualTo(403);
        assertThat(call(post("/api/v1/admin/attempts/{id}/regrade", id), user)).isEqualTo(403);
        assertThat(call(postFault(id), user)).isEqualTo(403);
        assertThat(call(postCompensate(id), user)).isEqualTo(403);
    }

    @Test
    void gradingModifyAloneAllowsGradingMutationsAndRegradeOnly() throws Exception {
        RequestPostProcessor user = asUser(Permissions.GRADING_MODIFY);
        UUID id = UUID.randomUUID();

        assertThat(call(post("/api/v1/admin/grading/items/{id}/claim", id), user)).isEqualTo(404);
        assertThat(call(putDraft(id), user)).isEqualTo(404);
        assertThat(call(postFinalize(id), user)).isEqualTo(404);
        assertThat(call(postReopen(id), user)).isEqualTo(404);
        assertThat(call(post("/api/v1/admin/attempts/{id}/regrade", id), user)).isEqualTo(404);

        assertThat(call(get("/api/v1/admin/grading/items"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items/counts"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items/{id}", id), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/attempts"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/attempts/{id}", id), user)).isEqualTo(403);
        assertThat(call(postFault(id), user)).isEqualTo(403);
        assertThat(call(postCompensate(id), user)).isEqualTo(403);
    }

    @Test
    void attemptViewAloneAllowsAttemptLookupOnly() throws Exception {
        RequestPostProcessor user = asUser(Permissions.ATTEMPT_VIEW);
        UUID id = UUID.randomUUID();

        assertThat(call(get("/api/v1/admin/attempts"), user)).isEqualTo(200);
        assertThat(call(get("/api/v1/admin/attempts/{id}", id), user)).isEqualTo(404);

        assertThat(call(post("/api/v1/admin/attempts/{id}/regrade", id), user)).isEqualTo(403);
        assertThat(call(postFault(id), user)).isEqualTo(403);
        assertThat(call(postCompensate(id), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items/counts"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items/{id}", id), user)).isEqualTo(403);
        assertThat(call(post("/api/v1/admin/grading/items/{id}/claim", id), user)).isEqualTo(403);
        assertThat(call(putDraft(id), user)).isEqualTo(403);
        assertThat(call(postFinalize(id), user)).isEqualTo(403);
        assertThat(call(postReopen(id), user)).isEqualTo(403);
    }

    @Test
    void attemptCompensateAloneAllowsFaultAndCompensateOnly() throws Exception {
        RequestPostProcessor user = asUser(Permissions.ATTEMPT_COMPENSATE);
        UUID id = UUID.randomUUID();

        assertThat(call(postFault(id), user)).isEqualTo(404);
        assertThat(call(postCompensate(id), user)).isEqualTo(404);

        assertThat(call(get("/api/v1/admin/attempts"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/attempts/{id}", id), user)).isEqualTo(403);
        assertThat(call(post("/api/v1/admin/attempts/{id}/regrade", id), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items/counts"), user)).isEqualTo(403);
        assertThat(call(get("/api/v1/admin/grading/items/{id}", id), user)).isEqualTo(403);
        assertThat(call(post("/api/v1/admin/grading/items/{id}/claim", id), user)).isEqualTo(403);
        assertThat(call(putDraft(id), user)).isEqualTo(403);
        assertThat(call(postFinalize(id), user)).isEqualTo(403);
        assertThat(call(postReopen(id), user)).isEqualTo(403);
    }

    @Test
    void noPermissionStudentAndAnonymousRejectedAcrossAllEndpoints() throws Exception {
        UUID id = UUID.randomUUID();
        List<Supplier<MockHttpServletRequestBuilder>> allEndpoints = List.of(
                () -> get("/api/v1/admin/grading/items"),
                () -> get("/api/v1/admin/grading/items/counts"),
                () -> get("/api/v1/admin/grading/items/{id}", id),
                () -> post("/api/v1/admin/grading/items/{id}/claim", id),
                () -> putDraft(id),
                () -> postFinalize(id),
                () -> postReopen(id),
                () -> get("/api/v1/admin/attempts"),
                () -> get("/api/v1/admin/attempts/{id}", id),
                () -> post("/api/v1/admin/attempts/{id}/regrade", id),
                () -> postFault(id),
                () -> postCompensate(id));

        RequestPostProcessor emptyManage = asUser();
        RequestPostProcessor student = asStudent();

        for (Supplier<MockHttpServletRequestBuilder> factory : allEndpoints) {
            assertThat(call(factory.get(), emptyManage)).isEqualTo(403);
            assertThat(call(factory.get(), student)).isEqualTo(403);
            assertThat(mockMvc.perform(factory.get()).andReturn().getResponse().getStatus())
                    .isEqualTo(401);
        }
    }

    private MockHttpServletRequestBuilder putDraft(UUID id) {
        return put("/api/v1/admin/grading/items/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new SaveEssayDraftRequest(null, BigDecimal.ONE, "ok", 0L)));
    }

    private MockHttpServletRequestBuilder postFinalize(UUID id) {
        return post("/api/v1/admin/grading/items/{id}/finalize", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new FinalizeEssayRequest(null, null, BigDecimal.ONE, "ok")));
    }

    private MockHttpServletRequestBuilder postReopen(UUID id) {
        return post("/api/v1/admin/grading/items/{id}/reopen", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new ReopenEssayRequest("Lý do")));
    }

    private MockHttpServletRequestBuilder postFault(UUID id) {
        return post("/api/v1/admin/attempts/{id}/fault", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new FaultAttemptRequest("Lý do")));
    }

    private MockHttpServletRequestBuilder postCompensate(UUID id) {
        return post("/api/v1/admin/attempts/{id}/compensate", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CompensateAttemptRequest("Lý do")));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int call(MockHttpServletRequestBuilder builder, RequestPostProcessor user) throws Exception {
        MvcResult result = mockMvc.perform(builder.with(user)).andReturn();
        return result.getResponse().getStatus();
    }
}
