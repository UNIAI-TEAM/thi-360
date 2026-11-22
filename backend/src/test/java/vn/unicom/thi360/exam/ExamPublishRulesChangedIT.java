package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.exam.application.ExamCheckCompletion;
import vn.unicom.thi360.exam.application.ExamCheckResultStore;
import vn.unicom.thi360.exam.application.ExamCheckScope;
import vn.unicom.thi360.exam.application.ExamCheckUnitEngine;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOutcome;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SolveStats;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/**
 * CR-4 AC-7 (FTM AC 58): a saved whole-exam result that was not evaluated under the R-12 rules cannot publish a
 * standard HSA or QDA exam with a science subject. Only a whole-exam check, valid or not, lifts the reason.
 */
@Import(ExamPublishRulesChangedIT.JumpingClockConfig.class)
class ExamPublishRulesChangedIT extends AbstractExamPublishIT {
    private static final String HSA_SCIENCE = """
            [{"id":"p3","participation":{"mode":"CHOICE","choose":1},"options":[
              {"id":"science","kind":"SUBJECT_CHOICE","countsByPick":[17,17,16],"sections":[
                {"id":"s-ly","subjectCode":"LY","blocks":[]}]},
              {"id":"english","kind":"SUBJECT","code":"ANH","sections":[]}]}]
            """;
    private static final String QDA_SCIENCE =
            "[{\"id\":\"codes\",\"codeSource\":{\"source\":\"GLOBAL_LIST\"},\"subjectPool\":[{\"id\":\"s-sinh\",\"subjectCode\":\"SINH\",\"blocks\":[]}]}]";
    private static final String QDA_PATHS_PLAIN = "[{\"id\":\"codes\",\"codeSource\":{\"source\":\"GLOBAL_LIST\"},\"subjectPool\":[]}]";

    @Autowired private ExamCheckCompletion completion;
    @MockitoSpyBean private ExamCheckUnitEngine units;
    @MockitoSpyBean private QuestionSourcePort questionSource;
    @Autowired private JumpingClock clock;

    private static final UUID SU = UUID.fromString("00000000-0000-7000-8200-000000000207");

    /** The system clock plus an offset the test moves once to cross a check deadline at a chosen moment. */
    static final class JumpingClock extends Clock {
        private volatile java.time.Duration offset = java.time.Duration.ZERO;
        private volatile java.time.Duration expireOnCheckerSample = null;
        private volatile int checkerSamples;

        void jump(java.time.Duration by) { offset = by; }
        void reset() { offset = java.time.Duration.ZERO; expireOnCheckerSample = null; checkerSamples = 0; }
        boolean jumped() { return !offset.isZero(); }

        /**
         * Expires the clock at the second sample the checker body itself takes: the first is the guard before solving,
         * the second the final guard after the result was assembled. Samples taken by the unit engine, the assembler,
         * the source reads or the topic evidence run in other classes and never count, so nothing before the final
         * guard sees the expired clock.
         */
        void expireOnLastSampleOfTheChecker(java.time.Duration by) { checkerSamples = 0; expireOnCheckerSample = by; }

        private static boolean takenByTheCheckerBody() {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                String type = frame.getClassName();
                if (type.equals("java.lang.Thread") || type.equals(JumpingClock.class.getName())) continue;
                return type.endsWith(".DefaultExamChecker") && frame.getMethodName().equals("check");
            }
            return false;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            java.time.Duration armed = expireOnCheckerSample;
            if (armed != null && takenByTheCheckerBody() && ++checkerSamples == 2) {
                offset = armed;
                expireOnCheckerSample = null;
            }
            return Instant.now().plus(offset);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JumpingClockConfig {
        @Bean
        @Primary
        JumpingClock jumpingClock() { return new JumpingClock(); }
    }

    // ---- the chain on a real exam: check, publish, GET, one-subject check, whole check --------------------

    /** A VALID whole-exam result saved before the rules: publish and GET say RULES_CHANGED; a subject check does not clear it. */
    @Test
    void subjectCheckDoesNotClear() throws Exception {
        String base = realHsaScienceExam();
        UUID exam = UUID.fromString(base.substring(base.lastIndexOf('/') + 1));
        JsonNode first = check(base, "{}");
        assertThat(first.path("status").asText()).isEqualTo("VALID");
        assertThat(first.has("rulesStamp")).isFalse();
        removeStamp(exam);

        publishRefusedWith(exam, "RULES_CHANGED", "default");
        JsonNode latest = latest(base);
        assertThat(latest.path("invalidReason").asText()).isEqualTo("RULES_CHANGED");
        assertThat(latest.path("stale").asBoolean()).isTrue();
        assertThat(latest.has("rulesStamp")).isFalse();
        assertThat(latest.path("status").asText()).isEqualTo("VALID");

        String sectionId = jdbc.queryForObject("SELECT jsonb_path_query_first(parts, '$.** ? (@.subjectCode == \"LY\").id') #>> '{}' "
                + "FROM exam.exam_structure WHERE exam_id = ?", String.class, exam);
        JsonNode oneSubject = check(base, "{\"scope\":\"SECTION\",\"sectionId\":\"" + sectionId + "\"}");
        assertThat(oneSubject.has("status")).isTrue();
        assertThat(latest(base).path("invalidReason").asText()).isEqualTo("RULES_CHANGED");
        publishRefusedWith(exam, "RULES_CHANGED", "default");
        assertThat(versions(exam)).isZero();
    }

