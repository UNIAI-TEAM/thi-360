package vn.unicom.thi360.commerce.entitlement.access;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPathPart;

/** What {@code PublishedExamPort.findServing} returns for the my-exams tests: a FULL exam on the given serving paths. */
final class ServingExams {

    private ServingExams() {
    }

    static ServingExam serving(UUID examId, String code, String status, ServingPath... paths) {
        List<String> pathIds = List.of(paths).stream().map(ServingPath::pathId).toList();
        Listing listing = new Listing("Tom tat", "Muc tieu", "Doi tuong", 199_000L, 3, true, false, true, "Ghi chu",
                "SCORE_AND_ANSWERS", "Mo ta", null);
        PublishedVersion version = new PublishedVersion(examId, 1, "SERVING", status,
                Instant.parse("2026-11-01T00:00:00Z"), code, "Bai thi " + code, null, "HSA", "FULL", null, null,
                "STANDARD", "PER_PART", null, new BigDecimal("150"), null, "LINEAR", listing, List.of(), List.of(),
                List.of(), pathIds, pathIds);
        return new ServingExam(examId, status, version, List.of(paths));
    }

    /** A path with no choice: every buyer can be given it. */
    static ServingPath fixedPath(String pathId, int questions, int minutes) {
        return new ServingPath(pathId, 1, List.of(), questions, BigDecimal.valueOf(questions), minutes,
                List.of(new ServingPathPart("p1", "Phan 1", questions, BigDecimal.valueOf(questions), minutes)),
                List.of());
    }

    /** A path that stands for the option {@code optionCode} of the part: only a buyer of that code can be given it. */
    static ServingPath optionPath(String pathId, String optionCode, int questions, int minutes) {
        PathChoice choice = new PathChoice("p1", "opt-" + optionCode, "COMBINATION", optionCode, "To hop " + optionCode);
        return new ServingPath(pathId, 1, List.of(choice), questions, BigDecimal.valueOf(questions), minutes,
                List.of(new ServingPathPart("p1", "Phan 1", questions, BigDecimal.valueOf(questions), minutes)),
                List.of());
    }
}
