package vn.unicom.thi360.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IdempotencyKeyId implements Serializable {

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "key", nullable = false)
    private String key;

    protected IdempotencyKeyId() {
    }

    public IdempotencyKeyId(String scope, String key) {
        this.scope = scope;
        this.key = key;
    }

    public String getScope() {
        return scope;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKeyId that)) return false;
        return Objects.equals(scope, that.scope) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, key);
    }
}
