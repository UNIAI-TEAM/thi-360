/**
 * Phần mà mọi nhóm của mọi môn đều tạm bỏ qua (SP2E Ngữ văn Phần II · Viết, K7, K15): số câu của phần và của môn là 0 theo
 * thiết kế, nên màn tạo bài không có ô nào để nhập (F5) và màn cấu trúc không kiểm số câu, không so tổng nhóm. Phần có cả
 * nhóm thường lẫn nhóm tạm bỏ qua không thuộc loại này. Một quy tắc cho hai màn; mỗi màn chỉ đếm nhóm theo dữ liệu của mình
 * (mẫu: `omittedBlocks`; cấu trúc: `omitted`).
 */
export function isAllOmittedPart(sections: ReadonlyArray<{ blockCount: number; omittedCount: number }>): boolean {
  return sections.length > 0 && sections.every((section) => section.omittedCount > 0 && section.omittedCount === section.blockCount);
}
