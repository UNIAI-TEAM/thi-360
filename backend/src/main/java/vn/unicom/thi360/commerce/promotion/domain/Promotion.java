package vn.unicom.thi360.commerce.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Automatic promotion or voucher definition ({@code commerce.promotion}, TM 7.1).
 *
 * <p>{@code revision} is bumped explicitly on admin mutations after taking a row lock; redemptions update
 * {@code usedCount} and {@code overLimitCount} atomically without bumping admin {@code revision}.
 */
@Entity
@Table(name = "promotion", schema = "commerce")
public class Promotion {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private PromotionKind kind;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "code")
    private String code;

    @Column(name = "code_normalized")
    private String codeNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "max_discount_vnd")
    private Long maxDiscountVnd;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "total_limit")
    private Integer totalLimit;

    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    @Column(name = "combinable")
    private Boolean combinable;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private PromotionScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience")
    private PromotionAudienceType audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PromotionStatus status;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "over_limit_count", nullable = false)
    private int overLimitCount;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_by_username", updatable = false)
    private String createdByUsername;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_by_username")
    private String updatedByUsername;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected Promotion() {
    }

    private Promotion(UUID id, PromotionKind kind, String name, String description, String code,
            DiscountType discountType, long discountValue, Long maxDiscountVnd, Instant startsAt, Instant endsAt,
            Integer totalLimit, Integer perUserLimit, Boolean combinable, PromotionScope scope,
            PromotionAudienceType audience, PromotionStatus status, Instant now, UUID actorId, String actorUsername) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.description = description;
        this.code = code == null ? null : code.trim();
        this.codeNormalized = PromotionCodeNormalizer.normalize(code);
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountVnd = maxDiscountVnd;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.totalLimit = totalLimit;
        this.perUserLimit = perUserLimit;
        this.combinable = combinable;
        this.scope = scope;
        this.audience = audience;
        this.status = status == null ? PromotionStatus.ACTIVE : status;
        this.usedCount = 0;
        this.overLimitCount = 0;
        this.revision = 1L;
        this.createdAt = now;
        this.createdBy = actorId;
        this.createdByUsername = actorUsername;
        this.updatedAt = now;
        this.updatedBy = actorId;
        this.updatedByUsername = actorUsername;
    }

    public static Promotion auto(UUID id, String name, String description, DiscountType discountType,
            long discountValue, Long maxDiscountVnd, Instant startsAt, Instant endsAt, Integer totalLimit,
            PromotionScope scope, PromotionStatus status, Instant now, UUID actorId, String actorUsername) {
        return new Promotion(id, PromotionKind.AUTO, name, description, null, discountType, discountValue,
                maxDiscountVnd, startsAt, endsAt, totalLimit, null, null, scope, null, status, now, actorId,
                actorUsername);
    }

    public static Promotion voucher(UUID id, String name, String description, String code, DiscountType discountType,
            long discountValue, Long maxDiscountVnd, Instant startsAt, Instant endsAt, Integer totalLimit,
            Integer perUserLimit, boolean combinable, PromotionScope scope, PromotionAudienceType audience,
            PromotionStatus status, Instant now, UUID actorId, String actorUsername) {
        return new Promotion(id, PromotionKind.VOUCHER, name, description, code, discountType, discountValue,
                maxDiscountVnd, startsAt, endsAt, totalLimit, perUserLimit, combinable, scope, audience, status,
                now, actorId, actorUsername);
    }

    public UUID getId() {
        return id;
    }

    public PromotionKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }

    public String getCodeNormalized() {
        return codeNormalized;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public long getDiscountValue() {
        return discountValue;
    }

    public Long getMaxDiscountVnd() {
        return maxDiscountVnd;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Integer getTotalLimit() {
        return totalLimit;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public Boolean getCombinable() {
        return combinable;
    }

    public boolean isCombinable() {
        return Boolean.TRUE.equals(combinable);
    }

    public PromotionScope getScope() {
        return scope;
    }

    public PromotionAudienceType getAudience() {
        return audience;
    }

    public PromotionStatus getStatus() {
        return status;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public int getOverLimitCount() {
        return overLimitCount;
    }

    public long getRevision() {
        return revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public String getUpdatedByUsername() {
        return updatedByUsername;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public UUID getDeletedBy() {
        return deletedBy;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void updateMetadata(String name, String description, Instant endsAt, Integer totalLimit,
            PromotionStatus status, Instant now, UUID actorId, String actorUsername) {
        this.name = name;
        this.description = description;
        this.endsAt = endsAt;
        this.totalLimit = totalLimit;
        this.status = status;
        touch(now, actorId, actorUsername);
    }

    public void updateAllFields(String name, String description, String code, DiscountType discountType,
            long discountValue, Long maxDiscountVnd, Instant startsAt, Instant endsAt, Integer totalLimit,
            Integer perUserLimit, Boolean combinable, PromotionScope scope, PromotionAudienceType audience,
            PromotionStatus status, Instant now, UUID actorId, String actorUsername) {
        this.name = name;
        this.description = description;
        if (this.kind == PromotionKind.VOUCHER) {
            this.code = code == null ? null : code.trim();
            this.codeNormalized = PromotionCodeNormalizer.normalize(this.code);
        } else {
            this.code = null;
            this.codeNormalized = null;
        }
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountVnd = maxDiscountVnd;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.totalLimit = totalLimit;
        this.perUserLimit = perUserLimit;
        this.combinable = combinable;
        this.scope = scope;
        this.audience = audience;
        this.status = status;
        touch(now, actorId, actorUsername);
    }

    public void stop(Instant now, UUID actorId, String actorUsername) {
        this.status = PromotionStatus.STOPPED;
        touch(now, actorId, actorUsername);
    }

    public void resume(Instant now, UUID actorId, String actorUsername) {
        this.status = PromotionStatus.ACTIVE;
        touch(now, actorId, actorUsername);
    }

    public void softDelete(Instant now, UUID actorId, String actorUsername) {
        this.deletedAt = now;
        this.deletedBy = actorId;
        touch(now, actorId, actorUsername);
    }

    private void touch(Instant now, UUID actorId, String actorUsername) {
        this.updatedAt = now;
        this.updatedBy = actorId;
        this.updatedByUsername = actorUsername;
        this.revision++;
    }
}
