import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { EXAM_EDITOR_TABS, examTabFromPathname, examTabHref } from "../editor-tabs";
import { ExamEditorLayout } from "../components/exam-editor-layout";
import { EXAM_ID, EXAM_PATH, examDetailWire, FakeServer } from "./fake-exams-server";

const nav = vi.hoisted(() => ({ pathname: "/" }));
vi.mock("next/navigation", () => ({
  usePathname: () => nav.pathname,
  useSearchParams: () => new URLSearchParams(),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

beforeEach(() => {
  new FakeServer().on("GET", EXAM_PATH, { status: 200, body: examDetailWire() }).install();
});

const TAB_NAMES = [
  "1. Thông tin chung",
  "2. Cấu trúc và ma trận",
  "3. Thông tin bán",
  "4. Kiểm tra, xem trước và công bố",
];

async function tabs(pathname: string, options?: Parameters<typeof renderWithProviders>[1]) {
  nav.pathname = pathname;
  renderWithProviders(<ExamEditorLayout examId={EXAM_ID}>nội dung trang con</ExamEditorLayout>, options);
  const bar = await screen.findByRole("navigation", { name: options?.locale === "en" ? "Exam sections" : "Các phần của bài thi" });
  return within(bar).getAllByRole("link");
}

describe("tab Thông tin bán: vị trí và địa chỉ", () => {
  it("nằm giữa Cấu trúc và Kiểm tra, mở được bằng /exams/[id]/listing; Lịch sử phiên bản nằm ở đầu trang", async () => {
    const links = await tabs(`/exams/${EXAM_ID}/listing`);

    expect(links.map((link) => link.textContent)).toEqual(TAB_NAMES);
    const listing = links[2]!;
    expect(listing).toHaveAttribute("href", `/exams/${EXAM_ID}/listing`);
    expect(listing).toHaveAttribute("aria-current", "page");
    expect(links.filter((link) => link.getAttribute("aria-current") === "page")).toEqual([listing]);
    const versionsLink = screen.getByRole("link", { name: "Lịch sử phiên bản" });
    expect(versionsLink).toHaveAttribute("href", `/exams/${EXAM_ID}/versions`);
    // Nút "Lịch sử phiên bản" luôn hiển thị ở đầu trang kể cả khi trang con không dùng portal `ExamEditorActions`.
    expect(versionsLink.closest("header")?.className ?? "").not.toContain(":empty");
    expect(screen.getByText("nội dung trang con")).toBeInTheDocument();
  });

  it("4 bước chính giữ địa chỉ cũ, Lịch sử phiên bản vẫn mở /versions", async () => {
    const links = await tabs(`/exams/${EXAM_ID}/structure`);
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      `/exams/${EXAM_ID}`,
      `/exams/${EXAM_ID}/structure`,
      `/exams/${EXAM_ID}/listing`,
      `/exams/${EXAM_ID}/review`,
    ]);
    expect(links[1]).toHaveAttribute("aria-current", "page");
  });

  it("tên tab theo ngôn ngữ: tiếng Anh không lẫn tiếng Việt", async () => {
    const links = await tabs(`/exams/${EXAM_ID}/listing`, { locale: "en", messages: en });
    expect(links.map((link) => link.textContent)).toEqual([
      "1. General information",
      "2. Structure and matrix",
      "3. Listing",
      "4. Check, preview and publish",
    ]);
    expect(screen.getByRole("link", { name: "Version history" })).toHaveAttribute("href", `/exams/${EXAM_ID}/versions`);
  });

  it("danh sách tab và hàm đường dẫn", () => {
    expect([...EXAM_EDITOR_TABS]).toEqual(["general", "structure", "listing", "review"]);
    expect(examTabHref("a b", "listing")).toBe("/exams/a%20b/listing");
    expect(examTabHref("x", "general")).toBe("/exams/x");
    expect(examTabFromPathname("/exams/x/listing")).toBe("listing");
    expect(examTabFromPathname("/exams/x/listing/")).toBe("listing");
    for (const tab of ["structure", "review", "versions"] as const) {
      expect(examTabFromPathname(`/exams/x/${tab}`)).toBe(tab);
    }
    expect(examTabFromPathname("/exams/x")).toBe("general");
    expect(examTabFromPathname("/exams/x/unknown")).toBe("general");
  });
});
