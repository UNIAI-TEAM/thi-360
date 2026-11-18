import { expect, test } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";

test.setTimeout(180_000);

/**
 * FE-4 (UNI-2665), sinh thử (2B mục 8 và 15.4): bài QDA theo phần Khoa học, sinh thử mã QĐ02 (Hóa học – Sinh học) cho đề
 * có Hóa học ở câu 1–25 và Sinh học ở câu 26–50. Cần stack có BE-8 (`POST /exams/{id}/preview` nhận `target`), mẫu QDA
 * theo phần và đủ câu Sẵn sàng của Hóa học, Sinh học trong ngân hàng mẫu (R__seed_catalog_dev_data).
 */
test("Sinh thử QĐ02 của bài QDA Khoa học: Hóa học câu 1–25, Sinh học câu 26–50", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: {
      code: `QDA-P-E2E-${RUN_ID}`.toUpperCase(),
      name: `QDA Khoa học sinh thử ${RUN_ID}`,
      kind: "QDA",
      scope: "PART",
      partCode: "SCIENCE",
    },
  });
  expect(created.status(), await created.text()).toBe(201);
  const { id } = (await created.json()) as { id: string };

  // Các đường của phần theo mã (QĐ01–QĐ05) lấy từ kết quả kiểm tra gần nhất: kiểm tra trước khi mở tab Xem trước.
  const checked = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, {
    headers: admin.headers,
    data: { scope: "EXAM" },
  });
  expect(checked.ok(), await checked.text()).toBe(true);

  await page.goto(`${ADMIN_URL}/exams/${id}/review?tab=preview`);
  const preview = page.getByRole("tabpanel", { name: "Đề xem trước" });
  await expect(preview).toBeVisible();

  // Bài có phần chọn mã: "Cả bài" khóa, sinh theo một mã.
  await expect(preview.getByText("Bài có phần chọn mã: hãy chọn một mã.")).toBeVisible();
  await preview.getByRole("combobox", { name: "Đường lựa chọn" }).click();
  await page.getByRole("option", { name: /Q[DĐ]02/ }).click();
  await preview.getByRole("button", { name: "Sinh đề xem trước" }).click();

  // Hóa học là 25 câu đầu, Sinh học 25 câu sau. Tiêu đề là từng khối ("Khối Hóa học · … · câu 1–21 · 21 câu", …): đọc dải
  // câu của mọi khối từng môn, mỗi dải nằm trong phần của môn và các dải ghép liền thành đúng dải đó (không gắn cứng cách chia khối).
  await expect(preview.getByRole("heading", { name: /^Khối Hóa học/ }).first()).toBeVisible({ timeout: 90_000 });
  for (const [subject, from, to] of [
    ["Hóa học", 1, 25],
    ["Sinh học", 26, 50],
  ] as const) {
    const headings = await preview.getByRole("heading", { name: new RegExp(`^Khối ${subject}`) }).allTextContents();
    const ranges = headings
      .map((text) => /câu (\d+)–(\d+)/.exec(text))
      .map((match, index) => {
        expect(match, `tiêu đề khối không nêu dải câu: ${headings[index]}`).not.toBeNull();
        return [Number(match![1]), Number(match![2])] as const;
      })
      .sort((a, b) => a[0] - b[0]);
    for (const [start, end] of ranges) {
      expect(start, `${subject}: khối ${start}–${end} ngoài ${from}–${to}`).toBeGreaterThanOrEqual(from);
      expect(end, `${subject}: khối ${start}–${end} ngoài ${from}–${to}`).toBeLessThanOrEqual(to);
    }
    // Phủ kín: khối đầu bắt đầu ở `from`, mỗi khối nối ngay khối trước, khối cuối kết thúc ở `to`.
    expect(ranges[0]?.[0], `${subject}: dải câu phải bắt đầu ở ${from}`).toBe(from);
    ranges.slice(1).forEach(([start], index) => expect(start, `${subject}: hở hoặc chồng giữa các khối`).toBe(ranges[index]![1] + 1));
    expect(ranges.at(-1)?.[1], `${subject}: dải câu phải kết thúc ở ${to}`).toBe(to);
  }
  await expect(preview.getByText(/Đề thử · Q[DĐ]02/)).toBeVisible();
});
