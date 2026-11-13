package vn.unicom.thi360.classroom.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.classroom.domain.ClassMember;
import vn.unicom.thi360.classroom.domain.ClassMemberHistory;
import vn.unicom.thi360.classroom.domain.ClassMemberHistoryAction;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberHistoryRepository;
import vn.unicom.thi360.classroom.infrastructure.ClassMemberRepository;
import vn.unicom.thi360.identity.spi.UserDeleted;
import vn.unicom.thi360.shared.ids.Ids;

@Component
public class UserDeletedClassCleanupListener {

    private static final String DEFAULT_DELETE_REASON = "Tài khoản đã bị xóa";

    private final ClassMemberRepository classMemberRepository;
    private final ClassMemberHistoryRepository classMemberHistoryRepository;
    private final Clock clock;

    public UserDeletedClassCleanupListener(
            ClassMemberRepository classMemberRepository,
            ClassMemberHistoryRepository classMemberHistoryRepository,
            Clock clock) {
        this.classMemberRepository = classMemberRepository;
        this.classMemberHistoryRepository = classMemberHistoryRepository;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void onUserDeleted(UserDeleted event) {
        if (event == null || event.userId() == null) {
            return;
        }
        List<ClassMember> memberships = classMemberRepository.findByIdUserIdOrderByIdClassIdAsc(event.userId());
        if (memberships.isEmpty()) {
            return;
        }

        Instant occurredAt = event.deletedAt() != null ? event.deletedAt() : Instant.now(clock);
        List<ClassMemberHistory> historyRows = new ArrayList<>(memberships.size());
        for (ClassMember membership : memberships) {
            historyRows.add(new ClassMemberHistory(
                    Ids.newId(),
                    membership.getClassId(),
                    event.userId(),
                    ClassMemberHistoryAction.LEAVE,
                    null,
                    event.actorId(),
                    DEFAULT_DELETE_REASON,
                    occurredAt));
        }

        classMemberRepository.deleteAllInBatch(memberships);
        classMemberRepository.flush();
        classMemberHistoryRepository.saveAll(historyRows);
        classMemberHistoryRepository.flush();
    }
}
