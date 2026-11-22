package vn.unicom.thi360.commerce.classassignment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.catalog.spi.CatalogItemView;
import vn.unicom.thi360.catalog.spi.CatalogLookup;
import vn.unicom.thi360.classroom.spi.ClassroomInfo;
import vn.unicom.thi360.classroom.spi.ClassroomLookupPort;
import vn.unicom.thi360.commerce.classassignment.api.AssignClassExamsRequest;
import vn.unicom.thi360.commerce.classassignment.api.AssignClassExamsResponse;
import vn.unicom.thi360.commerce.classassignment.api.AssignableExamItem;
import vn.unicom.thi360.commerce.classassignment.api.ClassExamAssignmentItem;
import vn.unicom.thi360.commerce.classassignment.api.RevokeClassExamAssignmentRequest;
import vn.unicom.thi360.commerce.classassignment.api.RevokeClassExamAssignmentResponse;
import vn.unicom.thi360.commerce.classassignment.api.UpdateClassExamAssignmentRequest;
import vn.unicom.thi360.commerce.classassignment.domain.ClassExamAssignment;
import vn.unicom.thi360.commerce.classassignment.domain.ClassExamAssignmentStatus;
import vn.unicom.thi360.commerce.classassignment.infrastructure.ClassExamAssignmentRepository;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementGrantService;
import vn.unicom.thi360.commerce.entitlement.application.EntitlementRowLocker;
import vn.unicom.thi360.commerce.entitlement.domain.Entitlement;
import vn.unicom.thi360.commerce.entitlement.domain.EntitlementGrant;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementGrantRepository;
import vn.unicom.thi360.commerce.entitlement.infrastructure.EntitlementRepository;
import vn.unicom.thi360.commerce.storefront.api.ExamKindRef;
import vn.unicom.thi360.commerce.storefront.api.LocalizedName;
import vn.unicom.thi360.exam.spi.PublishedExamPort;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Listing;
import vn.unicom.thi360.exam.spi.PublishedExamPort.Part;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PathChoice;
import vn.unicom.thi360.exam.spi.PublishedExamPort.PublishedVersion;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingExam;
import vn.unicom.thi360.exam.spi.PublishedExamPort.ServingPath;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.identity.spi.UserDirectory.UserSummary;
import vn.unicom.thi360.shared.api.ConflictException;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.NotFoundException;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.idempotency.IdempotentCommand;
import vn.unicom.thi360.shared.ids.Ids;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.AppMessages;

@Service
public class ClassExamAssignmentService {

    public static final Set<String> ALLOWED_RESULT_VIEWS =
            Set.of("SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION");

    private static final String MODULE = "commerce";
    private static final String TARGET_TYPE = "CLASS_EXAM_ASSIGNMENT";
    private static final String EXAM_GROUP = "EXAM";
    private static final String SUBJECT_GROUP = "SUBJECT";
    private static final String DEFAULT_RESULT_VIEW = "SCORE";

    private final ClassExamAssignmentRepository assignmentRepository;
    private final EntitlementRepository entitlementRepository;
    private final EntitlementGrantRepository grantRepository;
    private final EntitlementRowLocker locker;
    private final EntitlementGrantService entitlementGrantService;
    private final ClassroomLookupPort classroomLookupPort;
    private final PublishedExamPort publishedExamPort;
    private final CatalogLookup catalogLookup;
    private final UserDirectory userDirectory;
    private final CurrentUserProvider currentUserProvider;
    private final IdempotentCommand idempotentCommand;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final AppMessages messages;
    private final Clock clock;