    @Test
    void examCheckClears() throws Exception {
        String base = realHsaScienceExam();
        UUID exam = UUID.fromString(base.substring(base.lastIndexOf('/') + 1));
        check(base, "{}");
        removeStamp(exam);
        publishRefusedWith(exam, "RULES_CHANGED", "default");

        JsonNode again = check(base, "{}");
        assertThat(again.has("rulesStamp")).as("the stamp is not part of the API result").isFalse();
        assertThat(latest(base).path("invalidReason").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT result->>'rulesStamp' FROM exam.exam_check_run WHERE exam_id = ?", String.class, exam))
                .isEqualTo("FTM_R12");
        assertThat(latest(base).has("rulesStamp")).as("GET never shows the stored stamp, whatever the stored value").isFalse();
        publish(exam, "default").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(versions(exam)).isEqualTo(1);
    }

    // ---- QDA, overall result INVALID under the new rules: E24 as today -----------------------------------

    @Test
    void invalidOverallStampedE24() throws Exception {
        UUID exam = newExam("QDA", QDA_SCIENCE);
        saveOldCheck(exam, paths("codes:QD01=VALID", "codes:QD02=INVALID"));
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD01");

        // The whole check ran and stored its INVALID verdict: stamped, so the reason is gone.
        completeCheck(exam, paths("codes:QD01=VALID", "codes:QD02=INVALID"), ExamCheckScope.from(null, null, null));
        assertThat(stamp(exam)).isEqualTo("FTM_R12");
        assertThat(latestReason(exam)).isNull();
        // The invalid path is not serving yet: the valid path publishes.
        publish(exam, "codes:QD01").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.queryForObject("SELECT array_to_string(serving_path_ids, ',') FROM exam.exam_version WHERE exam_id = ?",
                String.class, exam)).isEqualTo("codes:QD01");
    }

    @Test
    void servedInvalidBlocks() throws Exception {
        UUID exam = newExam("QDA", QDA_SCIENCE);
        completeCheck(exam, paths("codes:QD01=VALID", "codes:QD02=VALID"), ExamCheckScope.from(null, null, null));
        publish(exam, "codes:QD01", "codes:QD02").andExpect(status().isOk());
        jdbc.update("UPDATE exam.exam SET description = 'Changed description' WHERE id = ?", exam);
        completeCheck(exam, paths("codes:QD01=VALID", "codes:QD02=INVALID"), ExamCheckScope.from(null, null, null));
        // Stamped: not RULES_CHANGED, E24 decides, and the serving path that turned INVALID blocks.
        publish(exam, "codes:QD01").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PUBLISH_ALL_PATHS_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("PATH_SERVING_INVALID"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("codes:QD02"));
        assertThat(versions(exam)).isEqualTo(1);
    }

    /** Checks and refused publishes never move the serving path or the published version. */
    @Test
    void failedOpsKeepServed() throws Exception {
        UUID exam = newExam("QDA", QDA_SCIENCE);
        completeCheck(exam, paths("codes:QD01=VALID", "codes:QD02=VALID"), ExamCheckScope.from(null, null, null));
        publish(exam, "codes:QD01", "codes:QD02").andExpect(status().isOk());
        String served = servedState(exam);
        assertThat(served).isEqualTo("1|SERVING|codes:QD01,codes:QD02");

        jdbc.update("UPDATE exam.exam SET description = 'Changed description' WHERE id = ?", exam);
        saveOldCheck(exam, paths("codes:QD01=VALID", "codes:QD02=VALID")); // a result saved before the rules
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD01", "codes:QD02");
        assertThat(servedState(exam)).isEqualTo(served);
        completeCheck(exam, paths("codes:QD01=VALID", "codes:QD02=INVALID"), ExamCheckScope.from("SECTION", null, "s-sinh"));
        assertThat(latestReason(exam)).isEqualTo("RULES_CHANGED");
        completeCheck(exam, paths("codes:QD01=VALID", "codes:QD02=INVALID"), ExamCheckScope.from(null, null, null));
        publish(exam, "codes:QD01").andExpect(status().isUnprocessableEntity());
        assertThat(servedState(exam)).isEqualTo(served);
    }

