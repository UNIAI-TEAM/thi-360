package vn.unicom.thi360.questionbank.domain.importing;

import java.util.UUID;

/**
 * Nút kiến thức đã nhận diện của một dòng nhập (mirror {@code KnowledgeRef} của api/ web):
 * đúng một nút {@code primary} khi có ít nhất một nút — nút đầu tiên trong ô.
 */
public record ImportKnowledgeRef(UUID nodeId, String code, boolean primary) {}
