package vn.unicom.thi360.identity.domain;

public record ImportRowRaw(
        String username,
        String passwordEnc,
        String fullName,
        String email,
        String phone,
        String classCode
) {
    public ImportRowRaw withoutPassword() {
        return new ImportRowRaw(username, null, fullName, email, phone, classCode);
    }

    @Override
    public String toString() {
        return "ImportRowRaw["
                + "username=" + username
                + ", passwordEnc=" + (passwordEnc == null ? "null" : "[REDACTED]")
                + ", fullName=" + fullName
                + ", email=" + email
                + ", phone=" + phone
                + ", classCode=" + classCode
                + ']';
    }
}
