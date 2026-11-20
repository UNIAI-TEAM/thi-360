package vn.unicom.thi360.commerce.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một dòng bài thi của đơn hàng (TM 8.1, 16.3). Đơn mua bài lẻ có 1 dòng; cấp quyền chạy theo từng dòng.
 */
@Entity
@Table(name = "purchase_order_item", schema = "commerce")
public class PurchaseOrderItem {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "exam_version_no", nullable = false, updatable = false)
    private int examVersionNo;

    @Column(name = "exam_code", nullable = false, updatable = false)
    private String examCode;

    @Column(name = "exam_name", nullable = false, updatable = false)
    private String examName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "option_codes", nullable = false, updatable = false, columnDefinition = "text[]")
    private String[] optionCodes;

    @Column(name = "attempts_granted", nullable = false, updatable = false)
    private int attemptsGranted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> benefits;

    @Column(name = "result_view", nullable = false, updatable = false)
    private String resultView;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    protected PurchaseOrderItem() {
    }

    public PurchaseOrderItem(
            UUID id,
            UUID orderId,
            UUID examId,
            int examVersionNo,
            String examCode,
            String examName,
            List<String> optionCodes,
            int attemptsGranted,
            Map<String, Object> benefits,
            String resultView,
            int position) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.examId = Objects.requireNonNull(examId, "examId must not be null");
        this.examVersionNo = examVersionNo;
        this.examCode = Objects.requireNonNull(examCode, "examCode must not be null");
        this.examName = Objects.requireNonNull(examName, "examName must not be null");
        this.optionCodes = optionCodes == null ? new String[0] : optionCodes.toArray(String[]::new);
        this.attemptsGranted = attemptsGranted;
        this.benefits = PurchaseOrder.copyNonNullEntries(benefits);
        this.resultView = Objects.requireNonNull(resultView, "resultView must not be null");
        this.position = position;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getExamId() { return examId; }
    public int getExamVersionNo() { return examVersionNo; }
    public String getExamCode() { return examCode; }
    public String getExamName() { return examName; }
    public List<String> getOptionCodes() { return List.of(optionCodes); }
    public int getAttemptsGranted() { return attemptsGranted; }
    public Map<String, Object> getBenefits() { return benefits; }
    public String getResultView() { return resultView; }
    public int getPosition() { return position; }
}
