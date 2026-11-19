import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EXAM_ID } from "./fake-exams-server";
import { boot, installForms, openStructure } from "./subject-form-harness";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

/**
 * CR-1 AC-8 (FTM AC 59 phần điều hướng), trên `StructureEditor` thật: `?block=`, bấm lỗi kiểm tra đi qua `navigation.ts`; MD giữ đích
 * cũ (màn nhóm); MT mở môn và đặt focus vào ô; DS mở môn và đặt focus vào dòng; khung không lộ nút thêm, xóa nhóm trái dạng. Màn MT/DS ở đây là
 * màn thử đăng ký qua `form-renderers.ts`; đích compact của màn thật là phần của CR-2, CR-2H, CR-3. Ca ON: `review-step2.test.tsx`.
 */
let restore: () => void;
beforeEach(() => {
  routerMock.push.mockReset();
  restore = installForms();
});
afterEach(() => restore());

const issue = (blockId: string, sectionId: string, subjectCode: string) => ({
  key: `GROUP_ANSWER_TYPE_MISSING|${blockId}`,
  code: "GROUP_ANSWER_TYPE_MISSING",
  severity: "ERROR",
  group: "STRUCTURE",
  partId: "part-1",
  sectionId,
  subjectCode,
  blockId,
  blockName: blockId,
});

const failedCheck = (blockId: string, sectionId: string, subjectCode: string) => ({
  examId: EXAM_ID,
  status: "INVALID",
  stale: false,
  checkedAt: "2026-11-17T01:00:00Z",
  durationSeconds: 3,
  timeLimitSeconds: 30,
  issueCount: 1,
  partCount: 1,
  totalPoints: 0,
  printedQuestions: 10,
  paths: [],
  groups: [{ group: "STRUCTURE", status: "FAIL", issues: [issue(blockId, sectionId, subjectCode)] }],
});

describe("Điều hướng theo biểu mẫu (FTM R-15)", () => {
  it("MD giữ đích cũ: ?block= của môn MD mở màn nhóm, không mở màn MT/DS", async () => {
    boot();
    openStructure("?block=toan-b1");
    expect(await screen.findByRole("textbox", { name: "Tên nhóm" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /^(MT|DS) / })).toBeNull();
  });

  it("MT: ?block= của nhóm thuộc môn MT mở môn, đặt focus vào ô, không mở editor nhóm đầy đủ", async () => {
    boot();
    openStructure("?block=sec-ly-set");
    expect(await screen.findByRole("heading", { name: "MT LY" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("textbox", { name: "MT LY cau don" })).toHaveFocus());
    expect(screen.queryByRole("textbox", { name: "Tên nhóm" })).toBeNull();
  });

  it("DS: ?block= của nhóm thuộc môn DS mở môn, đặt focus vào đúng dòng", async () => {
    boot();
    openStructure("?block=van-r2");
    expect(await screen.findByRole("heading", { name: "DS VAN" })).toBeInTheDocument();
    await waitFor(() => expect(document.querySelector('[data-form-row="van-r2"]')).toContainElement(document.activeElement as HTMLElement));
    expect(screen.queryByRole("textbox", { name: "Tên nhóm" })).toBeNull();
  });

  it("bấm lỗi kiểm tra của nhóm thuộc môn MT: vào ô của MT, không mở màn nhóm", async () => {
    const user = userEvent.setup();
    boot({ checkBody: failedCheck("sec-ly-set", "sec-ly", "LY") });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { level: 2, name: /TOAN/ });
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(await screen.findByRole("heading", { name: "MT LY" })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("textbox", { name: "MT LY cau don" })).toHaveFocus());
    expect(window.location.search).toBe("?block=sec-ly-set");
  });

  it("bấm lỗi kiểm tra của nhóm thuộc môn DS: mở dòng; môn MD: mở màn nhóm như cũ", async () => {
    const user = userEvent.setup();
    boot({ checkBody: failedCheck("van-r1", "sec-van", "VAN") });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { level: 2, name: /TOAN/ });
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    await waitFor(() => expect(document.querySelector('[data-form-row="van-r1"]')).toContainElement(document.activeElement as HTMLElement));
  });

  it("bấm lỗi kiểm tra của nhóm thuộc môn MD: mở màn nhóm (đích cũ)", async () => {
    const user = userEvent.setup();
    boot({ checkBody: failedCheck("toan-b1", "sec-toan", "TOAN") });
    openStructure("?section=sec-ly");
    await screen.findByRole("heading", { name: "MT LY" });
    await user.click(screen.getByRole("button", { name: "Tiếp tục" }));
    expect(await screen.findByRole("textbox", { name: "Tên nhóm" })).toBeInTheDocument();
  });

  it("khung không lộ nút thêm, xóa, đổi thứ tự nhóm khi mở MT hoặc DS; màn MD vẫn có", async () => {
    const user = userEvent.setup();
    boot();
    openStructure("?section=sec-ly");
    await screen.findByRole("heading", { name: "MT LY" });
    expect(screen.queryByRole("button", { name: /Thêm nhóm|Xóa nhóm|Lên|Xuống/ })).toBeNull();
    await user.click(within(await screen.findByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /VAN/ }));
    await screen.findByRole("heading", { name: "DS VAN" });
    expect(screen.queryByRole("button", { name: /Thêm nhóm|Xóa nhóm|Lên|Xuống/ })).toBeNull();
    await user.click(within(screen.getByRole("navigation", { name: "Cấu trúc" })).getByRole("button", { name: /TOAN/ }));
    expect(await screen.findByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
  });

  it("dạng bật mà chưa có màn vẽ: giữ MD, không để môn trống", async () => {
    restore();
    restore = installForms({ renderers: false });
    boot();
    openStructure("?section=sec-ly");
    expect(await screen.findByRole("heading", { level: 2, name: /LY/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Thêm nhóm/ })).toBeInTheDocument();
    expect(screen.queryByTestId("full-form-notice")).toBeNull();
  });
});
