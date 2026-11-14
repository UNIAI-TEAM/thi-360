package vn.unicom.thi360.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.classroom.domain.ClassMember;
import vn.unicom.thi360.classroom.domain.ClassMemberHistory;
import vn.unicom.thi360.classroom.domain.ClassMemberHistoryAction;
import vn.unicom.thi360.classroom.domain.ClassNormalizer;
import vn.unicom.thi360.classroom.domain.ClassRoom;
import vn.unicom.thi360.classroom.domain.ClassStatus;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberHistoryRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassRoomRepository;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.ids.Ids;

@Component
@Profile("dev")
@Order(2)
public class DevClassSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevClassSeeder.class);
    private static final String MODULE = "CLASSROOM";
    private static final String TARGET_TYPE_CLASS = "CLASS";
    private static final String SEED_REASON = "dev_seed";
    private static final String LOCK_SCOPE = "classroom";
    private static final String LOCK_KEY = "dev_class_seed";

    private static final List<SeedClass> SEED_CLASSES = List.of(
            new SeedClass("LOP-A", "Lớp A", "Nhóm ôn HSA buổi tối", "ops.admin", List.of("student01", "student02")),
            new SeedClass("LOP-B", "Lớp B", "Nhóm ôn QDA cuối tuần", "ops.admin", List.of("student03", "student04")));

    private final ClassRoomRepository classRoomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassMemberHistoryRepository classMemberHistoryRepository;
    private final UserDirectory userDirectory;
    private final ClassMemberLock classMemberLock;
    private final AuditRecorder auditRecorder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DevClassSeeder(
            ClassRoomRepository classRoomRepository,
            ClassMemberRepository classMemberRepository,
            ClassMemberHistoryRepository classMemberHistoryRepository,
            UserDirectory userDirectory,
            ClassMemberLock classMemberLock,
            AuditRecorder auditRecorder,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.classRoomRepository = Objects.requireNonNull(classRoomRepository);
        this.classMemberRepository = Objects.requireNonNull(classMemberRepository);
        this.classMemberHistoryRepository = Objects.requireNonNull(classMemberHistoryRepository);
        this.userDirectory = Objects.requireNonNull(userDirectory);
        this.classMemberLock = Objects.requireNonNull(classMemberLock);
        this.auditRecorder = Objects.requireNonNull(auditRecorder);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        int createdClasses = 0;
        int addedMembers = 0;
        SeedSummary summary = transactionTemplate.execute(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                    Object.class,
                    LOCK_SCOPE,
                    LOCK_KEY);

            Instant now = Instant.now(clock);
            int created = 0;
            int added = 0;
            for (SeedClass spec : SEED_CLASSES) {
                UUID ownerId = spec.ownerUsername() == null
                        ? null
                        : userDirectory.findActiveByUsername(spec.ownerUsername())
                                .map(UserDirectory.UserSummary::id)
                                .orElse(null);
                List<UUID> studentIds = spec.studentUsernames().stream()
                        .map(username -> userDirectory.findActiveByUsername(username)
                                .map(UserDirectory.UserSummary::id)
                                .orElseThrow(() -> new IllegalStateException("Missing seeded student: " + username)))
                        .toList();
                SeedOutcome outcome = ensureSeedClass(spec, ownerId, studentIds, now);
                if (outcome.createdClass()) {
                    created++;
                }
                added += outcome.addedMembers();
            }
            return new SeedSummary(created, added);
        });

        if (summary != null) {
            createdClasses = summary.createdClasses();
            addedMembers = summary.addedMembers();
        }
        log.atInfo()
                .addKeyValue("event", "dev_class_seed.verified")
                .addKeyValue("totalClasses", SEED_CLASSES.size())
                .addKeyValue("createdClasses", createdClasses)
                .addKeyValue("addedMembers", addedMembers)
                .log("Dev seed classes verified");
    }

    private SeedOutcome ensureSeedClass(
            SeedClass spec,
            UUID ownerUserId,
            List<UUID> studentIds,
            Instant occurredAt) {
        String trimmedCode = spec.code().trim();
        String normalizedCode = ClassNormalizer.normalizeCode(trimmedCode);
        ClassRoom classRoom = classRoomRepository.findByCodeNormalizedAndDeletedAtIsNullForUpdate(normalizedCode)
                .orElse(null);
        boolean createdClass = false;
        if (classRoom == null) {
            classRoom = new ClassRoom(
                    Ids.newId(),
                    trimmedCode,
                    spec.name().trim(),
                    spec.description(),
                    ownerUserId,
                    ClassStatus.ACTIVE,
                    occurredAt,
                    null);
            classRoom = classRoomRepository.saveAndFlush(classRoom);
            createdClass = true;

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("code", classRoom.getCode());
            after.put("name", classRoom.getName());
            after.put("description", classRoom.getDescription());
            after.put("ownerUserId", ownerUserId == null ? null : ownerUserId.toString());
            after.put("status", classRoom.getStatus().name());
            auditRecorder.record(AuditEvent.builder(MODULE, "CLASS_CREATED")
                    .target(TARGET_TYPE_CLASS, classRoom.getId().toString(), classRoom.getCode())
                    .after(after)
                    .reason(SEED_REASON)
                    .actorType(AuditActorType.SYSTEM)
                    .build());
        } else if (classRoom.getStatus() == ClassStatus.INACTIVE) {
            log.atInfo()
                    .addKeyValue("event", "dev_class_seed.skipped_inactive")
                    .addKeyValue("classCode", classRoom.getCode())
                    .addKeyValue("classId", classRoom.getId())
                    .log("Skipping dev seed member assignment for inactive class");
            return new SeedOutcome(false, 0);
        }

        if (studentIds.isEmpty()) {
            return new SeedOutcome(createdClass, 0);
        }

        Set<UUID> uniqueStudents = new LinkedHashSet<>();
        for (UUID studentId : studentIds) {
            if (studentId != null) {
                uniqueStudents.add(studentId);
            }
        }
        classMemberLock.lockUsers(uniqueStudents);

        List<ClassMember> newMembers = new ArrayList<>();
        List<ClassMemberHistory> historyRows = new ArrayList<>();
        List<String> addedUserIds = new ArrayList<>();
        for (UUID studentId : uniqueStudents) {
            if (classMemberRepository.existsByIdClassIdAndIdUserId(classRoom.getId(), studentId)) {
                continue;
            }
            newMembers.add(new ClassMember(classRoom.getId(), studentId, occurredAt, null));
            historyRows.add(new ClassMemberHistory(
                    Ids.newId(),
                    classRoom.getId(),
                    studentId,
                    ClassMemberHistoryAction.JOIN,
                    null,
                    null,
                    SEED_REASON,
                    occurredAt));
            addedUserIds.add(studentId.toString());
        }

        if (!newMembers.isEmpty()) {
            classMemberRepository.saveAll(newMembers);
            classMemberRepository.flush();
            classMemberHistoryRepository.saveAll(historyRows);
            classMemberHistoryRepository.flush();

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("addedUserIds", addedUserIds);
            after.put("addedCount", addedUserIds.size());
            auditRecorder.record(AuditEvent.builder(MODULE, "CLASS_MEMBER_ADDED")
                    .target(TARGET_TYPE_CLASS, classRoom.getId().toString(), classRoom.getCode())
                    .after(after)
                    .reason(SEED_REASON)
                    .actorType(AuditActorType.SYSTEM)
                    .build());
        }

        return new SeedOutcome(createdClass, newMembers.size());
    }

    private record SeedClass(
            String code,
            String name,
            String description,
            String ownerUsername,
            List<String> studentUsernames
    ) {
    }

    private record SeedOutcome(boolean createdClass, int addedMembers) {
    }

    private record SeedSummary(int createdClasses, int addedMembers) {
    }
}
