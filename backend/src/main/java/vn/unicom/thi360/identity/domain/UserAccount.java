package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_account", schema = "identity")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String username;

    @Column(name = "username_normalized", nullable = false)
    private String usernameNormalized;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String email;

    @Column(name = "email_normalized")
    private String emailNormalized;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    private String phone;

    @Column(nullable = false, columnDefinition = "smallint")
    private UserSource source;

    @Column(nullable = false, columnDefinition = "smallint")
    private UserStatus status;

    @Column(name = "inactive_reason")
    private String inactiveReason;

    @Column(name = "inactivated_at")
    private Instant inactivatedAt;

    @Column(name = "inactivated_by")
    private UUID inactivatedBy;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "delete_reason")
    private String deleteReason;

    @Column(name = "provisioning_role_ids")
    private String provisioningRoleIds;

    @Column(name = "idp_cleanup_pending", nullable = false)
    private boolean idpCleanupPending;
    @Column(name = "idp_enabled_sync_pending", nullable = false)
    private boolean idpEnabledSyncPending;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    protected UserAccount() {
    }

    public UserAccount(
            UUID id,
            String code,
            String username,
            String fullName,
            String email,
            String phone,
            UserSource source,
            UserStatus status,
            boolean mustChangePassword,
            Instant now,
            UUID actorId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null").trim();
        this.usernameNormalized = Normalizer.normalize(this.username);
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null").trim();
        this.email = email == null || email.isBlank() ? null : email.trim();
        this.emailNormalized = Normalizer.normalize(this.email);
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.mustChangePassword = mustChangePassword;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void markLoggedIn(Instant now) {
        this.lastLoginAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public void clearMustChangePassword(Instant now, UUID actorId) {
        this.mustChangePassword = false;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public boolean updateProfile(
            String fullName,
            String email,
            String phone,
            Instant now,
            UUID actorId) {
        boolean changed = false;
        if (fullName != null) {
            String trimmedFullName = fullName.trim();
            if (!Objects.equals(this.fullName, trimmedFullName)) {
                this.fullName = trimmedFullName;
                changed = true;
            }
        }
        if (email != null) {
            String trimmedEmail = email.isBlank() ? null : email.trim();
            String normalizedEmail = Normalizer.normalize(trimmedEmail);
            if (!Objects.equals(this.email, trimmedEmail)
                    || !Objects.equals(this.emailNormalized, normalizedEmail)) {
                if (!Objects.equals(this.emailNormalized, normalizedEmail)) {
                    this.emailVerifiedAt = null;
                }
                this.email = trimmedEmail;
                this.emailNormalized = normalizedEmail;
                changed = true;
            }
        }
        if (phone != null) {
            String trimmedPhone = phone.isBlank() ? null : phone.trim();
            if (!Objects.equals(this.phone, trimmedPhone)) {
                this.phone = trimmedPhone;
                changed = true;
            }
        }
        if (changed) {
            this.updatedAt = Objects.requireNonNull(now, "now must not be null");
            this.updatedBy = actorId;
        }
        return changed;
    }

    public void deactivate(String reason, Instant now, UUID actorId) {
        this.status = UserStatus.INACTIVE;
        this.inactiveReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.inactivatedAt = Objects.requireNonNull(now, "now must not be null");
        this.inactivatedBy = actorId;
        this.idpEnabledSyncPending = false;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void activate(Instant now, UUID actorId) {
        this.status = UserStatus.ACTIVE;
        this.inactiveReason = null;
        this.inactivatedAt = null;
        this.inactivatedBy = null;
        this.idpEnabledSyncPending = false;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public void updateMustChangePassword(boolean mustChangePassword, Instant now, UUID actorId) {
        this.mustChangePassword = mustChangePassword;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedBy = actorId;
    }

    public void softDelete(String reason, Instant now, UUID actorId) {
        this.status = UserStatus.INACTIVE;
        this.deletedAt = Objects.requireNonNull(now, "now must not be null");
        this.deletedBy = actorId;
        this.deleteReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.idpCleanupPending = true;
        this.idpEnabledSyncPending = false;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void setProvisioningRoleIds(String provisioningRoleIds) {
        this.provisioningRoleIds = provisioningRoleIds == null || provisioningRoleIds.isBlank()
                ? null
                : provisioningRoleIds.trim();
    }

    public void markIdpCleanupPending(boolean pending, Instant now) {
        this.idpCleanupPending = pending;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPhone() {
        return phone;
    }

    public UserSource getSource() {
        return source;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getInactiveReason() {
        return inactiveReason;
    }

    public Instant getInactivatedAt() {
        return inactivatedAt;
    }

    public UUID getInactivatedBy() {
        return inactivatedBy;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public String getDeleteReason() {
        return deleteReason;
    }
    public String getProvisioningRoleIds() {
        return provisioningRoleIds;
    }

    public boolean isIdpCleanupPending() {
        return idpCleanupPending;
    }
    public boolean isIdpEnabledSyncPending() {
        return idpEnabledSyncPending;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Long getVersion() {
        return version;
    }
}
