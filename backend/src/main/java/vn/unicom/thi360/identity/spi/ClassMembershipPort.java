package vn.unicom.thi360.identity.spi;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public interface ClassMembershipPort {

    List<UUID> validateAssignableClasses(Collection<UUID> classIds);

    void assignUserToClasses(UUID userId, Collection<UUID> classIds, UUID actorId, Instant occurredAt);

    Set<UUID> findUserIdsInClass(UUID classId);

    UserClassesResult getUserClasses(UUID userId);

    record UserClassesResult(
            List<CurrentClass> currentClasses,
            List<ClassHistoryEntry> history
    ) {
        public UserClassesResult {
            currentClasses = currentClasses == null ? List.of() : List.copyOf(currentClasses);
            history = history == null ? List.of() : List.copyOf(history);
        }

        public static UserClassesResult empty() {
            return new UserClassesResult(List.of(), List.of());
        }
    }

    record CurrentClass(
            UUID classId,
            String classCode,
            String className,
            String classStatus,
            Instant joinedAt,
            UUID addedBy,
            String addedByUsername
    ) {
    }

    record ClassHistoryEntry(
            UUID id,
            UUID classId,
            String classCode,
            String className,
            String action,
            UUID relatedClassId,
            String relatedClassCode,
            String relatedClassName,
            UUID actorId,
            String actorUsername,
            String reason,
            Instant occurredAt
    ) {
    }
    Map<String, ClassLookupInfo> findClassesByNormalizedCodes(Collection<String> normalizedCodes);

    record ClassLookupInfo(UUID id, String code, String codeNormalized, boolean active) {
    }
}