    public ClassExamAssignmentService(
            ClassExamAssignmentRepository assignmentRepository,
            EntitlementRepository entitlementRepository,
            EntitlementGrantRepository grantRepository,
            EntitlementRowLocker locker,
            EntitlementGrantService entitlementGrantService,
            ClassroomLookupPort classroomLookupPort,
            PublishedExamPort publishedExamPort,
            CatalogLookup catalogLookup,
            UserDirectory userDirectory,
            CurrentUserProvider currentUserProvider,
            IdempotentCommand idempotentCommand,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            AppMessages messages,
            Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.entitlementRepository = entitlementRepository;
        this.grantRepository = grantRepository;
        this.locker = locker;
        this.entitlementGrantService = entitlementGrantService;
        this.classroomLookupPort = classroomLookupPort;
        this.publishedExamPort = publishedExamPort;
        this.catalogLookup = catalogLookup;
        this.userDirectory = userDirectory;
        this.currentUserProvider = currentUserProvider;
        this.idempotentCommand = idempotentCommand;
        this.auditRecorder = auditRecorder;
        this.jdbcTemplate = jdbcTemplate;
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ClassExamAssignmentItem> listAssignments(UUID classId) {
        requireExistingClass(classId);
        List<ClassExamAssignment> assignments =
                assignmentRepository.findByClassIdOrderByAssignedAtDescIdDesc(classId);
        return toItems(assignments);
    }

    @Transactional(readOnly = true)
    public List<AssignableExamItem> listAssignableExams(UUID classId) {
        requireExistingClass(classId);
        Set<UUID> activeExamIds = new LinkedHashSet<>();
        for (ClassExamAssignment assignment :
                assignmentRepository.findByClassIdOrderByAssignedAtDescIdDesc(classId)) {
            if (assignment.getStatus() == ClassExamAssignmentStatus.ACTIVE) {
                activeExamIds.add(assignment.getExamId());
            }
        }

        Map<String, CatalogItemView> subjectsByCode = loadSubjectsByCode();
        Map<String, CatalogItemView> kindsByCode = loadKindsByCode();
        List<AssignableExamItem> items = new ArrayList<>();
        for (UUID examId : publishedExamPort.listPublishedExamIds()) {
            Optional<ServingExam> servingOpt = publishedExamPort.findServing(examId);
            if (servingOpt.isEmpty() || !"PUBLISHED".equals(servingOpt.get().status())) {
                continue;
            }
            PublishedVersion version = servingOpt.get().version();
            if (version == null || version.code() == null || version.name() == null) {
                continue;
            }
            Listing listing = version.listing();
            String defaultResultView = listing != null && ALLOWED_RESULT_VIEWS.contains(listing.resultView())
                    ? listing.resultView()
                    : DEFAULT_RESULT_VIEW;
            String buildMode = "REVIEW".equals(version.buildMode()) ? "REVIEW" : "STANDARD";
            String scope = version.scope() != null ? version.scope() : "FULL";
            items.add(new AssignableExamItem(
                    examId,
                    version.code(),
                    version.name(),
                    resolveExamKind(version, kindsByCode),
                    scope,
                    resolveScopeLabel(version, subjectsByCode),
                    buildMode,
                    version.versionNo(),
                    defaultResultView,
                    activeExamIds.contains(examId)));
        }
        items.sort(Comparator.comparing(item -> item.code().toLowerCase(Locale.ROOT)));
        return items;
    }

    @Transactional
    public AssignClassExamsResponse assignExams(
            UUID classId, String idempotencyKey, AssignClassExamsRequest request) {
        return idempotentCommand.execute(
                idempotencyKey,
                "classExam.assign",
                classId,
                request,
                AssignClassExamsResponse.class,
                () -> doAssignExams(classId, request));
    }

    private AssignClassExamsResponse doAssignExams(UUID classId, AssignClassExamsRequest request) {
        String normalizedResultView = request.resultView().trim();
        if (!ALLOWED_RESULT_VIEWS.contains(normalizedResultView)) {
            String msg = messages.get("CLASS_EXAM_RESULT_VIEW_INVALID");
            throw new ValidationException(
                    "CLASS_EXAM_RESULT_VIEW_INVALID",
                    msg,
                    List.of(new FieldError("resultView", "CLASS_EXAM_RESULT_VIEW_INVALID", msg)));
        }

        ClassroomInfo classroom = classroomLookupPort
                .lockActiveOrInactiveById(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", messages.get("NOT_FOUND")));
        if (!classroom.active()) {
            throw new ConflictException("CLASS_INACTIVE", messages.get("CLASS_INACTIVE"));
        }

        Set<UUID> uniqueExamIds = new LinkedHashSet<>(request.examIds());
        Map<UUID, ServingExam> servingByExam = new LinkedHashMap<>();
        for (UUID examId : uniqueExamIds) {
            ServingExam serving = publishedExamPort
                    .findServing(examId)
                    .orElseThrow(() -> new NotFoundException("EXAM_NOT_FOUND", messages.get("EXAM_NOT_FOUND")));
            if (!"PUBLISHED".equals(serving.status()) || serving.version() == null) {
                throw new ConflictException("EXAM_NOT_PUBLISHED", messages.get("EXAM_NOT_PUBLISHED"));
            }
            servingByExam.put(examId, serving);
        }

        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Instant now = clock.instant();
        boolean autoGrant = request.autoGrantNewMembers() == null || request.autoGrantNewMembers();
        String note = trimToNull(request.note());
        List<UUID> memberIds = new ArrayList<>(classroomLookupPort.findActiveMemberUserIds(classId));
        memberIds.sort(Comparator.naturalOrder());

        List<ClassExamAssignment> createdAssignments = new ArrayList<>(servingByExam.size());
        int totalGrantsCreated = 0;

        for (Map.Entry<UUID, ServingExam> entry : servingByExam.entrySet()) {
            UUID examId = entry.getKey();
            ServingExam serving = entry.getValue();
            PublishedVersion version = serving.version();
            List<String> optionCodes = extractServingOptionCodes(serving);
            Map<String, Object> benefits = extractBenefits(version.listing());

            ClassExamAssignment assignment = new ClassExamAssignment(
                    Ids.newId(),
                    classId,
                    examId,
                    version.versionNo(),
                    version.code(),
                    version.name(),
                    request.attemptsGranted(),
                    normalizedResultView,
                    optionCodes,
                    benefits,
                    autoGrant,
                    note,
                    actor.id(),
                    now);
            assignmentRepository.saveAndFlush(assignment);
            createdAssignments.add(assignment);

            int grantedForAssignment = grantAssignmentToMembers(
                    assignment, memberIds, actor.username(), now);
            totalGrantsCreated += grantedForAssignment;

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("classId", classId.toString());
            after.put("classCode", classroom.code());
            after.put("examId", examId.toString());
            after.put("examCode", version.code());
            after.put("examVersionNo", version.versionNo());
            after.put("attemptsGranted", request.attemptsGranted());
            after.put("resultView", normalizedResultView);
            after.put("autoGrantNewMembers", autoGrant);
            after.put("grantedMembersCount", grantedForAssignment);
            auditRecorder.record(AuditEvent.builder(MODULE, "CLASS_EXAM_ASSIGNED")
                    .target(TARGET_TYPE, assignment.getId().toString(), classroom.code() + ":" + version.code())
                    .reason(note)
                    .after(after)
                    .build());
        }

        return new AssignClassExamsResponse(toItems(createdAssignments), totalGrantsCreated);
    }

    @Transactional
    public ClassExamAssignmentItem updateAssignment(
            UUID classId, UUID assignmentId, UpdateClassExamAssignmentRequest request) {
        ClassroomInfo classroom = requireExistingClass(classId);
        ClassExamAssignment assignment = assignmentRepository
                .lockByIdAndClassId(assignmentId, classId)
                .orElseThrow(() -> new NotFoundException(
                        "CLASS_EXAM_ASSIGNMENT_NOT_FOUND", messages.get("CLASS_EXAM_ASSIGNMENT_NOT_FOUND")));
        if (assignment.getStatus() == ClassExamAssignmentStatus.REVOKED) {
            throw new ConflictException(
                    "CLASS_EXAM_ASSIGNMENT_REVOKED", messages.get("CLASS_EXAM_ASSIGNMENT_REVOKED"));
        }

        boolean previous = assignment.isAutoGrantNewMembers();
        boolean next = Boolean.TRUE.equals(request.autoGrantNewMembers());
        assignment.updateAutoGrantNewMembers(next, clock.instant());
        assignmentRepository.saveAndFlush(assignment);

        auditRecorder.record(AuditEvent.builder(MODULE, "CLASS_EXAM_ASSIGNMENT_UPDATED")
                .target(TARGET_TYPE, assignment.getId().toString(), classroom.code() + ":" + assignment.getExamCode())
                .before(Map.of("autoGrantNewMembers", previous))
                .after(Map.of("autoGrantNewMembers", next))
                .build());

        return toItems(List.of(assignment)).getFirst();
    }

    @Transactional
    public RevokeClassExamAssignmentResponse revokeAssignment(
            UUID classId, UUID assignmentId, RevokeClassExamAssignmentRequest request) {
        ClassroomInfo classroom = requireExistingClass(classId);
        ClassExamAssignment assignment = assignmentRepository
                .lockByIdAndClassId(assignmentId, classId)
                .orElseThrow(() -> new NotFoundException(
                        "CLASS_EXAM_ASSIGNMENT_NOT_FOUND", messages.get("CLASS_EXAM_ASSIGNMENT_NOT_FOUND")));

        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Instant now = clock.instant();
        String reason = request == null ? null : trimToNull(request.reason());
        boolean newlyRevoked = assignment.markRevoked(actor.id(), reason, now);
        assignmentRepository.saveAndFlush(assignment);

        int attemptsRevoked = revokeAllUnusedForAssignment(assignment.getId());

        if (newlyRevoked) {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", ClassExamAssignmentStatus.REVOKED.name());
            after.put("attemptsRevoked", attemptsRevoked);
            auditRecorder.record(AuditEvent.builder(MODULE, "CLASS_EXAM_ASSIGNMENT_REVOKED")
                    .target(TARGET_TYPE, assignment.getId().toString(), classroom.code() + ":" + assignment.getExamCode())
                    .reason(reason)
                    .after(after)
                    .build());
        }

        return new RevokeClassExamAssignmentResponse(toItems(List.of(assignment)).getFirst(), attemptsRevoked);
    }

    int grantActiveAssignmentsToNewMembers(UUID classId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        List<ClassExamAssignment> activeAssignments =
                assignmentRepository.findByClassIdAndStatusAndAutoGrantNewMembersTrueOrderByAssignedAtAscIdAsc(
                        classId, ClassExamAssignmentStatus.ACTIVE);
        if (activeAssignments.isEmpty()) {
            return 0;
        }
        List<UUID> sortedUsers = new ArrayList<>(new LinkedHashSet<>(userIds));
        sortedUsers.sort(Comparator.naturalOrder());
        Instant now = clock.instant();
        int totalGranted = 0;
        for (ClassExamAssignment assignment : activeAssignments) {
            totalGranted += grantAssignmentToMembers(assignment, sortedUsers, "SYSTEM_CLASS_AUTO", now);
        }
        return totalGranted;
    }

    int revokeUnusedForRemovedMember(UUID classId, UUID userId) {
        List<UUID> entitlementIds = entitlementRepository.findClassEntitlementIdsByUserAndClass(userId, classId);
        int totalRevoked = 0;
        for (UUID entitlementId : entitlementIds) {
            for (UUID grantId : grantRepository.findClassGrantIdsByEntitlementId(entitlementId)) {
                totalRevoked += entitlementGrantService.revokeUnused(grantId);
            }
        }
        return totalRevoked;
    }

    int revokeAllActiveAssignmentsForDeletedClass(UUID classId, UUID actorId, String reason) {
        List<ClassExamAssignment> activeAssignments =
                assignmentRepository.lockByClassIdAndStatus(classId, ClassExamAssignmentStatus.ACTIVE);
        Instant now = clock.instant();
        int totalRevoked = 0;
        for (ClassExamAssignment assignment : activeAssignments) {
            assignment.markRevoked(actorId, trimToNull(reason), now);
            assignmentRepository.saveAndFlush(assignment);
            totalRevoked += revokeAllUnusedForAssignment(assignment.getId());
        }
        return totalRevoked;
    }

    private int grantAssignmentToMembers(
            ClassExamAssignment assignment, List<UUID> sortedUserIds, String grantedBy, Instant now) {
        int created = 0;
        String actorName = StringUtils.hasText(grantedBy) ? grantedBy : "SYSTEM";
        for (UUID userId : sortedUserIds) {
            Entitlement entitlement =
                    locker.lockOrCreateClass(userId, assignment.getExamId(), assignment.getClassId());
            if (grantRepository
                    .findByClassAssignmentIdAndEntitlementId(assignment.getId(), entitlement.getId())
                    .isPresent()) {
                continue;
            }
            EntitlementGrant grant = EntitlementGrant.classAssignment(
                    Ids.newId(),
                    entitlement.getId(),
                    assignment.getId(),
                    assignment.getExamId(),
                    assignment.getOptionCodes(),
                    assignment.getAttemptsGranted(),
                    assignment.getBenefits(),
                    assignment.getResultView(),
                    assignment.getExamVersionNo(),
                    now,
                    actorName,
                    assignment.getNote());
            grantRepository.saveAndFlush(grant);
            entitlement.addAttempts(assignment.getAttemptsGranted(), now);
            created++;
        }
        return created;
    }

    private int revokeAllUnusedForAssignment(UUID assignmentId) {
        int revoked = 0;
        for (UUID grantId : grantRepository.findIdsByClassAssignmentId(assignmentId)) {
            revoked += entitlementGrantService.revokeUnused(grantId);
        }
        return revoked;
    }

    private ClassroomInfo requireExistingClass(UUID classId) {
        return classroomLookupPort
                .findById(classId)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", messages.get("NOT_FOUND")));
    }

    private List<ClassExamAssignmentItem> toItems(List<ClassExamAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<UUID> assignmentIds = assignments.stream().map(ClassExamAssignment::getId).toList();
        Map<UUID, GrantStats> statsByAssignment = loadGrantStats(assignmentIds);

        Set<UUID> actorIds = new LinkedHashSet<>();
        for (ClassExamAssignment a : assignments) {
            actorIds.add(a.getAssignedBy());
            if (a.getRevokedBy() != null) {
                actorIds.add(a.getRevokedBy());
            }
        }
        Map<UUID, UserSummary> usersById = userDirectory.findByIds(actorIds);
        Map<String, CatalogItemView> kindsByCode = loadKindsByCode();
        Map<UUID, Optional<ServingExam>> servingCache = new HashMap<>();

        List<ClassExamAssignmentItem> items = new ArrayList<>(assignments.size());
        for (ClassExamAssignment a : assignments) {
            Optional<ServingExam> serving =
                    servingCache.computeIfAbsent(a.getExamId(), publishedExamPort::findServing);
            PublishedVersion version = serving.map(ServingExam::version).orElse(null);
            ExamKindRef examKind = version != null
                    ? resolveExamKind(version, kindsByCode)
                    : new ExamKindRef("EXAM", "EXAM", "EXAM");
            String scope = version != null && version.scope() != null ? version.scope() : "FULL";
            GrantStats stats = statsByAssignment.getOrDefault(a.getId(), GrantStats.ZERO);
            UserSummary assignedUser = usersById.get(a.getAssignedBy());
            UserSummary revokedUser = a.getRevokedBy() == null ? null : usersById.get(a.getRevokedBy());

            items.add(new ClassExamAssignmentItem(
                    a.getId(),
                    a.getClassId(),
                    a.getExamId(),
                    a.getExamCode(),
                    a.getExamName(),
                    examKind,
                    scope,
                    a.getExamVersionNo(),
                    a.getAttemptsGranted(),
                    a.getResultView(),
                    a.isAutoGrantNewMembers(),
                    a.getNote(),
                    a.getStatus(),
                    stats.grantedMembersCount(),
                    stats.totalAttemptsUsed(),
                    stats.totalAttemptsLeft(),
                    stats.totalAttemptsRevoked(),
                    a.getAssignedBy(),
                    assignedUser == null ? null : assignedUser.username(),
                    a.getAssignedAt(),
                    a.getRevokedBy(),
                    revokedUser == null ? null : revokedUser.username(),
                    a.getRevokedAt(),
                    a.getRevokeReason()));
        }
        return items;
    }

    private Map<UUID, GrantStats> loadGrantStats(List<UUID> assignmentIds) {
        Map<UUID, GrantStats> stats = new HashMap<>();
        if (assignmentIds.isEmpty()) {
            return stats;
        }
        jdbcTemplate.query(
                """
                SELECT class_assignment_id,
                       COUNT(*)::int AS granted_members,
                       COALESCE(SUM(attempts_used), 0)::int AS total_used,
                       COALESCE(SUM(attempts - attempts_used - attempts_reserved - attempts_revoked), 0)::int AS total_left,
                       COALESCE(SUM(attempts_revoked), 0)::int AS total_revoked
                FROM commerce.entitlement_grant
                WHERE class_assignment_id = ANY (?)
                GROUP BY class_assignment_id
                """,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", assignmentIds.toArray())),
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    stats.put(
                            rs.getObject("class_assignment_id", UUID.class),
                            new GrantStats(
                                    rs.getInt("granted_members"),
                                    rs.getInt("total_used"),
                                    rs.getInt("total_left"),
                                    rs.getInt("total_revoked")));
                });
        return stats;
    }

    private Map<String, CatalogItemView> loadKindsByCode() {
        Map<String, CatalogItemView> byCode = new LinkedHashMap<>();
        for (CatalogItemView item : catalogLookup.listGroupItems(EXAM_GROUP)) {
            byCode.putIfAbsent(item.code().trim().toLowerCase(Locale.ROOT), item);
        }
        return byCode;
    }

    private Map<String, CatalogItemView> loadSubjectsByCode() {
        Map<String, CatalogItemView> byCode = new LinkedHashMap<>();
        for (CatalogItemView item : catalogLookup.listGroupItems(SUBJECT_GROUP)) {
            byCode.putIfAbsent(item.code().trim().toLowerCase(Locale.ROOT), item);
        }
        return byCode;
    }

    private ExamKindRef resolveExamKind(
            PublishedVersion version, Map<String, CatalogItemView> kindsByCode) {
        if (version.kindItemId() != null) {
            Optional<CatalogItemView> byId = catalogLookup.findItem(version.kindItemId());
            if (byId.isPresent()) {
                return new ExamKindRef(version.kindCode(), byId.get().nameVi(), byId.get().nameEn());
            }
        }
        if (version.kindCode() != null) {
            CatalogItemView item = kindsByCode.get(version.kindCode().trim().toLowerCase(Locale.ROOT));
            if (item != null) {
                return new ExamKindRef(version.kindCode(), item.nameVi(), item.nameEn());
            }
            return new ExamKindRef(version.kindCode(), version.kindCode(), version.kindCode());
        }
        return new ExamKindRef("EXAM", "EXAM", "EXAM");
    }

    private static LocalizedName resolveScopeLabel(
            PublishedVersion version, Map<String, CatalogItemView> subjectsByCode) {
        if ("PART".equals(version.scope())) {
            List<Part> parts = version.parts();
            String name = (parts == null || parts.isEmpty() || parts.getFirst().name() == null)
                    ? version.partCode()
                    : parts.getFirst().name();
            return name == null ? null : new LocalizedName(name, name);
        }
        if ("SUBJECT".equals(version.scope()) && version.subjectItemId() != null) {
            for (CatalogItemView item : subjectsByCode.values()) {
                if (item.id().equals(version.subjectItemId())) {
                    return new LocalizedName(item.nameVi(), item.nameEn());
                }
            }
        }
        return null;
    }

    private static List<String> extractServingOptionCodes(ServingExam serving) {
        if (serving == null || serving.servingPaths() == null) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (ServingPath path : serving.servingPaths()) {
            if (path.choices() == null) {
                continue;
            }
            for (PathChoice choice : path.choices()) {
                if (StringUtils.hasText(choice.code())) {
                    codes.add(choice.code().trim());
                }
            }
        }
        return codes.isEmpty() ? List.of() : List.copyOf(codes);
    }

    private static Map<String, Object> extractBenefits(Listing listing) {
        if (listing == null) {
            return Map.of();
        }
        Map<String, Object> benefits = new LinkedHashMap<>();
        benefits.put("aiExplanation", listing.benefitAiExplanation());
        benefits.put("strengthWeakness", listing.benefitStrengthWeakness());
        benefits.put("practice", listing.benefitPractice());
        if (StringUtils.hasText(listing.benefitNote())) {
            benefits.put("note", listing.benefitNote().trim());
        }
        return Map.copyOf(benefits);
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record GrantStats(
            int grantedMembersCount,
            int totalAttemptsUsed,
            int totalAttemptsLeft,
            int totalAttemptsRevoked) {
        static final GrantStats ZERO = new GrantStats(0, 0, 0, 0);
    }
}
