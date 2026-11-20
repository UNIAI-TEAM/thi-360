package vn.unicom.thi360.exam.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.unicom.thi360.AbstractIntegrationTest;
import vn.unicom.thi360.exam.spi.PaperSolverPort;
import vn.unicom.thi360.exam.spi.PaperSolverPort.GeneratedPaper;
import vn.unicom.thi360.exam.spi.PaperSolverPort.Selection;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.support.PublishedExamSetSupport;

/** Every exam of the published exam set generates three disjoint attempts on every path it serves (DS AC-12). */
class PublishedExamSetGenerationIT extends AbstractIntegrationTest {
    private static final List<String> SCIENCE_TRIPLE = List.of("LY", "HOA", "SINH");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PublishedExamSetSupport.useDatabase(registry, POSTGRES);
    }

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PublishedExamPort port;
    @Autowired PaperSolverPort solver;

    @BeforeEach
    void build() {
        PublishedExamSetSupport.ensureBuilt(context);
    }

    @Test
    void everyExamGeneratesThreeDisjointAttempts() {
        List<String> failures = new ArrayList<>();
        int generated = 0;
        for (var exam : PublishedExamSetDefinition.load(mapper).exams()) {
            UUID id = jdbc.queryForObject("SELECT id FROM exam.exam WHERE code = ?", UUID.class, exam.code());
            PublishedExamPort.ServingExam serving = port.findServing(id).orElseThrow();
            List<String> partIds = serving.version().parts().stream().map(PublishedExamPort.Part::id).toList();
            for (var path : serving.servingPaths()) {
                List<Selection> selections = new ArrayList<>();
                for (var choice : path.choices()) {
                    selections.add("SUBJECT_CHOICE".equals(choice.kind())
                            ? Selection.subjects(choice.partId(), choice.code(), SCIENCE_TRIPLE)
                            : Selection.option(choice.partId(), choice.code()));
                }
                Set<UUID> issued = new LinkedHashSet<>();
                for (int attempt = 1; attempt <= 3; attempt++) {
                    String label = exam.code() + " path " + path.pathId() + " attempt " + attempt;
                    try {
                        GeneratedPaper paper = solver.generate(id, serving.version().versionNo(), partIds, selections,
                                attempt, new HashSet<>(issued));
                        Set<UUID> now = new HashSet<>();
                        paper.parts().forEach(part -> part.sections().forEach(section -> section.groups()
                                .forEach(group -> group.questions().forEach(q -> now.add(q.questionId())))));
                        paper.dropped().forEach(dropped -> now.add(dropped.question().questionId()));
                        if (now.isEmpty()) {
                            failures.add(label + ": empty paper");
                        }
                        for (UUID question : now) {
                            if (issued.contains(question)) {
                                failures.add(label + ": repeats a question of an earlier attempt");
                                break;
                            }
                        }
                        issued.addAll(now);
                        generated++;
                    } catch (RuntimeException e) {
                        failures.add(label + ": " + e.getMessage());
                        break;
                    }
                }
            }
        }
        assertThat(failures).as("%d papers generated", generated).isEmpty();
    }
}
