"use client";

/**
 * Chỗ giữ của mục `word` trong registry (`INSERT_REGISTRY` đòi mọi mục có `load`). Mục này không hiện ở thanh công
 * cụ lẫn menu `/` (`showIn` luôn false), nên hộp này không bao giờ được mở. Hộp "Dán từ Word" thật nằm ở
 * `word-paste-dialog.tsx`, do form soạn câu hỏi mở (`features/questions/components/question-editor-form.tsx`).
 */
export default function WordInsertDialog() {
  return null;
}
