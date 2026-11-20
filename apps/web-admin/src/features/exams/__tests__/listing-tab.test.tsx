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

const TAB_NAMES = ["Thông tin chung", "Cấu trúc và ma trận", "Thông tin bán", "Kiểm tra và xem trước", "Phiên bản và công bố"];

async function tabs(pathname: string, options?: Parameters<typeof renderWithProviders>[1]) {
  nav.pathname = pathname;
  renderWithProviders(<ExamEditorLayout examId={EXAM_ID}>nội dung trang con</ExamEditorLayout>, options);
  const bar = await screen.findByRole("navigation", { name: options?.locale === "en" ? "Exam sections" : "Các phần của bài thi" });
  return within(bar).getAllByRole("link");
}

describe("tab Thông tin bán: vị trí và địa chỉ", () => {
  it("nằm giữa Cấu trúc và Kiểm tra, mở được bằng /exams/[id]/listing", async () => {
    const links = await tabs(`/exams/${EXAM_ID}/listing`);

    expect(links.map((link) => link.textContent)).toEqual(TAB_NAMES);
    const listing = links[2]!;
    expect(listing).toHaveAttribute("href", `/exams/${EXAM_ID}/listing`);
    expect(listing).toHaveAttribute("aria-current", "page");
    expect(links.filter((link) => link.getAttribute("aria-current") === "page")).toEqual([listing]);
    expect(screen.getByText("nội dung trang con")).toBeInTheDocument();
  });

  it("tab cũ vẫn mở đúng và giữ địa chỉ cũ", async () => {
    const links = await tabs(`/exams/${EXAM_ID}/structure`);
    expect(links.map((link) => link.getAttribute("href"))).toEqual([
      `/exams/${EXAM_ID}`,
      `/exams/${EXAM_ID}/structure`,
      `/exams/${EXAM_ID}/listing`,
      `/exams/${EXAM_ID}/review`,
      `/exams/${EXAM_ID}/versions`,
    ]);
    expect(links[1]).toHaveAttribute("aria-current", "page");
  });

  it("tên tab theo ngôn ngữ: tiếng Anh không lẫn tiếng Việt", async () => {
    const links = await tabs(`/exams/${EXAM_ID}/listing`, { locale: "en", messages: en });
    expect(links.map((link) => link.textContent)).toEqual([
      "Details",
      "Structure and matrix",
      "Listing",
      "Check and preview",
      "Versions and publishing",
    ]);
  });

  it("danh sách tab và hàm đường dẫn", () => {
    expect([...EXAM_EDITOR_TABS]).toEqual(["general", "structure", "listing", "review", "versions"]);
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
