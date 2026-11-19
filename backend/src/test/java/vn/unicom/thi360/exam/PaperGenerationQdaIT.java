package vn.unicom.thi360.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.unicom.thi360.exam.spi.PaperGenerationException;
import vn.unicom.thi360.exam.spi.PaperGenerationException.Reason;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedSection;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;

/** QDA FULL: the combination code of the third part, its subjects and their order come from the version snapshot. */
class PaperGenerationQdaIT extends PaperGenerationBaseIT {

    @Test
    void combinationOrderComesFromSnapshot() throws Exception {
        Published published = publishTemplate("QDA", "FULL", null, null, null);
        String codes = partIds(published).getLast();

        GeneratedPaper paper = generate(published, codes, "QD02");
        // QD02 is Hoa then Sinh in the Global List when the version is published.
        assertThat(paper.parts()).hasSize(1);
        assertThat(paper.parts().getFirst().sections()).extracting(GeneratedSection::subjectCode)
                .containsExactly("HOA", "SINH");

        // The order is read from config.combinations of the version: swap it there and the paper follows.
        ObjectNode config = (ObjectNode) versionConfig(published);
        for (JsonNode combination : config.path("combinations")) {
            if (!"QD02".equals(combination.path("code").asText())) continue;
            ArrayNode subjects = (ArrayNode) combination.path("subjects");
            for (JsonNode subject : subjects) {
                ((ObjectNode) subject).put("position", 1 - subject.path("position").asInt());
            }
        }
        jdbc.update("UPDATE exam.exam_version SET config = ?::jsonb WHERE exam_id = ? AND version_no = ?",
                config.toString(), published.examId(), published.versionNo());
        entities.clear();

        GeneratedPaper swapped = generate(published, codes, "QD02");
        assertThat(swapped.parts().getFirst().sections()).extracting(GeneratedSection::subjectCode)
                .containsExactly("SINH", "HOA");
    }

    @Test
    void globalListChangeDoesNotAffectOldVersion() throws Exception {
        Published published = publishTemplate("QDA", "FULL", null, null, null);
        String codes = partIds(published).getLast();
        GeneratedPaper before = generate(published, codes, "QD02");

        // After publication the Global List swaps the subjects of QD02 and gains a new code QD06.
        UUID qd02 = jdbc.queryForObject("SELECT v.id FROM catalog.combination_version v "
                + "JOIN catalog.catalog_item i ON i.id = v.item_id WHERE i.code = 'QD02' AND v.is_current", UUID.class);
        jdbc.update("UPDATE catalog.combination_subject SET position = 2 WHERE combination_version_id = ? AND position = 0", qd02);
        jdbc.update("UPDATE catalog.combination_subject SET position = 0 WHERE combination_version_id = ? AND position = 1", qd02);
        jdbc.update("UPDATE catalog.combination_subject SET position = 1 WHERE combination_version_id = ? AND position = 2", qd02);
        UUID item = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog.catalog_item(id,group_id,code,code_normalized,value,name_vi,path) "
                + "SELECT ?,g.id,'QD06','qd06',(SELECT max(value)+1 FROM catalog.catalog_item WHERE group_id=g.id),'QD06',? "
                + "FROM catalog.catalog_group g WHERE g.code='SUBJECT_COMBINATION'", item, item.toString());
        jdbc.update("INSERT INTO catalog.combination_version(id,item_id,version_no,exam_item_id,branch) "
                + "VALUES (?,?,1,?,'SCIENCE')", version, item, QDA_KIND);
        jdbc.update("INSERT INTO catalog.combination_subject(combination_version_id,subject_item_id,position) "
                + "VALUES (?,?,0),(?,?,1)", version,
                UUID.fromString("00000000-0000-7000-8200-000000000204"), version,
                UUID.fromString("00000000-0000-7000-8200-000000000208"));
        entities.clear();

        // The old version still generates the code it was published with, in the order it was published with.
        GeneratedPaper after = generate(published, codes, "QD02");
        assertThat(after).isEqualTo(before);
        assertThat(after.parts().getFirst().sections()).extracting(GeneratedSection::subjectCode)
                .containsExactly("HOA", "SINH");
        // The new code is not in the snapshot.
        assertThatThrownBy(() -> generate(published, codes, "QD06")).isInstanceOfSatisfying(
                PaperGenerationException.class, ex -> assertThat(ex.reason()).isEqualTo(Reason.GENERATION_FAILED));
    }

    private GeneratedPaper generate(Published published, String codesPart, String code) {
        return port.generate(published.examId(), published.versionNo(), List.of(codesPart),
                List.of(Selection.option(codesPart, code)), 21L, Set.of());
    }
}
