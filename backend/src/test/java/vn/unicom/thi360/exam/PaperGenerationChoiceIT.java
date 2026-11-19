package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;

/**
 * Selection boundary of the generation port on a published version with choice parts (AC-17, review R1-1, R1-2,
 * R1-7, R1-8): a choice is validated per part against the published paths before any candidate is read, only the
 * chosen options are built, and the result follows the structure order.
 */
class PaperGenerationChoiceIT extends PaperGenerationBaseIT {
    @MockitoSpyBean QuestionSourcePort sourceSpy;

    /** Two choice parts: PA = SA (two subjects, pick one) and PB = SC (two subjects, pick one) or SD (one subject). */
    private record Fixture(String parts, Subject s1, Subject s2, Subject s3, Subject s4, Subject s5, Subject s6) {}

    private Fixture fixture(boolean withIncompleteOption) {
        Subject s1 = pool(), s2 = pool(), s3 = pool(), s4 = pool(), s5 = pool(), s6 = pool();
        String incomplete = withIncompleteOption ? """
                ,{"id":"sbad","kind":"SUBJECT_CHOICE","code":"SBAD","sections":[
                   {"id":"sbad1","subjectCode":"%s","choiceGroup":"NATURAL",
                    "blocks":[{"id":"bbad","questionType":"SINGLE_CHOICE","questionCount":2}]}]}
                """.formatted(s5.code()) : "";
        String parts = """
                {"parts":[
                 {"id":"pa","order":1,"name":"PA","questionCount":2,"participation":{"mode":"CHOICE","choose":1},
                  "sections":[],"options":[
                   {"id":"sa","kind":"SUBJECT_CHOICE","code":"SA","chooseSubjects":1,"countsByPick":[2],"sections":[
                     {"id":"sa1","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"ba1","questionType":"SINGLE_CHOICE","questionCount":2}]},
                     {"id":"sa2","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"ba2","questionType":"SINGLE_CHOICE","questionCount":2}]}]}
                   %s]},
                 {"id":"pb","order":2,"name":"PB","questionCount":2,"participation":{"mode":"CHOICE","choose":1},
                  "sections":[],"options":[
                   {"id":"sc","kind":"SUBJECT_CHOICE","code":"SC","chooseSubjects":1,"countsByPick":[2],"sections":[
                     {"id":"sc1","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"bc1","questionType":"SINGLE_CHOICE","questionCount":2}]},
                     {"id":"sc2","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"bc2","questionType":"SINGLE_CHOICE","questionCount":2}]}]},
                   {"id":"sd","kind":"SUBJECT","code":"SD","sections":[
                     {"id":"sd1","subjectCode":"%s","questionCount":2,
                      "blocks":[{"id":"bd1","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}]}
                """.formatted(s1.code(), s2.code(), incomplete, s3.code(), s4.code(), s6.code());
        return new Fixture(parts, s1, s2, s3, s4, s5, s6);
    }

    private Subject pool() {
        Subject subject = newSubject();
        singles(subject, 6);
        return subject;
    }

