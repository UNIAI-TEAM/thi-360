package vn.unicom.thi360.questionbank.devdata;

import static vn.unicom.thi360.questionbank.spi.QuestionType.MATCHING;
import static vn.unicom.thi360.questionbank.spi.QuestionType.MULTIPLE_CHOICE;
import static vn.unicom.thi360.questionbank.spi.QuestionType.SHORT_ANSWER;
import static vn.unicom.thi360.questionbank.spi.QuestionType.SINGLE_CHOICE;
import static vn.unicom.thi360.questionbank.spi.QuestionType.TRUE_FALSE;

import java.util.List;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/** The shortfall families of {@link PoolSpec}; see there for how the counts were measured. */
final class PublishedQuestionPool {

    private PublishedQuestionPool() {
    }

    static final List<PoolSpec> SPECS = List.of(
            // HSA, QDA: Van and Tieng Anh tagged groups.
            PoolSpec.of("VAN-KHAC-NHOM", "VAN", SINGLE_CHOICE, 8, "1").tags("VAN_TU_KHAC_NHOM").veryHard(),
            PoolSpec.of("VAN-TU-SAI", "VAN", SINGLE_CHOICE, 8, "1").tags("VAN_TU_SAI").veryHard(),
            PoolSpec.of("VAN-CAP-TU", "VAN", SINGLE_CHOICE, 8, "1").tags("VAN_CAP_TU_DIEN").veryHard(),
            PoolSpec.of("VAN-MOT-CAU", "VAN", SINGLE_CHOICE, 24, "1").tags("VAN_NGU_LIEU_MOT_CAU").veryHard(),
            PoolSpec.of("ANH-HOAN-THANH", "ANH", SINGLE_CHOICE, 24, "1").tags("ANH_HOAN_THANH_CAU").veryHard(),
            PoolSpec.set("ANH-DOC-DIEN", "ANH", 4, 5, "1").tags("ANH_DOC_DIEN"),
            PoolSpec.set("ANH-DOC-HIEU-1", "ANH", 4, 5, "1").tags("ANH_DOC_HIEU_1"),
            PoolSpec.set("ANH-DOC-HIEU-2", "ANH", 4, 5, "1").tags("ANH_DOC_HIEU_2"),
            PoolSpec.of("ANH-LOGIC", "ANH", SINGLE_CHOICE, 8, "1").tags("ANH_TU_DUY_LOGIC").veryHard(),
            PoolSpec.of("ANH-SUY-LUAN", "ANH", SINGLE_CHOICE, 8, "1").tags("ANH_SUY_LUAN").veryHard(),
            PoolSpec.of("TOAN-SA-1", "TOAN", SHORT_ANSWER, 40, "1").veryHard(),
            // BCA: Tieng Anh at 0.75 points.
            PoolSpec.of("ANH-BCA-TVNP", "ANH", SINGLE_CHOICE, 16, "0.75").tags("ANH_TU_VUNG_NGU_PHAP"),
            PoolSpec.set("ANH-BCA-DOC-DIEN", "ANH", 4, 5, "0.75").tags("ANH_DOC_DIEN"),
            PoolSpec.set("ANH-BCA-DOC-HIEU", "ANH", 4, 5, "0.75").tags("ANH_DOC_HIEU"),
            // V-ACT: ten points per question, competencies of the template scope.
            PoolSpec.of("VACT-VN", "VIETNAMESE", SINGLE_CHOICE, 70, "10").competency("VACT-NN-TV"),
            PoolSpec.of("VACT-ANH-HTC", "ANH", SINGLE_CHOICE, 16, "10").competency("VACT-NN-TA")
                    .tags("ANH_HOAN_THANH_CAU"),
            PoolSpec.of("VACT-ANH-LOI", "ANH", SINGLE_CHOICE, 16, "10").competency("VACT-NN-TA")
                    .tags("ANH_PHAT_HIEN_LOI"),
            PoolSpec.of("VACT-ANH-TD", "ANH", SINGLE_CHOICE, 16, "10").competency("VACT-NN-TA")
                    .tags("ANH_DIEN_DAT_TUONG_DUONG"),
            PoolSpec.set("VACT-ANH-DH7", "ANH", 4, 7, "10").competency("VACT-NN-TA").tags("ANH_DOC_HIEU"),
            PoolSpec.set("VACT-ANH-DH8", "ANH", 4, 8, "10").competency("VACT-NN-TA").tags("ANH_DOC_HIEU"),
            PoolSpec.of("VACT-TOAN", "TOAN", SINGLE_CHOICE, 30, "10").competency("VACT-TOAN"),
            PoolSpec.set("VACT-TOAN-SET", "TOAN", 14, 3, "10").competency("VACT-TOAN"),
            PoolSpec.set("VACT-LOGIC", "SCIENTIFIC_THINKING", 14, 3, "10").competency("VACT-TD-LOGIC"),
            PoolSpec.set("VACT-SUY-LUAN", "SCIENTIFIC_THINKING", 20, 3, "10").competency("VACT-TD-SL"),
            // SP2E, THPTQG.
            PoolSpec.of("TOAN-DLTH", "TOAN", SINGLE_CHOICE, 8, "0.25").tags("TOAN_DU_LIEU_TONG_HOP"),
            PoolSpec.of("TIN-TF-1", "INFORMATICS", TRUE_FALSE, 10, "1"),
            // H-SCA.
            PoolSpec.of("HSCA-TOAN-SC", "TOAN", SINGLE_CHOICE, 40, "0.25"),
            PoolSpec.of("HSCA-HOA-SC", "HOA", SINGLE_CHOICE, 40, "0.25"),
            PoolSpec.of("HSCA-HOA-NDA", "HOA", MULTIPLE_CHOICE, 6, "0.25").partial(),
            PoolSpec.of("HSCA-TOAN-NDA", "TOAN", MULTIPLE_CHOICE, 6, "0.25").partial(),
            PoolSpec.of("HSCA-ANH-NGHE", "ANH", SINGLE_CHOICE, 18, "1").audio(),
            PoolSpec.set("HSCA-ANH-NGHE4", "ANH", 7, 4, "1").audio(),
            PoolSpec.set("HSCA-ANH-NGHE5", "ANH", 7, 5, "1").audio(),
            PoolSpec.set("HSCA-ANH-DOC", "ANH", 8, 10, "1"),
            // V-SAT: six points per question.
            PoolSpec.of("VSAT-TOAN-DS", "TOAN", TRUE_FALSE, 20, "6"),
            PoolSpec.of("VSAT-VAN-DS", "VAN", TRUE_FALSE, 20, "6"),
            PoolSpec.of("VSAT-ANH-DS", "ANH", TRUE_FALSE, 14, "6"),
            PoolSpec.set("VSAT-TOAN-CHUM", "TOAN", 4, 3, "6"),
            PoolSpec.of("VSAT-TOAN-GHEP", "TOAN", MATCHING, 10, "6"),
            PoolSpec.of("VSAT-VAN-GHEP", "VAN", MATCHING, 10, "6"),
            PoolSpec.of("VSAT-ANH-GHEP", "ANH", MATCHING, 10, "6"),
            PoolSpec.of("VSAT-TOAN-TLN", "TOAN", SHORT_ANSWER, 10, "6"),
            PoolSpec.set("VSAT-VAN-THO", "VAN", 4, 3, "6").tags("VAN_THO"),
            PoolSpec.set("VSAT-VAN-TRUYEN", "VAN", 4, 3, "6").tags("VAN_TRUYEN"),
            PoolSpec.set("VSAT-ANH-DOC", "ANH", 3, 8, "6")
                    .mixed(TRUE_FALSE, TRUE_FALSE, SINGLE_CHOICE, SINGLE_CHOICE, SINGLE_CHOICE, SINGLE_CHOICE,
                            SINGLE_CHOICE, SINGLE_CHOICE),
            PoolSpec.of("VSAT-ANH-CLOZE", "ANH", QuestionType.CLOZE, 3, "6"));
}
