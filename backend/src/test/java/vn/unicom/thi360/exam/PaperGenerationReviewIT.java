package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;

/** Review exams (ADD H29): with {@code everyChildInScope} every child of a picked cluster is in the knowledge scope. */
class PaperGenerationReviewIT extends PaperGenerationBaseIT {
    private static final UUID SCOPE_NODE = UUID.fromString("00000000-0000-7000-8200-000000001201");

    @Test
    void everyChildStaysInScope() throws Exception {
        // The review templates ship with the flag on (ADD H29).
        assertThat(jdbc.queryForObject("SELECT structure #>> '{parts,0,sections,0,knowledgeScope,everyChildInScope}' "
                + "FROM exam.exam_template WHERE code = 'VACT_REVIEW'", String.class)).isEqualTo("true");

        UUID outside = outsideScope();
        Subject subject = newSubject();
        // Five clusters wholly in the scope, five with one child outside it. Every cluster has a child inside.
        List<UUID> whole = sets(subject, 5, 3);
        List<UUID> mixed = sets(subject, 5, 3);
        whole.forEach(root -> children(root).forEach(child -> knowledge(child, SCOPE_NODE)));
        mixed.forEach(root -> {
            List<UUID> children = children(root);
            knowledge(children.get(0), SCOPE_NODE);
            knowledge(children.get(1), SCOPE_NODE);
            knowledge(children.get(2), outside);
        });

        // Control: without the flag a cluster with a child in the scope is a candidate, so mixed ones are drawn.
        Published loose = insertVersion(review(subject, false), null);
        Set<UUID> looseDrawn = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            looseDrawn.addAll(roots(port.generate(loose.examId(), 1, List.of("p1"), List.of(), seed, Set.of())));
        }
        assertThat(looseDrawn).containsAnyElementsOf(mixed);

