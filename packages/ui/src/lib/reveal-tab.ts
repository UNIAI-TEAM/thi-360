/**
 * Cuộn riêng thanh tab (không cuộn trang) để tab đang chọn hiện trọn, kèm một phần tab kế bên: bấm vào một tab
 * đang lộ một nửa thì tab sau nó cũng ló ra, người dùng chuột không có bánh xe ngang vẫn tới được mọi tab.
 * Tự tính `scrollLeft` thay cho `scrollIntoView`: trình duyệt chưa hỗ trợ `container: "nearest"` sẽ cuộn cả trang.
 */
export function revealTab(list: HTMLElement, tab: HTMLElement) {
  const peek = tab.offsetHeight;
  const listBox = list.getBoundingClientRect();
  const tabBox = tab.getBoundingClientRect();
  const hiddenAtStart = tabBox.left - peek - listBox.left;
  const hiddenAtEnd = tabBox.right + peek - listBox.right;
  if (hiddenAtStart < 0) list.scrollLeft += hiddenAtStart;
  else if (hiddenAtEnd > 0) list.scrollLeft += hiddenAtEnd;
}

/**
 * Giữ tab đang chọn trong tầm nhìn của thanh cuộn `list`: cuộn một lần ngay (tab mặc định có thể nằm ngoài phần
 * đang thấy) rồi mỗi khi thuộc tính `attribute` của một tab đổi. Trả hàm hủy theo dõi.
 */
export function followActiveTab(list: HTMLElement, activeSelector: string, attribute: string): () => void {
  const reveal = () => {
    const active = list.querySelector<HTMLElement>(activeSelector);
    if (active) revealTab(list, active);
  };
  reveal();
  if (typeof MutationObserver === "undefined") return () => {};
  const observer = new MutationObserver(reveal);
  observer.observe(list, { attributes: true, attributeFilter: [attribute], childList: true, subtree: true });
  return () => observer.disconnect();
}
