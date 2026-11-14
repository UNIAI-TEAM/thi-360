package vn.unicom.thi360.classroom.domain;

public enum ClassMemberHistoryAction {
    JOIN((short) 1),
    LEAVE((short) 2),
    TRANSFER_OUT((short) 3),
    TRANSFER_IN((short) 4);

    private final short code;

    ClassMemberHistoryAction(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static ClassMemberHistoryAction fromCode(short code) {
        for (ClassMemberHistoryAction value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown ClassMemberHistoryAction code: " + code);
    }
}
