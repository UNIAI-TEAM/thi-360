package vn.unicom.thi360.questionbank.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.AudioRequirement;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Candidate;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ChildCondition;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.Filter;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.ItemShape;
import vn.unicom.thi360.questionbank.spi.QuestionType;

/**
 * BE-19 (UNI-2691): asks the serving source for the seeded H-SCA / V-SAT samples with the filters a
 * template block of spec 2026-11-18 sections 4 and 5 produces. The filter is built exactly from the
 * block: only the fields the spec line names are set.
 */
abstract class SampleSourceSupport extends AbstractIntegrationTest {
    static final ItemShape FOUR_OPTIONS = new ItemShape(null, null, null, 4);
    static final ItemShape FOUR_STATEMENTS = new ItemShape(4, null, null, null);
    /** ADV:BE-19:2: V-SAT matching declares four left items and nothing else. */
    static final ItemShape FOUR_LEFT_ITEMS = new ItemShape(null, 4, null, null);
    private static final int LIMIT = 100_000;

    @Autowired JdbcTemplate jdbc;
    @Autowired QuestionSourcePort source;

    UUID subject(String code) {
        return jdbc.queryForObject("""
                select id from catalog.catalog_item
                where group_id = '00000000-0000-7000-8100-000000000002' and code = ? and status = 1
                """, UUID.class, code);
    }

    /** One template block; {@code null} fields are the ones the spec line does not state. */
    record Block(QuestionType type, QuestionType childType, List<String> tags, String points, ItemShape shape,
                 List<ChildCondition> children, List<List<String>> slotTagGroups, AudioRequirement audio) {
        static Block of(QuestionType type) {
            return new Block(type, null, List.of(), null, null, List.of(), List.of(), null);
        }
        Block childType(QuestionType value) {
            return new Block(type, value, tags, points, shape, children, slotTagGroups, audio);
        }
        Block tag(String code) {
            return new Block(type, childType, List.of(code), points, shape, children, slotTagGroups, audio);
        }
        Block points(String value) {
            return new Block(type, childType, tags, value, shape, children, slotTagGroups, audio);
        }
        Block shape(ItemShape value) {
            return new Block(type, childType, tags, points, value, children, slotTagGroups, audio);
        }
        Block children(ChildCondition... value) {
            return new Block(type, childType, tags, points, shape, List.of(value), slotTagGroups, audio);
        }
        Block slot(String code) {
            return new Block(type, childType, tags, points, shape, children, List.of(List.of(code)), audio);
        }
        Block audio(AudioRequirement value) {
            return new Block(type, childType, tags, points, shape, children, slotTagGroups, value);
        }
    }

    Filter filter(String subjectCode, Block block) {
        return new Filter(subject(subjectCode), List.of(), List.of(), block.type(), null, block.childType(),
                block.tags(), List.of(), block.points() == null ? null : new BigDecimal(block.points()),
                block.shape(), List.of(), block.children(), block.slotTagGroups(), List.of(), List.of(), null,
                List.of(), block.audio());
    }

    List<Candidate> candidates(Filter filter) {
        return source.findCandidates(filter, LIMIT);
    }

    /** At least {@code units} question units, through both the count and the candidate list. */
    void assertUnits(String line, Filter filter, int units) {
        assertThat(source.count(filter).total()).as(line + " count").isGreaterThanOrEqualTo(units);
        assertThat(candidates(filter).stream().mapToInt(c -> c.units().size()).sum())
                .as(line + " candidate units").isGreaterThanOrEqualTo(units);
    }

    /** At least {@code sets} whole sets (or Cloze passages) of exactly {@code size} units each. */
    List<Candidate> assertSets(String line, Filter filter, int sets, int size) {
        List<Candidate> sized = candidates(filter).stream().filter(c -> c.units().size() == size).toList();
        assertThat(sized).as(line + " sets of " + size).hasSizeGreaterThanOrEqualTo(sets);
        assertThat(source.count(filter).total()).as(line + " count").isGreaterThanOrEqualTo((long) sets * size);
        return sized;
    }
}
