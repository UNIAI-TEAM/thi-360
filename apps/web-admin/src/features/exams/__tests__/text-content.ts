/**
 * Bộ khớp theo `textContent` của phần tử: tên do mẫu đặt nằm trong `<span data-exam-content>` nên câu hiện trên màn bị
 * tách thành nhiều nút chữ, và `getByText("<cả câu>")` mặc định không thấy. Khớp phần tử sâu nhất có đủ chữ của câu.
 */
export function byTextContent(expected: string | RegExp) {
  const normalize = (value: string | null) => (value ?? "").replace(/\s+/g, " ").trim();
  const matches = (value: string) => (typeof expected === "string" ? value === expected : expected.test(value));
  return (_content: string, element: Element | null): boolean => {
    if (!element || element.tagName === "SCRIPT" || element.tagName === "STYLE") return false;
    if (!matches(normalize(element.textContent))) return false;
    return !Array.from(element.children).some((child) => matches(normalize(child.textContent)));
  };
}
