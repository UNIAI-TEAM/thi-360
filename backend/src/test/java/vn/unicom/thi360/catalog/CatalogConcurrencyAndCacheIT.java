package vn.unicom.thi360.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.catalog.application.CatalogCacheService;
import vn.unicom.thi360.catalog.application.CatalogSupportDao;
import vn.unicom.thi360.catalog.infrastructure.CatalogGroupRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRefRepository;
import vn.unicom.thi360.catalog.infrastructure.CatalogItemRepository;
import vn.unicom.thi360.identity.spi.Permissions;

// Own context (RepoHookConfig): concurrent scenarios hold several connections at once (paused
// transactions plus the competing request), above the run-wide test pool of 4; close the
// context and its pool after the class so it does not count against the shared Postgres limit.
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=8")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogConcurrencyAndCacheIT extends AbstractIntegrationTest {

    @FunctionalInterface
    interface InvocationCallable {
        Object call() throws Throwable;
    }

    @FunctionalInterface
    interface RepoHook {
        Object intercept(Object[] args, InvocationCallable proceed) throws Throwable;
    }

    private static final Map<String, RepoHook> HOOKS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class RepoHookConfig {
        @Bean
        static BeanPostProcessor catalogRepoHookPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    String prefix;
                    if (bean instanceof CatalogGroupRepository) {
                        prefix = "CatalogGroupRepository#";
                    } else if (bean instanceof CatalogItemRepository) {
                        prefix = "CatalogItemRepository#";
                    } else if (bean instanceof CatalogItemRefRepository) {
                        prefix = "CatalogItemRefRepository#";
                    } else {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            bean.getClass().getClassLoader(),
                            bean.getClass().getInterfaces(),
                            (proxy, method, args) -> {
                                RepoHook hook = HOOKS.get(prefix + method.getName());
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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    private CatalogSupportDao catalogSupportDao;

    @MockitoSpyBean
    private CatalogCacheService catalogCacheService;

    private final List<UUID> createdGroupIds = new ArrayList<>();
    private final Set<String> cacheGroupCodes = new HashSet<>();

    @AfterEach
    void cleanup() {
        HOOKS.clear();
        reset(catalogSupportDao, catalogCacheService);
        for (UUID gid : createdGroupIds) {
            jdbcTemplate.update(
                    "DELETE FROM catalog.catalog_item_ref WHERE item_id IN (SELECT id FROM catalog.catalog_item WHERE group_id = ?) OR ref_item_id IN (SELECT id FROM catalog.catalog_item WHERE group_id = ?)",
                    gid, gid);
            jdbcTemplate.update("DELETE FROM catalog.catalog_group_ref WHERE group_id = ? OR ref_group_id = ?", gid, gid);
            jdbcTemplate.update("DELETE FROM catalog.catalog_item WHERE group_id = ?", gid);
            jdbcTemplate.update("DELETE FROM catalog.catalog_group WHERE id = ?", gid);
        }
        createdGroupIds.clear();
        for (String code : cacheGroupCodes) {
            redisTemplate.delete(CatalogCacheService.optionsKey(code));
            redisTemplate.delete(CatalogCacheService.treeKey(code));
            redisTemplate.delete("catalog:gen:" + code);
        }
        cacheGroupCodes.clear();
    }

    @Test
    void concurrentGroupPatchDoesNotRewindLastValue() throws Exception {
        String code = "LASTVAL_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());
        UUID groupId = createGroup(code, "SIMPLE", null);
        createItem(groupId, null, "ITEM_1", "Mục 1");

        CountDownLatch advancedLatch = new CountDownLatch(1);
        CountDownLatch releaseCreateLatch = new CountDownLatch(1);
        CountDownLatch patchEnteringLockLatch = new CountDownLatch(1);
        AtomicBoolean armCreatePause = new AtomicBoolean(true);
        AtomicBoolean armPatchSignal = new AtomicBoolean(false);

        HOOKS.put("CatalogGroupRepository#advanceLastValue", (args, proceed) -> {
            Object res = proceed.call();
            if (groupId.equals(args[0]) && Integer.valueOf(2).equals(args[1]) && armCreatePause.compareAndSet(true, false)) {
                advancedLatch.countDown();
                assertThat(releaseCreateLatch.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return res;
        });

        HOOKS.put("CatalogGroupRepository#findByIdForUpdate", (args, proceed) -> {
            if (groupId.equals(args[0]) && armPatchSignal.compareAndSet(true, false)) {
                patchEnteringLockLatch.countDown();
            }
            return proceed.call();
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> createFuture = pool.submit(() -> mockMvc.perform(
                            post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            Map.of("code", "ITEM_2", "nameVi", "Mục 2"))))
                    .andReturn());

            assertThat(advancedLatch.await(10, TimeUnit.SECONDS)).isTrue();
            armPatchSignal.set(true);

            Future<MvcResult> patchFuture = pool.submit(() -> mockMvc.perform(
                            patch("/api/v1/admin/catalog/groups/{id}", groupId)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            Map.of("nameVi", "Tên nhóm mới", "version", 0))))
                    .andReturn());

            assertThat(patchEnteringLockLatch.await(10, TimeUnit.SECONDS)).isTrue();
            releaseCreateLatch.countDown();

            MvcResult createRes = createFuture.get(15, TimeUnit.SECONDS);
            MvcResult patchRes = patchFuture.get(15, TimeUnit.SECONDS);
            assertThat(createRes.getResponse().getStatus()).isEqualTo(201);
            assertThat(patchRes.getResponse().getStatus()).isEqualTo(200);

            JsonNode item2 = objectMapper.readTree(createRes.getResponse().getContentAsString());
            assertThat(item2.get("value").asInt()).isEqualTo(2);
            UUID item2Id = UUID.fromString(item2.get("id").asText());

            mockMvc.perform(delete("/api/v1/admin/catalog/items/{id}", item2Id)
                            .param("reason", "Xóa mục 2")
                            .with(asUser(Permissions.CATALOG_MODIFY)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                            .with(asUser(Permissions.CATALOG_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("code", "ITEM_3", "nameVi", "Mục 3"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.value").value(3));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentMovesOneSucceedsOtherGets412AndDescendantVersionBumped() throws Exception {
        String code = "CONC_MOVE_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());
        UUID groupId = createGroup(code, "TREE", List.of("Chương", "Bài", "Kiến thức"));

        UUID ch1Id = createItemId(groupId, null, "CH1", "Chương 1");
        UUID ch2Id = createItemId(groupId, null, "CH2", "Chương 2");
        UUID ch3Id = createItemId(groupId, null, "CH3", "Chương 3");
        UUID l1Id = createItemId(groupId, ch1Id, "L1", "Bài 1");
        UUID k1Id = createItemId(groupId, l1Id, "K1", "Kiến thức 1");

        CyclicBarrier beforeLockBarrier = new CyclicBarrier(2);
        HOOKS.put("CatalogItemRepository#findGroupIdById", (args, proceed) -> {
            Object res = proceed.call();
            if (l1Id.equals(args[0])) {
                beforeLockBarrier.await(10, TimeUnit.SECONDS);
            }
            return res;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> moveToCh2 = pool.submit(() -> mockMvc.perform(
                            put("/api/v1/admin/catalog/items/{id}/parent", l1Id)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            Map.of("parentId", ch2Id, "version", 0))))
                    .andReturn());

            Future<MvcResult> moveToCh3 = pool.submit(() -> mockMvc.perform(
                            put("/api/v1/admin/catalog/items/{id}/parent", l1Id)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            Map.of("parentId", ch3Id, "version", 0))))
                    .andReturn());

            int s1 = moveToCh2.get(15, TimeUnit.SECONDS).getResponse().getStatus();
            int s2 = moveToCh3.get(15, TimeUnit.SECONDS).getResponse().getStatus();
            assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 412);

            UUID winnerChId = s1 == 200 ? ch2Id : ch3Id;
            UUID loserChId = s1 == 200 ? ch3Id : ch2Id;

            mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", k1Id)
                            .with(asUser(Permissions.CATALOG_VIEW)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.path").value("/" + winnerChId + "/" + l1Id + "/" + k1Id + "/"));

            mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", k1Id)
                            .with(asUser(Permissions.CATALOG_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("nameVi", "Cũ", "version", 0))))
                    .andExpect(status().isPreconditionFailed())
                    .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

            mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                            .param("nodeId", winnerChId.toString())
                            .with(asStudent()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(k1Id.toString()));

            mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                            .param("nodeId", loserChId.toString())
                            .with(asStudent()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCreateAndMoveKeepChildPathConsistentWithMovedParent() throws Exception {
        String code = "CREATE_MOVE_" + suffix();
        cacheGroupCodes.add(code.toLowerCase());
        UUID groupId = createGroup(code, "TREE", List.of("Chương", "Bài", "Kiến thức"));

        UUID ch1Id = createItemId(groupId, null, "CH1", "Chương 1");
        UUID ch2Id = createItemId(groupId, null, "CH2", "Chương 2");
        UUID l1Id = createItemId(groupId, ch1Id, "L1", "Bài 1");

        CountDownLatch createReadOldParentReadyToInsert = new CountDownLatch(1);
        CountDownLatch moveCompletedLatch = new CountDownLatch(1);
        AtomicBoolean armCreatePause = new AtomicBoolean(true);
        AtomicBoolean moverBypassedLock = new AtomicBoolean(false);

        HOOKS.put("CatalogItemRepository#findMaxDisplayOrder", (args, proceed) -> {
            Object res = proceed.call();
            if (groupId.equals(args[0]) && l1Id.equals(args[1]) && armCreatePause.compareAndSet(true, false)) {
                // Parent L1's old path (/ch1/l1/) has already been read and copied for K_NEW, before saveAndFlush.
                createReadOldParentReadyToInsert.countDown();
                if (moveCompletedLatch.await(400, TimeUnit.MILLISECONDS)) {
                    moverBypassedLock.set(true);
                }
            }
            return res;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> createFuture = pool.submit(() -> mockMvc.perform(
                            post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of(
                                            "code", "K_NEW",
                                            "nameVi", "Kiến thức mới",
                                            "parentId", l1Id))))
                    .andReturn());

            assertThat(createReadOldParentReadyToInsert.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MvcResult> moveFuture = pool.submit(() -> {
                MvcResult res = mockMvc.perform(
                                put("/api/v1/admin/catalog/items/{id}/parent", l1Id)
                                        .with(asUser(Permissions.CATALOG_MODIFY))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(Map.of("parentId", ch2Id))))
                        .andReturn();
                moveCompletedLatch.countDown();
                return res;
            });

            MvcResult createRes = createFuture.get(15, TimeUnit.SECONDS);
            MvcResult moveRes = moveFuture.get(15, TimeUnit.SECONDS);
            assertThat(moverBypassedLock.get()).isFalse();
            assertThat(createRes.getResponse().getStatus()).isEqualTo(201);
            assertThat(moveRes.getResponse().getStatus()).isEqualTo(200);

            UUID kNewId = UUID.fromString(
                    objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText());

            mockMvc.perform(get("/api/v1/admin/catalog/items/{id}", kNewId)
                            .with(asUser(Permissions.CATALOG_VIEW)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value("/" + ch2Id + "/" + l1Id + "/" + kNewId + "/"));

            mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                            .param("nodeId", ch2Id.toString())
                            .with(asStudent()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(kNewId.toString()));

            mockMvc.perform(get("/api/v1/catalog/tree/descendants")
                            .param("nodeId", ch1Id.toString())
                            .with(asStudent()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void fencedCacheRejectsStaleOptionsAndTreePublicationsAfterEviction() throws Exception {
        // Part A: Options value cache fence
        String optCode = "FENCE_OPT_" + suffix();
        cacheGroupCodes.add(optCode.toLowerCase());
        UUID optGroupId = createGroup(optCode, "SIMPLE", null);
        createItem(optGroupId, null, "A", "Mục A");

        CountDownLatch optionsReadDone = new CountDownLatch(1);
        CountDownLatch releaseStaleOptionsPut = new CountDownLatch(1);
        AtomicBoolean armOptionsPut = new AtomicBoolean(true);

        doAnswer(inv -> {
            if (armOptionsPut.compareAndSet(true, false)) {
                optionsReadDone.countDown();
                assertThat(releaseStaleOptionsPut.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(catalogCacheService).putOptions(eq(optCode.toLowerCase()), any(), any());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> staleRead = pool.submit(() -> mockMvc.perform(
                            get("/api/v1/catalog/options").param("group", optCode).with(asStudent()))
                    .andReturn());

            assertThat(optionsReadDone.await(10, TimeUnit.SECONDS)).isTrue();
            createItem(optGroupId, null, "B", "Mục B");
            releaseStaleOptionsPut.countDown();

            MvcResult staleRes = staleRead.get(15, TimeUnit.SECONDS);
            assertThat(staleRes.getResponse().getStatus()).isEqualTo(200);

            mockMvc.perform(get("/api/v1/catalog/options").param("group", optCode).with(asStudent()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].code", containsInAnyOrder("A", "B")));

            // Part B: Tree hash cache fence
            String treeCode = "FENCE_TREE_" + suffix();
            cacheGroupCodes.add(treeCode.toLowerCase());
            UUID treeGroupId = createGroup(treeCode, "TREE", List.of("Chương", "Bài", "Kiến thức"));
            UUID ch1Id = createItemId(treeGroupId, null, "CH1", "Chương 1 cũ");

            CountDownLatch treeReadDone = new CountDownLatch(1);
            CountDownLatch releaseStaleTreePut = new CountDownLatch(1);
            AtomicBoolean armTreePut = new AtomicBoolean(true);

            doAnswer(inv -> {
                if (armTreePut.compareAndSet(true, false)) {
                    treeReadDone.countDown();
                    assertThat(releaseStaleTreePut.await(10, TimeUnit.SECONDS)).isTrue();
                }
                return inv.callRealMethod();
            }).when(catalogCacheService).putTreeNodes(eq(treeCode.toLowerCase()), any(), any(), any());

            Future<MvcResult> staleTreeRead = pool.submit(() -> mockMvc.perform(
                            get("/api/v1/catalog/tree").param("group", treeCode).with(asStudent()))
                    .andReturn());

            assertThat(treeReadDone.await(10, TimeUnit.SECONDS)).isTrue();
            mockMvc.perform(patch("/api/v1/admin/catalog/items/{id}", ch1Id)
                            .with(asUser(Permissions.CATALOG_MODIFY))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("nameVi", "Chương 1 mới", "version", 0))))
                    .andExpect(status().isOk());
            releaseStaleTreePut.countDown();

            MvcResult staleTreeRes = staleTreeRead.get(15, TimeUnit.SECONDS);
            assertThat(staleTreeRes.getResponse().getStatus()).isEqualTo(200);

            mockMvc.perform(get("/api/v1/catalog/tree").param("group", treeCode).with(asStudent()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].nameVi").value("Chương 1 mới"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRefGroupRemovalAndItemRefAttachmentSerializeCleanly() throws Exception {
        String sfx = suffix();
        String refCode = "REF_RACE_" + sfx;
        String treeCode = "TREE_RACE_" + sfx;
        cacheGroupCodes.add(refCode.toLowerCase());
        cacheGroupCodes.add(treeCode.toLowerCase());

        UUID refGroupId = createGroup(refCode, "SIMPLE", null);
        UUID r1Id = createItemId(refGroupId, null, "R1", "Tham chiếu 1");

        MvcResult treeRes = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", treeCode,
                                "nameVi", "Cây " + treeCode,
                                "kind", "TREE",
                                "levels", List.of("Chương", "Bài", "Kiến thức"),
                                "refGroupIds", List.of(refGroupId)))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID treeGroupId = UUID.fromString(objectMapper.readTree(treeRes.getResponse().getContentAsString()).get("id").asText());
        createdGroupIds.add(treeGroupId);

        UUID ch1Id = createItemId(treeGroupId, null, "CH1", "Chương 1");

        CountDownLatch removeHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseRemove = new CountDownLatch(1);
        CountDownLatch attachEnteringLock = new CountDownLatch(1);
        AtomicBoolean armRemovePause = new AtomicBoolean(true);
        AtomicBoolean armAttachSignal = new AtomicBoolean(false);

        HOOKS.put("CatalogItemRefRepository#countLinkedItems", (args, proceed) -> {
            Object res = proceed.call();
            if (treeGroupId.equals(args[0]) && refGroupId.equals(args[1]) && armRemovePause.compareAndSet(true, false)) {
                removeHoldingLock.countDown();
                assertThat(releaseRemove.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return res;
        });

        HOOKS.put("CatalogGroupRepository#findByIdForUpdate", (args, proceed) -> {
            if (treeGroupId.equals(args[0]) && armAttachSignal.compareAndSet(true, false)) {
                attachEnteringLock.countDown();
            }
            return proceed.call();
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> removeFuture = pool.submit(() -> mockMvc.perform(
                            delete("/api/v1/admin/catalog/groups/{id}/ref-groups/{refGroupId}",
                                    treeGroupId, refGroupId)
                                    .with(asUser(Permissions.CATALOG_MODIFY)))
                    .andReturn());

            assertThat(removeHoldingLock.await(10, TimeUnit.SECONDS)).isTrue();
            armAttachSignal.set(true);

            Future<MvcResult> attachFuture = pool.submit(() -> mockMvc.perform(
                            put("/api/v1/admin/catalog/items/{id}/refs/{refItemId}", ch1Id, r1Id)
                                    .with(asUser(Permissions.CATALOG_MODIFY)))
                    .andReturn());

            assertThat(attachEnteringLock.await(10, TimeUnit.SECONDS)).isTrue();
            releaseRemove.countDown();

            MvcResult removeRes = removeFuture.get(15, TimeUnit.SECONDS);
            MvcResult attachRes = attachFuture.get(15, TimeUnit.SECONDS);
            assertThat(removeRes.getResponse().getStatus()).isEqualTo(204);
            assertThat(attachRes.getResponse().getStatus()).isEqualTo(400);
            assertThat(objectMapper.readTree(attachRes.getResponse().getContentAsString()).get("code").asText())
                    .isEqualTo("CATALOG_REF_INVALID");

            Integer remaining = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM catalog.catalog_item_ref WHERE item_id = ? AND ref_item_id = ?",
                    Integer.class,
                    ch1Id,
                    r1Id);
            assertThat(remaining).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDuplicateGroupCodeDbConstraintTranslatedTo409() throws Exception {
        String code = "DUP_RACE_" + suffix();
        String norm = code.toLowerCase();
        cacheGroupCodes.add(norm);

        CyclicBarrier barrier = new CyclicBarrier(2);
        HOOKS.put("CatalogGroupRepository#existsByCodeNormalized", (args, proceed) -> {
            Object res = proceed.call();
            if (norm.equals(args[0])) {
                barrier.await(10, TimeUnit.SECONDS);
            }
            return res;
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> f1 = pool.submit(() -> mockMvc.perform(
                            post("/api/v1/admin/catalog/groups")
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            Map.of("code", code, "nameVi", "Nhóm 1"))))
                    .andReturn());

            Future<MvcResult> f2 = pool.submit(() -> mockMvc.perform(
                            post("/api/v1/admin/catalog/groups")
                                    .with(asUser(Permissions.CATALOG_MODIFY))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            Map.of("code", code.toLowerCase(), "nameVi", "Nhóm 2"))))
                    .andReturn());

            MvcResult r1 = f1.get(15, TimeUnit.SECONDS);
            MvcResult r2 = f2.get(15, TimeUnit.SECONDS);

            for (MvcResult res : List.of(r1, r2)) {
                if (res.getResponse().getStatus() == 201) {
                    createdGroupIds.add(UUID.fromString(
                            objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText()));
                }
            }

            assertThat(List.of(r1.getResponse().getStatus(), r2.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 409);

            MvcResult conflict = r1.getResponse().getStatus() == 409 ? r1 : r2;
            assertThat(objectMapper.readTree(conflict.getResponse().getContentAsString()).get("code").asText())
                    .isEqualTo("CATALOG_CODE_TAKEN");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nonCodeDataIntegrityViolationUsesStandardGlobal500ErrorAndTraceId() throws Exception {
        String code = "NON_CODE_DIV_" + suffix();
        HOOKS.put("CatalogGroupRepository#saveAndFlush", (args, proceed) -> {
            throw new DataIntegrityViolationException("simulated non-code constraint violation");
        });

        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", code, "nameVi", "Nhóm lỗi khác"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andReturn();

        String headerTraceId = res.getResponse().getHeader("X-Request-Id");
        assertThat(headerTraceId).isNotBlank();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("traceId").asText()).isEqualTo(headerTraceId);
    }
    private UUID createGroup(String code, String kind, List<String> levels) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("nameVi", "Nhóm " + code);
        body.put("kind", kind);
        if (levels != null) {
            body.put("levels", levels);
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups")
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdGroupIds.add(id);
        return id;
    }

    private JsonNode createItem(UUID groupId, UUID parentId, String code, String nameVi) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("code", code);
        body.put("nameVi", nameVi);
        if (parentId != null) {
            body.put("parentId", parentId.toString());
        }
        MvcResult res = mockMvc.perform(post("/api/v1/admin/catalog/groups/{id}/items", groupId)
                        .with(asUser(Permissions.CATALOG_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private UUID createItemId(UUID groupId, UUID parentId, String code, String nameVi) throws Exception {
        return UUID.fromString(createItem(groupId, parentId, code, nameVi).get("id").asText());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
