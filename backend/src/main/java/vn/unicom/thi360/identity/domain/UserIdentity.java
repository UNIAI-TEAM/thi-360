package vn.unicom.thi360.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "user_identity", schema = "identity")
public class UserIdentity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "smallint")
    private IdentityProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "email_at_link")
    private String emailAtLink;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Transient
    private boolean isNew = true;

    protected UserIdentity() {
    }

    public UserIdentity(
            UUID id,
            UUID userId,
            IdentityProvider provider,
            String providerSubject,
            String emailAtLink,
            Instant linkedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.providerSubject = Objects.requireNonNull(providerSubject, "providerSubject must not be null");
        this.emailAtLink = emailAtLink;
        this.linkedAt = Objects.requireNonNull(linkedAt, "linkedAt must not be null");
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getUserId() {
        return userId;
    }

    public IdentityProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getEmailAtLink() {
        return emailAtLink;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
