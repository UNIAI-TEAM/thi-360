package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.argThat;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditEventView;
import vn.unicom.thi360.audit.spi.AuditQuery;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.application.CatalogCacheService;
import vn.unicom.thi360.catalog.spi.CatalogUsage;
import vn.unicom.thi360.catalog.application.CatalogAdminService;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.outbox.OutboxRelay;

@Import({FakeCatalogUsageProviderConfig.class, CatalogCombinationApiIT.RepoHookConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogCombinationApiIT extends AbstractIntegrationTest {

    private static final UUID EXAM_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000001");
    private static final UUID SUBJECT_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000002");
    private static final UUID COMBINATION_GROUP_ID = UUID.fromString("00000000-0000-7000-8100-000000000007");

    private static final UUID SAMPLE_HSA_ID = UUID.fromString("00000000-0000-7000-8200-000000000101");
    private static final UUID SAMPLE_QDA_ID = UUID.fromString("00000000-0000-7000-8200-000000000102");
    private static final UUID SAMPLE_TOAN_ID = UUID.fromString("00000000-0000-7000-8200-000000000201");
    private static final UUID SAMPLE_VAN_ID = UUID.fromString("00000000-0000-7000-8200-000000000202");
    private static final UUID SAMPLE_ANH_ID = UUID.fromString("00000000-0000-7000-8200-000000000203");
    private static final UUID SAMPLE_LY_ID = UUID.fromString("00000000-0000-7000-8200-000000000204");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private FakeCatalogUsageProviderConfig.FakeCatalogUsageRegistry fakeUsageRegistry;

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private AuditQuery auditQuery;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @FunctionalInterface
    interface ProceedingCall {
        Object call() throws Throwable;
    }

    @FunctionalInterface
    interface RepoHook {
        Object intercept(Object[] args, ProceedingCall proceed) throws Throwable;
    }

    private static final Map<String, RepoHook> HOOKS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class RepoHookConfig {
        @Bean
        static BeanPostProcessor combinationItemRepoHookPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof CatalogItemRepository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            bean.getClass().getClassLoader(),
                            bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                RepoHook hook = HOOKS.get(method.getName());
                                if (hook != null) {
                                    return hook.intercept(args, () -> invokeTarget(bean, method, args));
                                }
                                return invokeTarget(bean, method, args);
                            });
                }
            };
        }

        private static Object invokeTarget(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
    }

    private final List<UUID> createdItemIds = new ArrayList<>();

    @BeforeEach
    void resetState() {
        fakeUsageRegistry.clear();
        HOOKS.clear();
        clearInvocations(auditRecorder);
    }

    @AfterEach
    void cleanup() {
        fakeUsageRegistry.clear();
        HOOKS.clear();
        for (int i = createdItemIds.size() - 1; i >= 0; i--) {
            UUID itemId = createdItemIds.get(i);
            jdbcTemplate.update(
                    "DELETE FROM catalog.combination_subject WHERE combination_version_id IN (SELECT id FROM catalog.combination_version WHERE item_id = ?)",
                    itemId);
            jdbcTemplate.update("DELETE FROM catalog.combination_version WHERE item_id = ?", itemId);
            jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE id = ?", itemId);
        }
        createdItemIds.clear();
        redisTemplate.delete(CatalogCacheService.optionsKey("subject_combination"));
        redisTemplate.delete(CatalogCacheService.optionsKey("exam"));
        redisTemplate.delete(CatalogCacheService.optionsKey("subject"));
    }

    @Test
    void createCombination_appearsInOptionsAndGetItem() throws Exception {
        String code = "HSA_COMBO_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp thử nghiệm",
                                "nameEn", "Test Combination",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_LY_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Quy chế 2026"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andExpect(jsonPath("$.combination.isCurrent").value(true))
                .andExpect(jsonPath("$.combination.examItemId").value(SAMPLE_HSA_ID.toString()))
                .andExpect(jsonPath("$.combination.examCode").value("HSA"))
                .andExpect(jsonPath("$.combination.sourceNote").value("Quy chế 2026"))
                .andExpect(jsonPath("$.combination.subjects", hasSize(3)))
                .andExpect(jsonPath("$.combination.subjects[0].code").value("TOAN"))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("LY"))
                .andExpect(jsonPath("$.combination.subjects[2].code").value("ANH"))
                .andReturn();

        UUID itemId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText());
        createdItemIds.add(itemId);

        mockMvc.perform(get("/api/v1/catalog/options")
                        .param("group", "SUBJECT_COMBINATION")
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem(code)));

        mockMvc.perform(get("/api/v1/catalog/items/{id}", itemId).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("LY"));
    }

    @Test
    void createCombination_duplicateCodeBlocked_andValidationsEnforced() throws Exception {
        String code = "QDA_DUP_" + suffix();
        MvcResult first = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp 1",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID)))))
                .andExpect(status().isCreated())
                .andReturn();
        createdItemIds.add(UUID.fromString(objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText()));

        // Case-insensitive duplicate code -> 409 CATALOG_CODE_TAKEN
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code.toLowerCase(),
                                "nameVi", "Trùng mã",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_CODE_TAKEN"));

        // Duplicate subjects -> 400
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "DUP_SUB_" + suffix(),
                                "nameVi", "Trùng môn",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_TOAN_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("DUPLICATE"));

        // Empty subjects -> 400
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "NO_SUB_" + suffix(),
                                "nameVi", "Thiếu môn",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of()))))
                .andExpect(status().isBadRequest());

        // Passing subject as examItemId -> 400
        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "BAD_EX_" + suffix(),
                                "nameVi", "Sai kỳ thi",
                                "examItemId", SAMPLE_TOAN_ID,
                                "subjectItemIds", List.of(SAMPLE_VAN_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_EXAM"));

        // Inactive exam & inactive subject rejected
        UUID inactiveExamId = createSimpleItem(EXAM_GROUP_ID, "EX_INACT_" + suffix(), "Kỳ thi ngừng", "INACTIVE");
        UUID inactiveSubjectId = createSimpleItem(SUBJECT_GROUP_ID, "SUB_INACT_" + suffix(), "Môn ngừng", "INACTIVE");

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "INACT_EX_" + suffix(),
                                "nameVi", "Kỳ thi ngừng",
                                "examItemId", inactiveExamId,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INACTIVE_EXAM"));

        mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "INACT_SUB_" + suffix(),
                                "nameVi", "Môn ngừng",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(inactiveSubjectId)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INACTIVE_SUBJECT"));
    }

    @Test
    void deactivatedCombinationAbsentFromOptions_butGetItemStillReturnsIt() throws Exception {
        String code = "COMBO_STOP_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp ngừng",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID)))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        long version = created.get("version").asLong();
        createdItemIds.add(itemId);

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INACTIVE",
                                "reason", "Ngừng tổ hợp thử nghiệm",
                                "version", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/catalog/options")
                        .param("group", "SUBJECT_COMBINATION")
                        .with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='" + code + "')]").isEmpty());

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.combination.versionNo").value(1));

        mockMvc.perform(get("/api/v1/catalog/items/{id}", itemId).with(asStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void editCombination_updatesInPlaceWhenUnreferenced_andCreatesV2WhenReferenced() throws Exception {
        String code = "COMBO_VER_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp phiên bản",
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Bản 1"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        UUID v1Id = UUID.fromString(created.get("combination").get("id").asText());
        long ver = created.get("version").asLong();
        createdItemIds.add(itemId);

        // Unreferenced -> edit subjects modifies v1 in place
        MvcResult inPlaceRes = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_LY_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Bản 1 sửa tại chỗ",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.id").value(v1Id.toString()))
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("LY"))
                .andReturn();
        ver = objectMapper.readTree(inPlaceRes.getResponse().getContentAsString()).get("version").asLong();

        // Mark referenced by fake provider -> reorder subjects creates v2; v1 retains old data
        fakeUsageRegistry.setUsages(
                itemId, List.of(new CatalogUsage("Bài thi giả lập", "EXAM_DEFINITION", 1, false, List.of("Đề QĐ-01"))));

        // Editing sourceNote alone without changing subjects/exam -> still in place (versionNo remains 1)
        MvcResult noteOnlyRes = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceNote", "Chỉ sửa ghi chú",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(1))
                .andReturn();
        ver = objectMapper.readTree(noteOnlyRes.getResponse().getContentAsString()).get("version").asLong();

        // Changing subject order creates v2
        MvcResult v2Res = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectItemIds", List.of(SAMPLE_ANH_ID, SAMPLE_TOAN_ID, SAMPLE_LY_ID),
                                "sourceNote", "Bản 2",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(2))
                .andExpect(jsonPath("$.combination.isCurrent").value(true))
                .andExpect(jsonPath("$.combination.subjects[0].code").value("ANH"))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("TOAN"))
                .andExpect(jsonPath("$.combination.subjects[2].code").value("LY"))
                .andReturn();
        UUID v2Id = UUID.fromString(
                objectMapper.readTree(v2Res.getResponse().getContentAsString()).get("combination").get("id").asText());
        assertThat(v2Id).isNotEqualTo(v1Id);

        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/versions", itemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[0].isCurrent").value(true))
                .andExpect(jsonPath("$[1].versionNo").value(1))
                .andExpect(jsonPath("$[1].isCurrent").value(false));

        // Historical v1 endpoint still returns old subject list in original order
        mockMvc.perform(get("/api/v1/admin/catalog/combination-versions/{versionId}", v1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.isCurrent").value(false))
                .andExpect(jsonPath("$.subjects[0].code").value("TOAN"))
                .andExpect(jsonPath("$.subjects[1].code").value("LY"))
                .andExpect(jsonPath("$.subjects[2].code").value("ANH"));

        verify(auditRecorder, atLeastOnce()).record(argThat(e ->
                "CATALOG".equals(e.module()) && "COMBINATION_VERSION_CREATED".equals(e.action())));
    }

    @Test
    void concurrentCombinationEdits_keepSingleCurrentVersion() throws Exception {
        String code = "COMBO_CONC_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp đồng thời",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID)))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        long initialVersion = created.get("version").asLong();
        createdItemIds.add(itemId);

        fakeUsageRegistry.setUsages(
                itemId, List.of(new CatalogUsage("Bài thi giả lập", "EXAM_DEFINITION", 1, false, List.of("Đề HSA-01"))));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> c1 = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                                .with(asUser(Permissions.CATALOG_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_LY_ID),
                                        "version", initialVersion))))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> c2 = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                                .with(asUser(Permissions.CATALOG_MODIFY))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_ANH_ID),
                                        "version", initialVersion))))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> f1 = pool.submit(c1);
            Future<Integer> f2 = pool.submit(c2);
            ready.await();
            start.countDown();
            List<Integer> statuses = List.of(f1.get(), f2.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        } finally {
            pool.shutdownNow();
        }

        Integer currentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catalog.combination_version WHERE item_id = ? AND is_current = true",
                Integer.class,
                itemId);
        assertThat(currentCount).isEqualTo(1);
    }

    @Test
    void versionEndpointsRejectMissingPermissionWith403() throws Exception {
        UUID anyId = Ids.newId();
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/versions", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/combination-versions/{versionId}", anyId)
                        .with(asUser(Permissions.USER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}/versions", anyId)
                        .with(asStudent()))
                .andExpect(status().isForbidden());
    }

    @Test
    void editCombination_usesPerVersionUsageSignal_keepsUnreferencedV2InPlace_andBlocksItemDelete() throws Exception {
        String code = "COMBO_PER_VER_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp theo phiên bản",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID),
                                "sourceNote", "Bản 1"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        UUID v1Id = UUID.fromString(created.get("combination").get("id").asText());
        long ver = created.get("version").asLong();
        createdItemIds.add(itemId);

        // Reference ONLY v1 -> editing v1 creates v2
        fakeUsageRegistry.setUsages(
                itemId, List.of(new CatalogUsage("Bài thi giả lập", "EXAM_DEFINITION", 1, true, List.of("Đề v1"))));
        fakeUsageRegistry.setReferencedVersions(itemId, Set.of(v1Id));

        MvcResult v2Res = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_LY_ID),
                                "sourceNote", "Bản 2",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(2))
                .andReturn();
        JsonNode v2Json = objectMapper.readTree(v2Res.getResponse().getContentAsString());
        UUID v2Id = UUID.fromString(v2Json.get("combination").get("id").asText());
        ver = v2Json.get("version").asLong();

        // Still only v1 is referenced (current v2 is unreferenced) -> editing v2 keeps v2 in place!
        MvcResult v2InPlaceRes = mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Bản 2 sửa tại chỗ",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.id").value(v2Id.toString()))
                .andExpect(jsonPath("$.combination.versionNo").value(2))
                .andExpect(jsonPath("$.combination.subjects[1].code").value("ANH"))
                .andReturn();
        ver = objectMapper.readTree(v2InPlaceRes.getResponse().getContentAsString()).get("version").asLong();

        // Historical v1 remains intact
        mockMvc.perform(get("/api/v1/admin/catalog/combination-versions/{versionId}", v1Id)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.isCurrent").value(false))
                .andExpect(jsonPath("$.subjects[1].code").value("VAN"));

        // Deleting the item is still blocked by item-wide usage of v1
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/catalog/items/{id}", itemId)
                        .param("reason", "Thử xóa khi v1 đang dùng")
                        .with(asUser(Permissions.CATALOG_MODIFY)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_ITEM_IN_USE"));

        // Now reference current v2 -> editing v2 creates v3
        fakeUsageRegistry.setReferencedVersions(itemId, Set.of(v1Id, v2Id));
        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "subjectItemIds", List.of(SAMPLE_LY_ID, SAMPLE_ANH_ID),
                                "sourceNote", "Bản 3",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.versionNo").value(3))
                .andExpect(jsonPath("$.combination.isCurrent").value(true));
    }

    @Test
    void inPlaceCombinationEdit_recordsOrderedBeforeAfterAuditSnapshot_andRollbackLeavesNothing() throws Exception {
        while (outboxRelay.relayBatch() > 0) {
            // drain backlog
        }

        String code = "COMBO_AUD_" + suffix();
        MvcResult createRes = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", "Tổ hợp audit",
                                "examItemId", SAMPLE_HSA_ID,
                                "subjectItemIds", List.of(SAMPLE_TOAN_ID, SAMPLE_VAN_ID),
                                "sourceNote", "Ghi chú ban đầu"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        UUID itemId = UUID.fromString(created.get("id").asText());
        UUID v1Id = UUID.fromString(created.get("combination").get("id").asText());
        long ver = created.get("version").asLong();
        createdItemIds.add(itemId);

        mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "examItemId", SAMPLE_QDA_ID,
                                "subjectItemIds", List.of(SAMPLE_ANH_ID, SAMPLE_TOAN_ID, SAMPLE_LY_ID),
                                "sourceNote", "Ghi chú sửa tại chỗ",
                                "version", ver))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.id").value(v1Id.toString()))
                .andExpect(jsonPath("$.combination.versionNo").value(1));

        while (outboxRelay.relayBatch() > 0) {
            // relay new events
        }

        List<AuditEventView> events = auditQuery.findByTarget(
                "CATALOG_ITEM", itemId.toString(), PageQuery.of(0, 20, null, List.of("occurredAt"))).items();
        AuditEventView updatedEvent = events.stream()
                .filter(e -> "CATALOG_ITEM_UPDATED".equals(e.action()))
                .findFirst()
                .orElseThrow();

        assertThat(updatedEvent.before())
                .containsEntry("versionId", v1Id.toString())
                .containsEntry("versionNo", 1)
                .containsEntry("examItemId", SAMPLE_HSA_ID.toString())
                .containsEntry("sourceNote", "Ghi chú ban đầu")
                .containsEntry("subjectItemIds", List.of(SAMPLE_TOAN_ID.toString(), SAMPLE_VAN_ID.toString()));
        assertThat(updatedEvent.after())
                .containsEntry("versionId", v1Id.toString())
                .containsEntry("versionNo", 1)
                .containsEntry("examItemId", SAMPLE_QDA_ID.toString())
                .containsEntry("sourceNote", "Ghi chú sửa tại chỗ")
                .containsEntry("subjectItemIds", List.of(
                        SAMPLE_ANH_ID.toString(), SAMPLE_TOAN_ID.toString(), SAMPLE_LY_ID.toString()));

        // Rolled-back transaction retains neither business change nor audit event
        int countBeforeRollback = events.size();
        long currentVersion = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andReturn().getResponse().getContentAsString()).get("version").asLong();
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            new vn.unicom.thi360.shared.security.CurrentUser(
                                    Ids.newId(),
                                    "audit.rollback",
                                    vn.unicom.thi360.shared.security.Realm.APP,
                                    vn.unicom.thi360.shared.security.Access.MANAGE,
                                    vn.unicom.thi360.shared.security.PermissionSet.ofCodes(
                                            Set.of(Permissions.CATALOG_MODIFY, Permissions.CATALOG_VIEW))),
                            null,
                            List.of()));
            transactionTemplate.executeWithoutResult(status -> {
                catalogAdminService.updateItem(itemId, new vn.unicom.thi360.catalog.api.UpdateCatalogItemRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        currentVersion,
                        SAMPLE_HSA_ID,
                        List.of(SAMPLE_VAN_ID),
                        "Sẽ rollback"));
                throw new IllegalStateException("Simulated rollback");
            });
        } catch (IllegalStateException expected) {
            // expected
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        while (outboxRelay.relayBatch() > 0) {
            // relay any events if erroneously persisted
        }
        List<AuditEventView> eventsAfter = auditQuery.findByTarget(
                "CATALOG_ITEM", itemId.toString(), PageQuery.of(0, 20, null, List.of("occurredAt"))).items();
        assertThat(eventsAfter).hasSize(countBeforeRollback);
        mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", itemId)
                        .with(asUser(Permissions.CATALOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.combination.sourceNote").value("Ghi chú sửa tại chỗ"))
                .andExpect(jsonPath("$.combination.examItemId").value(SAMPLE_QDA_ID.toString()));
    }

    @Test
    void concurrentCombinationCreateAndSubjectOrExamDeactivation_mutuallyExclusiveUnderLock() throws Exception {
        // Scenario A: Combination creation locks subject first -> subject deactivation waits and gets 409 CATALOG_CHANGE_BLOCKED
        UUID subjAId = createSimpleItem(SUBJECT_GROUP_ID, "SUB_RACE_A_" + suffix(), "Môn race A", "ACTIVE");
        CountDownLatch createLockedSubjA = new CountDownLatch(1);
        CountDownLatch deactivateAboutToLockSubjA = new CountDownLatch(1);
        AtomicBoolean firstLockA = new AtomicBoolean(true);

        HOOKS.put("findByIdForUpdate", (args, proceed) -> {
            UUID idArg = (UUID) args[0];
            if (subjAId.equals(idArg)) {
                if (firstLockA.compareAndSet(true, false)) {
                    Object res = proceed.call();
                    createLockedSubjA.countDown();
                    assertThat(deactivateAboutToLockSubjA.await(10, TimeUnit.SECONDS)).isTrue();
                    return res;
                }
                deactivateAboutToLockSubjA.countDown();
            }
            return proceed.call();
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            String comboACode = "COMBO_RACE_A_" + suffix();
            Future<MvcResult> createFuture = pool.submit(() -> mockMvc.perform(
                            post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "code", comboACode,
                                            "nameVi", "Tổ hợp race A",
                                            "examItemId", SAMPLE_HSA_ID,
                                            "subjectItemIds", List.of(subjAId)))))
                    .andReturn());

            assertThat(createLockedSubjA.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> deactivateFuture = pool.submit(() -> mockMvc.perform(
                            patch("/api/v1/admin/catalog/items/{id}", subjAId)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "status", "INACTIVE",
                                            "reason", "Ngừng môn đồng thời",
                                            "version", 0))))
                    .andReturn());

            MvcResult createRes = createFuture.get(15, TimeUnit.SECONDS);
            MvcResult deactRes = deactivateFuture.get(15, TimeUnit.SECONDS);
            assertThat(createRes.getResponse().getStatus()).isEqualTo(201);
            createdItemIds.add(UUID.fromString(
                    objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText()));
            assertThat(deactRes.getResponse().getStatus()).isEqualTo(409);
            assertThat(objectMapper.readTree(deactRes.getResponse().getContentAsString()).get("code").asText())
                    .isEqualTo("CATALOG_CHANGE_BLOCKED");

            // Scenario B: Exam deactivation locks exam first -> combination creation waits and gets 400 INACTIVE_EXAM
            HOOKS.clear();
            UUID examBId = createSimpleItem(EXAM_GROUP_ID, "EX_RACE_B_" + suffix(), "Kỳ thi race B", "ACTIVE");
            CountDownLatch deactivateLockedExamB = new CountDownLatch(1);
            CountDownLatch createAboutToLockExamB = new CountDownLatch(1);
            AtomicBoolean firstLockB = new AtomicBoolean(true);

            HOOKS.put("findByIdForUpdate", (args, proceed) -> {
                UUID idArg = (UUID) args[0];
                if (examBId.equals(idArg)) {
                    if (firstLockB.compareAndSet(true, false)) {
                        Object res = proceed.call();
                        deactivateLockedExamB.countDown();
                        assertThat(createAboutToLockExamB.await(10, TimeUnit.SECONDS)).isTrue();
                        return res;
                    }
                    createAboutToLockExamB.countDown();
                }
                return proceed.call();
            });

            Future<MvcResult> deactExamFuture = pool.submit(() -> mockMvc.perform(
                            patch("/api/v1/admin/catalog/items/{id}", examBId)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "status", "INACTIVE",
                                            "reason", "Ngừng kỳ thi đồng thời",
                                            "version", 0))))
                    .andReturn());

            assertThat(deactivateLockedExamB.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> createComboBFuture = pool.submit(() -> mockMvc.perform(
                            post("/api/v1/admin/catalog/groups/{id}/items", COMBINATION_GROUP_ID)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "code", "COMBO_RACE_B_" + suffix(),
                                            "nameVi", "Tổ hợp race B",
                                            "examItemId", examBId,
                                            "subjectItemIds", List.of(SAMPLE_TOAN_ID)))))
                    .andReturn());

            MvcResult deactExamRes = deactExamFuture.get(15, TimeUnit.SECONDS);
            MvcResult createComboBRes = createComboBFuture.get(15, TimeUnit.SECONDS);
            assertThat(deactExamRes.getResponse().getStatus()).isEqualTo(200);
            assertThat(createComboBRes.getResponse().getStatus()).isEqualTo(400);
            assertThat(createComboBRes.getResponse().getContentAsString()).contains("INACTIVE_EXAM");
        } finally {
            HOOKS.clear();
            pool.shutdownNow();
        }
    }

    private UUID createSimpleItem(UUID groupId, String code, String nameVi, String status) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "nameVi", nameVi,
                                "status", status))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdItemIds.add(id);
        return id;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
