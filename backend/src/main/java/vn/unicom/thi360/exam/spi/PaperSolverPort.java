package vn.unicom.thi360.exam.spi;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generation gate of the exam module for the attempt module (LT 3, F4, F5).
 *
 * <p>The problem is built from the published version snapshot ({@code exam_version}), never from the working copy:
 * parts, subject pool, {@code config.combinations}, exam quotas, flexible cluster groups, {@code everyChildInScope}
 * and the trim policy all come from the snapshot. Candidates come from the serving question source.
 *
 * <p>The call is stateless and safe to run concurrently. It reads only; it writes no usage ledger row, no exam data
 * and no preview snapshot, and it does not order the paper: ordering and option shuffling belong to
 * {@link PaperOrdering}, usage recording to the caller. It must be called outside a database transaction.
 *
 * <p>Same seed and same data give the same paper.
 */
public interface PaperSolverPort {

    /**
     * Generates the requested parts of one published version.
     *
     * @param examId            exam id
     * @param versionNo         published version number
     * @param partCodes         ids of the parts of the snapshot to generate now; must not be empty. The result
     *                          lists them in structure order, whatever the order given here
     * @param selections        the choices of the choice parts listed in {@code partCodes}: one entry per part
     *                          whose participation is {@code CHOICE} (HSA science or English, QDA combination code,
     *                          BCA code, Informatics branch); none for parts every student takes. The chosen
     *                          options must lie on one path the version published ({@code published_path_ids}), and
     *                          the subjects of each selection must belong to that selection's own options
     * @param seed              generation seed
     * @param excludeQuestionIds root and child question ids already issued in the attempt; none of them is picked
     * @return every requested question, grouped by part, section (subject) and group, plus the single questions a
     *         trimmed subject dropped (HSA science: 17 solved, 16 kept)
     * @throws PaperGenerationException {@code GENERATION_FAILED} when the version, a part code or a selection is not
     *         in the snapshot or the source cannot fill the quotas (nothing partial is returned);
     *         {@code GENERATION_TIMEOUT} when the deadline runs out
     */
    GeneratedPaper generate(UUID examId, int versionNo, List<String> partCodes, List<Selection> selections,
            long seed, Set<UUID> excludeQuestionIds);

    /**
     * The choice of one choice part. {@code optionCodes} are the codes of the chosen options (exactly the part's
     * {@code choose}; a QDA combination code, an Informatics branch such as {@code CS}, a subject option such as
     * {@code ANH}, or the code of a subject-choice option such as {@code SCIENCE}). {@code subjectCodes} lists the
     * chosen subjects of a subject-choice option (HSA science: three of five).
     */
    record Selection(String partCode, List<String> optionCodes, List<String> subjectCodes) {
        public Selection {
            optionCodes = optionCodes == null ? List.of() : List.copyOf(optionCodes);
            subjectCodes = subjectCodes == null ? List.of() : List.copyOf(subjectCodes);
        }

        /** One option, no subject list (a branch, a code, a subject option). */
        public static Selection option(String partCode, String optionCode) {
            return new Selection(partCode, List.of(optionCode), List.of());
        }

        /** One subject-choice option with its chosen subjects. */
        public static Selection subjects(String partCode, String optionCode, List<String> subjectCodes) {
            return new Selection(partCode, List.of(optionCode), subjectCodes);
        }
    }

    /** A serving question version, the unit of {@code (questionId, versionNo)}. A question set is one root. */
    record GeneratedQuestion(UUID questionId, int versionNo) {}

    /**
     * Questions picked for one authored block of the published snapshot, in the order the solver picked them.
     * {@code groupId} is the block id of the snapshot, once per section. A block with passage slots (HSA / QDA
     * Literature) is one group: the picks of all its slots, whole roots, in slot order.
     */
    record GeneratedGroup(String groupId, List<GeneratedQuestion> questions, boolean shuffleUnits) {
        public GeneratedGroup {
            questions = List.copyOf(questions);
        }

        public GeneratedGroup(String groupId, List<GeneratedQuestion> questions) {
            this(groupId, questions, true);
        }
    }

    /** One section of a part (a subject), with the number of questions it holds after trimming. */
    record GeneratedSection(
            String sectionId,
            String subjectCode,
            int questionCount,
            boolean mixBlocks,
            List<GeneratedGroup> groups) {
        public GeneratedSection {
            groups = List.copyOf(groups);
        }

        public GeneratedSection(String sectionId, String subjectCode, int questionCount, List<GeneratedGroup> groups) {
            this(sectionId, subjectCode, questionCount, false, groups);
        }
    }

    /** One generated part; sections follow the structure order (combination order for a code part). */
    record GeneratedPart(String partCode, List<GeneratedSection> sections) {
        public GeneratedPart {
            sections = List.copyOf(sections);
        }
    }

    /** A single question a trimmed subject solved but did not keep. It is not in the paper. */
    record DroppedQuestion(String partCode, String sectionId, String subjectCode, GeneratedQuestion question) {}

    /** The generated parts in structure order, and the dropped questions. */
    record GeneratedPaper(List<GeneratedPart> parts, List<DroppedQuestion> dropped) {
        public GeneratedPaper {
            parts = List.copyOf(parts);
            dropped = List.copyOf(dropped);
        }
    }
}
