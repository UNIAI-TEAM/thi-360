package vn.unicom.thi360.attempt.testsupport;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.unicom.thi360.attempt.application.AudioFileProbe;
import vn.unicom.thi360.questionbank.spi.QuestionAttachmentPort.Attachment;
import vn.unicom.thi360.questionbank.spi.QuestionSourcePort.VersionKey;
import vn.unicom.thi360.shared.testapi.TestApiController;
import vn.unicom.thi360.shared.testapi.TestApiPaths;

/**
 * E2E-only control endpoint and {@link AudioFileProbe} wrapper allowing Playwright tests to toggle
 * simulated missing audio storage faults for an exam without altering real storage objects (plan B6).
 */
@TestApiController
@Hidden
@RequestMapping(
        value = TestApiPaths.PREFIX + "/audio-faults",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class E2eAudioFaultController {

    private final E2eAudioFaultProbe probe;

    public E2eAudioFaultController(E2eAudioFaultProbe probe) {
        this.probe = Objects.requireNonNull(probe);
    }

    public record AudioFaultResponse(UUID examId, boolean active) {}

    @PostMapping("/{examId}")
    public AudioFaultResponse enable(@PathVariable UUID examId) {
        probe.enable(examId);
        return new AudioFaultResponse(examId, true);
    }

    @DeleteMapping("/{examId}")
    public AudioFaultResponse disable(@PathVariable UUID examId) {
        probe.disable(examId);
        return new AudioFaultResponse(examId, false);
    }

    @Component
    @Primary
    @Profile(TestApiPaths.E2E_PROFILE)
    public static class E2eAudioFaultProbe implements AudioFileProbe {

        private final AudioFileProbe.DefaultAudioFileProbe delegate;
        private final Set<UUID> faultedExamIds = ConcurrentHashMap.newKeySet();

        public E2eAudioFaultProbe(AudioFileProbe.DefaultAudioFileProbe delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }

        public void enable(UUID examId) {
            faultedExamIds.add(Objects.requireNonNull(examId));
        }

        public void disable(UUID examId) {
            if (examId != null) {
                faultedExamIds.remove(examId);
            }
        }

        @Override
        public Map<VersionKey, List<Attachment>> probe(
                UUID examId, Collection<VersionKey> versions, boolean verifyStorage) {
            Map<VersionKey, List<Attachment>> base =
                    delegate.probe(examId, versions, verifyStorage);
            if (!verifyStorage
                    || examId == null
                    || !faultedExamIds.contains(examId)
                    || base.isEmpty()) {
                return base;
            }
            Map<VersionKey, List<Attachment>> faulted = new LinkedHashMap<>(base.size());
            for (Map.Entry<VersionKey, List<Attachment>> entry : base.entrySet()) {
                List<Attachment> updated = new ArrayList<>(entry.getValue().size());
                for (Attachment att : entry.getValue()) {
                    if (att.mime() != null
                            && att.mime().toLowerCase(Locale.ROOT).startsWith("audio/")) {
                        updated.add(
                                new Attachment(att.id(), att.name(), att.mime(), Boolean.FALSE));
                    } else {
                        updated.add(att);
                    }
                }
                faulted.put(entry.getKey(), List.copyOf(updated));
            }
            return Map.copyOf(faulted);
        }
    }
}
