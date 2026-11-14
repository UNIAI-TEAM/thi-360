package vn.unicom.thi360.identity.application;

import java.util.UUID;

final class StaleBatchLeaseException extends RuntimeException {

    private final UUID batchId;
    private final UUID leaseToken;

    StaleBatchLeaseException(UUID batchId, UUID leaseToken) {
        super("Bulk import batch lease is no longer held: batchId=" + batchId + ", leaseToken=" + leaseToken);
        this.batchId = batchId;
        this.leaseToken = leaseToken;
    }

    UUID getBatchId() {
        return batchId;
    }

    UUID getLeaseToken() {
        return leaseToken;
    }
}