        // With the flag, only whole-in-scope clusters, whatever the seed.
        Published strict = insertVersion(review(subject, true), null);
        for (long seed = 1; seed <= 20; seed++) {
            assertOnlyWholeInScopeClusters(port.generate(strict.examId(), 1, List.of("p1"), List.of(), seed, Set.of()),
                    mixed);
        }
    }

    /**
     * The real V-ACT review template through the screen's requests, check and publish: the flag the template ships
     * survives into the published snapshot, and what the port generates from that version is whole clusters in scope.
     */
    @Test
    void reviewTemplateKeepsTheFlagThroughCheckAndPublish() throws Exception {
        UUID outside = outsideScope();
        Subject subject = newSubject();
        List<UUID> whole = sets(subject, 5, 3);
        List<UUID> mixed = sets(subject, 5, 3);
        whole.forEach(root -> children(root).forEach(child -> knowledge(child, SCOPE_NODE)));
        mixed.forEach(root -> {
            List<UUID> children = children(root);
            knowledge(children.get(0), SCOPE_NODE);
            knowledge(children.get(1), SCOPE_NODE);
            knowledge(children.get(2), outside);
        });
        String templatePart = jdbc.queryForObject(
                "SELECT structure->'parts'->0->>'id' FROM exam.exam_template WHERE code = 'VACT_REVIEW'", String.class);
        ObjectNode create = mapper.createObjectNode().put("kind", "VACT").put("scope", "SUBJECT")
                .put("subjectCode", subject.code()).put("buildMode", "REVIEW").put("name", "EX-2 review")
                .put("code", "EX2R-" + UUID.randomUUID().toString().substring(0, 8)).put("durationMinutes", 45);
        create.putArray("overview").addObject().put("partId", templatePart).put("questionCount", 6)
                .put("durationMinutes", 45);
        JsonNode created = json(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(create.toString()), 201);
        assertThat(created.path("templateCode").asText()).isEqualTo("VACT_REVIEW");
        String id = created.path("id").asText();

        // Step 2 of the screen: pick the scope node and the shape of the block. The flag is the template's.
        JsonNode structure = json(get("/api/v1/admin/exams/" + id + "/structure").with(asUser(Permissions.EXAM_VIEW)), 200);
        ArrayNode parts = (ArrayNode) structure.path("parts").deepCopy();
        ObjectNode section = (ObjectNode) parts.get(0).path("sections").get(0);
        ObjectNode scope = (ObjectNode) section.path("knowledgeScope");
        assertThat(scope.path("everyChildInScope").asBoolean()).isTrue();
        scope.putArray("nodeIds").add(SCOPE_NODE.toString());
        section.put("questionCount", 6);
        ((ObjectNode) section.path("blocks").get(0)).put("questionType", "QUESTION_SET")
                .put("childAnswerType", "SINGLE_CHOICE").put("setCount", 2).put("setSize", 3).put("questionCount", 6);
        ObjectNode save = mapper.createObjectNode().put("expectedRevision", structure.path("revision").asLong());
        save.set("parts", parts);
        json(put("/api/v1/admin/exams/" + id + "/structure").with(asUser(Permissions.EXAM_MODIFY))
                .contentType(MediaType.APPLICATION_JSON).content(save.toString()), 200);

        Published published = checkAndPublish(UUID.fromString(id));

        JsonNode snapshot = mapper.readTree(jdbc.queryForObject("SELECT parts::text FROM exam.exam_version "
                + "WHERE exam_id = ? AND version_no = ?", String.class, published.examId(), published.versionNo()));
        JsonNode published0 = snapshot.path("parts").get(0).path("sections").get(0).path("knowledgeScope");
        assertThat(published0.path("everyChildInScope").asBoolean()).as("flag of the published review").isTrue();
        assertThat(published0.path("nodeIds")).extracting(JsonNode::asText).containsExactly(SCOPE_NODE.toString());

        Set<UUID> drawn = new HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            GeneratedPaper paper = port.generate(published.examId(), published.versionNo(), partIds(published),
                    List.of(), seed, Set.of());
            assertOnlyWholeInScopeClusters(paper, mixed);
            drawn.addAll(roots(paper));
        }
        // The seed moves the draw inside the whole clusters: this is the pool, not one lucky pair.
        assertThat(drawn).hasSizeGreaterThan(2).isSubsetOf(whole);
    }

    private void assertOnlyWholeInScopeClusters(GeneratedPaper paper, List<UUID> mixed) {
        List<UUID> roots = roots(paper);
        assertThat(roots).hasSize(2).doesNotHaveDuplicates().doesNotContainAnyElementsOf(mixed);
        for (UUID root : roots) {
            for (UUID child : children(root)) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM questionbank.question_knowledge "
                        + "WHERE question_id = ? AND knowledge_item_id = ?", Long.class, child, SCOPE_NODE))
                        .as("child %s of %s in scope", child, root).isEqualTo(1L);
            }
        }
    }

    /** A knowledge node that is neither inside nor above the review's scope node. */
    private UUID outsideScope() {
        return jdbc.queryForObject("SELECT i.id FROM catalog.catalog_item i JOIN catalog.catalog_group g "
                + "ON g.id = i.group_id WHERE g.code = 'KNOWLEDGE' AND i.status = 1 AND i.id <> ? "
                + "AND i.path NOT LIKE (SELECT path FROM catalog.catalog_item WHERE id = ?) || '%' "
                + "AND (SELECT path FROM catalog.catalog_item WHERE id = ?) NOT LIKE i.path || '%' LIMIT 1",
                UUID.class, SCOPE_NODE, SCOPE_NODE, SCOPE_NODE);
    }

    private List<UUID> children(UUID root) {
        return new ArrayList<>(jdbc.queryForList("SELECT id FROM questionbank.question WHERE parent_id = ? "
                + "ORDER BY position", UUID.class, root));
    }

    private void knowledge(UUID question, UUID node) {
        jdbc.update("INSERT INTO questionbank.question_knowledge(question_id,knowledge_item_id,is_primary) "
                + "VALUES (?,?,true)", question, node);
    }

    /** A review of two clusters of three children each, in the knowledge scope of {@link #SCOPE_NODE}. */
    private static String review(Subject subject, boolean everyChildInScope) {
        return "{\"parts\":[{\"id\":\"p1\",\"order\":1,\"name\":\"Review\",\"questionCount\":6,"
                + "\"questionCountOnPaper\":6,\"questionCountRequired\":6,"
                + "\"participation\":{\"mode\":\"ALL\",\"choose\":null},\"sections\":[{\"id\":\"s1\",\"subjectCode\":\""
                + subject.code() + "\",\"questionCount\":6,\"knowledgeScope\":{\"nodeIds\":[\"" + SCOPE_NODE
                + "\"],\"competencyCodes\":[],\"everyChildInScope\":" + everyChildInScope + "},"
                + "\"blocks\":[{\"id\":\"b1\",\"questionType\":\"QUESTION_SET\",\"childAnswerType\":\"SINGLE_CHOICE\","
                + "\"setCount\":2,\"setSize\":3,\"questionCount\":6}]}],\"options\":[]}]}";
    }
}
