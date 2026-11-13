package vn.unicom.thi360.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.audit.spi.AuditActorType;
import vn.unicom.thi360.audit.spi.AuditEvent;
import vn.unicom.thi360.audit.spi.AuditRecorder;
import vn.unicom.thi360.classroom.domain.ClassMember;
import vn.unicom.thi360.classroom.domain.ClassMemberHistory;
import vn.unicom.thi360.classroom.domain.ClassMemberHistoryAction;
import vn.unicom.thi360.classroom.domain.ClassRoom;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberHistoryRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassRoomRepository;
import vn.unicom.thi360.identity.spi.UserDeleted;
import vn.unicom.thi360.identity.spi.UserDirectory;
import vn.unicom.thi360.shared.ids.Ids;

@Component
public class UserDeletedClassCleanupListener {

    private static final String DEFAULT_DELETE_REASON = "Tài khoản đã bị xóa";
    private static final String MODULE = "CLASSROOM";
    private static final String TARGET_TYPE_CLASS = "CLASS";

    private final ClassMemberRepository classMemberRepository;
    private final ClassMemberHistoryRepository classMemberHistoryRepository;
    private final ClassRoomRepository classRoomRepository;
    private final UserDirectory userDirectory;
    private final ClassMemberLock classMemberLock;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public UserDeletedClassCleanupListener(
            ClassMemberRepository classMemberRepository,
            ClassMemberHistoryRepository classMemberHistoryRepository,
            ClassRoomRepository classRoomRepository,
            UserDirectory userDirectory,
            ClassMemberLock classMemberLock,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.classMemberRepository = classMemberRepository;
        this.classMemberHistoryRepository = classMemberHistoryRepository;
        this.classRoomRepository = classRoomRepository;
        this.userDirectory = userDirectory;
        this.classMemberLock = classMemberLock;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void onUserDeleted(UserDeleted event) {
        if (event == null || event.userId() == null) {
            return;
        }

        classMemberLock.lockUser(event.userId());

        List<ClassMember> memberships = classMemberRepository.findByIdUserIdOrderByIdClassIdAsc(event.userId());
        if (memberships.isEmpty()) {
            return;
        }

        String reason = userDirectory.findDeleteReason(event.userId()).orElse(DEFAULT_DELETE_REASON);
        Instant occurredAt = event.deletedAt() != null ? event.deletedAt() : Instant.now(clock);
        List<ClassMemberHistory> historyRows = new ArrayList<>(memberships.size());
        Set<UUID> classIds = new LinkedHashSet<>(memberships.size());
        for (ClassMember membership : memberships) {
            classIds.add(membership.getClassId());
            historyRows.add(new ClassMemberHistory(
                    Ids.newId(),
                    membership.getClassId(),
                    event.userId(),
                    ClassMemberHistoryAction.LEAVE,
                    null,
                    event.actorId(),
                    reason,
                    occurredAt));
        }

        Map<UUID, ClassRoom> classesById = new HashMap<>();
        for (ClassRoom classRoom : classRoomRepository.findAllById(classIds)) {
            classesById.put(classRoom.getId(), classRoom);
        }

        classMemberRepository.deleteAllInBatch(memberships);
        classMemberRepository.flush();
        classMemberHistoryRepository.saveAll(historyRows);
        classMemberHistoryRepository.flush();

        UserDirectory.UserSummary actorSummary = event.actorId() == null
                ? null
                : userDirectory.findById(event.actorId()).orElse(null);
        AuditActorType actorType;
        if (event.actorId() == null) {
            actorType = AuditActorType.SYSTEM;
        } else if (actorSummary != null && actorSummary.platformAdmin()) {
            actorType = AuditActorType.PLATFORM;
        } else {
            actorType = AuditActorType.USER;
        }
        String actorUsername = actorSummary == null ? null : actorSummary.username();

        for (ClassMember membership : memberships) {
            ClassRoom classRoom = classesById.get(membership.getClassId());
            String classCode = classRoom == null ? membership.getClassId().toString() : classRoom.getCode();
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("classId", membership.getClassId().toString());
            before.put("classCode", classCode);
            before.put("userId", event.userId().toString());
            if (event.username() != null) {
                before.put("username", event.username());
            }
            AuditEvent.Builder auditBuilder = AuditEvent.builder(MODULE, "CLASS_MEMBER_REMOVED")
                    .target(TARGET_TYPE_CLASS, membership.getClassId().toString(), classCode)
                    .before(before)
                    .reason(reason);
            if (event.actorId() != null) {
                auditBuilder.actor(event.actorId(), actorUsername, null, actorType);
            } else {
                auditBuilder.actorType(AuditActorType.SYSTEM);
            }
            auditRecorder.record(auditBuilder.build());
        }
    }
}
