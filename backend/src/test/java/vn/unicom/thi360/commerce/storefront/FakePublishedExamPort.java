package vn.unicom.thi360.commerce.storefront;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.unicom.thi360.exam.spi.PublishedExamPort;

/**
 * A hand-driven {@link PublishedExamPort} (plan CM-1, test data): the test says what the exam module serves, so events
 * and rebuilds can be replayed, reordered and failed at will. A draft is simply not served, as in the real port.
 */
public class FakePublishedExamPort implements PublishedExamPort {

    private final Map<UUID, ServingExam> serving = new ConcurrentHashMap<>();
    private final Map<UUID, CoverImage> covers = new ConcurrentHashMap<>();
    private final Set<UUID> failing = ConcurrentHashMap.newKeySet();

    /** From now on the exam is served as given (a newer version, a discontinued exam, a stale read ...). */
    public void serve(ServingExam exam) {
        serving.put(exam.examId(), exam);
    }

    /** The exam is a draft again (or never was published). */
    public void withdraw(UUID examId) {
        serving.remove(examId);
    }

    public void cover(UUID assetId, String contentType, byte[] content) {
        covers.put(assetId, new CoverImage(contentType, content));
    }

    /** {@code findServing} of this exam throws until {@link #recover}. */
    public void failFor(UUID examId) {
        failing.add(examId);
    }

    public void recover(UUID examId) {
        failing.remove(examId);
    }

    @Override
    public Optional<ServingExam> findServing(UUID examId) {
        if (failing.contains(examId)) {
            throw new IllegalStateException("exam module unavailable for " + examId);
        }
        return Optional.ofNullable(serving.get(examId));
    }

    @Override
    public Optional<PublishedVersion> loadVersion(UUID examId, int versionNo) {
        return Optional.ofNullable(serving.get(examId)).map(ServingExam::version)
                .filter(version -> version.versionNo() == versionNo);
    }

    @Override
    public List<UUID> listPublishedExamIds() {
        return new ArrayList<>(serving.keySet());
    }

    @Override
    public Optional<CoverImage> openCover(UUID assetId) {
        return Optional.ofNullable(covers.get(assetId));
    }
}