    /**
     * R1-12: a whole-exam run that never evaluated the paths against the rules cannot vouch for them. It never
     * receives a stamp, even if the preceding run was stamped; a completed run still stamps.
     */
    @Test
    void incompleteWholeRunDoesNotStamp() throws Exception {
        ExamCheckScope whole = ExamCheckScope.from(null, null, null);
        UUID exam = newExam("QDA", QDA_SCIENCE);
        saveOldCheck(exam, paths("codes:QD01=VALID", "codes:QD02=VALID"));
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD01");

        completeCheck(exam, paths("codes:QD01=INVALID", "codes:QD02=INVALID"), whole, false);
        assertThat(stamp(exam)).isNull();
        assertThat(latestReason(exam)).isEqualTo("RULES_CHANGED");
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD01");

        completeCheck(exam, paths("codes:QD01=VALID", "codes:QD02=INVALID"), whole, true);
        assertThat(stamp(exam)).isEqualTo("FTM_R12");
        assertThat(latestReason(exam)).isNull();

        // A new incomplete run cannot inherit the prior run's stamp.
        completeCheck(exam, paths("codes:QD01=INVALID", "codes:QD02=INVALID"), whole, false);
        assertThat(stamp(exam)).isNull();
        assertThat(latestReason(exam)).isEqualTo("RULES_CHANGED");
        assertThat(latest("/api/v1/admin/exams/" + exam).path("status").asText()).isEqualTo("INVALID");
    }

    /**
     * R1-10: a successful non-empty PART check on an unstamped science exam saves its units only. The whole-exam run
     * keeps its id and its missing stamp, so GET and publish still say RULES_CHANGED.
     */
    @Test
    void partCheckKeepsTheRunAndTheReason() throws Exception {
        String base = realHsaScienceExam();
        UUID exam = UUID.fromString(base.substring(base.lastIndexOf('/') + 1));
        check(base, "{}");
        removeStamp(exam);
        UUID run = jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id = ?", UUID.class, exam);
        String partId = jdbc.queryForObject("SELECT parts #>> '{parts,0,id}' FROM exam.exam_structure WHERE exam_id = ?", String.class, exam);

        JsonNode part = check(base, "{\"scope\":\"PART\",\"partId\":\"" + partId + "\"}");
        assertThat(part.path("status").asText()).isEqualTo("VALID");
        assertThat(part.path("paths")).as("a non-empty part check").isNotEmpty();

        assertThat(jdbc.queryForObject("SELECT id FROM exam.exam_check_run WHERE exam_id = ?", UUID.class, exam)).isEqualTo(run);
        assertThat(stamp(exam)).isNull();
        assertThat(latest(base).path("invalidReason").asText()).isEqualTo("RULES_CHANGED");
        publishRefusedWith(exam, "RULES_CHANGED", "default");
        assertThat(versions(exam)).isZero();
    }

