#!/usr/bin/env node
// Xuất mẫu cấu trúc bài thi đang hoạt động từ một stack dev để làm fixture khám phá của biểu mẫu theo môn (FTM, plan B8).
//
//   node scripts/export-exam-templates.mjs --api http://localhost:8178 --origin http://localhost:3279
//
// Gọi GET /api/v1/admin/exam-templates với phiên của tài khoản dev `platform`, giữ mẫu `active = true`, tách mẫu REVIEW ra
// tệp riêng. Ghi kèm SHA nguồn (HEAD của thư mục đang chạy) và ngày xuất. Không in cookie hay mật khẩu.
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function option(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const api = option("api", process.env.THI360_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");
const origin = option("origin", process.env.THI360_WEB_URL ?? "http://localhost:3000").replace(/\/+$/, "");
const outDir = resolve(root, option("out", "apps/web-admin/src/features/exams/__tests__/fixtures"));
const password = process.env.THI360_DEV_PLATFORM_PASSWORD ?? "platform-dev-only";

const login = await fetch(`${api}/api/v1/auth/login`, {
  method: "POST",
  headers: { "content-type": "application/json", origin },
  body: JSON.stringify({ identifier: "platform", password }),
});
if (!login.ok) throw new Error(`dang nhap that bai: ${login.status}`);
const cookies = login.headers
  .getSetCookie()
  .map((line) => line.split(";")[0])
  .join("; ");

const response = await fetch(`${api}/api/v1/admin/exam-templates`, { headers: { cookie: cookies } });
if (!response.ok) throw new Error(`doc mau that bai: ${response.status}`);
const all = await response.json();
const active = all.filter((template) => template.active === true).sort((a, b) => a.code.localeCompare(b.code));

const source = {
  sha: execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim(),
  exportedAt: new Date().toISOString().slice(0, 10),
  note: "Du lieu kham pha, khong thay oracle BA: ky vong viet tay trong test (FTM R-1, R-7).",
};

mkdirSync(outDir, { recursive: true });
const write = (name, templates) => {
  writeFileSync(resolve(outDir, name), `${JSON.stringify({ source, count: templates.length, templates }, null, 2)}\n`);
  console.log(`${name}: ${templates.length} mau`);
};
write("exam-templates-dot3.json", active.filter((template) => template.buildMode !== "REVIEW"));
write("exam-templates-dot3-review.json", active.filter((template) => template.buildMode === "REVIEW"));
