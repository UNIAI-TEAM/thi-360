import type { KnowledgeNode, KnowledgeRef } from "./types";

/** Duyệt cây theo chiều sâu, kèm đường dẫn từ gốc (không gồm chính nút). */
export function walkKnowledge(
  nodes: readonly KnowledgeNode[],
  visit: (node: KnowledgeNode, ancestors: readonly KnowledgeNode[]) => void,
  ancestors: readonly KnowledgeNode[] = [],
): void {
  for (const node of nodes) {
    visit(node, ancestors);
    walkKnowledge(node.children, visit, [...ancestors, node]);
  }
}

export interface KnowledgeIndexEntry {
  node: KnowledgeNode;
  ancestors: readonly KnowledgeNode[];
}

export function indexKnowledge(nodes: readonly KnowledgeNode[]): Map<string, KnowledgeIndexEntry> {
  const index = new Map<string, KnowledgeIndexEntry>();
  walkKnowledge(nodes, (node, ancestors) => index.set(node.id, { node, ancestors }));
  return index;
}

/** Đường dẫn hiển thị "Chương › Bài" của một nút (bỏ nút môn). */
export function knowledgePath(entry: KnowledgeIndexEntry): string {
  return entry.ancestors
    .filter((ancestor) => ancestor.level !== "SUBJECT")
    .map((ancestor) => ancestor.name)
    .join(" › ");
}

/**
 * Tên hiển thị của một mục kiến thức đã gắn câu hỏi: lấy từ cây tham chiếu (đã theo ngôn ngữ đang xem) thay vì
 * `ref.name` của API câu hỏi — tên đó được lưu khi gắn, không đổi ngôn ngữ theo phiên (F-01). Chỉ dùng `ref.name`
 * khi nút không còn trong cây tham chiếu (kiến thức đã xóa ngang hàng).
 */
export function knowledgeName(index: ReadonlyMap<string, KnowledgeIndexEntry>, ref: KnowledgeRef): string {
  return index.get(ref.nodeId)?.node.name ?? ref.name;
}

/** Số nút cấp Kiến thức đang hoạt động bên dưới một nút. */
export function countSelectableLeaves(node: KnowledgeNode): number {
  if (node.level === "KNOWLEDGE") return node.active ? 1 : 0;
  return node.children.reduce((sum, child) => sum + countSelectableLeaves(child), 0);
}

/** Lọc cây theo môn, lớp và chữ tìm (mã hoặc tên); giữ nhánh có nút khớp. */
export function filterKnowledgeTree(
  nodes: readonly KnowledgeNode[],
  filter: { subjectCode?: string | null; gradeCode?: string | null; query?: string },
): KnowledgeNode[] {
  const query = filter.query?.trim().toLocaleLowerCase("vi") ?? "";
  const matches = (node: KnowledgeNode) =>
    !query ||
    node.code.toLocaleLowerCase("vi").includes(query) ||
    node.name.toLocaleLowerCase("vi").includes(query);
  const prune = (node: KnowledgeNode): KnowledgeNode | null => {
    if (filter.subjectCode && node.subjectCode !== filter.subjectCode) return null;
    if (filter.gradeCode && node.gradeCode !== null && node.gradeCode !== filter.gradeCode) return null;
    if (node.level === "KNOWLEDGE") return node.active && matches(node) ? node : null;
    if (matches(node) && query) {
      const all = node.children.map(prune).filter((child): child is KnowledgeNode => child !== null);
      return { ...node, children: all.length ? all : node.children };
    }
    const children = node.children.map(prune).filter((child): child is KnowledgeNode => child !== null);
    return children.length ? { ...node, children } : null;
  };
  return nodes.map(prune).filter((node): node is KnowledgeNode => node !== null);
}

/**
 * Thu cây theo giới hạn của nơi gọi: chỉ giữ nút nằm trong `within` (hoặc dưới nó) và bỏ nút thuộc `exclude` (cùng nhánh
 * con). Tổ tiên của nút trong `within` vẫn hiện để dẫn đường nhưng nằm trong `pathOnly`, không tick được.
 */
export function restrictKnowledgeTree(
  nodes: readonly KnowledgeNode[],
  restrict: { within?: readonly string[] | null; exclude?: readonly string[] | null },
): { tree: KnowledgeNode[]; pathOnly: ReadonlySet<string> } {
  const within = restrict.within?.length ? new Set(restrict.within) : null;
  const exclude = new Set(restrict.exclude ?? []);
  const pathOnly = new Set<string>();
  if (!within && exclude.size === 0) return { tree: [...nodes], pathOnly };
  const prune = (node: KnowledgeNode, inside: boolean): KnowledgeNode | null => {
    if (exclude.has(node.id)) return null;
    const nowInside = inside || !within || within.has(node.id);
    const children = node.children.map((child) => prune(child, nowInside)).filter((child): child is KnowledgeNode => child !== null);
    if (nowInside) return children.length === node.children.length ? node : { ...node, children };
    if (children.length === 0) return null;
    pathOnly.add(node.id);
    return { ...node, children };
  };
  const tree = nodes.map((node) => prune(node, false)).filter((node): node is KnowledgeNode => node !== null);
  return { tree, pathOnly };
}

/** Bảo đảm đúng một kiến thức chính khi danh sách có mục: giữ mục đang chính, không có thì lấy mục đầu. */
export function normalizePrimary(refs: readonly KnowledgeRef[]): KnowledgeRef[] {
  if (refs.length === 0) return [];
  const primaryIndex = Math.max(
    0,
    refs.findIndex((ref) => ref.primary),
  );
  return refs.map((ref, index) => ({ ...ref, primary: index === primaryIndex }));
}
