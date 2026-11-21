package vn.unicom.thi360.commerce.bundle.devdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.commerce.bundle.api.BundleDetailResponse;
import vn.unicom.thi360.commerce.bundle.api.BundleRevisionRequest;
import vn.unicom.thi360.commerce.bundle.api.CreateBundleRequest;
import vn.unicom.thi360.commerce.bundle.api.UpdateBundleRequest;
import vn.unicom.thi360.commerce.bundle.application.BundleAdminService;
import vn.unicom.thi360.commerce.bundle.devdata.BundleDevDataDefinition.Sp2eBundle;
import vn.unicom.thi360.commerce.bundle.domain.Bundle;
import vn.unicom.thi360.commerce.bundle.domain.BundleCodeNormalizer;
import vn.unicom.thi360.commerce.bundle.domain.BundleStatus;
import vn.unicom.thi360.commerce.bundle.infrastructure.BundleRepository;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.UserSummary;
import vn.unicom.thi360.shared.security.Access;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserAuthenticationToken;
import vn.unicom.thi360.shared.security.PermissionSet;
import vn.unicom.thi360.shared.security.Realm;

/**
 * Tạo và công bố bộ đề mẫu {@code DS-BO-SP2E-3MON} từ {@code devdata/published-exam-set.json} một cách idempotent
 * qua {@link BundleAdminService} và {@link PublishedExamPort} (AC-28).
 */
@Component
@Profile({"dev", "e2e", "test"})
public class BundleDevDataBuilder {

    private static final String ACTOR = "sys.admin";
    private static final String EXAM_GROUP = "EXAM";

    private final UserDirectory users;
    private final CatalogLookup catalog;
    private final PublishedExamPort publishedExamPort;
    private final BundleRepository bundleRepository;
    private final BundleAdminService bundleAdminService;
    private final ObjectMapper mapper;

    public BundleDevDataBuilder(
            UserDirectory users,
            CatalogLookup catalog,
            PublishedExamPort publishedExamPort,
            BundleRepository bundleRepository,
            BundleAdminService bundleAdminService,
            ObjectMapper mapper) {
        this.users = Objects.requireNonNull(users);
        this.catalog = Objects.requireNonNull(catalog);
        this.publishedExamPort = Objects.requireNonNull(publishedExamPort);
        this.bundleRepository = Objects.requireNonNull(bundleRepository);
        this.bundleAdminService = Objects.requireNonNull(bundleAdminService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Report build() {
        Sp2eBundle def = BundleDevDataDefinition.load(mapper).sp2eBundle();
        if (def == null) {
            return new Report(0, 0);
        }

        String normalizedCode = BundleCodeNormalizer.normalize(def.code());
        Bundle existing = bundleRepository.findByCodeNormalized(normalizedCode).orElse(null);
        if (existing != null && existing.getStatus() != BundleStatus.DRAFT) {
            return new Report(0, 1);
        }

        UserSummary actor = users.findActiveByUsername(ACTOR)
                .orElseThrow(() -> new IllegalStateException("Missing active devdata actor: " + ACTOR));
        UUID examKindItemId = catalog.findActiveItem(EXAM_GROUP, def.kind())
                .orElseThrow(() -> new IllegalStateException("Missing active catalog exam kind: " + def.kind()))
                .id();
        List<UUID> examIds = resolveExamIds(def.examCodes());

        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(new CurrentUserAuthenticationToken(
                    new CurrentUser(actor.id(), actor.username(), Realm.APP, Access.MANAGE, PermissionSet.all()),
                    null));
            SecurityContextHolder.setContext(ctx);

            BundleDetailResponse target;
            if (existing == null) {
                target = bundleAdminService.create(new CreateBundleRequest(
                        def.code(),
                        def.name(),
                        def.summary(),
                        def.description(),
                        examKindItemId,
                        def.priceVnd(),
                        def.attemptsPerExam(),
                        true,
                        false,
                        true,
                        null,
                        def.resultView(),
                        examIds));
            } else {
                target = bundleAdminService.update(existing.getId(), new UpdateBundleRequest(
                        existing.getRevision(),
                        def.code(),
                        def.name(),
                        def.summary(),
                        def.description(),
                        examKindItemId,
                        def.priceVnd(),
                        def.attemptsPerExam(),
                        true,
                        false,
                        true,
                        null,
                        def.resultView(),
                        examIds));
            }
            bundleAdminService.publish(target.id(), new BundleRevisionRequest(target.revision()));
            return new Report(1, 0);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private List<UUID> resolveExamIds(List<String> examCodes) {
        Map<String, UUID> examIdsByCode = new LinkedHashMap<>();
        for (UUID id : publishedExamPort.listPublishedExamIds()) {
            ServingExam serving = publishedExamPort.findServing(id).orElse(null);
            if (serving != null && serving.version() != null && "PUBLISHED".equals(serving.status())) {
                examIdsByCode.put(BundleCodeNormalizer.normalize(serving.version().code()), serving.examId());
            }
        }
        List<UUID> resolved = new ArrayList<>(examCodes.size());
        for (String code : examCodes) {
            UUID examId = examIdsByCode.get(BundleCodeNormalizer.normalize(code));
            if (examId == null) {
                throw new IllegalStateException("Missing serving published exam for bundle devdata: " + code);
            }
            resolved.add(examId);
        }
        return List.copyOf(resolved);
    }

    public record Report(int created, int skipped) {
    }
}