    /**
     * AC-58a as one chain on a real QDA exam: a VALID result saved before the rules for a Lý section with three sets is
     * refused with RULES_CHANGED; the real whole recheck then issues SECTION_SET_COUNT_RANGE at the Lý section, only the
     * combinations with Lý fail, the result is stamped, and the valid combinations publish while no refused call moved
     * the serving state. The old result is a real check of the same structure taken while the rules did not apply
     * (a review exam skips them), without the stamp.
     */
    @Test
    void oldValidQdaPhysicsThreeSetsRecheckedForReal() throws Exception {
        UUID exam = qdaExamWithPhysicsThreeSets();
        String base = "/api/v1/admin/exams/" + exam;
        JsonNode old = check(base, "{}");
        assertThat(old.path("status").asText()).as("old check: %s", old.path("groups")).isEqualTo("VALID");
        removeStamp(exam);
        jdbc.update("UPDATE exam.exam SET build_mode = 'STANDARD' WHERE id = ?", exam);
        entityManager.clear();
        List<String> allPaths = new java.util.ArrayList<>();
        old.path("paths").forEach(path -> allPaths.add(path.path("pathId").asText()));
        assertThat(allPaths).containsExactlyInAnyOrder("codes:QD01", "codes:QD02", "codes:QD03", "codes:QD04", "codes:QD05");
        publishRefusedWith(exam, "RULES_CHANGED", allPaths.toArray(String[]::new));
        assertThat(latestReason(exam)).isEqualTo("RULES_CHANGED");
        assertThat(versions(exam)).isZero();

        JsonNode again = check(base, "{}");
        assertThat(again.path("status").asText()).isEqualTo("INVALID");
        JsonNode range = null;
        for (JsonNode list : again.path("groups").findValues("issues")) {
            for (JsonNode item : list) if ("SECTION_SET_COUNT_RANGE".equals(item.path("code").asText())) range = item;
        }
        assertThat(range).as("SECTION_SET_COUNT_RANGE in %s", again.path("groups")).isNotNull();
        assertThat(range.path("sectionId").asText()).isEqualTo("ly");
        assertThat(range.path("subjectCode").asText()).isEqualTo("LY");
        assertThat(range.path("required").asInt()).isEqualTo(2);
        assertThat(range.path("available").asInt()).isEqualTo(3);
        java.util.Map<String, String> outcome = new java.util.TreeMap<>();
        for (JsonNode path : again.path("paths")) {
            boolean carries = path.path("issues").toString().contains("SECTION_SET_COUNT_RANGE");
            outcome.put(path.path("pathId").asText(), path.path("result").asText() + (carries ? "+RANGE" : ""));
        }
        // QD01 Lý+Hóa, QD03 Lý+Sinh hold the Lý section; QD02, QD04, QD05 do not.
        assertThat(outcome).containsEntry("codes:QD01", "INVALID+RANGE").containsEntry("codes:QD03", "INVALID+RANGE")
                .containsEntry("codes:QD02", "VALID").containsEntry("codes:QD04", "VALID").containsEntry("codes:QD05", "VALID");
        assertThat(stamp(exam)).isEqualTo("FTM_R12");
        assertThat(latestReason(exam)).isNull();

        publish(exam, "codes:QD01").andExpect(status().isUnprocessableEntity());
        assertThat(versions(exam)).isZero();
        publish(exam, "codes:QD02", "codes:QD04", "codes:QD05").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(servedState(exam)).isEqualTo("1|SERVING|codes:QD02,codes:QD04,codes:QD05");
    }