    @Test
    void validOptionNextToAnIncompleteSiblingGenerates() throws Exception {
        Fixture f = fixture(true);
        Published published = insertVersion(f.parts(), null, "sa+sc");

        // SBAD has no chooseSubjects: it is not publishable, so only SA is. SA must not depend on its sibling.
        GeneratedPaper paper = port.generate(published.examId(), 1, List.of("pa"),
                List.of(Selection.subjects("pa", "SA", List.of(f.s1().code()))), 5L, Set.of());

        assertThat(paper.parts()).hasSize(1);
        assertThat(section(paper, f.s1().code()).questionCount()).isEqualTo(2);
        assertThat(roots(paper)).hasSize(2).doesNotHaveDuplicates();

        clearInvocations(sourceSpy);
        refused(() -> port.generate(published.examId(), 1, List.of("pa"),
                List.of(Selection.subjects("pa", "SBAD", List.of(f.s5().code()))), 5L, Set.of()));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());
    }

    @Test
    void subjectCodesAreCheckedPerSelectionBeforeAnySourceRead() throws Exception {
        Fixture f = fixture(false);
        Published published = insertVersion(f.parts(), null, "sa+sc", "sa+sd");
        String s1 = f.s1().code(), s3 = f.s3().code(), s4 = f.s4().code();
        List<String> both = List.of("pa", "pb");

        GeneratedPaper valid = port.generate(published.examId(), 1, both, List.of(
                Selection.subjects("pa", "SA", List.of(s1)), Selection.subjects("pb", "SC", List.of(s3))), 5L, Set.of());
        assertThat(part(valid, "pa").sections()).extracting(s -> s.subjectCode()).containsExactly(s1);
        assertThat(part(valid, "pb").sections()).extracting(s -> s.subjectCode()).containsExactly(s3);

        clearInvocations(sourceSpy);
        // The two parts hold each other's subjects.
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of(s3)),
                Selection.subjects("pb", "SC", List.of(s1)));
        // One part holds the union and the other none.
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of(s1, s3)),
                Selection.subjects("pb", "SC", List.of()));
        // A subject of the other part on top of the part's own one.
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of(s1)),
                Selection.subjects("pb", "SC", List.of(s1)));
        // A normal option (SD) takes the subject the first part is missing.
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of()),
                Selection.subjects("pb", "SD", List.of(s1)));
        // A normal option takes no subject list at all.
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of(s1)),
                Selection.subjects("pb", "SD", List.of(s3)));
        // More subjects than the choice allows, a repeated subject, an unknown subject, an unknown option.
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of(s1)),
                Selection.subjects("pb", "SC", List.of(s3, s4)));
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of(s1, s1)),
                Selection.subjects("pb", "SC", List.of(s3)));
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of("NO_SUCH_SUBJECT")),
                Selection.subjects("pb", "SC", List.of(s3)));
        refusedPair(published, both, Selection.subjects("pa", "NOPE", List.of(s1)),
                Selection.subjects("pb", "SC", List.of(s3)));
        // A choice part without a selection.
        refused(() -> port.generate(published.examId(), 1, both,
                List.of(Selection.subjects("pa", "SA", List.of(s1))), 5L, Set.of()));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());

        // The spy does see the reads of a valid call.
        port.generate(published.examId(), 1, List.of("pb"),
                List.of(Selection.subjects("pb", "SC", List.of(s4))), 5L, Set.of());
        verify(sourceSpy).findCandidates(any(), anyInt());
    }

    @Test
    void resultFollowsTheStructureOrderNotTheRequestOrder() throws Exception {
        Fixture f = fixture(false);
        Published published = insertVersion(f.parts(), null, "sa+sc");

        GeneratedPaper paper = port.generate(published.examId(), 1, List.of("pb", "pa"), List.of(
                Selection.subjects("pb", "SC", List.of(f.s3().code())),
                Selection.subjects("pa", "SA", List.of(f.s1().code()))), 5L, Set.of());

        assertThat(paper.parts()).extracting(part -> part.partCode()).containsExactly("pa", "pb");
    }

    @Test
    void onlyOptionsOfThePublishedPathsAreAcceptedForThatVersion() throws Exception {
        Fixture f = fixture(false);
        String s1 = f.s1().code(), s3 = f.s3().code();
        // Version 1 published SA+SC only: SD is a valid option of the structure but was not published.
        Published v1 = insertVersion(f.parts(), null, "sa+sc");
        clearInvocations(sourceSpy);
        refused(() -> port.generate(v1.examId(), 1, List.of("pb"), List.of(Selection.option("pb", "SD")), 5L, Set.of()));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());
        assertThat(roots(port.generate(v1.examId(), 1, List.of("pb"),
                List.of(Selection.subjects("pb", "SC", List.of(s3))), 5L, Set.of()))).hasSize(2);

        // The two parts are one path: a combination that no published path holds is refused although each option is.
        Published v2 = insertVersionRow(v1.examId(), 2, f.parts(), null, "sa+sd");
        clearInvocations(sourceSpy);
        refused(() -> port.generate(v2.examId(), 2, List.of("pb"),
                List.of(Selection.subjects("pb", "SC", List.of(s3))), 5L, Set.of()));
        refused(() -> port.generate(v2.examId(), 2, List.of("pa", "pb"), List.of(
                Selection.subjects("pa", "SA", List.of(s1)), Selection.subjects("pb", "SC", List.of(s3))), 5L, Set.of()));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());
        // Version 2 serves SD, version 1 does not; a path of an old version is not a path of the new one.
        assertThat(roots(port.generate(v2.examId(), 2, List.of("pb"), List.of(Selection.option("pb", "SD")),
                5L, Set.of()))).hasSize(2);
        clearInvocations(sourceSpy);
        refused(() -> port.generate(v1.examId(), 1, List.of("pb"), List.of(Selection.option("pb", "SD")), 5L, Set.of()));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());
    }

    @Test
    void aVersionWithoutPublishedPathsGeneratesNothing() throws Exception {
        Subject subject = newSubject();
        singles(subject, 10);
        Published published = insertVersion(singleSection(subject, 3), null, new String[0]);
        clearInvocations(sourceSpy);

        refused(() -> port.generate(published.examId(), 1, List.of("p1"), List.of(), 1L, Set.of()));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());
    }

    /** PA offers M or P and PB offers M or C: the two parts share M, and a part's pick is its own. */
    private String overlappingPools(Subject m, Subject p, Subject c) {
        return """
                {"parts":[
                 {"id":"pa","order":1,"name":"PA","questionCount":2,"participation":{"mode":"CHOICE","choose":1},
                  "sections":[],"options":[
                   {"id":"sa","kind":"SUBJECT_CHOICE","code":"SA","chooseSubjects":1,"countsByPick":[2],"sections":[
                     {"id":"sa1","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"ba1","questionType":"SINGLE_CHOICE","questionCount":2}]},
                     {"id":"sa2","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"ba2","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]},
                 {"id":"pb","order":2,"name":"PB","questionCount":2,"participation":{"mode":"CHOICE","choose":1},
                  "sections":[],"options":[
                   {"id":"sb","kind":"SUBJECT_CHOICE","code":"SB","chooseSubjects":1,"countsByPick":[2],"sections":[
                     {"id":"sb1","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"bb1","questionType":"SINGLE_CHOICE","questionCount":2}]},
                     {"id":"sb2","subjectCode":"%s","choiceGroup":"NATURAL",
                      "blocks":[{"id":"bb2","questionType":"SINGLE_CHOICE","questionCount":2}]}]}]}]}
                """.formatted(m.code(), p.code(), m.code(), c.code());
    }

    @Test
    void partsThatOfferTheSameSubjectKeepTheirOwnPick() throws Exception {
        Subject m = pool(), p = pool(), c = pool();
        Published published = insertVersion(overlappingPools(m, p, c), null, "sa+sb");
        List<String> both = List.of("pa", "pb");

        // PA takes M, PB takes C: M is offered by both parts but only PA chose it.
        GeneratedPaper split = port.generate(published.examId(), 1, both, List.of(
                Selection.subjects("pa", "SA", List.of(m.code())),
                Selection.subjects("pb", "SB", List.of(c.code()))), 5L, Set.of());
        assertThat(part(split, "pa").sections()).extracting(s -> s.subjectCode()).containsExactly(m.code());
        assertThat(part(split, "pb").sections()).extracting(s -> s.subjectCode()).containsExactly(c.code());
        assertThat(roots(split)).hasSize(4).doesNotHaveDuplicates();

        // The other way round, and both parts on the shared subject.
        GeneratedPaper swapped = port.generate(published.examId(), 1, both, List.of(
                Selection.subjects("pa", "SA", List.of(p.code())),
                Selection.subjects("pb", "SB", List.of(m.code()))), 5L, Set.of());
        assertThat(part(swapped, "pa").sections()).extracting(s -> s.subjectCode()).containsExactly(p.code());
        assertThat(part(swapped, "pb").sections()).extracting(s -> s.subjectCode()).containsExactly(m.code());
        GeneratedPaper shared = port.generate(published.examId(), 1, both, List.of(
                Selection.subjects("pa", "SA", List.of(m.code())),
                Selection.subjects("pb", "SB", List.of(m.code()))), 5L, Set.of());
        assertThat(roots(shared)).hasSize(4).doesNotHaveDuplicates();

        // A subject a part does not offer is still refused before any source read.
        clearInvocations(sourceSpy);
        refusedPair(published, both, Selection.subjects("pa", "SA", List.of(c.code())),
                Selection.subjects("pb", "SB", List.of(m.code())));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());
    }

    /** PA = OA or OB and PB = OX or OY; the version published OA+OX and OB+OY only. */
    private String twoByTwo(Subject a, Subject b, Subject x, Subject y) {
        return """
                {"parts":[
                 {"id":"pa","order":1,"name":"PA","questionCount":2,"participation":{"mode":"CHOICE","choose":1},
                  "sections":[],"options":[%s,%s]},
                 {"id":"pb","order":2,"name":"PB","questionCount":2,"participation":{"mode":"CHOICE","choose":1},
                  "sections":[],"options":[%s,%s]}]}
                """.formatted(normalOption("oa", a), normalOption("ob", b), normalOption("ox", x),
                normalOption("oy", y));
    }

    private static String normalOption(String id, Subject subject) {
        return """
                {"id":"%s","kind":"SUBJECT","code":"%s","sections":[
                  {"id":"%s1","subjectCode":"%s","questionCount":2,
                   "blocks":[{"id":"b%s1","questionType":"SINGLE_CHOICE","questionCount":2}]}]}
                """.formatted(id, id.toUpperCase(), id, subject.code(), id);
    }

    @Test
    void optionsOfDifferentPathsOfOneVersionAreNotCombined() throws Exception {
        Subject a = pool(), b = pool(), x = pool(), y = pool();
        Published published = insertVersion(twoByTwo(a, b, x, y), null, "oa+ox", "ob+oy");
        List<String> both = List.of("pa", "pb");

        // OA and OY are each published, but never together: refused before any source read.
        clearInvocations(sourceSpy);
        refusedPair(published, both, Selection.option("pa", "OA"), Selection.option("pb", "OY"));
        refusedPair(published, both, Selection.option("pa", "OB"), Selection.option("pb", "OX"));
        verify(sourceSpy, never()).findCandidates(any(), anyInt());

        GeneratedPaper first = port.generate(published.examId(), 1, both,
                List.of(Selection.option("pa", "OA"), Selection.option("pb", "OX")), 5L, Set.of());
        assertThat(part(first, "pa").sections()).extracting(s -> s.subjectCode()).containsExactly(a.code());
        assertThat(part(first, "pb").sections()).extracting(s -> s.subjectCode()).containsExactly(x.code());
        GeneratedPaper second = port.generate(published.examId(), 1, both,
                List.of(Selection.option("pa", "OB"), Selection.option("pb", "OY")), 5L, Set.of());
        assertThat(part(second, "pa").sections()).extracting(s -> s.subjectCode()).containsExactly(b.code());
        assertThat(part(second, "pb").sections()).extracting(s -> s.subjectCode()).containsExactly(y.code());
    }

    private void refusedPair(Published published, List<String> parts, Selection first, Selection second) {
        refused(() -> port.generate(published.examId(), 1, parts, List.of(first, second), 5L, Set.of()));
    }

    private static void refused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(PaperGenerationException.class,
                ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_FAILED));
    }
}
