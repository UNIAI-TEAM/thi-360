package vn.unicom.thi360.exam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.exam.domain.Exam;
import vn.unicom.thi360.exam.domain.ExamStructureCodec;
import vn.unicom.thi360.exam.domain.ExamStructureDocument;
import vn.unicom.thi360.exam.domain.solver.SolverLimits;
import vn.unicom.thi360.exam.domain.solver.SectionOnPath;
import vn.unicom.thi360.exam.domain.solver.SolveOutcome;
import vn.unicom.thi360.exam.domain.solver.SolveReason;
import vn.unicom.thi360.exam.domain.solver.SolveStats;
import vn.unicom.thi360.exam.domain.solver.SolveStatus;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionType;

class ExamCheckUnitEngineTests {
    @Test
    void unchangedSubjectsReuseTheirProofAndOnlyEditedSubjectIsSolved() {
        var mapper = new ObjectMapper();
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var store = mock(ExamCheckUnitStore.class);
        UUID physics = UUID.randomUUID(), chemistry = UUID.randomUUID();
        var physicsItem = item(physics);
        var chemistryItem = item(chemistry);
        when(catalog.findActiveItem(eq("SUBJECT"), eq("LY"))).thenReturn(Optional.of(physicsItem));
        when(catalog.findActiveItem(eq("SUBJECT"), eq("HOA"))).thenReturn(Optional.of(chemistryItem));
        doAnswer(call -> {
            UUID subject = ((QuestionSourcePort.Filter) call.getArgument(0)).subjectId();
            UUID question = subject.equals(physics) ? physics : chemistry;
            var unit = new QuestionSourcePort.Unit(question, null, QuestionType.SINGLE_CHOICE,
                    null, null, List.of(), List.of(), BigDecimal.ONE);
            return List.of(new QuestionSourcePort.Candidate(question, 1, null, List.of(unit), null, null));
        }).when(source).findCandidates(any(), anyInt());
        var limits = SolverLimits.defaults();
        var clock = Clock.systemUTC();
        var engine = spy(new ExamCheckUnitEngine(store,
                new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper)), catalog, mapper, limits, clock));
        var checker = new DefaultExamChecker(new SolverInputBuilder(catalog, source, limits), limits, clock, mapper, catalog, source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), engine, new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        var exam = new Exam(UUID.randomUUID(), "UNIT", "Unit", null, "FULL", "SHARED", 60,
                null, "RAW", clock.instant(), UUID.randomUUID());
        exam.applyStructureSummary(2, false, false, 0);
        var config = mapper.valueToTree(ExamCheckFingerprint.Config.from(exam));
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash", config, null, 0, "hash", mapper.createObjectNode());
        when(store.load(exam.getId())).thenReturn(Map.of());
        var first = check(checker, exam, document(mapper, false), input, clock, ExamCheckScope.from(null, null, null));
        assertThat(first.result().path("status").asText()).isEqualTo("VALID");
        assertThat(first.units()).hasSize(2).allSatisfy(unit -> assertThat(unit.outcome()).isEqualTo("VALID"));
        verify(engine, times(2)).solveUnit(any(), any(), any());
        when(store.load(exam.getId())).thenReturn(first.units().stream().collect(Collectors.toMap(ExamCheckUnitStore.Unit::key, unit -> unit)));
        clearInvocations(engine);
        var second = check(checker, exam, document(mapper, false), input, clock, ExamCheckScope.from(null, null, null));
        assertThat(second.result().path("status").asText()).isEqualTo("VALID");
        verify(engine, times(0)).solveUnit(any(), any(), any());
        clearInvocations(engine);
        var changed = check(checker, exam, document(mapper, true), input, clock, ExamCheckScope.from(null, null, null));
        assertThat(changed.result().path("status").asText()).isEqualTo("VALID");
        verify(engine, times(1)).solveUnit(any(), any(), any());

        var draft = mapper.valueToTree(document(mapper, false));
        var chemistrySection = (com.fasterxml.jackson.databind.node.ObjectNode)
                draft.path("parts").get(0).path("sections").get(1);
        var chemistryBlock = (com.fasterxml.jackson.databind.node.ObjectNode) chemistrySection.path("blocks").get(0);
        chemistryBlock.putArray("childConditions").addObject().put("to", 1);
        var unrelatedDraft = new ExamStructureCodec(mapper).read(draft.toString());
        clearInvocations(engine);
        var scoped = check(checker, exam, unrelatedDraft, input, clock, ExamCheckScope.from("SECTION", null, "physics"));
        assertThat(scoped.result().path("status").asText()).isEqualTo("VALID");
        verify(engine, times(0)).solveUnit(any(), any(), any());
        var snapshots = mock(ExamCheckSnapshotReader.class);
        when(snapshots.read(exam.getId())).thenReturn(new ExamCheckSnapshotReader.Snapshot(exam, unrelatedDraft, input, null));
        var states = new ExamCheckUnitStates(snapshots, store,
                new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper)),
                new SolverInputBuilder(catalog, source, limits), limits, catalog, mapper, source,
                new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        assertThat(states.states(exam.getId())).extracting(state -> state.state())
                .containsExactly("CHECKED", "INCOMPLETE");

        chemistrySection.put("subjectCode", "LY");
        var sharedDraft = new ExamStructureCodec(mapper).read(draft.toString());
        var shared = check(checker, exam, sharedDraft, input, clock, ExamCheckScope.from("SECTION", null, "physics"));
        assertThat(shared.result().path("status").asText()).isEqualTo("INVALID");
        assertThat(shared.result().toString()).contains("SECTION_INCOMPLETE");
        assertThat(shared.units()).singleElement().satisfies(unit -> {
            assertThat(unit.outcome()).isEqualTo("INVALID");
            assertThat(unit.sectionIds()).containsExactlyInAnyOrder("physics", "chemistry");
        });
        when(snapshots.read(exam.getId())).thenReturn(new ExamCheckSnapshotReader.Snapshot(exam, sharedDraft, input, null));
        assertThat(states.states(exam.getId())).extracting(state -> state.state())
                .containsExactly("INCOMPLETE", "INCOMPLETE");

        var otherPart = mapper.valueToTree(document(mapper, false));
        var brokenPart = ((com.fasterxml.jackson.databind.node.ArrayNode) otherPart.path("parts")).addObject();
        brokenPart.put("id", "broken-part").put("name", "Broken part");
        brokenPart.putObject("participation").put("mode", "CHOICE");
        var separateDraft = new ExamStructureCodec(mapper).read(otherPart.toString());
        var separate = check(checker, exam, separateDraft, input, clock, ExamCheckScope.from("SECTION", null, "physics"));
        assertThat(separate.result().path("status").asText()).isEqualTo("VALID");
        when(snapshots.read(exam.getId())).thenReturn(new ExamCheckSnapshotReader.Snapshot(exam, separateDraft, input, null));
        assertThat(states.states(exam.getId())).extracting(state -> state.state())
                .containsExactly("CHECKED", "CHECKED");
        brokenPart.putArray("sections").addObject().put("id", "broken-section")
                .put("subjectCode", "VAN").put("questionCount", 1)
                .putArray("blocks").addObject().put("id", "broken-block")
                .put("questionType", "SINGLE_CHOICE").put("questionCount", 1);
        var invalidPartDraft = new ExamStructureCodec(mapper).read(otherPart.toString());
        when(snapshots.read(exam.getId())).thenReturn(new ExamCheckSnapshotReader.Snapshot(exam, invalidPartDraft, input, null));
        assertThat(states.states(exam.getId())).extracting(state -> state.state())
                .containsExactly("CHECKED", "CHECKED", "INCOMPLETE");
        verify(engine, times(0)).solveUnit(any(), any(), any());
    }

    @Test
    void missingCombinationSubjectOnlyInvalidatesAffectedCode() {
        var mapper = new ObjectMapper();
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var store = mock(ExamCheckUnitStore.class);
        UUID physics = UUID.randomUUID();
        var physicsItem = item(physics);
        when(catalog.findActiveItem("SUBJECT", "LY")).thenReturn(Optional.of(physicsItem));
        var subject = new vn.unicom.thi360.catalog.spi.CatalogCombinationSubjectView(physics, "LY", "Ly", "Physics");
        var missing = new vn.unicom.thi360.catalog.spi.CatalogCombinationSubjectView(UUID.randomUUID(), "HOA", "Hoa", "Chemistry");
        var good = new vn.unicom.thi360.catalog.spi.CatalogCombinationView(UUID.randomUUID(), "GOOD", "Good", "Good",
                null, null, null, null, null, 1, List.of(subject));
        var bad = new vn.unicom.thi360.catalog.spi.CatalogCombinationView(UUID.randomUUID(), "BAD", "Bad", "Bad",
                null, null, null, null, null, 1, List.of(missing));
        when(catalog.listCombinations(null)).thenReturn(List.of(good, bad));
        var question = new QuestionSourcePort.Unit(physics, null, QuestionType.SINGLE_CHOICE,
                null, null, List.of(), List.of(), BigDecimal.ONE);
        when(source.findCandidates(any(), anyInt())).thenReturn(List.of(
                new QuestionSourcePort.Candidate(physics, 1, null, List.of(question), null, null)));
        var limits = SolverLimits.defaults();
        var clock = Clock.systemUTC();
        var fingerprints = new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper));
        var builder = new SolverInputBuilder(catalog, source, limits);
        var engine = new ExamCheckUnitEngine(store, fingerprints, catalog, mapper, limits, clock);
        var checker = new DefaultExamChecker(builder, limits, clock, mapper, catalog, source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), engine, new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        var exam = new Exam(UUID.randomUUID(), "CODES", "Codes", null, "FULL", "SHARED", 60,
                null, "RAW", clock.instant(), UUID.randomUUID());
        exam.applyStructureSummary(1, false, false, 0);
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, 0, "hash", mapper.createObjectNode());
        var document = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","name":"Codes","questionCount":1,"codeSource":{"source":"GLOBAL_LIST"},
                  "subjectPool":[{"id":"ly","subjectCode":"LY","questionCount":1,"blocks":[
                    {"id":"physics","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """);
        when(store.load(exam.getId())).thenReturn(Map.of());
        var checked = check(checker, exam, document, input, clock, ExamCheckScope.from(null, null, null));
        var paths = checked.result().path("paths");
        var goodPath = java.util.stream.StreamSupport.stream(paths.spliterator(), false)
                .filter(path -> "p:GOOD".equals(path.path("pathId").asText())).findFirst().orElseThrow();
        var badPath = java.util.stream.StreamSupport.stream(paths.spliterator(), false)
                .filter(path -> "p:BAD".equals(path.path("pathId").asText())).findFirst().orElseThrow();
        assertThat(goodPath.path("result").asText()).isEqualTo("VALID");
        assertThat(badPath.path("result").asText()).isEqualTo("INVALID");
        assertThat(badPath.path("issues").toString()).contains("COMBINATION_SUBJECT_MISSING", "BAD");
        assertThat(checked.witness().toString()).contains("p:GOOD").doesNotContain("p:BAD");
        assertThat(checked.units()).singleElement().satisfies(unit -> assertThat(unit.outcome()).isEqualTo("VALID"));
        when(store.load(exam.getId())).thenReturn(checked.units().stream()
                .collect(Collectors.toMap(ExamCheckUnitStore.Unit::key, unit -> unit)));
        var snapshots = mock(ExamCheckSnapshotReader.class);
        when(snapshots.read(exam.getId())).thenReturn(new ExamCheckSnapshotReader.Snapshot(exam, document, input, null));
        var states = new ExamCheckUnitStates(snapshots, store, fingerprints, builder, limits, catalog, mapper, source,
                new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        assertThat(states.states(exam.getId())).extracting(state -> state.state()).containsExactly("CHECKED");
    }

    @Test
    void emptyPartCheckReportsItsLocation() {
        var mapper = new ObjectMapper();
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var store = mock(ExamCheckUnitStore.class);
        var limits = SolverLimits.defaults();
        var clock = Clock.systemUTC();
        var builder = new SolverInputBuilder(catalog, source, limits);
        var fingerprints = new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper));
        var engine = new ExamCheckUnitEngine(store, fingerprints, catalog, mapper, limits, clock);
        var checker = new DefaultExamChecker(builder, limits, clock, mapper, catalog, source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), engine, new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        var exam = new Exam(UUID.randomUUID(), "EMPTY", "Empty", null, "FULL", "SHARED", null,
                null, "RAW", clock.instant(), UUID.randomUUID());
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, 0, "hash", mapper.createObjectNode());
        var document = new ExamStructureCodec(mapper).read(
                "{\"parts\":[{\"id\":\"empty\",\"name\":\"Empty\",\"questionCount\":1}]}");

        var checked = check(checker, exam, document, input, clock, ExamCheckScope.from("PART", "empty", null));
        assertThat(checked.result().path("status").asText()).isEqualTo("INVALID");
        var issues = checked.result().path("groups").findValues("issues").stream()
                .flatMap(group -> java.util.stream.StreamSupport.stream(group.spliterator(), false))
                .filter(issue -> "PART_EMPTY".equals(issue.path("code").asText())).toList();
        assertThat(issues).singleElement().satisfies(issue ->
                assertThat(issue.path("location").path("partId").asText()).isEqualTo("empty"));
        assertThat(checked.units()).isEmpty();
    }

    @Test
    void malformedExclusiveOptionDoesNotInvalidateOtherOptionOfSameSubject() {
        var mapper = new ObjectMapper();
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var store = mock(ExamCheckUnitStore.class);
        UUID subject = UUID.randomUUID();
        var subjectItem = item(subject);
        when(catalog.findActiveItem("SUBJECT", "LY")).thenReturn(Optional.of(subjectItem));
        var question = new QuestionSourcePort.Unit(subject, null, QuestionType.SINGLE_CHOICE,
                null, null, List.of(), List.of(), BigDecimal.ONE);
        when(source.findCandidates(any(), anyInt())).thenReturn(List.of(
                new QuestionSourcePort.Candidate(subject, 1, null, List.of(question), null, null)));
        var limits = SolverLimits.defaults();
        var clock = Clock.systemUTC();
        var fingerprints = new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper));
        var builder = new SolverInputBuilder(catalog, source, limits);
        var engine = new ExamCheckUnitEngine(store, fingerprints, catalog, mapper, limits, clock);
        var checker = new DefaultExamChecker(builder, limits, clock, mapper, catalog, source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), engine, new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        var exam = new Exam(UUID.randomUUID(), "UNIT", "Unit", null, "FULL", "SHARED", null,
                null, "RAW", clock.instant(), UUID.randomUUID());
        exam.applyStructureSummary(1, false, false, 0);
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, 0, "hash", mapper.createObjectNode());
        var codec = new ExamStructureCodec(mapper);
        var document = codec.read("""
                {"parts":[{"id":"p","name":"Part","questionCount":1,
                  "participation":{"mode":"CHOICE","choose":1},"options":[
                    {"id":"good","kind":"SUBJECT","code":"LY","sections":[
                      {"id":"good-section","subjectCode":"LY","questionCount":1,"blocks":[
                        {"id":"good-block","questionType":"SINGLE_CHOICE","questionCount":1},
                        {"id":"omitted-essay","questionType":"ESSAY","questionCount":1,"omitted":true}]}]},
                    {"id":"bad","kind":"SUBJECT_CHOICE","code":"LY","sections":[
                      {"id":"bad-section","subjectCode":"LY","questionCount":1,"blocks":[
                        {"id":"bad-block","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}]}
                """);
        when(store.load(exam.getId())).thenReturn(Map.of());
        var checked = check(checker, exam, document, input, clock,
                ExamCheckScope.from("SECTION", null, "good-section"));
        assertThat(checked.result().path("status").asText()).isEqualTo("VALID");
        assertThat(checked.units()).singleElement().satisfies(unit -> assertThat(unit.outcome()).isEqualTo("VALID"));
        when(store.load(exam.getId())).thenReturn(checked.units().stream()
                .collect(Collectors.toMap(ExamCheckUnitStore.Unit::key, unit -> unit)));
        var partCheck = check(checker, exam, document, input, clock,
                ExamCheckScope.from("PART", "p", null));
        assertThat(partCheck.result().path("status").asText()).isEqualTo("INVALID");
        assertThat(partCheck.units()).isEmpty();
        var snapshots = mock(ExamCheckSnapshotReader.class);
        when(snapshots.read(exam.getId())).thenReturn(new ExamCheckSnapshotReader.Snapshot(exam, document, input, null));
        var states = new ExamCheckUnitStates(snapshots, store, fingerprints, builder, limits, catalog, mapper, source,
                new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        assertThat(states.states(exam.getId())).extracting(state -> state.state())
                .containsExactly("CHECKED", "INCOMPLETE");
    }

    /**
     * CR-4 R2-2: a run is complete when no unit of it was left unconfirmed, whatever the verdict of the paths that hold
     * the units: an invalid unit next to an unconfirmed one in the same tuple folds into an INVALID path, which shows no
     * unconfirmed path or selection. The real planner and engine run; only the per-unit solver answers are forced.
     */
    @Test
    void aRunWithAnUnconfirmedUnitIsIncompleteEvenWhenItsTupleIsInvalid() {
        var mapper = new ObjectMapper();
        var catalog = mock(CatalogLookup.class);
        var source = mock(QuestionSourcePort.class);
        var store = mock(ExamCheckUnitStore.class);
        UUID physics = UUID.randomUUID(), chemistry = UUID.randomUUID();
        var physicsItem = item(physics);
        var chemistryItem = item(chemistry);
        when(catalog.findActiveItem(eq("SUBJECT"), eq("LY"))).thenReturn(Optional.of(physicsItem));
        when(catalog.findActiveItem(eq("SUBJECT"), eq("HOA"))).thenReturn(Optional.of(chemistryItem));
        doAnswer(call -> {
            UUID subject = ((QuestionSourcePort.Filter) call.getArgument(0)).subjectId();
            var unit = new QuestionSourcePort.Unit(subject, null, QuestionType.SINGLE_CHOICE,
                    null, null, List.of(), List.of(), BigDecimal.ONE);
            return List.of(new QuestionSourcePort.Candidate(subject, 1, null, List.of(unit), null, null));
        }).when(source).findCandidates(any(), anyInt());
        var limits = SolverLimits.defaults();
        var clock = Clock.systemUTC();
        var engine = spy(new ExamCheckUnitEngine(store,
                new ExamCheckUnitFingerprint(mapper, new ExamCheckFingerprint(mapper)), catalog, mapper, limits, clock));
        Map<String, SolveStatus> forced = new HashMap<>();
        doAnswer(call -> {
            List<SectionOnPath> sections = call.getArgument(0);
            SolveStatus status = forced.get(sections.getFirst().subjectCode());
            if (status == null) return call.callRealMethod();
            return new SolveOutcome(status, status == SolveStatus.UNCONFIRMED ? SolveReason.DEADLINE_EXCEEDED : null,
                    List.of(), SolveStats.EMPTY);
        }).when(engine).solveUnit(any(), any(), any());
        var checker = new DefaultExamChecker(new SolverInputBuilder(catalog, source, limits), limits, clock, mapper, catalog,
                source, org.mockito.Mockito.mock(vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.class), engine,
                new ExamCheckTemplateFacts(catalog, org.mockito.Mockito.mock(ExamTemplateService.class), mapper));
        var exam = new Exam(UUID.randomUUID(), "UNIT", "Unit", null, "FULL", "SHARED", 60,
                null, "RAW", clock.instant(), UUID.randomUUID());
        exam.applyStructureSummary(2, false, false, 0);
        var input = new ExamCheckResultStore.Input(exam.getId(), 0, "hash",
                mapper.valueToTree(ExamCheckFingerprint.Config.from(exam)), null, 0, "hash", mapper.createObjectNode());
        when(store.load(exam.getId())).thenReturn(Map.of());
        var whole = ExamCheckScope.from(null, null, null);

        // Both subjects solved: complete.
        var plain = check(checker, exam, document(mapper, false), input, clock, whole);
        assertThat(plain.result().path("status").asText()).isEqualTo("VALID");
        assertThat(plain.rulesEvaluated()).as("all units solved").isTrue();

        // A completed mixed run (one unit proved invalid, the other solved) is complete.
        forced.put("LY", SolveStatus.INVALID);
        var mixedDone = check(checker, exam, document(mapper, false), input, clock, whole);
        assertThat(mixedDone.result().path("paths").get(0).path("result").asText()).isEqualTo("INVALID");
        assertThat(mixedDone.rulesEvaluated()).as("invalid + valid units").isTrue();

        // An unconfirmed unit alone: incomplete (the path itself shows it).
        forced.clear();
        forced.put("HOA", SolveStatus.UNCONFIRMED);
        assertThat(check(checker, exam, document(mapper, false), input, clock, whole).rulesEvaluated())
                .as("valid + unconfirmed units").isFalse();

        // The same tuple holds an invalid and an unconfirmed unit: the path folds to INVALID, the run is still incomplete.
        forced.put("LY", SolveStatus.INVALID);
        var folded = check(checker, exam, document(mapper, false), input, clock, whole);
        assertThat(folded.result().path("paths").get(0).path("result").asText()).isEqualTo("INVALID");
        assertThat(folded.rulesEvaluated()).as("invalid + unconfirmed units of one tuple").isFalse();

        // The same with a subject choice: the only pick holds both units, so its selection shows INVALID as well and no
        // path or selection of the result says UNCONFIRMED; the run is incomplete all the same.
        var choice = new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","name":"Part","questionCount":2,"participation":{"mode":"CHOICE","choose":1},"options":[
                  {"id":"science","kind":"SUBJECT_CHOICE","code":"SCIENCE","chooseSubjects":2,"countsByPick":[1,1],"sections":[
                    {"id":"physics","subjectCode":"LY","questionCount":1,"choiceGroup":"NATURAL","blocks":[
                      {"id":"bp","questionType":"SINGLE_CHOICE","questionCount":1}]},
                    {"id":"chemistry","subjectCode":"HOA","questionCount":1,"choiceGroup":"NATURAL","blocks":[
                      {"id":"bc","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}]}
                """);
        var hidden = check(checker, exam, choice, input, clock, whole);
        var hiddenPath = hidden.result().path("paths").get(0);
        assertThat(hiddenPath.path("result").asText()).isEqualTo("INVALID");
        for (var selection : hiddenPath.path("selections")) {
            assertThat(selection.path("result").asText()).as("no selection advertises the unconfirmed unit").isEqualTo("INVALID");
        }
        assertThat(hidden.rulesEvaluated()).as("invalid + unconfirmed units of the only pick").isFalse();
        forced.remove("HOA");
        assertThat(check(checker, exam, choice, input, clock, whole).rulesEvaluated())
                .as("the same pick with the unconfirmed unit solved").isTrue();
    }

    private static CatalogItemView item(UUID id) {
        var item = mock(CatalogItemView.class);
        when(item.id()).thenReturn(id);
        return item;
    }

    private static ExamChecker.Evaluation check(DefaultExamChecker checker, Exam exam, ExamStructureDocument document,
            ExamCheckResultStore.Input input, Clock clock, ExamCheckScope scope) {
        return checker.check(new ExamCheckSnapshotReader.Snapshot(exam, document, input, null),
                clock.instant().plusSeconds(30), Duration.ofSeconds(30), scope);
    }

    private static ExamStructureDocument document(ObjectMapper mapper, boolean changed) {
        return new ExamStructureCodec(mapper).read("""
                {"parts":[{"id":"p","name":"Part","questionCount":2,"sections":[
                  {"id":"physics","subjectCode":"LY","questionCount":1,"blocks":[
                    {"id":"bp","questionType":"SINGLE_CHOICE","questionCount":1%s}]},
                  {"id":"chemistry","subjectCode":"HOA","questionCount":1,"blocks":[
                    {"id":"bc","questionType":"SINGLE_CHOICE","questionCount":1}]}]}]}
                """.formatted(changed ? ",\"requiredPointsPerQuestion\":1" : ""));
    }
}