    /**
     * R2-2 on a real QDA exam: Sử and Địa share QD04. One unit of that tuple is proved invalid and the other is left
     * unconfirmed, so the path folds to INVALID and no path or selection says UNCONFIRMED, while an earlier combination
     * is VALID. The run is incomplete: no stamp, GET and publish keep RULES_CHANGED, nothing publishes. A run whose
     * only extra is a unit proved invalid is complete and stamps.
     */
    @Test
    void anInvalidAndAnUnconfirmedUnitOfOneTupleKeepTheOldResultUnstamped() throws Exception {
        UUID exam = qdaExamWithPhysicsThreeSets();
        String base = "/api/v1/admin/exams/" + exam;
        JsonNode old = check(base, "{}");
        assertThat(old.path("status").asText()).as("old check: %s", old.path("groups")).isEqualTo("VALID");
        removeStamp(exam);
        jdbc.update("UPDATE exam.exam SET build_mode = 'STANDARD' WHERE id = ?", exam);
        entityManager.clear();
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD02");

        java.util.Map<String, SolveStatus> forced = new java.util.concurrent.ConcurrentHashMap<>();
        doAnswer(call -> {
            java.util.List<SectionOnPath> sections = call.getArgument(0);
            SolveStatus status = forced.get(sections.getFirst().subjectCode());
            if (status == null) return call.callRealMethod();
            return new SolveOutcome(status, status == SolveStatus.UNCONFIRMED ? SolveReason.DEADLINE_EXCEEDED : null,
                    List.of(), SolveStats.EMPTY);
        }).when(units).solveUnit(any(), any(), any());

        // The units of the first check are VALID and would be reused: forget them so that every unit is solved again.
        jdbc.update("DELETE FROM exam.exam_check_unit WHERE exam_id = ?", exam);
        entityManager.clear();
        forced.put("SU", SolveStatus.INVALID);
        forced.put("DIA", SolveStatus.UNCONFIRMED);
        JsonNode incomplete = check(base, "{}");
        java.util.Map<String, String> outcome = new java.util.TreeMap<>();
        for (JsonNode path : incomplete.path("paths")) outcome.put(path.path("pathId").asText(), path.path("result").asText());
        assertThat(outcome).as("QD04 folds to INVALID, QD02 is valid").containsEntry("codes:QD04", "INVALID").containsEntry("codes:QD02", "VALID");
        assertThat(stamp(exam)).as("an incomplete run never stamps").isNull();
        assertThat(latestReason(exam)).isEqualTo("RULES_CHANGED");
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD02");
        assertThat(versions(exam)).isZero();

        // Control: the same run with only the invalid unit is complete and stamps.
        forced.remove("DIA");
        JsonNode complete = check(base, "{}");
        assertThat(complete.path("paths")).isNotEmpty();
        assertThat(stamp(exam)).isEqualTo("FTM_R12");
        assertThat(latestReason(exam)).isNull();
        // QDA codes are chosen after the attempt starts (E24): the publish names every valid path of the check.
        publish(exam, "codes:QD02", "codes:QD05").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    /**
     * R3-1: every unit proof is terminal (all reused), then the deadline passes while the result is rendered: the pool
     * of the first Sử/Địa path is read after the time budget. The earlier Hóa+Sinh path was rendered in time and keeps
     * its VALID witness, the Sử+Địa path is UNCONFIRMED. The result cannot vouch for the rules: no stamp, GET and
     * publish keep RULES_CHANGED, no version is written. The control without the late read stamps although its
     * overall verdict is INVALID.
     */
    @Test
    void aDeadlineAfterTerminalProofsDuringRenderingKeepsTheOldResultUnstamped() throws Exception {
        UUID exam = qdaExamWithPhysicsThreeSets();
        String base = "/api/v1/admin/exams/" + exam;
        JsonNode old = check(base, "{}");
        assertThat(old.path("status").asText()).as("old check: %s", old.path("groups")).isEqualTo("VALID");
        removeStamp(exam);
        jdbc.update("UPDATE exam.exam SET build_mode = 'STANDARD' WHERE id = ?", exam);
        entityManager.clear();
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD02");

        // The units of the first check stay: the next run reuses every proof and solves nothing, so each pool is read
        // for the first time while the result is rendered, path after path.
        org.mockito.Mockito.clearInvocations(units);
        doAnswer(call -> {
            List<?> found = (List<?>) call.callRealMethod();
            QuestionSourcePort.Filter filter = call.getArgument(0);
            boolean rendering = java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                    .anyMatch(frame -> frame.getClassName().endsWith("ExamCheckSources"));
            if (rendering && SU.equals(filter.subjectId())) clock.jump(java.time.Duration.ofSeconds(125));
            return found;
        }).when(questionSource).findCandidates(any(), org.mockito.ArgumentMatchers.anyInt());
        JsonNode late = check(base, "{}");
        org.mockito.Mockito.verify(units, org.mockito.Mockito.never()).solveUnit(any(), any(), any());
        assertThat(clock.jumped()).as("the deadline passed while rendering").isTrue();
        java.util.Map<String, String> outcome = new java.util.TreeMap<>();
        for (JsonNode path : late.path("paths")) outcome.put(path.path("pathId").asText(), path.path("result").asText());
        assertThat(outcome).as("QD02 was rendered in time, QD04 was not").containsEntry("codes:QD02", "VALID")
                .containsEntry("codes:QD04", "UNCONFIRMED");
        assertThat(stamp(exam)).as("a run cut short while rendering never stamps").isNull();
        assertThat(latestReason(exam)).isEqualTo("RULES_CHANGED");
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD02");
        assertThat(versions(exam)).isZero();

        // Control: the same run in time is complete; its overall INVALID verdict (Lý with three sets) stamps.
        clock.reset();
        org.mockito.Mockito.reset(questionSource);
        JsonNode complete = check(base, "{}");
        assertThat(complete.path("status").asText()).isEqualTo("INVALID");
        assertThat(stamp(exam)).isEqualTo("FTM_R12");
        assertThat(latestReason(exam)).isNull();
        // QDA codes are chosen after the attempt starts (E24): the publish names every valid path of the check.
        publish(exam, "codes:QD02", "codes:QD04", "codes:QD05").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    /**
     * R4-4: the deadline passes only AFTER the whole result was proofed and rendered: every proof is reused, no pool is
     * read late and no earlier step sees the expired clock; only the checker's own last sample, after assembly, does.
     * That sample alone keeps the run from vouching for the rules: no stamp, GET and publish keep RULES_CHANGED, no
     * version is written, although every path was rendered in time. The unarmed control stamps.
     */
    @Test
    void aDeadlineThatPassesOnlyAfterFullRenderingKeepsTheOldResultUnstamped() throws Exception {
        UUID exam = qdaExamWithPhysicsThreeSets();
        String base = "/api/v1/admin/exams/" + exam;
        JsonNode old = check(base, "{}");
        assertThat(old.path("status").asText()).as("old check: %s", old.path("groups")).isEqualTo("VALID");
        removeStamp(exam);
        jdbc.update("UPDATE exam.exam SET build_mode = 'STANDARD' WHERE id = ?", exam);
        entityManager.clear();
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD02");

        org.mockito.Mockito.clearInvocations(units);
        clock.expireOnLastSampleOfTheChecker(java.time.Duration.ofSeconds(125));
        JsonNode late = check(base, "{}");
        org.mockito.Mockito.verify(units, org.mockito.Mockito.never()).solveUnit(any(), any(), any());
        assertThat(clock.jumped()).as("the checker's last sample saw the expired clock").isTrue();
        java.util.Map<String, String> outcome = new java.util.TreeMap<>();
        for (JsonNode path : late.path("paths")) outcome.put(path.path("pathId").asText(), path.path("result").asText());
        assertThat(outcome).as("every path was rendered in time, none was cut short")
                .containsEntry("codes:QD02", "VALID").containsEntry("codes:QD04", "VALID")
                .containsEntry("codes:QD05", "VALID").doesNotContainValue("UNCONFIRMED");
        assertThat(stamp(exam)).as("an expired run never stamps, whatever was rendered").isNull();
        assertThat(latestReason(exam)).isEqualTo("RULES_CHANGED");
        publishRefusedWith(exam, "RULES_CHANGED", "codes:QD02");
        assertThat(versions(exam)).isZero();

        // Control: the same run in time stamps.
        clock.reset();
        JsonNode complete = check(base, "{}");
        assertThat(complete.path("status").asText()).isEqualTo("INVALID");
        assertThat(stamp(exam)).isEqualTo("FTM_R12");
        assertThat(latestReason(exam)).isNull();
    }

    // ---- not affected -------------------------------------------------------------------------------------

    @Test
    void otherKindsAndReviewUnaffected() throws Exception {
        // BCA with a science subject: no limits, no reason. A review QDA exam with a science subject: none either.
        UUID bca = newExam("BCA", HSA_SCIENCE);
        saveOldCheck(bca, paths("science=VALID", "english=VALID"));
        UUID review = newExam("QDA", QDA_SCIENCE);
        jdbc.update("UPDATE exam.exam SET build_mode = 'REVIEW' WHERE id = ?", review);
        saveOldCheck(review, paths("codes:QD01=VALID"));
        // H-SCA is another kind without limits: a science subject there keeps its old result too.
        UUID hsca = newExam("HSCA", HSA_SCIENCE);
        saveOldCheck(hsca, paths("science=VALID", "english=VALID"));
        // A standard HSA exam with no limited subject keeps its old result, and so does a QDA exam without a pool subject.
        UUID noScience = newExam("HSA", ExamPublishAllPathsIT.HSA_PART3);
        saveOldCheck(noScience, paths("science=VALID", "english=VALID"));
        UUID plainQda = newExam("QDA", QDA_PATHS_PLAIN);
        saveOldCheck(plainQda, paths("codes:QD01=VALID"));
        // The same standard HSA and QDA exams WITH a science subject are asked to check again.
        UUID hsa = newExam("HSA", HSA_SCIENCE);
        saveOldCheck(hsa, paths("science=VALID", "english=VALID"));
        UUID qda = newExam("QDA", QDA_SCIENCE);
        saveOldCheck(qda, paths("codes:QD01=VALID"));
        for (UUID unaffected : List.of(bca, review, noScience, plainQda, hsca)) assertThat(latestReason(unaffected)).isNull();
        for (UUID affected : List.of(hsa, qda)) assertThat(latestReason(affected)).isEqualTo("RULES_CHANGED");
        // The list filter agrees with the assessment.
        List<String> valid = listed("VALID").findValuesAsText("id");
        List<String> recheck = listed("NEEDS_RECHECK").findValuesAsText("id");
        for (UUID unaffected : List.of(bca, review, noScience, plainQda, hsca)) {
            assertThat(valid).contains(unaffected.toString());
            assertThat(recheck).doesNotContain(unaffected.toString());
        }
        for (UUID affected : List.of(hsa, qda)) {
            assertThat(recheck).contains(affected.toString());
            assertThat(valid).doesNotContain(affected.toString());
        }
        publish(bca, "science", "english").andExpect(status().isOk());
        publish(noScience, "science", "english").andExpect(status().isOk());
        publish(plainQda, "codes:QD01").andExpect(status().isOk());
        publish(review, "codes:QD01").andExpect(status().isOk());
        publish(hsca, "science", "english").andExpect(status().isOk());
    }

    /**
     * R1-13: the blocks a SUBJECT option holds directly are no section of {@code sections}, {@code subjectPool} or
     * {@code options[].sections}. The check raises no R-12 code for them (ExamCheckSubjectLimitsIT), so an old result
     * of such an exam keeps its status: no RULES_CHANGED on GET, in the list or at publish.
     */
    @Test
    void optionBlocksOfASubjectKeepTheOldResult() throws Exception {
        UUID exam = newExam("HSA", "[{\"id\":\"p3\",\"participation\":{\"mode\":\"CHOICE\",\"choose\":1},\"options\":["
                + "{\"id\":\"science\",\"kind\":\"SUBJECT\",\"code\":\"LY\",\"blocks\":[{\"id\":\"b\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":10}]},"
                + "{\"id\":\"english\",\"kind\":\"SUBJECT\",\"code\":\"ANH\",\"sections\":[]}]}]");
        saveOldCheck(exam, paths("science=VALID", "english=VALID"));
        assertThat(latestReason(exam)).isNull();
        assertThat(listed("VALID").findValuesAsText("id")).contains(exam.toString());
        assertThat(listed("NEEDS_RECHECK").findValuesAsText("id")).doesNotContain(exam.toString());
        publish(exam, "science", "english").andExpect(status().isOk());
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    /**
     * A QDA FULL exam (review mode, so the R-12 rules do not apply to its first check) with five combinations of two
     * subjects of 10 questions each and Lý built from three sets of 3 and one short answer. Each block has its own ref.
     */
    private UUID qdaExamWithPhysicsThreeSets() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exam.exam (id, code, code_normalized, name, kind_item_id, scope, timing, question_count, duration_minutes, "
                + "build_mode, has_unpublished_changes) VALUES (?, ?, ?, 'AC-58a', ?, 'FULL', 'SHARED', 20, 60, 'REVIEW', true)",
                id, id.toString(), id.toString(), kind("QDA"));
        String set3 = "{\"id\":\"%s-set\",\"questionType\":\"QUESTION_SET\",\"questionCount\":%d,\"setCount\":%d,\"setSize\":3,\"childAnswerType\":\"SINGLE_CHOICE\"}";
        String single = "{\"id\":\"%s-single\",\"questionType\":\"SINGLE_CHOICE\",\"questionCount\":%d}";
        String fill = "{\"id\":\"%s-fill\",\"questionType\":\"SHORT_ANSWER\",\"questionCount\":1}";
        String[][] sections = {
                {"ly", "LY", "204", String.format(set3, "ly", 9, 3), String.format(fill, "ly")},
                {"hoa", "HOA", "205", String.format(single, "hoa", 6), String.format(set3, "hoa", 3, 1), String.format(fill, "hoa")},
                {"sinh", "SINH", "206", String.format(single, "sinh", 6), String.format(set3, "sinh", 3, 1), String.format(fill, "sinh")},
                {"su", "SU", "207", String.format(single, "su", 7), String.format(set3, "su", 3, 1)},
                {"dia", "DIA", "208", String.format(single, "dia", 7), String.format(set3, "dia", 3, 1)},
                {"anh", "ANH", "203", String.format(single, "anh", 20)}};
        StringBuilder pool = new StringBuilder();
        for (String[] section : sections) {
            String[] blocks = java.util.Arrays.copyOfRange(section, 3, section.length);
            if (pool.length() > 0) pool.append(',');
            pool.append("{\"id\":\"").append(section[0]).append("\",\"subjectCode\":\"").append(section[1]).append("\",\"questionCount\":")
                    .append(section[0].equals("anh") ? 20 : 10).append(",\"blocks\":[").append(String.join(",", blocks)).append("]}");
            for (String block : blocks) {
                String blockId = block.substring(block.indexOf("\"id\":\"") + 6, block.indexOf('"', block.indexOf("\"id\":\"") + 6));
                jdbc.update("INSERT INTO exam.exam_block_ref(id, exam_id, block_id, part_id, section_id, subject_item_id) VALUES (?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), id, blockId, "codes", section[0], UUID.fromString("00000000-0000-7000-8200-000000000" + section[2]));
            }
        }
        jdbc.update("INSERT INTO exam.exam_structure (exam_id, parts, structure_hash) VALUES (?, ?::jsonb, 'ac-58a')", id,
                "{\"parts\":[{\"id\":\"codes\",\"name\":\"Codes\",\"codeSource\":{\"source\":\"GLOBAL_LIST\"},\"subjectPool\":[" + pool + "]}]}");
        ExamListingFixture.fill(jdbc, id);
        return id;
    }

