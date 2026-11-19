package vn.unicom.thi360.commerce.entitlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Attempts of one account on one exam (TM 8.5). Its counters always equal the sum of its grants' counters:
 * every change goes through the methods below together with the matching {@link EntitlementGrant} method.
 */
@Entity
@Table(name = "entitlement", schema = "commerce")
public class Entitlement {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private EntitlementSource source;

    @Column(name = "source_ref", updatable = false)
    private UUID sourceRef;

    @Column(name = "attempts_total", nullable = false)
    private int attemptsTotal;

    @Column(name = "attempts_used", nullable = false)
    private int attemptsUsed;

    @Column(name = "attempts_reserved", nullable = false)
    private int attemptsReserved;

    @Column(name = "attempts_revoked", nullable = false)
    private int attemptsRevoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Entitlement() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getExamId() {
        return examId;
    }

    public EntitlementSource getSource() {
        return source;
    }

    public int getAttemptsTotal() {
        return attemptsTotal;
    }

    public int getAttemptsUsed() {
        return attemptsUsed;
    }

    public int getAttemptsReserved() {
        return attemptsReserved;
    }

    public int getAttemptsRevoked() {
        return attemptsRevoked;
    }

    public int attemptsLeft() {
        return attemptsTotal - attemptsUsed - attemptsReserved - attemptsRevoked;
    }

    public void addAttempts(int attempts, Instant now) {
        attemptsTotal += attempts;
        updatedAt = now;
    }

    public void reserveOne(Instant now) {
        attemptsReserved++;
        updatedAt = now;
    }

    public void confirmOne(Instant now) {
        attemptsReserved--;
        attemptsUsed++;
        updatedAt = now;
    }

    public void releaseOne(Instant now) {
        attemptsReserved--;
        updatedAt = now;
    }

    public void releaseOneToRevoked(Instant now) {
        attemptsReserved--;
        attemptsRevoked++;
        updatedAt = now;
    }

    public void revoke(int attempts, Instant now) {
        attemptsRevoked += attempts;
        updatedAt = now;
    }
}
