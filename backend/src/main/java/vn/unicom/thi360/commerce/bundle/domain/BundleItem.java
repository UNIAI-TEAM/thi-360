package vn.unicom.thi360.commerce.bundle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Member exam of a bundle working copy ({@code commerce.bundle_item}, TM 16.1). */
@Entity
@Table(name = "bundle_item", schema = "commerce")
public class BundleItem {

    @Id
    private UUID id;

    @Column(name = "bundle_id", nullable = false, updatable = false)
    private UUID bundleId;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    protected BundleItem() {
    }

    public BundleItem(UUID id, UUID bundleId, UUID examId, int position) {
        this.id = id;
        this.bundleId = bundleId;
        this.examId = examId;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public UUID getExamId() {
        return examId;
    }

    public int getPosition() {
        return position;
    }
}
