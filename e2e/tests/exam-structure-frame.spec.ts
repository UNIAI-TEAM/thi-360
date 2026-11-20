import { expect, test, type APIRequestContext, type Request } from "@playwright/test";
import { ADMIN_URL, adminApi, loginAdmin, RUN_ID } from "./helpers/editor-qa";

/**
 * Bước 2 (khung bốn vùng, R-05): sửa một phần, chuyển sang phần khác thì hiện hộp thoại ba nút; "Bỏ thay đổi" khôi phục
 * giá trị đã lưu. Chạy trên bài HSA Toàn bài tạo bằng API (mẫu cấu trúc nạp sẵn).
 */
test("sửa phần rồi chuyển phần: hộp thoại ba nút, Bỏ thay đổi trả về giá trị đã lưu", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: `HSA-F-E2E-${RUN_ID}`.toUpperCase(), name: `HSA khung bước 2 ${RUN_ID}`, kind: "HSA", scope: "FULL" },
  });
  expect(created.status(), await created.text()).toBe(201);
  const { id } = (await created.json()) as { id: string };

  await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
  const list = page.getByRole("navigation", { name: "Cấu trúc" });
  await expect(list).toBeVisible();

  // Mở Phần 1 (chưa có thay đổi nên không hỏi), sửa điểm tối đa của phần. Nút phần là nút đầu của mỗi mục mức trên:
  // chọn theo cấu trúc vùng danh sách, không theo tên (tên phần lấy từ mẫu, không còn tiền tố "Phần N"). Mục đầu
  // danh sách là "Tổng quan bài" (FE-3b), không phải phần.
  const parts = list.locator(":scope > ol > li:not(:first-child) > button[data-list-item]");
  await parts.nth(0).click();
  const points = page.getByRole("textbox", { name: /Điểm tối đa của phần/ });
  await expect(points).toBeVisible();
  const saved = await points.inputValue();
  await points.fill(saved === "77" ? "78" : "77");
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();

  // Chuyển sang Phần 2: hộp thoại ba nút.
  await parts.nth(1).click();
  const dialog = page.getByRole("dialog", { name: /^Lưu thay đổi của/ });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Ở lại" })).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Lưu và chuyển" })).toBeVisible();

  // Bỏ thay đổi: sang Phần 2, rồi về Phần 1 thì ô điểm trở lại giá trị đã lưu.
  await dialog.getByRole("button", { name: "Bỏ thay đổi" }).click();
  await expect(dialog).toBeHidden();
  await parts.nth(0).click();
  await expect(page.getByRole("textbox", { name: /Điểm tối đa của phần/ })).toHaveValue(saved);
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

/**
 * `?block=` đi qua `navigation.ts` (FTM R-15): khi mọi dạng biểu mẫu theo môn đang tắt, đường dẫn tới một nhóm vẫn mở màn nhóm đầy đủ
 * như trước, không có dòng chữ "thiết lập đầy đủ". Bài HSA Toàn bài tạo bằng API; id nhóm lấy từ cấu trúc đã lưu.
 */
