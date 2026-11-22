package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.application.ExamPreviewStore;
import vn.unicom.thi360.exam.application.SolverInputBuilder;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.GroupQuota;
import vn.unicom.thi360.exam.domain.solver.PathCandidate;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOption;
import vn.unicom.thi360.exam.domain.solver.SolvePart;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;

/**
 * AC-23: HSA FULL, the Math and Literature parts, with 2,000 candidates in every block. The measured time goes to the
 * lane report; over 30 seconds is reported to the Advisor, the deadline is not changed here.
 */
@Tag("perf")
class PaperGenerationPerfIT extends PaperGenerationBaseIT {
    private static final int PER_BLOCK = 2_000;

    @Autowired CatalogLookup catalog;
    @Autowired ExamPreviewStore store;
    @Autowired SolverInputBuilder builder;

    @Test
    void hsaMathAndLiteratureWithTwoThousandCandidatesPerBlockGenerateWithinTheDeadline() throws Exception {
        Published published = publishTemplate("HSA", "FULL", null, null, null);
        List<String> parts = partIds(published);
        List<String> requested = List.of(parts.get(0), parts.get(1));

        ExamPreviewStore.Snapshot snapshot = store.loadVersion(published.examId(), published.versionNo());
        ExamStructureDocument all = new ExamStructureCodec(mapper).read(snapshot.parts());
        ExamStructureDocument document = new ExamStructureDocument(
                all.parts().stream().filter(part -> requested.contains(part.id())).toList(), all.examQuotas());

        // Bring every block of the two parts to 2,000 candidates with copies of what the seed already supplies.
        int blocks = 0;
        for (SolvePart part : builder.build(snapshot.exam().getKindItemId(), document).parts()) {
            for (GroupQuota group : groups(part)) {
                blocks++;
                List<UUID> sources = candidates(document, snapshot, group).stream()
                        .map(candidate -> UUID.fromString(candidate.questionId())).toList();
                assertThat(sources).as("seed source of block %s", group.groupId()).isNotEmpty();
                int copies = (PER_BLOCK - sources.size() + sources.size() - 1) / sources.size();
                if (copies > 0) cloneMany(sources, copies);
            }
        }
        for (SolvePart part : builder.build(snapshot.exam().getKindItemId(), document).parts()) {
            for (GroupQuota group : groups(part)) {
                assertThat(candidates(document, snapshot, group).size()).as("candidates of block %s", group.groupId())
                        .isGreaterThanOrEqualTo(PER_BLOCK);
            }
        }

        long worst = 0;
        GeneratedPaper paper = null;
        for (long seed = 1; seed <= 3; seed++) {
            long start = System.nanoTime();
            paper = port.generate(published.examId(), published.versionNo(), requested, List.of(), seed, Set.of());
            long ms = (System.nanoTime() - start) / 1_000_000;
            worst = Math.max(worst, ms);
            System.out.println("PERF PaperGeneration HSA FULL Toan+Van blocks=" + blocks + " candidates>=" + PER_BLOCK
                    + " seed=" + seed + " ms=" + ms);
        }
        System.out.println("PERF PaperGeneration HSA FULL Toan+Van worst ms=" + worst);
        assertThat(paper.parts()).hasSize(2);
        assertThat(paper.parts()).allSatisfy(part -> assertThat(part.sections().getFirst().questionCount()).isEqualTo(50));
        assertThat(worst).isLessThan(30_000);
    }

    @Test
    void hsaMathAndLiteratureWithMapidRulesAndTwoThousandCandidatesPerBlockGenerateWithinTheDeadline() throws Exception {
        var createReq = mapper.createObjectNode()
                .put("kind", "HSA")
                .put("scope", "FULL")
                .put("name", "UNI-2771 HSA FULL MAPID Perf")
                .put("code", "U2771-" + UUID.randomUUID().toString().substring(0, 8))
                .put("durationMinutes", 60);
        var created = json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/exams")
                .with(asUser(vn.unicom.thi360.identity.spi.Permissions.EXAM_MODIFY))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(createReq.toString()), 201);
        UUID examId = UUID.fromString(created.path("id").asText());

