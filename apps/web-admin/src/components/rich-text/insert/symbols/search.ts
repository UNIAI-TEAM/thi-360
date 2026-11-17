/**
 * Tìm ký hiệu theo tên (spec 4.4): không phân biệt hoa/thường và dấu, nên "do c", "độ C" và "DO C" như nhau; "omega" và
 * "ômêga" khác chữ gốc nhưng cùng tên ở hai ngôn ngữ nên cả hai đều có trong dữ liệu tên.
 */

/** Bỏ dấu thanh/dấu phụ và chữ hoa; chữ đ/Đ không tách được bằng NFD nên đổi riêng. */
export function foldForSearch(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\u0111/gi, "d")
    .toLowerCase();
}

/** `true` khi mọi từ của `query` nằm trong ít nhất một trong các chuỗi `haystacks` (đã gấp dấu). Truy vấn rỗng khớp hết. */
export function matchesQuery(query: string, haystacks: string[]): boolean {
  const words = foldForSearch(query).split(/\s+/).filter(Boolean);
  if (words.length === 0) return true;
  const folded = haystacks.map(foldForSearch).join("\n");
  return words.every((word) => folded.includes(word));
}
