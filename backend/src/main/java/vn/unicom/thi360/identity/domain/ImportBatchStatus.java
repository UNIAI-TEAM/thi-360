package vn.unicom.thi360.identity.domain;

public enum ImportBatchStatus {
    UPLOADED((short) 1),
    VALIDATED((short) 2),
    CONFIRMED((short) 3),
    PROCESSING((short) 4),
    COMPLETED((short) 5),
    COMPLETED_WITH_ERRORS((short) 6),
    FAILED((short) 7);

    private final short code;

    ImportBatchStatus(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED;
    }

    public static ImportBatchStatus fromCode(short code) {
        for (ImportBatchStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown ImportBatchStatus code: " + code);
    }
}