    private String realHsaScienceExam() throws Exception {
        ObjectNode body = mapper.createObjectNode().put("kind", "HSA").put("scope", "SUBJECT").put("subjectCode", "LY")
                .put("durationMinutes", 60).put("name", "CR-4 RULES_CHANGED").put("code", "RC-" + UUID.randomUUID().toString().substring(0, 8));
        JsonNode created = mapper.readTree(mockMvc.perform(post("/api/v1/admin/exams").with(asUser(Permissions.EXAM_MODIFY))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.path("id").asText());
        ExamListingFixture.fill(jdbc, id);
        entityManager.flush();
        entityManager.clear();
        return "/api/v1/admin/exams/" + id;
    }

    private JsonNode check(String base, String body) throws Exception {
        return mapper.readTree(mockMvc.perform(post(base + "/check").with(asUser(Permissions.EXAM_MODIFY))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode latest(String base) throws Exception {
        return mapper.readTree(mockMvc.perform(get(base + "/check").with(asUser(Permissions.EXAM_VIEW)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String latestReason(UUID exam) throws Exception {
        JsonNode reason = latest("/api/v1/admin/exams/" + exam).path("invalidReason");
        return reason.isNull() || reason.isMissingNode() ? null : reason.asText();
    }

    private JsonNode listed(String checkStatus) throws Exception {
        return mapper.readTree(mockMvc.perform(get("/api/v1/admin/exams").param("checkStatus", checkStatus).param("size", "200")
                        .with(asUser(Permissions.EXAM_VIEW))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void publishRefusedWith(UUID exam, String reason, String... paths) throws Exception {
        publish(exam, paths).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHECK_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value(reason));
    }

    /** A saved whole-exam result as it was before the rules: the store stamps every save, so the stamp is removed again. */
    private void saveOldCheck(UUID exam, Map<String, String> pathResults) {
        saveCheck(exam, pathResults);
        removeStamp(exam);
    }

    private void removeStamp(UUID exam) {
        assertThat(jdbc.update("UPDATE exam.exam_check_run SET result = result - 'rulesStamp' WHERE exam_id = ?", exam)).isEqualTo(1);
    }

    private String stamp(UUID exam) {
        return jdbc.queryForObject("SELECT result->>'rulesStamp' FROM exam.exam_check_run WHERE exam_id = ?", String.class, exam);
    }

    private long versions(UUID exam) {
        return jdbc.queryForObject("SELECT count(*) FROM exam.exam_version WHERE exam_id = ?", Long.class, exam);
    }

    private String servedState(UUID exam) {
        return jdbc.queryForObject("SELECT string_agg(version_no || '|' || state || '|' || array_to_string(serving_path_ids, ','), ';' "
                + "ORDER BY version_no) FROM exam.exam_version WHERE exam_id = ?", String.class, exam);
    }

    private void completeCheck(UUID examId, Map<String, String> pathResults, ExamCheckScope scope) {
        completeCheck(examId, pathResults, scope, true);
    }

    /**
     * Saves a check the way production does: through {@link ExamCheckCompletion}, which stamps whole-exam results only.
     * {@code rulesEvaluated = false} is a run whose checker never evaluated the paths (spent budget, path limit).
     */
    private void completeCheck(UUID examId, Map<String, String> pathResults, ExamCheckScope scope, boolean rulesEvaluated) {
        entityManager.flush();
        entityManager.clear();
        var exam = exams.findById(examId).orElseThrow();
        var profile = profiles.findById(exam.getKindItemId()).orElseThrow();
        var input = new ExamCheckResultStore.Input(examId, exam.getRevision(), "publish-it",
                mapper.valueToTree(vn.unicom.thi360.exam.application.ExamCheckFingerprint.Config.from(exam)),
                profile.getRevision(), catalog.revision(), "test", mapper.valueToTree(source.sourceRevisions(List.of(SUBJECT))));
        ObjectNode outcome = mapper.createObjectNode();
        ArrayNode paths = outcome.putArray("paths");
        ArrayNode witnesses = mapper.createArrayNode();
        boolean allValid = true;
        for (var entry : pathResults.entrySet()) {
            paths.addObject().put("pathId", entry.getKey()).put("result", entry.getValue());
            if (!"VALID".equals(entry.getValue())) { allValid = false; continue; }
            ObjectNode witness = witnesses.addObject();
            ArrayNode chosen = witness.putObject("path").putArray("chosen");
            if (!"default".equals(entry.getKey())) for (String option : entry.getKey().split("\\+")) chosen.addObject().put("optionId", option);
            witness.putArray("witness").addObject().putArray("groups").addObject().putArray("picks")
                    .addObject().put("questionId", question().toString()).put("versionNo", 1);
        }
        outcome.put("status", allValid ? "VALID" : "INVALID").put("issueCount", 0);
        var done = completion.complete(input, null, outcome, witnesses, List.of(), rulesEvaluated, scope, Instant.now(), 1,
                new ExamCheckCompletion.Actor(UUID.randomUUID(), "tester", AuditActorType.SYSTEM), "trace-test");
        assertThat(done.accepted()).isTrue();
        entityManager.clear();
    }
}
