package vn.unicom.thi360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.unicom.thi360.AbstractKeycloakIntegrationTest;
import vn.unicom.thi360.identity.infrastructure.KeycloakIdentityClient;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;

/**
 * AC-01.6 "no hard delete path in the API" (UNI-2553, T16).
 *
 * <p>Account removal is soft delete only. The two routes that remove an account
 * ({@code DELETE /api/v1/admin/users/{id}} and the bulk action {@code DELETE} on
 * {@code POST /api/v1/admin/users/bulk-actions}) must leave the {@code identity.user_account} row in
 * place with {@code deleted_at} set. A runtime scan of the handler mappings makes a newly added
 * route that could remove accounts fail this test until it is covered here.
 */
class NoHardDeleteIT extends AbstractKeycloakIntegrationTest {

    private static final String SINGLE_DELETE = "DELETE /api/v1/admin/users/{id}";
    private static final String BULK_ACTIONS = "POST /api/v1/admin/users/bulk-actions";

    /**
     * DELETE routes that carry a user id but remove a relation, not an account: they delete a row of
     * {@code classroom.class_member}, never of {@code identity.user_account}.
     */
    private static final Set<String> NON_ACCOUNT_USER_ROUTES = Set.of("DELETE /api/v1/admin/classes/{id}/members/{userId}");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private KeycloakIdentityClient keycloakClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (UUID userId : createdUserIds) {
            try {
                keycloakClient.deleteUser(userId);
            } catch (Exception ignored) {
                // already deleted by the endpoint under test
            }
            jdbcTemplate.update("DELETE FROM identity.user_identity WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_permission WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM identity.user_account WHERE id = ?", userId);
            redisTemplate.delete(List.of(
                    "user-sess:" + userId, "user-sess-seq:" + userId, "user-rev-gen:" + userId));
        }
        createdUserIds.clear();
    }

    @Test
    void singleDelete_keepsAccountRowWithDeletedAt() throws Exception {
        UUID userId = createUser("nohard.single");
        assertThat(accountRows(userId)).hasSize(1);
        assertThat(accountRows(userId).getFirst().get("deleted_at")).isNull();

        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId)
                        .param("reason", "AC-01.6 soft delete")
                        .with(asUser(Permissions.USER_MODIFY)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = accountRows(userId);
        assertThat(rows).as("identity.user_account row after DELETE /admin/users/{id}").hasSize(1);
        assertThat(rows.getFirst().get("deleted_at")).as("deleted_at").isNotNull();
    }

    @Test
    void bulkDelete_keepsEveryAccountRowWithDeletedAt() throws Exception {
        UUID u1 = createUser("nohard.bulk.a");
        UUID u2 = createUser("nohard.bulk.b");

        MvcResult res = mockMvc.perform(post("/api/v1/admin/users/bulk-actions")
                        .header("Idempotency-Key", "nohard-" + UUID.randomUUID())
                        .with(asUser(Permissions.USER_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "DELETE",
                                  "userIds": ["%s", "%s"],
                                  "reason": "AC-01.6 bulk soft delete"
                                }
                                """.formatted(u1, u2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andReturn();
        assertThat(objectMapper.readTree(res.getResponse().getContentAsString()).get("results")).hasSize(2);

        for (UUID id : List.of(u1, u2)) {
            List<Map<String, Object>> rows = accountRows(id);
            assertThat(rows).as("identity.user_account row %s after bulk DELETE", id).hasSize(1);
            assertThat(rows.getFirst().get("deleted_at")).as("deleted_at of %s", id).isNotNull();
        }
    }

    @Test
    void everyDeleteRouteTouchingUsersIsCoveredByTheTwoSoftDeleteTests() {
        Set<String> deleteRoutesTouchingUsers = new TreeSet<>();
        boolean bulkActionsRouteExists = false;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : entry.getKey().getPatternValues()) {
                // A mapping with no method restriction answers DELETE as well.
                boolean mayDelete = methods.isEmpty() || methods.contains(RequestMethod.DELETE);
                if (mayDelete && touchesUsers(pattern)) {
                    deleteRoutesTouchingUsers.add((methods.isEmpty() ? "ANY" : "DELETE") + " " + pattern);
                }
                if (methods.contains(RequestMethod.POST) && pattern.equals("/api/v1/admin/users/bulk-actions")) {
                    bulkActionsRouteExists = true;
                }
            }
        }

        assertThat(bulkActionsRouteExists)
                .as("%s is the route covered by bulkDelete_keepsEveryAccountRowWithDeletedAt", BULK_ACTIONS)
                .isTrue();

        Set<String> unexpected = new TreeSet<>(deleteRoutesTouchingUsers);
        unexpected.remove(SINGLE_DELETE);
        unexpected.removeAll(NON_ACCOUNT_USER_ROUTES);
        assertThat(unexpected)
                .as("DELETE routes touching users other than %s (soft-delete covered by"
                        + " singleDelete_keepsAccountRowWithDeletedAt): a new route that removes accounts needs"
                        + " a row-survives test here, or an entry in NON_ACCOUNT_USER_ROUTES if it only removes"
                        + " a relation", SINGLE_DELETE)
                .isEmpty();
        assertThat(deleteRoutesTouchingUsers)
                .as("the covered single-delete route is still mapped")
                .contains(SINGLE_DELETE);
        System.out.println("DELETE routes touching users (runtime scan): " + deleteRoutesTouchingUsers);
    }

    /** A route touches users when a path segment is {@code users}/{@code accounts} or a {@code {userId}} variable. */
    private static boolean touchesUsers(String pattern) {
        for (String segment : pattern.split("/")) {
            String s = segment.toLowerCase(java.util.Locale.ROOT);
            if (s.equals("users") || s.equals("accounts") || s.equals("{userid}")) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> accountRows(UUID userId) {
        return jdbcTemplate.queryForList(
                "SELECT id, username, deleted_at FROM identity.user_account WHERE id = ?", userId);
    }

    private UUID createUser(String prefix) throws Exception {
        String username = prefix + "." + Ids.newId().toString().substring(0, 8);
        MvcResult res = mockMvc.perform(post("/api/v1/admin/users")
                        .with(asPlatformAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Valid@1234",
                                  "fullName": "No Hard Delete"
                                }
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdUserIds.add(id);
        return id;
    }
}