test("?block= mở màn nhóm đầy đủ khi biểu mẫu theo môn đang tắt", async ({ context, page }) => {
  await loginAdmin(context, "vi");
  const admin = await adminApi();
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { code: `HSA-B-E2E-${RUN_ID}`.toUpperCase(), name: `HSA nhóm ${RUN_ID}`, kind: "HSA", scope: "FULL" },
  });
  expect(created.status(), await created.text()).toBe(201);
  const { id } = (await created.json()) as { id: string };
  const structure = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`);
  expect(structure.status(), await structure.text()).toBe(200);
  const { parts } = (await structure.json()) as { parts: Array<{ sections?: Array<{ blocks?: Array<{ id: string }> }> }> };
  const blockId = parts.flatMap((part) => part.sections ?? []).flatMap((section) => section.blocks ?? [])[0]?.id;
  expect(blockId).toBeTruthy();

  await page.goto(`${ADMIN_URL}/exams/${id}/structure?block=${blockId}`);
  await expect(page.getByRole("textbox", { name: "Tên nhóm" })).toBeVisible();
  await expect(page.getByTestId("full-form-notice")).toHaveCount(0);
});

/* ---------- FTM AC 6 / plan B4: wire của PUT khi lưu trên stack thật ---------- */

type Wire = Record<string, unknown>;

/**
 * Loại trừ có tên của wire (plan B4). Hai nhóm đầu là của `defaultFormSaveIsIdentity` (jsdom); hai nhóm sau chỉ lộ ra trên stack
 * thật, do `toApiParts` (api-mapping.ts, không sửa trong CR-1) làm hôm nay:
 *  1. `null`/`undefined` (thiếu trường ≡ null);
 *  2. `tagCodes: []`, `answerTypeLocked: false` mà `mapBlock` điền khi máy chủ bỏ trống;
 *  3. mảng rỗng `[]` ≡ thiếu trường: máy chủ trả `[]`, `toApiParts` không gửi trường rỗng;
 *  4. `positionRange`: trường dẫn xuất chỉ có ở GET, máy chủ tính lại từ thứ tự khối, không nhận trong `PUT`.
 */
const MAPPING_DEFAULTED_FIELDS: Readonly<Record<string, unknown>> = { tagCodes: [], answerTypeLocked: false };
const DERIVED_READONLY_FIELDS: readonly string[] = ["positionRange"];

function withoutMappingDefaults(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(withoutMappingDefaults);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.entries(value as Wire)
      .filter(
        ([key, item]) =>
          item !== null &&
          item !== undefined &&
          !(Array.isArray(item) && item.length === 0) &&
          !DERIVED_READONLY_FIELDS.includes(key) &&
          !(key in MAPPING_DEFAULTED_FIELDS && JSON.stringify(item) === JSON.stringify(MAPPING_DEFAULTED_FIELDS[key])),
      )
      .map(([key, item]) => [key, withoutMappingDefaults(item)] as const),
  );
}

/** Khác biệt hai wire theo đường dẫn, sau khi bỏ các điểm loại trừ có tên (bản y như `diffWire` của jsdom). */
function diffWire(before: unknown, after: unknown, path = "$"): string[] {
  const a = withoutMappingDefaults(before);
  const b = withoutMappingDefaults(after);
  if (JSON.stringify(a) === JSON.stringify(b)) return [];
  if (Array.isArray(a) && Array.isArray(b) && a.length === b.length) {
    return a.flatMap((item, index) => diffWire(item, b[index], `${path}[${index}]`));
  }
  if (a && b && typeof a === "object" && typeof b === "object" && !Array.isArray(a) && !Array.isArray(b)) {
    const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
    return [...keys].flatMap((key) => diffWire((a as Wire)[key], (b as Wire)[key], `${path}.${key}`));
  }
  return [`${path}: ${JSON.stringify(a)} -> ${JSON.stringify(b)}`];
}

/** Đọc giá trị theo đường dẫn `$.a[2].b` mà `diffWire` in ra. */
function valueAtPath(root: unknown, path: string): unknown {
  return path
    .replace(/^\$\.?/, "")
    .replace(/\[(\d+)\]/g, ".$1")
    .split(".")
    .filter(Boolean)
    .reduce<unknown>((node, key) => (node && typeof node === "object" ? (node as Wire)[key] : undefined), root);
}

/**
 * Chênh lệch thuộc đúng đường dẫn HSA-C không (review R1-1): `.matrix.gradeEnforcement`, `.matrix.difficultyEnforcement`
 * của mục hoặc khối, và `.topicQuotas[i].enforcement` CHỈ khi chỉ tiêu đó gắn đúng một môn. `examQuotas` (đường dẫn
 * `$.examQuotas[i]...`) và chỉ tiêu chủ đề nhiều môn (H6) không bao giờ được cho phép.
 */
function hsaCEnforcementPath(expected: { parts: Wire[]; examQuotas: unknown[] }, line: string): boolean {
  const match = /^(\$.+?)\.(gradeEnforcement|difficultyEnforcement|enforcement): "REFERENCE" -> "REQUIRED"$/.exec(line);
  if (!match) return false;
  const [, base, field] = match as unknown as [string, string, string];
  if (base.endsWith(".matrix")) return true;
  if (field !== "enforcement") return false;
  const quotaMatch = /^(.*\.topicQuotas)\[(\d+)\]$/.exec(base);
  if (!quotaMatch) return false;
  const quota = valueAtPath(expected, `${quotaMatch[1]}[${quotaMatch[2]}]`) as { subjectCodes?: string[] } | undefined;
  return quota?.subjectCodes === undefined || quota.subjectCodes.length === 1;
}

async function createStructureExam(
  admin: { api: APIRequestContext; headers: Record<string, string> },
  data: Record<string, unknown>,
): Promise<string> {
  const created = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data,
  });
  expect(created.status(), await created.text()).toBe(201);
  return ((await created.json()) as { id: string }).id;
}

async function getStructureWire(
  admin: { api: APIRequestContext; headers: Record<string, string> },
  id: string,
): Promise<{ revision: number; parts: unknown[]; examQuotas?: unknown[] }> {
  const response = await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, { headers: admin.headers });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as { revision: number; parts: unknown[]; examQuotas?: unknown[] };
}

/**
 * FTM AC 6 (plan B4) trên stack thật: PUT `/structure` do giao diện gửi khi lưu giữ nguyên toàn bộ wire đã nạp (GET), trừ
 * đúng trường người dùng sửa và các loại trừ có tên của `defaultFormSaveIsIdentity` (null/undefined; `tagCodes: []`,
 * `answerTypeLocked: false` mà `api-mapping.ts` điền; chuẩn hóa HSA-C Tham khảo -> Bắt buộc chỉ ở bài HSA). Đọc lại sau lưu
 * phải khớp body đã gửi. Bốn bài: HSA FULL (có `options`, `choiceGroup`), H-SCA Anh (cặp DS), SPT môn GDKTPL (`ECON_LAW` trên
 * wire; MD vì cờ SPT tắt) và QDA FULL (MD).
 *
 * Nút "Lưu" chỉ bật khi có thay đổi chưa lưu (kiểm ở đầu mỗi bài: tắt, không có PUT nào khi mở màn), nên "lưu không sửa gì"
 * không đi qua giao diện được: mỗi bài sửa đúng một ô, "Điểm tối đa của phần" của phần đầu, như ca đầu của tệp này.
 */
test("PUT wire giữ nguyên GET đã nạp, chỉ đổi ô đã sửa (HSA FULL, H-SCA Anh, SPT GDKTPL, QDA)", async ({ context, page }) => {
  test.setTimeout(240_000);
  await loginAdmin(context, "vi");
  const admin = await adminApi();

  const cases: Array<{ name: string; data: Record<string, unknown>; hsa: boolean }> = [
    { name: "HSA FULL", data: { kind: "HSA", scope: "FULL" }, hsa: true },
    { name: "H-SCA Anh", data: { kind: "HSCA", scope: "SUBJECT", subjectCode: "ANH", durationMinutes: 60 }, hsa: false },
    { name: "SPT GDKTPL", data: { kind: "SPT", scope: "SUBJECT", subjectCode: "ECON_LAW" }, hsa: false },
    { name: "QDA FULL", data: { kind: "QDA", scope: "FULL" }, hsa: false },
  ];

  const putRequests: Request[] = [];
  page.on("request", (request) => {
    if (request.method() === "PUT" && /\/api\/v1\/admin\/exams\/[^/]+\/structure$/.test(request.url())) {
      putRequests.push(request);
    }
  });

  for (const item of cases) {
    const id = await createStructureExam(admin, {
      code: `E2E-WIRE-${item.data.kind}-${RUN_ID}`.toUpperCase().slice(0, 30),
      name: `${item.name} wire ${RUN_ID}`,
      ...item.data,
    });
    const before = await getStructureWire(admin, id);
    expect(before.parts.length, `${item.name}: không có phần`).toBeGreaterThan(0);

    putRequests.length = 0;
    await page.goto(`${ADMIN_URL}/exams/${id}/structure`);
    const list = page.getByRole("navigation", { name: "Cấu trúc" });
    await expect(list).toBeVisible();
    const saveButton = page.getByRole("button", { name: "Lưu", exact: true });
    await expect(saveButton, `${item.name}: nút Lưu bật khi chưa sửa`).toBeDisabled();
    expect(putRequests, `${item.name}: có PUT khi mở màn`).toHaveLength(0);

    // Sửa đúng một ô: điểm tối đa của phần đầu (mục thứ hai của danh sách, sau "Tổng quan bài").
    await list.locator(":scope > ol > li:not(:first-child) > button[data-list-item]").nth(0).click();
    const points = page.getByRole("textbox", { name: /Điểm tối đa của phần/ });
    await expect(points).toBeVisible();
    const edited = (await points.inputValue()) === "77" ? "78" : "77";
    await points.fill(edited);
    await expect(saveButton).toBeEnabled();
    await saveButton.click();
    await expect.poll(() => putRequests.length, { timeout: 20_000 }).toBe(1);
    await expect(page.getByText("Có thay đổi chưa lưu")).toHaveCount(0);

    const sent = putRequests[0]!.postDataJSON() as { parts: Wire[]; examQuotas?: unknown[]; expectedRevision: number };
    expect(sent.expectedRevision, `${item.name}: expectedRevision`).toBe(before.revision);

    // Kỳ vọng: wire đã nạp, chỉ khác `parts[0].maxPoints`.
    const expected = structuredClone({ parts: before.parts, examQuotas: before.examQuotas ?? [] }) as { parts: Wire[]; examQuotas: unknown[] };
    expected.parts[0]!.maxPoints = Number(edited);
    const diffs = diffWire(expected, { parts: sent.parts, examQuotas: sent.examQuotas ?? [] });
    // Chỉ cho phép đúng đường dẫn HSA-C: mức enforcement của ma trận và của chỉ tiêu chủ đề MỘT môn. Không bao giờ cho
    // `examQuotas` (chỉ tiêu Anh chờ chuyên gia, AC-56) và chỉ tiêu nhiều môn (H6), và không cho đường dẫn nào khác.
    const allowed = item.hsa ? diffs.filter((line) => !hsaCEnforcementPath(expected, line)) : diffs;
    expect(allowed, `${item.name}: PUT khác GET ngoài ô đã sửa`).toEqual([]);

    // FTM AC-56: chỉ tiêu cấp bài của Tiếng Anh (chờ chuyên gia) phải còn Tham khảo và không đổi khi lưu.
    if (item.hsa) {
      const englishQuotas = (sent.examQuotas ?? []).filter(
        (quota) => (quota as { subjectCodes?: string[] }).subjectCodes?.length === 1 && (quota as { subjectCodes?: string[] }).subjectCodes?.[0] === "ANH",
      );
      for (const quota of englishQuotas) {
        expect((quota as { enforcement?: string }).enforcement, `${item.name}: chỉ tiêu Anh đổi sang Bắt buộc`).toBe("REFERENCE");
      }
      expect(diffWire(before.examQuotas ?? [], sent.examQuotas ?? []), `${item.name}: examQuotas đổi`).toEqual([]);
    }

    const after = await getStructureWire(admin, id);
    expect(
      diffWire({ parts: sent.parts, examQuotas: sent.examQuotas ?? [] }, { parts: after.parts, examQuotas: after.examQuotas ?? [] }),
      `${item.name}: GET sau lưu khác body đã gửi`,
    ).toEqual([]);
    expect(putRequests, `${item.name}: gửi PUT nhiều lần`).toHaveLength(1);
    console.log(`[wire] ${item.name}: parts=${sent.parts.length} diffVsExpected=${JSON.stringify(diffs)}`);
  }
});
