# Ảnh tham chiếu công thức của web (`web-renders`)

Thư mục này chứa ảnh PNG tham chiếu của web cho mọi dòng `block` có `katexRenders = true` và có `sheetKey` trong `contracts/mobile/rich-text/formulas.json` (225 mã nhóm của danh mục và tờ `repo-sample`, tổng 226 thư mục con).

## Môi trường chụp

- **Máy chụp:** máy chính (Windows).
- **Trình duyệt:** Chromium đi kèm Playwright, project `chromium` dùng cấu hình `"Desktop Chrome"` (`e2e/formula-sheets.config.ts`).
- **Thông số hiển thị:** tỉ lệ điểm ảnh `1` (`deviceScaleFactor: 1`), bề rộng khối công thức `390px`, chế độ sáng (nền `#ffffff`, chữ `#11181c` theo `DESIGN.md`), CSS của `katex` lấy từ gói `katex` của `packages/ui`.
- **Phông dự phòng:** các ký tự không có trong bộ phông KaTeX (ví dụ chữ tiếng Việt trong `\text{…}`) do trình duyệt lấy từ phông dự phòng của hệ điều hành Windows tại máy chụp.

## Lệnh chụp lại (Plan A5)

1. Sinh lại `web-renders/manifest.json` và 226 trang HTML tờ ảnh (nằm trong `e2e/test-results/formula-sheets/html/`, không commit):

```bash
RICH_TEXT_REFERENCE_SHEETS=1 pnpm --filter @thi360/ui test src/rich-text/__tests__/reference-set.test.ts
```

2. Chụp lại toàn bộ ảnh PNG bằng Playwright (không cần backend):

```bash
pnpm --filter @thi360/e2e exec playwright test -c formula-sheets.config.ts
```

## Quy tắc chụp lại

Chỉ chụp lại khi `docs/specs/fixtures/formula-catalogue.json`, các mẫu công thức có sẵn trong repo hoặc phiên bản `katex` của `packages/ui` thay đổi. Khi đó các phép kiểm trùng danh mục (`matchesCatalogue`), ghim phiên bản KaTeX (`katexVersionPinned`) và mã băm HTML (`webRendersHashCurrent`) cùng báo đỏ; không so từng điểm ảnh giữa các máy hoặc sau khi hệ điều hành / Chromium đổi phông dự phòng.
