package vn.unicom.thi360.commerce.classassignment.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.classroom.spi.ClassDeleted;
import vn.unicom.thi360.classroom.spi.ClassMemberRemoved;
import vn.unicom.thi360.classroom.spi.ClassMembersAdded;

/**
 * Keeps class-scoped exam entitlements synchronized inside the same transaction whenever classroom
 * membership changes or a class is soft-deleted (UNI-2774, C12, C15).
 */
@Component
public class ClassMembershipEntitlementListener {

    private final ClassExamAssignmentService assignmentService;

    public ClassMembershipEntitlementListener(ClassExamAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onClassMembersAdded(ClassMembersAdded event) {
        assignmentService.grantActiveAssignmentsToNewMembers(event.classId(), event.userIds());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onClassMemberRemoved(ClassMemberRemoved event) {
        assignmentService.revokeUnusedForRemovedMember(event.classId(), event.userId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onClassDeleted(ClassDeleted event) {
        assignmentService.revokeAllActiveAssignmentsForDeletedClass(
                event.classId(), event.actorId(), event.reason());
    }
}
