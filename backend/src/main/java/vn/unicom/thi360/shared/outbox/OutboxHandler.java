package vn.unicom.thi360.shared.outbox;

@FunctionalInterface
public interface OutboxHandler {

    void handle(OutboxEvent event) throws Exception;
}