        var structureResp = json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/admin/exams/" + examId + "/structure")
                .with(asUser(vn.unicom.thi360.identity.spi.Permissions.EXAM_VIEW)), 200);
        long revision = structureResp.path("revision").asLong();
        var partsNode = (com.fasterxml.jackson.databind.node.ArrayNode) structureResp.path("parts").deepCopy();
        List<String> requested = List.of(partsNode.get(0).path("id").asText(), partsNode.get(1).path("id").asText());

        // Pick 3 unrelated active KNOWLEDGE catalog nodes for MATRIX rules.
        List<UUID> disjointNodes = new ArrayList<>();
        List<String> chosenPaths = new ArrayList<>();
        for (CatalogItemView item : catalog.listActiveGroupItems("KNOWLEDGE")) {
            if (chosenPaths.stream().noneMatch(p -> p.startsWith(item.path()) || item.path().startsWith(p))) {
                disjointNodes.add(item.id());
                chosenPaths.add(item.path());
                if (disjointNodes.size() == 3) break;
            }
        }
        assertThat(disjointNodes).hasSize(3);

        var mathSection = (com.fasterxml.jackson.databind.node.ObjectNode) partsNode.get(0).path("sections").get(0);
        String mathFirstBlockId = mathSection.path("blocks").get(0).path("id").asText();
        String mathSecondBlockId = mathSection.path("blocks").get(1).path("id").asText();
        mathSection.put("mapidMode", "MATRIX");
        var mathRules = mapper.createArrayNode();
        mathRules.add(mapper.createObjectNode()
                .put("id", "m-exact-n0")
                .put("type", "COUNT")
                .put("comparator", "EXACT")
                .put("count", 3)
                .set("nodeIds", mapper.createArrayNode().add(disjointNodes.get(0).toString())));
        mathRules.add(mapper.createObjectNode()
                .put("id", "m-min-n1-b2")
                .put("type", "COUNT")
                .put("groupId", mathSecondBlockId)
                .put("comparator", "AT_LEAST")
                .put("count", 2)
                .set("nodeIds", mapper.createArrayNode().add(disjointNodes.get(1).toString())));
        mathSection.set("mapidRules", mathRules);

        var litSection = (com.fasterxml.jackson.databind.node.ObjectNode) partsNode.get(1).path("sections").get(0);
        litSection.put("mapidMode", "MATRIX");
        var litRules = mapper.createArrayNode();
        litRules.add(mapper.createObjectNode()
                .put("id", "v-exact-n2")
                .put("type", "COUNT")
                .put("comparator", "EXACT")
                .put("count", 2)
                .set("nodeIds", mapper.createArrayNode().add(disjointNodes.get(2).toString())));
        litSection.set("mapidRules", litRules);

        var putBody = mapper.createObjectNode().put("expectedRevision", revision);
        putBody.set("parts", partsNode);
        json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/admin/exams/" + examId + "/structure")
                .with(asUser(vn.unicom.thi360.identity.spi.Permissions.EXAM_MODIFY))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(putBody.toString()), 200);

        UUID kindItemId = jdbc.queryForObject("SELECT kind_item_id FROM exam.exam WHERE id = ?", UUID.class, examId);
        var savedStructureJson = mapper.createObjectNode();
        savedStructureJson.set("parts", partsNode);
        ExamStructureDocument all = new ExamStructureCodec(mapper).read(savedStructureJson.toString());
        ExamStructureDocument document = new ExamStructureDocument(
                all.parts().stream().filter(part -> requested.contains(part.id())).toList(), all.examQuotas());

        // Clone every block of Math + Literature up to >= 2,000 candidates.
        for (SolvePart part : builder.build(kindItemId, document).parts()) {
            for (GroupQuota group : groups(part)) {
                List<UUID> sources = builder.build(kindItemId, document).candidates().candidatesFor(group).stream()
                        .map(candidate -> UUID.fromString(candidate.questionId())).toList();
                assertThat(sources).isNotEmpty();
                int copies = (PER_BLOCK - sources.size() + sources.size() - 1) / sources.size();
                if (copies > 0) cloneMany(sources, copies);
            }
        }

        // Tag subsets of Math and Literature candidates so MAPID EXACT/AT_LEAST rules actively constrain the solver.
        List<UUID> mathBlock1Pool = builder.build(kindItemId, document).candidates()
                .candidatesFor(groups(builder.build(kindItemId, document).parts().get(0)).get(0))
                .stream().limit(250).map(c -> UUID.fromString(c.questionId())).toList();
        List<UUID> mathBlock2Pool = builder.build(kindItemId, document).candidates()
                .candidatesFor(groups(builder.build(kindItemId, document).parts().get(0)).get(1))
                .stream().limit(250).map(c -> UUID.fromString(c.questionId())).toList();
        List<UUID> litSinglePool = builder.build(kindItemId, document).candidates()
                .candidatesFor(groups(builder.build(kindItemId, document).parts().get(1)).stream()
                        .filter(g -> g.kind() == GroupQuota.GroupKind.SINGLE).findFirst().orElseThrow())
                .stream().limit(250).map(c -> UUID.fromString(c.questionId())).toList();
        tagKnowledge(mathBlock1Pool, disjointNodes.get(0));
        tagKnowledge(mathBlock2Pool, disjointNodes.get(1));
        tagKnowledge(litSinglePool, disjointNodes.get(2));

        long checkStart = System.nanoTime();
        Published published = checkAndPublish(examId);
        long checkMs = (System.nanoTime() - checkStart) / 1_000_000;
        System.out.println("PERF PaperCheck+Publish HSA FULL MAPID ms=" + checkMs);
        assertThat(checkMs).isLessThan(30_000);

        long worst = 0;
        GeneratedPaper paper = null;
        for (long seed = 1; seed <= 3; seed++) {
            long start = System.nanoTime();
            paper = port.generate(published.examId(), published.versionNo(), requested, List.of(), seed, Set.of());
            long ms = (System.nanoTime() - start) / 1_000_000;
            worst = Math.max(worst, ms);
            System.out.println("PERF PaperGeneration HSA FULL MAPID Toan+Van candidates>=" + PER_BLOCK
                    + " seed=" + seed + " ms=" + ms);
        }
        System.out.println("PERF PaperGeneration HSA FULL MAPID Toan+Van worst ms=" + worst);
        assertThat(paper.parts()).hasSize(2);
        assertThat(worst).isLessThan(30_000);
    }

    private void tagKnowledge(List<UUID> questionIds, UUID knowledgeId) {
        for (UUID qid : questionIds) {
            jdbc.update("INSERT INTO questionbank.question_knowledge(question_id, knowledge_item_id, is_primary) "
                    + "VALUES (?, ?, false) ON CONFLICT DO NOTHING", qid, knowledgeId);
        }
    }

    private List<PathCandidate> candidates(ExamStructureDocument document, ExamPreviewStore.Snapshot snapshot,
            GroupQuota group) {
        // A fresh input per read: the source caches what it has already fetched. Groups compare by value, so a group
        // taken from one input is answered by another.
        return builder.build(snapshot.exam().getKindItemId(), document).candidates().candidatesFor(group);
    }

    private static List<GroupQuota> groups(SolvePart part) {
        List<SectionOnPath> sections = new ArrayList<>();
        if (part instanceof SolvePart.Fixed fixed) {
            sections.addAll(fixed.sections());
        } else if (part instanceof SolvePart.Choice choice) {
            sections.addAll(choice.commonSections());
            choice.options().forEach(option -> {
                if (option instanceof SolveOption.Sections normal) sections.addAll(normal.sections());
            });
        }
        List<GroupQuota> groups = new ArrayList<>();
        sections.forEach(section -> groups.addAll(section.groups()));
        return groups;
    }
}
